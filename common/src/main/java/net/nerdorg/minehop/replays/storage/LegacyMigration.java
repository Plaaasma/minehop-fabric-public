package net.nerdorg.minehop.replays.storage;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import net.minecraft.server.MinecraftServer;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.data.DataManager;
import net.nerdorg.minehop.replays.ReplayManager;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Converts a world's version 1 replay store (minehop_replays.json: every frame of every run in one JSON document,
 * all of it in memory) into the v2 store ({@link ReplayStore}), once, in the background.
 *
 * <p>States, as the server sees them:
 * <ol>
 *   <li><b>Legacy (migrating)</b>: the server runs on the legacy store exactly as before (all replay features, new
 *       finishes saved to minehop_replays.json) while a background thread, after the server has started:
 *       <ol>
 *         <li>copies the store file, read through a stream opened at the start (its content is pinned even if a
 *             save replaces the file meanwhile), to {@code minehop_replays.legacy.json.gz} (a lossless backup: the
 *             SHA-256 of the decompressed copy is checked against the original's before it is kept);</li>
 *         <li>reads that copy one run at a time (constant memory) and writes each run's file into the v2 folder,
 *             skipping a run whose file already exists and matches (an interrupted migration resumes), then reads
 *             every file back, decodes it and compares it with the source (positions within 1/8192 block);</li>
 *         <li>moves run files an earlier attempt wrote for runs no longer in the store to _deleted.</li>
 *       </ol>
 *       The v2 folder is not used while this happens; minehop_replays.json is never modified by the migration.</li>
 *   <li><b>Switch</b> (server thread, once the legacy writer is idle): the runs in memory are compared with what was
 *       migrated: runs finished meanwhile are added to the v2 store, runs removed meanwhile are moved out, changed
 *       statuses and UUIDs carried over; every current PB and WR row must resolve to the same run as before and every
 *       run must keep its status (WR / PB / kept / orphan / invalidated). Only then does the session switch to the v2
 *       store, drop the frames from memory, write the run log and finally the marker {@code store.json}, which makes
 *       the v2 store the world's store on every later start. The report goes to {@code migration-report.txt}.</li>
 *   <li><b>Failed</b> (any check, an I/O error, the server stopping): nothing changes for the running server, which
 *       keeps using the legacy store; the next start tries again (and resumes).</li>
 * </ol>
 * Disable with {@code -Dminehop.replayMigration=false}. A store that didn't load completely is never migrated.
 */
public final class LegacyMigration {
    public static final String BACKUP_FILE = "minehop_replays.legacy.json.gz";
    private static final double POSITION_TOLERANCE = 0.5D / MhrpCodec.POSITION_SCALE + 1.0E-9D;
    private static final double ANGLE_TOLERANCE = 180.0D / MhrpCodec.ANGLE_SCALE + 1.0E-4D;
    private static final double SPEED_TOLERANCE = 0.5D / MhrpCodec.SPEED_SCALE + 1.0E-6D;
    private static final double EFFICIENCY_TOLERANCE = 0.5D / MhrpCodec.EFFICIENCY_SCALE + 1.0E-4D;

    private enum Phase { OFF, RUNNING, SWITCHING, DONE, FAILED }

    private static Phase phase = Phase.OFF;
    private static Job job;
    private static Thread thread;
    private static String outcome = "";

    private LegacyMigration() {
    }

    /** One line for /replaytest: where the migration is. */
    public static String status() {
        Job current = job;
        String progress = current == null ? "" : " (" + current.progress.get() + " runs read)";
        return "migration " + phase + progress + (outcome.isEmpty() ? "" : ": " + outcome);
    }

    public static boolean isRunning() {
        return phase == Phase.RUNNING || phase == Phase.SWITCHING;
    }

    public static void onServerStarted(MinecraftServer server) {
        phase = Phase.OFF;
        job = null;
        thread = null;
        outcome = "";
        if (ReplayManager.storeMode() != ReplayManager.StoreMode.LEGACY) {
            return;
        }
        if ("false".equalsIgnoreCase(System.getProperty("minehop.replayMigration"))) {
            Minehop.LOGGER.info("Replay store: staying on {} (-Dminehop.replayMigration=false)", ReplayManager.REPLAYS_FILE);
            return;
        }
        if (!ReplayManager.legacyLoadedCompletely()) {
            Minehop.LOGGER.error("Replay store: {} did not load completely this session, so it is not migrated (see the load "
                    + "error above); the server keeps using it", ReplayManager.REPLAYS_FILE);
            return;
        }
        Path legacy = ReplayManager.legacyStorePath(server);
        Path source = Files.exists(legacy) ? legacy : legacy.resolveSibling(ReplayManager.REPLAYS_FILE + ".bak");
        InputStream in;
        long size;
        try {
            // Opened here, on the server thread: the stream keeps this content even if a save replaces the file.
            in = Files.newInputStream(source);
            size = Files.size(source);
        } catch (IOException e) {
            Minehop.LOGGER.error("Replay store: could not open {} to migrate it; the server keeps using it", source, e);
            return;
        }
        job = new Job(server, source, in, size, ReplayStore.root(server), legacy.resolveSibling(BACKUP_FILE));
        thread = new Thread(job, "Minehop replay migration");
        thread.setDaemon(true);
        thread.setPriority(Thread.NORM_PRIORITY - 1);
        phase = Phase.RUNNING;
        thread.start();
        Minehop.LOGGER.info("Replay store: migrating {} ({} MB) to {} in the background; the server keeps using {} until the "
                + "new store is complete and verified", source.getFileName(), size >> 20, ReplayStore.DIRECTORY, ReplayManager.REPLAYS_FILE);
    }

    public static void tick(MinecraftServer server) {
        if (phase == Phase.RUNNING && job != null && job.result != null) {
            phase = Phase.SWITCHING;
        }
        if (phase == Phase.SWITCHING && server.getTickCount() % 20 == 0) {
            trySwitch(server);
        }
    }

    public static void onServerStopping(MinecraftServer server) {
        if (job != null && (phase == Phase.RUNNING || phase == Phase.SWITCHING)) {
            job.cancelled.set(true);
            Minehop.LOGGER.warn("Replay store: the server is stopping before the migration finished; it continues on the next start");
        }
        if (thread != null) {
            try {
                thread.join(20_000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (phase == Phase.RUNNING || phase == Phase.SWITCHING) {
            phase = Phase.OFF;
        }
    }

    // ------------------------------------------------------------------------------------------
    // Background part
    // ------------------------------------------------------------------------------------------

    /** What was written for one run (immutable fields as written to its file). */
    private record Migrated(String id, String file, int frames, double[] bounds, String map, String name, String uuid,
                            double time, long savedAt, String acFlags, String invalidated) {
    }

    /** A run file in the v2 folder that the legacy store doesn't have and the migration didn't write. */
    private record Extra(String file, MhrpHeader header, String invalidated) {
    }

    private static final class Result {
        private boolean ok;
        private String failure = "";
        private String sourceSha = "";
        private final Map<String, Migrated> runs = new LinkedHashMap<>();
        private final List<Extra> extras = new ArrayList<>();
        private final StringBuilder report = new StringBuilder();
    }

    private static final class Job implements Runnable {
        private final MinecraftServer server;
        private final Path source;
        private final InputStream in;
        private final long sourceBytes;
        private final Path root;
        private final Path backup;
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private final AtomicInteger progress = new AtomicInteger();
        private volatile Result result;

        // Statistics (job thread only).
        private int sourceRuns;
        private long sourceFrames;
        private int written;
        private int resumed;
        private int rewritten;
        private long migratedFrames;
        private long fileBytes;
        private long allocatedBytes;
        private double maxPositionError;
        private double maxAngleError;
        private double maxSpeedError;
        private double maxEfficiencyError;
        private int repairedRuns;
        private int nullFrames;
        private final Map<String, Integer> skipped = new TreeMap<>();
        private int staleMoved;
        private int tempDeleted;

        private Job(MinecraftServer server, Path source, InputStream in, long sourceBytes, Path root, Path backup) {
            this.server = server;
            this.source = source;
            this.in = in;
            this.sourceBytes = sourceBytes;
            this.root = root;
            this.backup = backup;
        }

        @Override
        public void run() {
            Result out = new Result();
            long start = System.nanoTime();
            StringBuilder r = out.report;
            r.append("Minehop replay store migration: ").append(ReplayManager.REPLAYS_FILE).append(" -> ")
                    .append(ReplayStore.DIRECTORY).append(" (MHRP v").append(MhrpCodec.VERSION).append(")\n");
            r.append("started: ").append(now()).append('\n');
            r.append("source: ").append(this.source).append(" (").append(this.sourceBytes).append(" bytes)\n");
            try {
                Files.createDirectories(this.root);
                Path backupTemp = this.backup.resolveSibling(this.backup.getFileName() + ".tmp");
                long t0 = System.nanoTime();
                String sourceSha = copyToBackup(backupTemp);
                if (sourceSha == null) {
                    fail(out, "cancelled (server stopping)");
                    return;
                }
                out.sourceSha = sourceSha;
                long t1 = System.nanoTime();
                r.append(String.format(Locale.ROOT, "backup: %s written in %.1f s (%d bytes compressed), source SHA-256 %s%n",
                        this.backup.getFileName(), (t1 - t0) / 1.0E9D, Files.size(backupTemp), sourceSha));

                String backupSha = migrateFrom(backupTemp, out);
                if (backupSha == null) {
                    return;
                }
                if (!backupSha.equals(sourceSha)) {
                    fail(out, "the backup does not decompress to the original (SHA-256 " + backupSha + " vs " + sourceSha + ")");
                    return;
                }
                RunLog.moveAtomically(backupTemp, this.backup);
                long t2 = System.nanoTime();
                r.append("backup verified: decompresses to the original byte for byte (same SHA-256); kept as ")
                        .append(this.backup.getFileName()).append('\n');
                r.append(String.format(Locale.ROOT, "runs converted in %.1f s%n", (t2 - t1) / 1.0E9D));

                cleanUp(out);
                long t3 = System.nanoTime();
                appendStatistics(r);
                r.append(String.format(Locale.ROOT, "background part took %.1f s%n", (t3 - start) / 1.0E9D));
                out.ok = true;
            } catch (Exception | OutOfMemoryError e) {
                Minehop.LOGGER.error("Replay store migration failed", e);
                fail(out, e.toString());
            } finally {
                try {
                    this.in.close();
                } catch (IOException ignored) {
                    // nothing to do
                }
                this.result = out;
            }
        }

        private void fail(Result out, String why) {
            out.ok = false;
            out.failure = why;
        }

        private boolean cancelled(Result out) {
            if (this.cancelled.get()) {
                fail(out, "cancelled (server stopping)");
                return true;
            }
            return false;
        }

        /** Copies the pinned source to the gzip backup; returns the source's SHA-256 (null if cancelled). */
        private String copyToBackup(Path backupTemp) throws Exception {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            long copied = 0L;
            try (OutputStream out = new GZIPOutputStream(new BufferedOutputStream(Files.newOutputStream(backupTemp), 1 << 16), 1 << 16)) {
                byte[] buffer = new byte[1 << 16];
                int n;
                while ((n = this.in.read(buffer)) > 0) {
                    if (this.cancelled.get()) {
                        return null;
                    }
                    sha.update(buffer, 0, n);
                    out.write(buffer, 0, n);
                    copied += n;
                }
            }
            try (FileChannel channel = FileChannel.open(backupTemp, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            if (copied != this.sourceBytes) {
                Minehop.LOGGER.info("Replay store migration: read {} bytes of {} (size changed while opening)", copied, this.sourceBytes);
            }
            return HexFormat.of().formatHex(sha.digest());
        }

        /** Reads the backup run by run and writes/verifies each run's file; returns the backup's SHA-256 (null on failure). */
        private String migrateFrom(Path backupTemp, Result out) throws Exception {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            Set<String> ids = new HashSet<>();
            try (DigestInputStream digest = new DigestInputStream(new GZIPInputStream(Files.newInputStream(backupTemp), 1 << 16), sha);
                 JsonReader json = new JsonReader(new InputStreamReader(new BufferedInputStream(digest, 1 << 16), StandardCharsets.UTF_8))) {
                json.setLenient(true); // as JsonStorage reads it
                if (!openRunArray(json)) {
                    fail(out, "the store holds no list of runs");
                    return null;
                }
                while (json.hasNext()) {
                    if (cancelled(out)) {
                        return null;
                    }
                    Source run = readRun(json);
                    this.progress.incrementAndGet();
                    if (!migrate(run, ids, out)) {
                        return null;
                    }
                }
                // The rest of the document (closing brackets, any other keys) still has to go through the digest.
                byte[] rest = new byte[1 << 16];
                while (digest.read(rest) >= 0) {
                    // drain
                }
            }
            return HexFormat.of().formatHex(sha.digest());
        }

        /** Positions the reader inside the run array: {"_v":N,"data":[...]} or a bare [...]. */
        private static boolean openRunArray(JsonReader json) throws IOException {
            if (json.peek() == JsonToken.BEGIN_ARRAY) {
                json.beginArray();
                return true;
            }
            if (json.peek() != JsonToken.BEGIN_OBJECT) {
                return false;
            }
            json.beginObject();
            while (json.hasNext()) {
                if ("data".equals(json.nextName()) && json.peek() == JsonToken.BEGIN_ARRAY) {
                    json.beginArray();
                    return true;
                }
                json.skipValue();
            }
            return false;
        }

        /** One run as the legacy store holds it. */
        private static final class Source {
            private String id;
            private String map;
            private String name;
            private String uuid;
            private double time;
            private long savedAt;
            private String acFlags;
            private String invalidated;
            private ReplayFrames frames;
            private boolean framesNull = true;
            private int nullFrames;
        }

        private static Source readRun(JsonReader json) throws IOException {
            Source run = new Source();
            if (json.peek() == JsonToken.NULL) {
                json.nextNull();
                return run;
            }
            json.beginObject();
            while (json.hasNext()) {
                switch (json.nextName()) {
                    case "replay_id" -> run.id = nextString(json);
                    case "map_name" -> run.map = nextString(json);
                    case "player_name" -> run.name = nextString(json);
                    case "player_uuid" -> run.uuid = nextString(json);
                    case "time" -> run.time = nextDouble(json);
                    case "saved_at" -> run.savedAt = nextLong(json);
                    case "ac_flags" -> run.acFlags = nextString(json);
                    case "invalidated" -> run.invalidated = nextString(json);
                    case "replayEntries" -> readFrames(json, run);
                    default -> json.skipValue();
                }
            }
            json.endObject();
            return run;
        }

        private static void readFrames(JsonReader json, Source run) throws IOException {
            if (json.peek() == JsonToken.NULL) {
                json.nextNull();
                return;
            }
            run.framesNull = false;
            ReplayFrames.Builder frames = ReplayFrames.builder(512);
            json.beginArray();
            while (json.hasNext()) {
                if (json.peek() == JsonToken.NULL) {
                    json.nextNull();
                    run.nullFrames++;
                    continue;
                }
                double x = 0.0D;
                double y = 0.0D;
                double z = 0.0D;
                double xrot = 0.0D;
                double yrot = 0.0D;
                double jumps = 0.0D;
                double speed = 0.0D;
                double efficiency = 0.0D;
                json.beginObject();
                while (json.hasNext()) {
                    switch (json.nextName()) {
                        case "x" -> x = nextDouble(json);
                        case "y" -> y = nextDouble(json);
                        case "z" -> z = nextDouble(json);
                        case "xrot" -> xrot = nextDouble(json);
                        case "yrot" -> yrot = nextDouble(json);
                        case "jump_count" -> jumps = nextDouble(json);
                        case "last_jump_speed" -> speed = nextDouble(json);
                        case "efficiency" -> efficiency = nextDouble(json);
                        default -> json.skipValue();
                    }
                }
                json.endObject();
                // Exactly what ReplayManager.toFrames makes of the same ReplayEntry.
                frames.add(x, y, z, (float) yrot, (float) xrot, (int) Math.round(jumps), (float) speed, (float) efficiency, 0);
            }
            json.endArray();
            run.frames = frames.build();
        }

        private static String nextString(JsonReader json) throws IOException {
            if (json.peek() == JsonToken.NULL) {
                json.nextNull();
                return null;
            }
            return json.nextString();
        }

        private static double nextDouble(JsonReader json) throws IOException {
            if (json.peek() == JsonToken.NULL) {
                json.nextNull();
                return 0.0D;
            }
            return json.nextDouble();
        }

        private static long nextLong(JsonReader json) throws IOException {
            if (json.peek() == JsonToken.NULL) {
                json.nextNull();
                return 0L;
            }
            return json.nextLong();
        }

        private void skip(String why) {
            this.skipped.merge(why, 1, Integer::sum);
        }

        /** Writes (or verifies an existing) file for one run. False = the migration has failed (see out.failure). */
        private boolean migrate(Source run, Set<String> ids, Result out) throws IOException {
            this.sourceRuns++;
            this.nullFrames += run.nullFrames;
            int frameCount = run.frames == null ? 0 : run.frames.size();
            this.sourceFrames += frameCount;
            // The legacy loader's rules: these runs never made it into memory (or got a fresh id/time there, in which
            // case the switch adds them from memory).
            if (run.map == null || run.map.isBlank()) {
                skip("no map name (dropped by the legacy loader too)");
                return true;
            }
            if (run.framesNull || (frameCount == 0 && run.nullFrames == 0)) {
                skip("no frames (dropped by the legacy loader too)");
                return true;
            }
            if (!Double.isFinite(run.time) || run.time <= 0.0D) {
                skip("invalid time (dropped by the legacy loader too)");
                return true;
            }
            if (run.id == null || run.id.isBlank() || run.savedAt <= 0L) {
                skip("no id or save time (the legacy loader assigns them; carried over from memory at the switch)");
                return true;
            }
            if (frameCount == 0) {
                skip("only null frames (can't be played in either store)");
                return true;
            }
            if (!ids.add(run.id)) {
                fail(out, "two runs share the id " + run.id);
                return false;
            }
            String relative = StorePaths.relativeFile(run.map, run.id);
            Path target = StorePaths.resolveRunFile(this.root, relative);
            if (target == null) {
                fail(out, "no valid file name for run " + run.id);
                return false;
            }
            MhrpHeader header = new MhrpHeader();
            header.replayId = run.id;
            header.mapName = run.map;
            header.playerName = run.name == null ? "" : run.name;
            header.playerUuid = run.uuid == null ? "" : run.uuid;
            header.time = run.time;
            header.savedAt = run.savedAt;
            header.acFlags = run.acFlags == null ? "" : run.acFlags;
            header.flags = MhrpHeader.FLAG_MIGRATED;

            boolean done = false;
            if (Files.exists(target)) {
                try {
                    MhrpCodec.Decoded existing = MhrpCodec.decode(Files.readAllBytes(target));
                    if (sameRun(existing.header(), header) && framesMatch(existing.frames(), run.frames, false)) {
                        this.resumed++;
                        done = true;
                    } else {
                        this.rewritten++;
                    }
                } catch (MhrpFormatException e) {
                    this.rewritten++;
                }
            }
            if (!done) {
                byte[] bytes = MhrpCodec.encode(header, run.frames);
                ReplayStore.writeFileAtomically(target, bytes);
                this.written++;
            }
            // Read back what is on disk now and compare it with the source.
            byte[] stored = Files.readAllBytes(target);
            MhrpCodec.Decoded decoded;
            try {
                decoded = MhrpCodec.decode(stored);
            } catch (MhrpFormatException e) {
                fail(out, "run " + run.id + " does not read back: " + e.getMessage());
                return false;
            }
            if (!sameRun(decoded.header(), header) || !framesMatch(decoded.frames(), run.frames, true)) {
                fail(out, "run " + run.id + " reads back different from the source");
                return false;
            }
            if ((decoded.header().flags & MhrpHeader.FLAG_REPAIRED_FRAMES) != 0) {
                this.repairedRuns++;
            }
            this.migratedFrames += frameCount;
            this.fileBytes += stored.length;
            this.allocatedBytes += (stored.length + 4095L) / 4096L * 4096L;
            out.runs.put(run.id, new Migrated(run.id, relative, frameCount, decoded.frames().bounds(), header.mapName,
                    header.playerName, header.playerUuid, run.time, run.savedAt, header.acFlags,
                    run.invalidated == null ? "" : run.invalidated));
            return true;
        }

        private static boolean sameRun(MhrpHeader a, MhrpHeader b) {
            return a.replayId.equals(b.replayId) && a.mapName.equals(b.mapName) && a.playerName.equals(b.playerName)
                    && a.playerUuid.equals(b.playerUuid) && Double.compare(a.time, b.time) == 0 && a.savedAt == b.savedAt
                    && a.acFlags.equals(b.acFlags);
        }

        /** Decoded frames within the quantisation bounds of the source; with {@code record}, tracks the largest errors. */
        private boolean framesMatch(ReplayFrames decoded, ReplayFrames source, boolean record) {
            if (decoded.size() != source.size()) {
                return false;
            }
            double position = 0.0D;
            double angle = 0.0D;
            double speed = 0.0D;
            double efficiency = 0.0D;
            for (int i = 0; i < source.size(); i++) {
                if ((decoded.flags(i) & ReplayFrames.FLAG_REPAIRED) != 0) {
                    continue; // stored as the previous good value; counted in repairedRuns
                }
                position = Math.max(position, Math.max(Math.abs(decoded.x(i) - source.x(i)),
                        Math.max(Math.abs(decoded.y(i) - source.y(i)), Math.abs(decoded.z(i) - source.z(i)))));
                angle = Math.max(angle, Math.max(Math.abs(decoded.yaw(i) - source.yaw(i)) - Math.ulp(source.yaw(i)),
                        Math.abs(decoded.pitch(i) - source.pitch(i)) - Math.ulp(source.pitch(i))));
                speed = Math.max(speed, Math.abs(decoded.lastJumpSpeed(i) - source.lastJumpSpeed(i)));
                efficiency = Math.max(efficiency, Math.abs(decoded.efficiency(i) - source.efficiency(i)));
                if (decoded.jumpCount(i) != source.jumpCount(i)) {
                    return false;
                }
            }
            if (record) {
                this.maxPositionError = Math.max(this.maxPositionError, position);
                this.maxAngleError = Math.max(this.maxAngleError, angle);
                this.maxSpeedError = Math.max(this.maxSpeedError, speed);
                this.maxEfficiencyError = Math.max(this.maxEfficiencyError, efficiency);
            }
            return position <= POSITION_TOLERANCE && angle <= ANGLE_TOLERANCE && speed <= SPEED_TOLERANCE
                    && efficiency <= EFFICIENCY_TOLERANCE;
        }

        /**
         * Run files in the v2 folder the source doesn't have: written by an earlier attempt for runs removed since
         * (moved to _deleted), or by the v2 store itself after an earlier switch whose marker never got written
         * (kept and added at the switch). Leftover temp files are deleted.
         */
        private void cleanUp(Result out) throws IOException {
            Set<String> migratedFiles = new HashSet<>();
            for (Migrated run : out.runs.values()) {
                migratedFiles.add(run.file());
            }
            try (DirectoryStream<Path> directories = Files.newDirectoryStream(this.root)) {
                for (Path directory : directories) {
                    String directoryName = directory.getFileName().toString();
                    if (!StorePaths.isMapKey(directoryName) || !Files.isDirectory(directory)) {
                        continue;
                    }
                    try (DirectoryStream<Path> files = Files.newDirectoryStream(directory)) {
                        for (Path file : files) {
                            String name = file.getFileName().toString();
                            if (name.endsWith(StorePaths.TEMP_SUFFIX)) {
                                Files.deleteIfExists(file);
                                this.tempDeleted++;
                                continue;
                            }
                            if (!name.endsWith(StorePaths.EXTENSION) || migratedFiles.contains(directoryName + "/" + name)) {
                                continue;
                            }
                            MhrpHeader header;
                            try {
                                header = MhrpCodec.decodeHeader(Files.readAllBytes(file), true);
                            } catch (MhrpFormatException e) {
                                continue; // not ours to judge here; the store quarantines it if it is ever loaded
                            }
                            if ((header.flags & MhrpHeader.FLAG_MIGRATED) != 0) {
                                ReplayStore.moveToDeleted(this.root, file, StorePaths.sibling(file, StorePaths.STATUS_EXTENSION));
                                this.staleMoved++;
                            } else {
                                String invalidated = "";
                                Path status = StorePaths.sibling(file, StorePaths.STATUS_EXTENSION);
                                if (Files.exists(status) && Files.size(status) < (1 << 16)) {
                                    invalidated = Files.readString(status, StandardCharsets.UTF_8);
                                }
                                out.extras.add(new Extra(directoryName + "/" + name, header, invalidated));
                            }
                        }
                    }
                }
            }
        }

        private void appendStatistics(StringBuilder r) {
            r.append("source runs: ").append(this.sourceRuns).append(", frames: ").append(this.sourceFrames)
                    .append(this.nullFrames > 0 ? " (+" + this.nullFrames + " null frames)" : "").append('\n');
            for (Map.Entry<String, Integer> skip : this.skipped.entrySet()) {
                r.append("  not converted: ").append(skip.getValue()).append(" run(s): ").append(skip.getKey()).append('\n');
            }
            r.append("run files: ").append(this.written).append(" written, ").append(this.resumed)
                    .append(" already present from an interrupted attempt and verified, ").append(this.rewritten)
                    .append(" present but different (rewritten)\n");
            r.append("frames converted: ").append(this.migratedFrames).append('\n');
            r.append(String.format(Locale.ROOT, "size: %.2f MB in run files (%.2f MB allocated at 4 KiB per file); source %.2f MB%n",
                    this.fileBytes / 1.0E6D, this.allocatedBytes / 1.0E6D, this.sourceBytes / 1.0E6D));
            r.append(String.format(Locale.ROOT, "read-back errors: position %.4e blocks (bound %.4e), angle %.4e degrees, "
                            + "jump speed %.2e, efficiency %.2e; jump counts exact%n", this.maxPositionError, 0.5D / MhrpCodec.POSITION_SCALE,
                    this.maxAngleError, this.maxSpeedError, this.maxEfficiencyError));
            if (this.repairedRuns > 0) {
                r.append("runs with non-finite frames (stored as the previous good value, flagged): ").append(this.repairedRuns).append('\n');
            }
            if (this.staleMoved > 0 || this.tempDeleted > 0) {
                r.append("cleanup: ").append(this.staleMoved).append(" file(s) from an earlier attempt for runs no longer in the store moved to ")
                        .append(ReplayStore.DELETED_DIRECTORY).append(", ").append(this.tempDeleted).append(" temp file(s) deleted\n");
            }
        }
    }

    // ------------------------------------------------------------------------------------------
    // Switch (server thread)
    // ------------------------------------------------------------------------------------------

    private static void trySwitch(MinecraftServer server) {
        if (!ReplayManager.legacyWriterIdle()) {
            return; // a legacy save reads the frame lists that the switch drops: wait for it
        }
        Result result = job.result;
        if (!result.ok) {
            fail(server, result.failure, result.report);
            return;
        }
        long start = System.nanoTime();
        StringBuilder r = result.report;
        List<ReplayManager.Replay> runs = Minehop.replayList;
        Map<String, ReplayManager.Replay> byId = new HashMap<>();
        List<ReplayManager.Replay> added = new ArrayList<>();
        int statusChanges = 0;
        int uuidChanges = 0;
        for (ReplayManager.Replay replay : runs) {
            if (replay == null || replay.replay_id == null) {
                continue;
            }
            if (byId.put(replay.replay_id, replay) != null) {
                fail(server, "two runs in memory share the id " + replay.replay_id, r);
                return;
            }
            Migrated migrated = result.runs.get(replay.replay_id);
            int frames = replay.replayEntries == null ? 0 : (int) replay.replayEntries.stream().filter(Objects::nonNull).count();
            if (migrated == null) {
                if (frames == 0) {
                    fail(server, "run " + replay.replay_id + " is in memory without frames", r);
                    return;
                }
                added.add(replay);
                continue;
            }
            if (!migrated.map().equals(replay.map_name) || !migrated.name().equals(nullToEmpty(replay.player_name))
                    || Double.compare(migrated.time(), replay.time) != 0 || migrated.savedAt() != replay.saved_at
                    || !migrated.acFlags().equals(nullToEmpty(replay.ac_flags)) || migrated.frames() != frames) {
                fail(server, "run " + replay.replay_id + " in memory differs from the migrated copy", r);
                return;
            }
            if (!migrated.invalidated().equals(nullToEmpty(replay.invalidated))) {
                statusChanges++;
            }
            if (!migrated.uuid().equals(nullToEmpty(replay.player_uuid))) {
                uuidChanges++;
            }
        }
        List<String> removed = new ArrayList<>();
        for (String id : result.runs.keySet()) {
            if (!byId.containsKey(id)) {
                removed.add(id);
            }
        }
        if (removed.size() > Math.max(50, result.runs.size() / 20)) {
            fail(server, removed.size() + " migrated runs are no longer in memory; that many removals during the migration "
                    + "is unexpected, so the switch is refused", r);
            return;
        }

        Census before = census(runs);

        ReplayStore v2;
        try {
            v2 = ReplayStore.createEmpty(server, job.root);
        } catch (IOException e) {
            fail(server, "could not open the new store: " + e, r);
            return;
        }
        for (ReplayManager.Replay replay : runs) {
            Migrated migrated = replay == null ? null : result.runs.get(replay.replay_id);
            if (migrated != null) {
                v2.adoptWritten(replay, migrated.file(), migrated.frames(), migrated.bounds(), migrated.uuid());
            }
        }
        List<ReplayManager.Replay> extrasAdded = new ArrayList<>();
        for (Extra extra : result.extras) {
            MhrpHeader h = extra.header();
            if (byId.containsKey(h.replayId)) {
                continue;
            }
            ReplayManager.Replay replay = new ReplayManager.Replay(h.replayId, h.mapName, h.playerName, h.playerUuid, h.time, h.savedAt, null);
            replay.ac_flags = h.acFlags;
            replay.invalidated = extra.invalidated();
            v2.adoptWritten(replay, extra.file(), h.frameCount, h.bounds(), h.playerUuid);
            extrasAdded.add(replay);
        }
        runs.addAll(extrasAdded);
        ReplayManager.switchToV2(v2);

        Census after = census(runs.subList(0, runs.size() - extrasAdded.size()));
        Map<String, String> resolvedBefore = before.rows();
        Map<String, String> resolvedAfter = after.rows();
        Map<String, Integer> statusesBefore = before.statuses();
        Map<String, Integer> statusesAfter = after.statuses();
        List<String> differences = new ArrayList<>();
        for (Map.Entry<String, String> row : resolvedBefore.entrySet()) {
            String now = resolvedAfter.get(row.getKey());
            // A row no run backed before may be backed now only by a run found in the folder (extras).
            if (!row.getValue().equals(now) && !(row.getValue().isEmpty() && isExtra(now, extrasAdded))) {
                differences.add(row.getKey() + ": " + display(row.getValue()) + " -> " + display(now));
            }
        }
        if (!differences.isEmpty() || !statusesBefore.equals(statusesAfter)) {
            ReplayManager.switchBackToLegacy();
            v2.discard();
            runs.removeAll(extrasAdded);
            fail(server, "the new store does not resolve every PB/WR row and run status the same way: "
                    + (differences.size() > 10 ? differences.subList(0, 10) + " ..." : differences)
                    + " statuses " + statusesBefore + " -> " + statusesAfter, r);
            return;
        }

        // Commit: from here on the session runs on the v2 store.
        for (ReplayManager.Replay replay : added) {
            v2.addRun(replay, new ArrayList<>(replay.replayEntries), Double.NaN, -1L, 0);
        }
        for (String id : removed) {
            v2.moveRunFilesToDeleted(result.runs.get(id).file(), "removed during the migration:");
        }
        v2.sync(runs);
        long frames = 0L;
        for (ReplayManager.Replay replay : runs) {
            frames += ReplayManager.frameCount(replay);
            replay.replayEntries = null;
        }
        long resolvedRows = resolvedAfter.values().stream().filter(id -> !id.isEmpty()).count();
        r.append("switch: ").append(added.size()).append(" run(s) finished during the migration added, ").append(removed.size())
                .append(" removed during it moved to ").append(ReplayStore.DELETED_DIRECTORY).append(", ").append(statusChanges)
                .append(" status change(s) and ").append(uuidChanges).append(" UUID backfill(s) carried over, ")
                .append(extrasAdded.size()).append(" run(s) found only in the new folder added\n");
        r.append("PB/WR rows: ").append(resolvedBefore.size()).append(" checked, ").append(resolvedRows)
                .append(" backed by a run; every row resolves to the same run as on the legacy store\n");
        r.append("run statuses (unchanged): ").append(statusesAfter).append('\n');
        r.append("new store: ").append(runs.size()).append(" runs, ").append(frames).append(" frames\n");
        r.append(String.format(Locale.ROOT, "switch took %.1f ms on the server thread%n", (System.nanoTime() - start) / 1.0E6D));
        r.append("finished: ").append(now()).append('\n');
        r.append("result: SWITCHED. The migration did not modify ").append(ReplayManager.REPLAYS_FILE)
                .append(" (it is kept, and no longer used from now on); ").append(BACKUP_FILE)
                .append(" is a compressed copy of it as it was when the migration started.\n");

        ReplayStore.Marker marker = new ReplayStore.Marker();
        marker.created_at = System.currentTimeMillis();
        marker.created_by = "migration from " + ReplayManager.REPLAYS_FILE;
        marker.legacy_bytes = job.sourceBytes;
        marker.legacy_sha256 = result.sourceSha;
        marker.runs = runs.size();
        marker.frames = frames;
        String reportText = r.toString();
        Path reportFile = job.root.resolve(ReplayStore.REPORT_FILE);
        // Order on the writer thread: the run files of runs added above, the run log, the marker, the report.
        v2.compactNow();
        v2.writeMarker(marker);
        v2.onWriter(() -> writeReport(reportFile, reportText));
        phase = Phase.DONE;
        outcome = "switched to the new store (" + runs.size() + " runs)";
        Minehop.LOGGER.info("Replay store: migration complete, now using {} ({} runs, {} frames; report: {})",
                ReplayStore.DIRECTORY, runs.size(), frames, reportFile);
        for (String line : reportText.split("\n")) {
            Minehop.LOGGER.info("[replay migration] {}", line);
        }
    }

    private static boolean isExtra(String id, List<ReplayManager.Replay> extras) {
        for (ReplayManager.Replay replay : extras) {
            if (replay.replay_id.equals(id)) {
                return true;
            }
        }
        return false;
    }

    private static String display(String id) {
        return id == null || id.isEmpty() ? "none" : id;
    }

    /**
     * Every PB and WR row with the id of the run backing it ("" = none), via the same lookup the commands use, and how
     * many runs are in each status (see {@link #status}).
     */
    public record Census(Map<String, String> rows, Map<String, Integer> statuses) {
    }

    public static Census census(List<ReplayManager.Replay> runs) {
        Map<String, String> rows = new LinkedHashMap<>();
        Set<ReplayManager.Replay> personalBests = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        Set<ReplayManager.Replay> worldRecords = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        Map<String, List<DataManager.RecordData>> personalRowsByMap = new HashMap<>();
        addRows(rows, "PB", Minehop.personalRecordList, personalBests, personalRowsByMap);
        addRows(rows, "WR", Minehop.recordList, worldRecords, null);
        Map<String, Integer> counts = new TreeMap<>();
        for (ReplayManager.Replay replay : runs) {
            if (replay != null) {
                counts.merge(status(replay, worldRecords, personalBests, personalRowsByMap), 1, Integer::sum);
            }
        }
        return new Census(rows, counts);
    }

    private static void addRows(Map<String, String> rows, String kind, List<DataManager.RecordData> list,
                                Set<ReplayManager.Replay> backing, Map<String, List<DataManager.RecordData>> byMap) {
        if (list == null) {
            return;
        }
        for (DataManager.RecordData row : list) {
            if (row == null) {
                continue;
            }
            ReplayManager.Replay replay = ReplayManager.getReplayForRecord(row);
            String key = kind + " " + row.map_name + " " + (row.uuid == null || row.uuid.isBlank() ? "name:" + row.name : row.uuid)
                    + " " + row.time;
            rows.put(key, replay == null ? "" : replay.replay_id);
            if (replay != null) {
                backing.add(replay);
            }
            if (byMap != null && row.map_name != null) {
                byMap.computeIfAbsent(row.map_name, map -> new ArrayList<>()).add(row);
            }
        }
    }

    /**
     * A run's status: "unavailable" (its frames can't be loaded), "invalidated", "wr" (backs a WR row), "pb" (backs
     * its player's PB row), "kept" (slower than its player's PB), "orphan" (no PB row of its player on the map, or
     * faster than it: never watchable, e.g. the old 0.0x s runs or runs whose times were purged).
     */
    private static String status(ReplayManager.Replay replay, Set<ReplayManager.Replay> worldRecords,
                                 Set<ReplayManager.Replay> personalBests, Map<String, List<DataManager.RecordData>> personalRowsByMap) {
        if (!ReplayManager.unavailableReason(replay).isEmpty()) {
            return "unavailable";
        }
        if (replay.invalidated != null && !replay.invalidated.isBlank()) {
            return "invalidated";
        }
        if (worldRecords.contains(replay)) {
            return "wr";
        }
        if (personalBests.contains(replay)) {
            return "pb";
        }
        // DataManager.getPersonalRecord over this map's rows only.
        DataManager.RecordData pb = null;
        for (DataManager.RecordData row : personalRowsByMap.getOrDefault(replay.map_name, List.of())) {
            if (DataManager.recordBelongsTo(row, replay.player_name, replay.player_uuid) && (pb == null || row.time < pb.time)) {
                pb = row;
            }
        }
        if (pb == null) {
            return "orphan";
        }
        return replay.time < pb.time - 1.0E-5D ? "orphan" : "kept";
    }

    private static void fail(MinecraftServer server, String why, StringBuilder report) {
        phase = Phase.FAILED;
        outcome = why;
        report.append("finished: ").append(now()).append('\n');
        report.append("result: FAILED: ").append(why).append('\n');
        report.append("The server keeps using ").append(ReplayManager.REPLAYS_FILE)
                .append(" (unchanged); the migration is tried again on the next start.\n");
        Minehop.LOGGER.error("Replay store migration did not complete: {}. The server keeps using {} (unchanged); it is tried "
                + "again on the next start.", why, ReplayManager.REPLAYS_FILE);
        Path reportFile = job.root.resolve(ReplayStore.REPORT_FILE);
        String text = report.toString();
        CompletableFuture.runAsync(() -> writeReport(reportFile, text));
    }

    private static void writeReport(Path file, String text) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, text, StandardCharsets.UTF_8);
        } catch (IOException e) {
            Minehop.LOGGER.warn("Could not write the replay migration report {}", file, e);
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String now() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(new Date());
    }
}
