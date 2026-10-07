package net.nerdorg.minehop.replays.storage;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.replays.ReplayManager;
import net.nerdorg.minehop.util.JsonStorage;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * The replay store (version 2): one MHRP file per finished run under {@code world/MineHop_Data/replays/}, plus the run
 * log, and in memory only the runs' metadata ({@link ReplayManager.Replay} without frames, the list the leaderboard
 * tools, history and PB/WR lookups use) and a capped cache of decoded frames.
 *
 * <pre>
 * replays/store.json                 marker: present = this store is the replay store (written last by the migration)
 * replays/runs.jsonl (+ .bak)        run log (RunLog)
 * replays/{map key}/{id}.mhr         a run, written once (temp file, fsync, atomic move) and never rewritten
 * replays/{map key}/{id}.inv         its invalidation reason, if invalidated (so a rebuild keeps the status)
 * replays/{map key}/{id}.mhr.corrupt a run file that failed to decode (quarantined; that run's frames are unavailable)
 * replays/_deleted/...               runs removed by an admin purge or a map deletion (kept for manual recovery)
 * replays/migration-report.txt       what the migration from minehop_replays.json did
 * </pre>
 *
 * <p>Threads: every method is for the server thread, which owns the metadata, the cache and the load callbacks.
 * Files are written by one writer thread in submission order (a failed write is retried with backoff and its frames
 * stay in memory meanwhile) and read by one reader thread; results come back through {@code server.execute}. A
 * finish costs the server thread a column copy of its frames; encoding and writing happen on the writer.
 *
 * <p>Startup reads only the run log and lists the map folders. The folders are the ground truth: a run file the log
 * doesn't know (a crash between writing the file and the log, or a lost log) is added from its header, a known run
 * without a file is kept as history with its frames unavailable, a status file wins over the log, a complete leftover
 * temp file is finished.
 */
public final class ReplayStore {
    public static final String DIRECTORY = "MineHop_Data/replays";
    public static final String MARKER_FILE = "store.json";
    public static final String DELETED_DIRECTORY = "_deleted";
    public static final String REPORT_FILE = "migration-report.txt";
    private static final int MARKER_VERSION = 1;
    private static final long[] RETRY_DELAYS_SECONDS = {5L, 30L, 60L, 300L};
    private static final long DEFAULT_CACHE_MB = 32L;
    private static final int MAX_STATUS_BYTES = 1 << 16;

    /** DEV ONLY (ReplayStoreHarness): the next N run/status file writes fail with an IOException, like a full disk. */
    public static final AtomicInteger INJECT_WRITE_FAILURES = new AtomicInteger();

    private final MinecraftServer server;
    private final Path root;
    private final Path logFile;
    private final ScheduledThreadPoolExecutor writer;
    private final ExecutorService reader;
    private final Map<String, Entry> entries = new HashMap<>();
    private final Map<String, PendingWrite> pendingWrites = new ConcurrentHashMap<>();
    private final Set<String> deleted = ConcurrentHashMap.newKeySet();
    private final LinkedHashMap<String, ReplayFrames> cache = new LinkedHashMap<>(64, 0.75F, true);
    private final Map<String, List<Consumer<ReplayFrames>>> loading = new HashMap<>();
    private final long cacheBudget;
    private long cacheBytes;
    private volatile boolean closing;
    private final Stats stats = new Stats();

    /** What the store keeps per run besides the metadata in {@link ReplayManager.Replay}. Server thread only. */
    public static final class Entry {
        private final ReplayManager.Replay replay;
        private final String file;
        private int frames;
        // Set by the writer thread for a finish (its frames are turned into columns there).
        private volatile double[] bounds;
        private String unavailable = "";
        private String storedInvalidated;
        private String storedUuid;
        private double serverTime = Double.NaN;
        private long clientTicks = -1L;
        private int preFrames;
        private int postFrames;

        /** {@code storedInvalidated}/{@code storedUuid}: what the files on disk say (sync writes any difference). */
        private Entry(ReplayManager.Replay replay, String file, int frames, double[] bounds, String storedInvalidated, String storedUuid) {
            this.replay = replay;
            this.file = file;
            this.frames = frames;
            this.bounds = bounds;
            this.storedInvalidated = nullToEmpty(storedInvalidated);
            this.storedUuid = nullToEmpty(storedUuid);
        }

        public String file() {
            return this.file;
        }

        public int frames() {
            return this.frames;
        }

        public double[] bounds() {
            return this.bounds;
        }

        /** Why the frames can't be loaded, "" if they can. */
        public String unavailable() {
            return this.unavailable;
        }
    }

    /**
     * The frames of a run being written: the recorded frame list (a shallow copy; recorded frames are never changed)
     * until it is turned into columns, once, by whichever thread needs them first (normally the writer).
     */
    private static final class FrameSource {
        private List<ReplayManager.ReplayEntry> entries;
        private ReplayFrames frames;

        private FrameSource(ReplayFrames frames) {
            this.frames = frames;
        }

        private FrameSource(List<ReplayManager.ReplayEntry> entries) {
            this.entries = entries;
        }

        private synchronized ReplayFrames get() {
            if (this.frames == null) {
                this.frames = ReplayManager.toFrames(this.entries);
                this.entries = null;
            }
            return this.frames;
        }
    }

    /** A run file not yet on disk: its frames stay here (and loadable) until the write succeeds. */
    private record PendingWrite(String id, MhrpHeader header, FrameSource source, Path target, RunLog.Line line, Entry entry, int attempt) {
        private ReplayFrames frames() {
            return this.source.get();
        }
    }

    /** Counters for /replaytest and the logs. */
    public static final class Stats {
        public final AtomicLong runsWritten = new AtomicLong();
        public final AtomicLong bytesWritten = new AtomicLong();
        public final AtomicLong writeFailures = new AtomicLong();
        public final AtomicLong loads = new AtomicLong();
        public final AtomicLong loadFailures = new AtomicLong();
        public final AtomicLong cacheHits = new AtomicLong();
        public final AtomicLong quarantined = new AtomicLong();
        public volatile long lastFinishNanos;
        public volatile long maxFinishNanos;
    }

    /** What opening the store found (logged at startup). */
    public static final class OpenReport {
        public int logLines;
        public int badLogLines;
        public boolean logFromBackup;
        public int runs;
        public int recoveredFromHeaders;
        public int missingFiles;
        public int quarantinedFiles;
        public int statusFiles;
        public int completedTempFiles;
        public long millis;

        @Override
        public String toString() {
            return this.runs + " runs (" + this.logLines + " log lines, " + this.badLogLines + " unreadable"
                    + (this.logFromBackup ? ", log restored from runs.jsonl.bak" : "") + "; " + this.recoveredFromHeaders
                    + " recovered from file headers, " + this.completedTempFiles + " unfinished writes completed, "
                    + this.missingFiles + " without a file, " + this.quarantinedFiles + " quarantined, " + this.statusFiles
                    + " status files) in " + this.millis + " ms";
        }
    }

    private ReplayStore(MinecraftServer server, Path root) {
        this.server = server;
        this.root = root;
        this.logFile = root.resolve(RunLog.FILE);
        this.cacheBudget = Math.max(1L, Long.getLong("minehop.replayCacheMB", DEFAULT_CACHE_MB)) << 20;
        this.writer = new ScheduledThreadPoolExecutor(1, runnable -> {
            Thread thread = new Thread(runnable, "Minehop replay store writer");
            thread.setDaemon(true);
            return thread;
        });
        this.writer.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        this.reader = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "Minehop replay store reader");
            thread.setDaemon(true);
            return thread;
        });
        // Started now, not by the first finish or load (which would pay for creating the threads).
        this.writer.prestartAllCoreThreads();
        this.reader.execute(() -> { });
    }

    public static Path root(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve(DIRECTORY).toAbsolutePath().normalize();
    }

    /** True if the world uses this store (the migration has finished, or the world started without a legacy store). */
    public static boolean isActive(Path root) {
        return Files.exists(root.resolve(MARKER_FILE)) || Files.exists(root.resolve(MARKER_FILE + ".bak"));
    }

    /** Marker content (informational: the marker's presence is what counts). */
    public static final class Marker {
        public String format = "MHRP";
        public int version = MhrpCodec.VERSION;
        public long created_at;
        public String created_by = "";
        public String legacy_sha256 = "";
        public long legacy_bytes;
        public int runs;
        public long frames;
    }

    /** Opens the store on the server thread: reads the run log and lists the folders. */
    public static ReplayStore open(MinecraftServer server, Path root, OpenReport report) throws IOException {
        Files.createDirectories(root);
        ReplayStore store = new ReplayStore(server, root);
        store.load(report);
        return store;
    }

    /** An empty store over a folder the migration has filled (no scan: the migration adopts its runs itself). */
    static ReplayStore createEmpty(MinecraftServer server, Path root) throws IOException {
        Files.createDirectories(root);
        return new ReplayStore(server, root);
    }

    /** Throws the store away without writing anything (a migration that didn't switch over). */
    void discard() {
        this.closing = true;
        this.writer.shutdownNow();
        this.reader.shutdownNow();
    }

    /** Moves a run file (and its status/corrupt siblings) to _deleted on the writer thread. */
    void moveRunFilesToDeleted(String relativeFile, String why) {
        Path runFile = StorePaths.resolveRunFile(this.root, relativeFile);
        if (runFile == null) {
            return;
        }
        submitWithRetry(why + " " + relativeFile, 0, () -> moveToDeleted(this.root, runFile,
                StorePaths.sibling(runFile, StorePaths.STATUS_EXTENSION), StorePaths.sibling(runFile, StorePaths.CORRUPT_EXTENSION)));
    }

    public Path rootPath() {
        return this.root;
    }

    public Stats stats() {
        return this.stats;
    }

    /** The runs, oldest first (the list the rest of the mod uses as Minehop.replayList). */
    public List<ReplayManager.Replay> runs() {
        List<ReplayManager.Replay> runs = new ArrayList<>(this.entries.size());
        for (Entry entry : this.entries.values()) {
            runs.add(entry.replay);
        }
        runs.sort(Comparator.comparingLong((ReplayManager.Replay r) -> r.saved_at).thenComparing(r -> r.replay_id));
        return runs;
    }

    public Entry entry(ReplayManager.Replay replay) {
        if (replay == null || replay.replay_id == null) {
            return null;
        }
        Entry entry = this.entries.get(replay.replay_id);
        return entry != null && entry.replay == replay ? entry : null;
    }

    // ------------------------------------------------------------------------------------------
    // Opening
    // ------------------------------------------------------------------------------------------

    private static final class FileSet {
        private final Path directory;
        private final String base;
        private boolean run;
        private boolean corrupt;
        private boolean status;
        private boolean temp;

        private FileSet(Path directory, String base) {
            this.directory = directory;
            this.base = base;
        }

        private Path path(String extension) {
            return this.directory.resolve(this.base + extension);
        }

        private String relative() {
            return this.directory.getFileName() + "/" + this.base + StorePaths.EXTENSION;
        }
    }

    private void load(OpenReport report) {
        long start = System.nanoTime();
        RunLog.ReadResult log;
        try {
            log = RunLog.read(this.logFile);
            if (!log.existed()) {
                Path backup = this.root.resolve(RunLog.FILE + ".bak");
                if (Files.exists(backup)) {
                    log = RunLog.read(backup);
                    report.logFromBackup = true;
                }
            }
        } catch (IOException e) {
            Minehop.LOGGER.error("Could not read the replay run log {}; rebuilding the index from the run files", this.logFile, e);
            log = new RunLog.ReadResult(List.of(), 0, false, true);
        }
        report.logLines = log.lines().size();
        report.badLogLines = log.badLines();

        Map<String, RunLog.Line> runLines = new LinkedHashMap<>();
        Map<String, String> invalidations = new HashMap<>();
        Set<String> deletedIds = new HashSet<>();
        int nonRunLines = 0;
        int duplicateRunLines = 0;
        for (RunLog.Line line : log.lines()) {
            switch (line.op) {
                case RunLog.OP_RUN -> {
                    if (runLines.put(line.id, line) != null) {
                        duplicateRunLines++;
                    }
                }
                case RunLog.OP_INVALIDATE -> {
                    invalidations.put(line.id, line.invalidated);
                    nonRunLines++;
                }
                case RunLog.OP_DELETE -> {
                    deletedIds.add(line.id);
                    nonRunLines++;
                }
                default -> {
                }
            }
        }
        deletedIds.forEach(runLines::remove);

        Map<String, FileSet> files = scanFiles();
        Map<String, String> strings = new HashMap<>();

        for (RunLog.Line line : runLines.values()) {
            Path runFile = StorePaths.resolveRunFile(this.root, line.file);
            if (runFile == null) {
                Minehop.LOGGER.warn("Replay run log: ignoring run {} with an invalid file path '{}'", line.id, line.file);
                report.badLogLines++;
                continue;
            }
            ReplayManager.Replay replay = new ReplayManager.Replay(line.id, intern(strings, line.map), intern(strings, nullToEmpty(line.name)),
                    intern(strings, nullToEmpty(line.uuid)), line.time, line.saved_at, null);
            replay.ac_flags = nullToEmpty(line.ac_flags);
            replay.invalidated = nullToEmpty(line.invalidated);
            String invalidated = invalidations.get(line.id);
            if (invalidated != null && !invalidated.isBlank()) {
                replay.invalidated = invalidated;
            }
            String key = runFile.getParent().getFileName() + "/" + runFile.getFileName().toString()
                    .substring(0, runFile.getFileName().toString().length() - StorePaths.EXTENSION.length());
            FileSet set = files.remove(key);
            Entry entry = new Entry(replay, line.file, line.frames, line.bounds, "", replay.player_uuid);
            entry.serverTime = line.server_time == null ? Double.NaN : line.server_time;
            entry.clientTicks = line.client_ticks == null ? -1L : line.client_ticks;
            entry.preFrames = line.pre == null ? 0 : line.pre;
            entry.postFrames = line.post == null ? 0 : line.post;
            if (set == null || !set.run) {
                if (set != null && set.corrupt) {
                    entry.unavailable = "quarantined (corrupt file)";
                    report.quarantinedFiles++;
                } else {
                    entry.unavailable = "missing file";
                    report.missingFiles++;
                }
            }
            boolean hasStatusFile = set != null && set.status;
            if (set != null) {
                applyStatusFile(set, replay, report);
                if (set.temp) {
                    deleteQuietly(set.path(StorePaths.EXTENSION + StorePaths.TEMP_SUFFIX));
                }
            }
            // An invalidation only the log knows about gets its status file back (the sync below writes it).
            entry.storedInvalidated = hasStatusFile || replay.invalidated.isBlank() ? replay.invalidated : "";
            this.entries.put(line.id, entry);
        }

        // Files the log doesn't know: a crash between the file and its log line, or a lost or damaged log.
        for (FileSet set : files.values()) {
            recoverFromFiles(set, deletedIds, strings, report);
        }

        report.runs = this.entries.size();
        report.millis = (System.nanoTime() - start) / 1_000_000L;
        boolean compact = report.badLogLines > 0 || !log.endsWithNewline() || nonRunLines > 0 || duplicateRunLines > 0
                || report.recoveredFromHeaders > 0 || report.completedTempFiles > 0 || report.logFromBackup
                || (!log.existed() && !this.entries.isEmpty());
        // Status files lost while the log kept the invalidation are written again.
        sync(runs());
        if (compact) {
            scheduleCompaction();
        }
    }

    private Map<String, FileSet> scanFiles() {
        Map<String, FileSet> files = new LinkedHashMap<>();
        try (DirectoryStream<Path> directories = Files.newDirectoryStream(this.root)) {
            for (Path directory : directories) {
                String directoryName = directory.getFileName().toString();
                if (!StorePaths.isMapKey(directoryName) || !Files.isDirectory(directory)) {
                    continue;
                }
                try (DirectoryStream<Path> list = Files.newDirectoryStream(directory)) {
                    for (Path file : list) {
                        String name = file.getFileName().toString();
                        String base;
                        int kind;
                        if (name.endsWith(StorePaths.CORRUPT_EXTENSION)) {
                            base = name.substring(0, name.length() - StorePaths.CORRUPT_EXTENSION.length());
                            kind = 1;
                        } else if (name.endsWith(StorePaths.EXTENSION + StorePaths.TEMP_SUFFIX)) {
                            base = name.substring(0, name.length() - (StorePaths.EXTENSION + StorePaths.TEMP_SUFFIX).length());
                            kind = 3;
                        } else if (name.endsWith(StorePaths.STATUS_EXTENSION + StorePaths.TEMP_SUFFIX)) {
                            deleteQuietly(file);
                            continue;
                        } else if (name.endsWith(StorePaths.EXTENSION)) {
                            base = name.substring(0, name.length() - StorePaths.EXTENSION.length());
                            kind = 0;
                        } else if (name.endsWith(StorePaths.STATUS_EXTENSION)) {
                            base = name.substring(0, name.length() - StorePaths.STATUS_EXTENSION.length());
                            kind = 2;
                        } else {
                            continue;
                        }
                        if (!StorePaths.isFileBase(base)) {
                            continue;
                        }
                        FileSet set = files.computeIfAbsent(directoryName + "/" + base, k -> new FileSet(directory, base));
                        switch (kind) {
                            case 0 -> set.run = true;
                            case 1 -> set.corrupt = true;
                            case 2 -> set.status = true;
                            default -> set.temp = true;
                        }
                    }
                }
            }
        } catch (IOException e) {
            Minehop.LOGGER.error("Could not list the replay store folders under {}", this.root, e);
        }
        return files;
    }

    private void recoverFromFiles(FileSet set, Set<String> deletedIds, Map<String, String> strings, OpenReport report) {
        Path runFile = set.path(StorePaths.EXTENSION);
        if (!set.run && set.temp) {
            // A write that stopped between the temp file and the move: finish it if the temp file is complete.
            Path temp = set.path(StorePaths.EXTENSION + StorePaths.TEMP_SUFFIX);
            try {
                MhrpCodec.decode(Files.readAllBytes(temp));
                RunLog.moveAtomically(temp, runFile);
                set.run = true;
                report.completedTempFiles++;
            } catch (Exception e) {
                deleteQuietly(temp);
            }
        } else if (set.temp) {
            deleteQuietly(set.path(StorePaths.EXTENSION + StorePaths.TEMP_SUFFIX));
        }
        if (!set.run && !set.corrupt) {
            return;
        }
        MhrpHeader header = null;
        String unavailable = "";
        if (set.run) {
            try {
                header = MhrpCodec.decodeHeader(Files.readAllBytes(runFile), true);
            } catch (MhrpFormatException e) {
                if (e.isCorrupt()) {
                    header = headerWithoutCrc(runFile);
                    unavailable = "quarantined (corrupt file)";
                    quarantineNow(runFile, e);
                } else {
                    Minehop.LOGGER.warn("Replay file {} is not readable by this version ({}); left alone", runFile, e.getMessage());
                    return;
                }
            } catch (IOException e) {
                Minehop.LOGGER.warn("Could not read replay file {} to recover it", runFile, e);
                return;
            }
        } else {
            header = headerWithoutCrc(set.path(StorePaths.CORRUPT_EXTENSION));
            unavailable = "quarantined (corrupt file)";
        }
        if (header == null) {
            Minehop.LOGGER.warn("Replay file {} has no readable header; it can't be added back", runFile);
            return;
        }
        if (deletedIds.contains(header.replayId)) {
            // Deleted, but moving the file away failed last time: finish that now.
            try {
                moveToDeleted(this.root, set.path(StorePaths.EXTENSION), set.path(StorePaths.STATUS_EXTENSION), set.path(StorePaths.CORRUPT_EXTENSION));
            } catch (RuntimeException e) {
                Minehop.LOGGER.warn("Could not move the files of deleted run {} to {}", header.replayId, DELETED_DIRECTORY, e);
            }
            return;
        }
        if (this.entries.containsKey(header.replayId)) {
            Minehop.LOGGER.warn("Replay file {} holds run {}, which the log already has in {}; ignored", runFile,
                    header.replayId, this.entries.get(header.replayId).file);
            return;
        }
        ReplayManager.Replay replay = new ReplayManager.Replay(header.replayId, intern(strings, header.mapName),
                intern(strings, header.playerName), intern(strings, header.playerUuid), header.time, header.savedAt, null);
        replay.ac_flags = header.acFlags;
        replay.invalidated = "";
        Entry entry = new Entry(replay, set.relative(), header.frameCount, header.bounds(), "", header.playerUuid);
        entry.unavailable = unavailable;
        entry.serverTime = header.serverTime;
        entry.clientTicks = header.clientTicks;
        entry.preFrames = header.preFrames;
        entry.postFrames = header.postFrames;
        applyStatusFile(set, replay, report);
        entry.storedInvalidated = replay.invalidated;
        if (!unavailable.isEmpty()) {
            report.quarantinedFiles++;
        }
        this.entries.put(header.replayId, entry);
        report.recoveredFromHeaders++;
    }

    private static MhrpHeader headerWithoutCrc(Path file) {
        try {
            byte[] bytes = Files.readAllBytes(file);
            return MhrpCodec.decodeHeader(bytes, false);
        } catch (Exception e) {
            return null;
        }
    }

    private void applyStatusFile(FileSet set, ReplayManager.Replay replay, OpenReport report) {
        if (!set.status) {
            return;
        }
        Path status = set.path(StorePaths.STATUS_EXTENSION);
        try {
            if (Files.size(status) > MAX_STATUS_BYTES) {
                return;
            }
            String reason = Files.readString(status, StandardCharsets.UTF_8);
            if (!reason.isBlank()) {
                replay.invalidated = reason;
                report.statusFiles++;
            }
        } catch (IOException e) {
            Minehop.LOGGER.warn("Could not read replay status file {}", status, e);
        }
    }

    private static String intern(Map<String, String> strings, String value) {
        return strings.computeIfAbsent(value, v -> v);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    // ------------------------------------------------------------------------------------------
    // Adding runs
    // ------------------------------------------------------------------------------------------

    /**
     * Adds a finished run: the metadata is live at once and the file is written by the writer thread, which also turns
     * the frames into columns; until the file is written the frames stay in memory (and loadable). {@code frames}: the
     * recorded frames, not copied (pass a list nothing will change). {@code serverTime}/{@code clientTicks}: NaN / -1 if
     * unknown. The server thread's cost doesn't depend on the run's length.
     */
    public void addRun(ReplayManager.Replay replay, List<ReplayManager.ReplayEntry> frames, double serverTime, long clientTicks, int headerFlags) {
        // Recordings hold no null frames; if one ever does, the writer corrects the count once it has the columns.
        addRun(replay, new FrameSource(frames), frames.size(), null, serverTime, clientTicks, headerFlags, 0, 0);
    }

    /**
     * {@link #addRun(ReplayManager.Replay, List, double, long, int)} with frames already in columns. Their layout
     * (pre/post frames, tick-stream recording) goes into the file and the run log.
     */
    public void addRun(ReplayManager.Replay replay, ReplayFrames frames, double serverTime, long clientTicks, int headerFlags) {
        int flags = headerFlags | (frames.tickStream() ? MhrpHeader.FLAG_TICK_STREAM : 0);
        addRun(replay, new FrameSource(frames), frames.size(), frames.bounds(), serverTime, clientTicks, flags,
                frames.preFrames(), frames.postFrames());
        cachePut(replay.replay_id, frames);
    }

    private void addRun(ReplayManager.Replay replay, FrameSource frames, int frameCount, double[] bounds, double serverTime,
                        long clientTicks, int headerFlags, int preFrames, int postFrames) {
        String file = StorePaths.relativeFile(replay.map_name, replay.replay_id);
        Entry entry = new Entry(replay, file, frameCount, bounds, "", replay.player_uuid);
        entry.serverTime = serverTime;
        entry.clientTicks = clientTicks;
        entry.preFrames = preFrames;
        entry.postFrames = postFrames;
        this.entries.put(replay.replay_id, entry);
        this.deleted.remove(replay.replay_id);
        MhrpHeader header = header(entry);
        header.flags = headerFlags;
        PendingWrite write = new PendingWrite(replay.replay_id, header, frames, StorePaths.resolveRunFile(this.root, file),
                runLine(entry), entry, 0);
        this.pendingWrites.put(write.id(), write);
        this.writer.execute(() -> writeRun(write));
        String invalidated = nullToEmpty(replay.invalidated);
        if (!invalidated.isBlank()) {
            entry.storedInvalidated = invalidated;
            writeStatus(entry, invalidated);
        }
    }

    /**
     * Adds a run whose file the migration has already written and verified, without writing anything: the caller
     * then syncs (status files for invalidated runs) and compacts the log. {@code headerUuid}: the UUID in the file.
     */
    public void adoptWritten(ReplayManager.Replay replay, String file, int frames, double[] bounds, String headerUuid) {
        this.entries.put(replay.replay_id, new Entry(replay, file, frames, bounds, "", headerUuid));
    }

    private static MhrpHeader header(Entry entry) {
        ReplayManager.Replay replay = entry.replay;
        MhrpHeader header = new MhrpHeader();
        header.replayId = replay.replay_id;
        header.mapName = replay.map_name;
        header.playerUuid = nullToEmpty(replay.player_uuid);
        header.playerName = nullToEmpty(replay.player_name);
        header.time = replay.time;
        header.savedAt = replay.saved_at;
        header.serverTime = entry.serverTime;
        header.clientTicks = entry.clientTicks;
        header.acFlags = nullToEmpty(replay.ac_flags);
        header.preFrames = entry.preFrames;
        header.postFrames = entry.postFrames;
        return header;
    }

    private static RunLog.Line runLine(Entry entry) {
        ReplayManager.Replay replay = entry.replay;
        RunLog.Line line = new RunLog.Line();
        line.op = RunLog.OP_RUN;
        line.id = replay.replay_id;
        line.map = replay.map_name;
        line.uuid = nullToEmpty(replay.player_uuid);
        line.name = nullToEmpty(replay.player_name);
        line.time = replay.time;
        line.saved_at = replay.saved_at;
        line.frames = entry.frames;
        line.ac_flags = nullToEmpty(replay.ac_flags);
        line.file = entry.file;
        line.bounds = entry.bounds;
        line.server_time = Double.isNaN(entry.serverTime) ? null : entry.serverTime;
        line.client_ticks = entry.clientTicks < 0L ? null : entry.clientTicks;
        line.pre = entry.preFrames == 0 ? null : entry.preFrames;
        line.post = entry.postFrames == 0 ? null : entry.postFrames;
        line.invalidated = replay.invalidated == null || replay.invalidated.isBlank() ? null : replay.invalidated;
        return line;
    }

    // ------------------------------------------------------------------------------------------
    // Status changes and deletions
    // ------------------------------------------------------------------------------------------

    /**
     * Brings the files in line with {@code runs} (the live metadata list): runs no longer in it are removed (moved to
     * _deleted), a changed invalidation is written to the run's status file and the log, a backfilled UUID is
     * compacted into the log. A run in the list the store doesn't know is written if it carries frames.
     */
    public void sync(List<ReplayManager.Replay> runs) {
        Set<String> present = new HashSet<>();
        boolean compact = false;
        if (runs != null) {
            for (ReplayManager.Replay replay : runs) {
                if (replay == null || replay.replay_id == null || replay.replay_id.isBlank()) {
                    continue;
                }
                present.add(replay.replay_id);
                Entry entry = this.entries.get(replay.replay_id);
                if (entry == null) {
                    if (ReplayManager.isSavePending(replay)) {
                        continue; // a finish still taking its post-run frames: added when they are complete
                    }
                    if (replay.replayEntries != null && !replay.replayEntries.isEmpty()) {
                        addRun(replay, new ArrayList<>(replay.replayEntries), Double.NaN, -1L, 0);
                    } else {
                        Minehop.LOGGER.warn("Replay {} is in the run list but not in the store and has no frames; not saved", replay.replay_id);
                    }
                    continue;
                }
                if (entry.replay != replay) {
                    Minehop.LOGGER.warn("Two replays share the id {}; only the stored one is kept", replay.replay_id);
                    continue;
                }
                String invalidated = nullToEmpty(replay.invalidated);
                if (!invalidated.equals(entry.storedInvalidated)) {
                    entry.storedInvalidated = invalidated;
                    writeStatus(entry, invalidated);
                }
                String uuid = nullToEmpty(replay.player_uuid);
                if (!uuid.equals(entry.storedUuid)) {
                    entry.storedUuid = uuid;
                    compact = true;
                }
            }
        }
        for (Iterator<Map.Entry<String, Entry>> it = this.entries.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, Entry> mapping = it.next();
            if (!present.contains(mapping.getKey())) {
                it.remove();
                remove(mapping.getKey(), mapping.getValue());
            }
        }
        if (compact) {
            scheduleCompaction();
        }
    }

    private void writeStatus(Entry entry, String invalidated) {
        String id = entry.replay.replay_id;
        Path runFile = StorePaths.resolveRunFile(this.root, entry.file);
        if (runFile == null) {
            return;
        }
        Path statusFile = StorePaths.sibling(runFile, StorePaths.STATUS_EXTENSION);
        RunLog.Line line = RunLog.Line.invalidate(id, invalidated);
        submitWithRetry("status of run " + id, 0, () -> {
            if (this.deleted.contains(id)) {
                return;
            }
            failIfInjected();
            if (invalidated.isBlank()) {
                Files.deleteIfExists(statusFile);
            } else {
                writeFileAtomically(statusFile, invalidated.getBytes(StandardCharsets.UTF_8));
            }
            RunLog.append(this.logFile, List.of(line));
        });
    }

    private void remove(String id, Entry entry) {
        this.deleted.add(id);
        this.pendingWrites.remove(id);
        ReplayFrames cached = this.cache.remove(id);
        if (cached != null) {
            this.cacheBytes -= cached.approxBytes();
        }
        Path runFile = StorePaths.resolveRunFile(this.root, entry.file);
        if (runFile == null) {
            return;
        }
        submitWithRetry("removal of run " + id, 0, () -> {
            moveToDeleted(this.root, runFile, StorePaths.sibling(runFile, StorePaths.STATUS_EXTENSION),
                    StorePaths.sibling(runFile, StorePaths.CORRUPT_EXTENSION));
            RunLog.append(this.logFile, List.of(RunLog.Line.delete(id)));
        });
    }

    /** Moves a run's files to _deleted/{map key}/ (writer thread, the migration thread, or the server thread while opening). */
    static void moveToDeleted(Path root, Path... files) {
        for (Path file : files) {
            if (!Files.exists(file)) {
                continue;
            }
            Path target = root.resolve(DELETED_DIRECTORY).resolve(file.getParent().getFileName()).resolve(file.getFileName());
            try {
                Files.createDirectories(target.getParent());
                for (int n = 1; Files.exists(target); n++) {
                    target = target.resolveSibling(file.getFileName() + "." + n);
                }
                RunLog.moveAtomically(file, target);
            } catch (IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        }
    }

    /**
     * The run's file, if it has been written and can be read (not still being written, not quarantined or missing);
     * else null. Replay streaming sends its column blocks as they are (see MhrpCodec#rewriteHeader).
     */
    public Path writtenFile(ReplayManager.Replay replay) {
        Entry entry = entry(replay);
        if (entry == null || !entry.unavailable.isEmpty() || this.pendingWrites.containsKey(replay.replay_id) || this.closing) {
            return null;
        }
        return StorePaths.resolveRunFile(this.root, entry.file);
    }

    // ------------------------------------------------------------------------------------------
    // Frames
    // ------------------------------------------------------------------------------------------

    /** The run's frames if they are in memory (cache, or a write still pending); null otherwise. Never reads the disk. */
    public ReplayFrames cached(ReplayManager.Replay replay) {
        if (replay == null || replay.replay_id == null) {
            return null;
        }
        PendingWrite pending = this.pendingWrites.get(replay.replay_id);
        if (pending != null) {
            return pending.frames();
        }
        ReplayFrames frames = this.cache.get(replay.replay_id);
        if (frames != null) {
            this.stats.cacheHits.incrementAndGet();
        }
        return frames;
    }

    /**
     * Gets the run's frames and passes them to {@code callback} on the server thread: at once if they are in memory,
     * else after the reader thread has read and decoded the file. Null if the frames are unavailable (missing or
     * corrupt file: a corrupt file is quarantined and the run marked unavailable).
     */
    public void load(ReplayManager.Replay replay, Consumer<ReplayFrames> callback) {
        ReplayFrames frames = cached(replay);
        if (frames != null) {
            deliver(callback, frames);
            return;
        }
        Entry entry = entry(replay);
        if (entry == null || !entry.unavailable.isEmpty() || this.closing) {
            deliver(callback, null);
            return;
        }
        String id = replay.replay_id;
        List<Consumer<ReplayFrames>> waiting = this.loading.get(id);
        if (waiting != null) {
            waiting.add(callback);
            return;
        }
        waiting = new ArrayList<>();
        waiting.add(callback);
        this.loading.put(id, waiting);
        Path runFile = StorePaths.resolveRunFile(this.root, entry.file);
        this.reader.execute(() -> {
            LoadResult result = read(runFile, id);
            this.server.execute(() -> completeLoad(replay, result));
        });
    }

    private record LoadResult(ReplayFrames frames, String problem, MhrpFormatException format, boolean transientError) {
    }

    private static LoadResult read(Path runFile, String id) {
        if (runFile == null) {
            return new LoadResult(null, "invalid file path", null, false);
        }
        try {
            long size = Files.size(runFile);
            if (size > MhrpCodec.MAX_FILE_BYTES) {
                return new LoadResult(null, "file too large (" + size + " bytes)", null, false);
            }
            MhrpCodec.Decoded decoded = MhrpCodec.decode(Files.readAllBytes(runFile));
            if (!id.equals(decoded.header().replayId)) {
                return new LoadResult(null, "file holds run " + decoded.header().replayId, null, false);
            }
            return new LoadResult(decoded.frames(), null, null, false);
        } catch (NoSuchFileException e) {
            return new LoadResult(null, "missing file", null, false);
        } catch (MhrpFormatException e) {
            return new LoadResult(null, e.getMessage(), e, false);
        } catch (IOException | OutOfMemoryError e) {
            return new LoadResult(null, e.toString(), null, true);
        } catch (RuntimeException e) {
            return new LoadResult(null, e.toString(), null, true);
        }
    }

    private void completeLoad(ReplayManager.Replay replay, LoadResult result) {
        List<Consumer<ReplayFrames>> callbacks = this.loading.remove(replay.replay_id);
        Entry entry = this.entries.get(replay.replay_id);
        ReplayFrames frames = result.frames();
        this.stats.loads.incrementAndGet();
        if (frames != null) {
            if (entry != null) {
                entry.frames = frames.size();
                cachePut(replay.replay_id, frames);
            }
        } else {
            this.stats.loadFailures.incrementAndGet();
            if (result.transientError()) {
                Minehop.LOGGER.warn("Could not read the replay of {} on {} ({}): {}", replay.player_name, replay.map_name,
                        replay.replay_id, result.problem());
            } else if (entry != null) {
                boolean corrupt = result.format() != null && result.format().isCorrupt();
                entry.unavailable = corrupt ? "quarantined (" + result.format().kind() + ")" : result.problem();
                Minehop.LOGGER.error("The replay of {}'s {} run on {} ({}) can't be played: {}{}", replay.player_name,
                        String.format(java.util.Locale.ROOT, "%.3fs", replay.time), replay.map_name, replay.replay_id,
                        result.problem(), corrupt ? "; quarantined as " + entry.file + ".corrupt" : "");
                if (corrupt) {
                    this.stats.quarantined.incrementAndGet();
                    Path runFile = StorePaths.resolveRunFile(this.root, entry.file);
                    MhrpFormatException format = result.format();
                    this.writer.execute(() -> quarantineNow(runFile, format));
                }
            }
        }
        if (callbacks != null) {
            for (Consumer<ReplayFrames> callback : callbacks) {
                deliver(callback, frames);
            }
        }
    }

    private static void quarantineNow(Path runFile, MhrpFormatException why) {
        if (runFile == null) {
            return;
        }
        try {
            RunLog.moveAtomically(runFile, StorePaths.sibling(runFile, StorePaths.CORRUPT_EXTENSION));
            Minehop.LOGGER.error("Quarantined corrupt replay file {} ({})", runFile, why.getMessage());
        } catch (IOException e) {
            Minehop.LOGGER.error("Could not quarantine corrupt replay file {}", runFile, e);
        }
    }

    private static void deliver(Consumer<ReplayFrames> callback, ReplayFrames frames) {
        try {
            callback.accept(frames);
        } catch (RuntimeException e) {
            Minehop.LOGGER.error("Replay frame callback failed", e);
        }
    }

    private void cachePut(String id, ReplayFrames frames) {
        ReplayFrames previous = this.cache.put(id, frames);
        if (previous != null) {
            this.cacheBytes -= previous.approxBytes();
        }
        this.cacheBytes += frames.approxBytes();
        Iterator<Map.Entry<String, ReplayFrames>> it = this.cache.entrySet().iterator();
        while (this.cacheBytes > this.cacheBudget && it.hasNext()) {
            Map.Entry<String, ReplayFrames> eldest = it.next();
            if (eldest.getKey().equals(id)) {
                continue; // keep the newest even if it alone is over budget
            }
            this.cacheBytes -= eldest.getValue().approxBytes();
            it.remove();
        }
    }

    /** Drops every cached frame list (they are reloaded on demand). Used by the dev harness. */
    public void clearCache() {
        this.cache.clear();
        this.cacheBytes = 0L;
    }

    public long cacheBytes() {
        return this.cacheBytes;
    }

    public int cachedRuns() {
        return this.cache.size();
    }

    public int pendingWrites() {
        return this.pendingWrites.size();
    }

    // ------------------------------------------------------------------------------------------
    // Writer thread
    // ------------------------------------------------------------------------------------------

    private interface IoTask {
        void run() throws IOException;
    }

    private void writeRun(PendingWrite write) {
        if (this.deleted.contains(write.id()) || this.pendingWrites.get(write.id()) != write) {
            return;
        }
        try {
            ReplayFrames frames = write.frames();
            double[] bounds = frames.bounds();
            write.entry().bounds = bounds;
            write.line().bounds = bounds;
            if (write.line().frames != frames.size()) {
                write.line().frames = frames.size();
                Entry entry = write.entry();
                this.server.execute(() -> entry.frames = frames.size());
            }
            failIfInjected();
            byte[] bytes = MhrpCodec.encode(write.header(), frames);
            writeFileAtomically(write.target(), bytes);
            RunLog.append(this.logFile, List.of(write.line()));
            this.pendingWrites.remove(write.id(), write);
            this.stats.runsWritten.incrementAndGet();
            this.stats.bytesWritten.addAndGet(bytes.length);
            // A run that was just finished is the likeliest to be watched next: keep it decoded.
            String id = write.id();
            this.server.execute(() -> {
                if (!this.deleted.contains(id) && this.entries.containsKey(id)) {
                    cachePut(id, frames);
                }
            });
        } catch (IllegalArgumentException e) {
            // Not a disk problem: retrying can't help. The frames stay in memory until the server stops.
            Minehop.LOGGER.error("Can't store the replay {}: {}", write.id(), e.getMessage());
        } catch (Exception | OutOfMemoryError e) {
            this.stats.writeFailures.incrementAndGet();
            if (this.closing) {
                Minehop.LOGGER.error("Writing the replay {} failed during shutdown", write.id(), e);
                return;
            }
            long delay = RETRY_DELAYS_SECONDS[Math.min(write.attempt(), RETRY_DELAYS_SECONDS.length - 1)];
            Minehop.LOGGER.error("Writing the replay {} ({}) failed (attempt {}); its frames stay in memory and the write is retried in {} s",
                    write.id(), write.target(), write.attempt() + 1, delay, e);
            PendingWrite retry = new PendingWrite(write.id(), write.header(), write.source(), write.target(), write.line(), write.entry(),
                    write.attempt() + 1);
            if (this.pendingWrites.replace(write.id(), write, retry)) {
                this.writer.schedule(() -> writeRun(retry), delay, TimeUnit.SECONDS);
            }
        }
    }

    private void submitWithRetry(String what, int attempt, IoTask task) {
        this.writer.execute(() -> {
            try {
                task.run();
            } catch (Exception | OutOfMemoryError e) {
                this.stats.writeFailures.incrementAndGet();
                if (this.closing) {
                    Minehop.LOGGER.error("Replay store: {} failed during shutdown", what, e);
                    return;
                }
                long delay = RETRY_DELAYS_SECONDS[Math.min(attempt, RETRY_DELAYS_SECONDS.length - 1)];
                Minehop.LOGGER.error("Replay store: {} failed (attempt {}); retrying in {} s", what, attempt + 1, delay, e);
                this.writer.schedule(() -> submitWithRetry(what, attempt + 1, task), delay, TimeUnit.SECONDS);
            }
        });
    }

    private static void failIfInjected() throws IOException {
        if (INJECT_WRITE_FAILURES.get() > 0 && INJECT_WRITE_FAILURES.getAndDecrement() > 0) {
            throw new IOException("injected write failure (dev harness)");
        }
    }

    /** Temp file, fsync, atomic move: the target is either absent or complete. */
    static void writeFileAtomically(Path target, byte[] bytes) throws IOException {
        Files.createDirectories(target.getParent());
        Path temp = target.resolveSibling(target.getFileName() + StorePaths.TEMP_SUFFIX);
        try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING)) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
            channel.force(true);
        }
        RunLog.moveAtomically(temp, target);
    }

    private static void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            Minehop.LOGGER.warn("Could not delete {}", file, e);
        }
    }

    /**
     * Rewrites the run log from the current metadata on the writer thread. Lines appended for anything queued before
     * are in the snapshot; anything queued after appends to the new log. A failed compaction takes a fresh snapshot
     * when it is retried (an old one would drop lines appended in between).
     */
    public void scheduleCompaction() {
        scheduleCompaction(0);
    }

    private void scheduleCompaction(int attempt) {
        if (this.closing) {
            return;
        }
        List<RunLog.Line> lines = snapshotLines();
        this.writer.execute(() -> {
            try {
                RunLog.writeAll(this.logFile, lines);
            } catch (Exception | OutOfMemoryError e) {
                this.stats.writeFailures.incrementAndGet();
                long delay = RETRY_DELAYS_SECONDS[Math.min(attempt, RETRY_DELAYS_SECONDS.length - 1)];
                Minehop.LOGGER.error("Replay store: run log compaction failed (attempt {}); retrying in {} s", attempt + 1, delay, e);
                if (!this.closing) {
                    this.writer.schedule(() -> this.server.execute(() -> scheduleCompaction(attempt + 1)), delay, TimeUnit.SECONDS);
                }
            }
        });
    }

    private List<RunLog.Line> snapshotLines() {
        List<ReplayManager.Replay> runs = runs();
        List<RunLog.Line> lines = new ArrayList<>(runs.size());
        for (ReplayManager.Replay replay : runs) {
            Entry entry = this.entries.get(replay.replay_id);
            if (entry != null) {
                lines.add(runLine(entry));
            }
        }
        return lines;
    }

    /** Writes the run log from the current metadata now, on the writer thread, after everything queued before it. */
    public Future<?> compactNow() {
        List<RunLog.Line> lines = snapshotLines();
        return this.writer.submit(() -> {
            RunLog.writeAll(this.logFile, lines);
            return null;
        });
    }

    /** Writes the marker that makes this the world's replay store (after everything queued before it). */
    public Future<?> writeMarker(Marker marker) {
        return this.writer.submit(() -> {
            if (!JsonStorage.writeAtomic(this.root.resolve(MARKER_FILE), MARKER_VERSION, marker)) {
                throw new IOException("could not write " + MARKER_FILE);
            }
            return null;
        });
    }

    /** Runs a task on the writer thread after everything queued before it. */
    public Future<?> onWriter(Runnable task) {
        return this.writer.submit(task);
    }

    /**
     * Waits until everything queued on the writer so far is done (not retries scheduled for later); true if no run
     * write is pending afterwards.
     */
    public boolean flush(long timeoutMillis) {
        try {
            this.writer.submit(() -> { }).get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            Minehop.LOGGER.warn("Replay store flush did not finish in {} ms", timeoutMillis);
        }
        return this.pendingWrites.isEmpty();
    }

    /**
     * Server stopping: one last attempt at every pending write, then waits for the writer (up to a minute). Runs whose
     * frames still couldn't be written are logged; their metadata stays in the log.
     */
    public void close() {
        this.closing = true;
        List<PendingWrite> pending = new ArrayList<>(this.pendingWrites.values());
        this.writer.execute(() -> {
            for (PendingWrite write : pending) {
                PendingWrite current = this.pendingWrites.get(write.id());
                if (current != null) {
                    writeRun(current);
                }
            }
        });
        this.writer.shutdown();
        try {
            if (!this.writer.awaitTermination(60, TimeUnit.SECONDS)) {
                Minehop.LOGGER.error("Replay store writer did not finish within 60 s");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        this.reader.shutdownNow();
        if (!this.pendingWrites.isEmpty()) {
            Minehop.LOGGER.error("Replay store: {} run file(s) could not be written and are lost (metadata kept in the run log): {}",
                    this.pendingWrites.size(), this.pendingWrites.keySet());
        }
    }

    public boolean isClosing() {
        return this.closing;
    }

    /** Reads every run file on the reader thread and reports how many decode (dev harness and diagnostics). */
    public void verifyAll(Consumer<String> report) {
        List<Map.Entry<String, String>> files = new ArrayList<>();
        for (Entry entry : this.entries.values()) {
            if (entry.unavailable.isEmpty()) {
                files.add(Map.entry(entry.replay.replay_id, entry.file));
            }
        }
        this.reader.execute(() -> {
            long start = System.nanoTime();
            int ok = 0;
            long frames = 0L;
            List<String> bad = new ArrayList<>();
            for (Map.Entry<String, String> file : files) {
                LoadResult result = read(StorePaths.resolveRunFile(this.root, file.getValue()), file.getKey());
                if (result.frames() != null) {
                    ok++;
                    frames += result.frames().size();
                } else if (bad.size() < 20) {
                    bad.add(file.getKey() + ": " + result.problem());
                }
            }
            String text = String.format(java.util.Locale.ROOT, "verified %d/%d run files, %d frames, in %.1f s%s", ok, files.size(),
                    frames, (System.nanoTime() - start) / 1.0E9D, bad.isEmpty() ? "" : "; failures: " + bad);
            this.server.execute(() -> report.accept(text));
        });
    }

    /** Ids of runs whose frames are unavailable, with the reason. */
    public Map<String, String> unavailableRuns() {
        Map<String, String> result = new LinkedHashMap<>();
        for (Entry entry : this.entries.values()) {
            if (!entry.unavailable.isEmpty()) {
                result.put(entry.replay.replay_id, entry.unavailable);
            }
        }
        return Collections.unmodifiableMap(result);
    }

    static boolean same(String a, String b) {
        return Objects.equals(a == null ? "" : a, b == null ? "" : b);
    }
}
