package net.nerdorg.minehop.replays;

import com.google.gson.reflect.TypeToken;
import net.nerdorg.minehop.platform.Services;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.data.DataManager;
import net.nerdorg.minehop.replays.storage.LegacyMigration;
import net.nerdorg.minehop.replays.storage.ReplayFrames;
import net.nerdorg.minehop.replays.storage.ReplayStore;
import net.nerdorg.minehop.util.JsonStorage;

import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Saved runs ("replays") and their lookups. Every finished run is kept: {@link Minehop#replayList} holds every run's
 * metadata and is also the run history (/map manage history, flagged runs, invalidation by id, the PB fallback).
 *
 * <p>Two stores, one active per world ({@link #storeMode()}):
 * <ul>
 *   <li>{@link StoreMode#V2}: {@link ReplayStore}, one compact file per run plus a run log; the list holds metadata
 *       only and frames are loaded on demand ({@link #loadFrames}) into a capped cache.</li>
 *   <li>{@link StoreMode#LEGACY}: the version 1 store, all runs with all frames in minehop_replays.json and in memory.
 *       Only used until {@link LegacyMigration} has converted a world (or with -Dminehop.replayMigration=false).</li>
 * </ul>
 * The rest of the mod uses the methods here and works the same on both.
 */
public class ReplayManager {
    private static final Type replayListType = new TypeToken<List<Replay>>(){}.getType();
    public static final String REPLAYS_FILE = "minehop_replays.json";
    // Bump when the on-disk Replay shape changes; migrate in loadRecordReplays/register.
    private static final int SCHEMA_VERSION = 1;

    public enum StoreMode {
        /** minehop_replays.json, every frame in memory (until migrated). */
        LEGACY,
        /** world/MineHop_Data/replays (ReplayStore). */
        V2
    }

    private static volatile StoreMode mode = StoreMode.LEGACY;
    /** The v2 store while {@link #mode} is V2 (null if it failed to open; replays are then not saved this session). */
    private static ReplayStore store;
    /** Legacy store: the file was read without errors (only then may it be migrated). */
    private static boolean legacyLoadedCompletely;

    // The replay store is one large file (hundreds of MB in production) rewritten as a whole. Writing it on the
    // server thread after every finish froze the server for seconds, and that stall then made the run-timer check
    // reject the player's next run. Saves now go to one background writer: it writes the newest snapshot of the
    // store and skips a snapshot older than what is already on disk, so a late background write can never undo a
    // newer save (e.g. the synchronous one at shutdown). Only the shutdown save still runs on the server thread.
    private static final ScheduledExecutorService WRITER = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Minehop replay writer");
        thread.setDaemon(true);
        return thread;
    });
    private static final long RETRY_DELAY_SECONDS = 30L;
    private static final Object WRITE_LOCK = new Object();
    private static final AtomicLong SAVE_SEQUENCE = new AtomicLong();
    private static final java.util.Map<Path, Long> WRITTEN_SEQUENCE = new java.util.HashMap<>(); // guarded by WRITE_LOCK
    private static final AtomicReference<LegacySave> PENDING_SAVE = new AtomicReference<>();
    private static final AtomicBoolean LEGACY_WRITING = new AtomicBoolean();

    private record LegacySave(Path file, long sequence, List<Replay> snapshot) {
    }

    /** Finished runs whose frames are still being completed (see {@link #beginSave}), by replay id. */
    private static final java.util.Map<String, PendingSave> PENDING_RUNS = new java.util.HashMap<>();

    /**
     * A finished run whose metadata is already in the run list (lookups, PB/WR, history see it) while its frames are
     * still being completed: the recorder adds the post-run frames for up to a second after the finish, then calls
     * {@link #complete}. Meanwhile the run counts as playable and {@link #loadFrames} waits for it. Server thread only.
     */
    public static final class PendingSave {
        private final Replay stored;
        private final ServerLevel world;
        private final double serverTime;
        private final long clientTicks;
        private final int headerFlags;
        private final int knownFrames;
        private final List<Consumer<ReplayFrames>> waiting = new ArrayList<>();
        private boolean done;

        private PendingSave(Replay stored, ServerLevel world, double serverTime, long clientTicks, int headerFlags, int knownFrames) {
            this.stored = stored;
            this.world = world;
            this.serverTime = serverTime;
            this.clientTicks = clientTicks;
            this.headerFlags = headerFlags;
            this.knownFrames = knownFrames;
        }

        public Replay replay() {
            return this.stored;
        }

        /** Whether the store keeps post-run frames (the legacy store only keeps the run itself). */
        public boolean wantsPostFrames() {
            return mode == StoreMode.V2;
        }

        /**
         * The run's frames are complete (with their layout): stores them and hands them to everyone waiting. A run that
         * was removed from the run list meanwhile (purged with its map or player) is not stored.
         */
        public void complete(ReplayFrames frames) {
            if (this.done) {
                return;
            }
            this.done = true;
            PENDING_RUNS.remove(this.stored.replay_id, this);
            ReplayFrames delivered = null;
            if (frames != null && !frames.isEmpty() && Minehop.replayList != null && Minehop.replayList.contains(this.stored)) {
                delivered = store(frames);
            }
            if (delivered == null && Minehop.replayList != null) {
                // Nothing was stored: don't keep a run without frames in the list (the legacy store can't hold one).
                Minehop.replayList.remove(this.stored);
            }
            for (Consumer<ReplayFrames> callback : this.waiting) {
                try {
                    callback.accept(delivered);
                } catch (RuntimeException e) {
                    Minehop.LOGGER.error("Replay frame callback failed", e);
                }
            }
            this.waiting.clear();
        }

        private ReplayFrames store(ReplayFrames frames) {
            if (mode == StoreMode.V2) {
                if (store == null) {
                    Minehop.LOGGER.error("The replay store is not available; {}'s run on {} was not saved", this.stored.player_name,
                            this.stored.map_name);
                    return null;
                }
                long start = System.nanoTime();
                store.addRun(this.stored, frames, this.serverTime, this.clientTicks, this.headerFlags);
                long nanos = System.nanoTime() - start;
                store.stats().lastFinishNanos = nanos;
                store.stats().maxFinishNanos = Math.max(store.stats().maxFinishNanos, nanos);
                return frames;
            }
            // The legacy store (until migrated) keeps only the run's own frames, one per entry, without flags.
            List<ReplayEntry> entries = new ArrayList<>(frames.runEnd() - frames.runStart());
            ReplayFrames.Builder run = ReplayFrames.builder(frames.runEnd() - frames.runStart());
            for (int i = frames.runStart(); i < frames.runEnd(); i++) {
                entries.add(entryAt(frames, i));
                run.add(frames, i, 0);
            }
            this.stored.replayEntries = entries;
            saveRecordReplaysAsync(this.world, Minehop.replayList);
            return run.build();
        }
    }

    public static class ReplayEntry {
        public double x;
        public double y;
        public double z;
        public double xrot;
        public double yrot;
        public double jump_count;
        public double last_jump_speed;
        public double efficiency;

        public ReplayEntry(double x, double y, double z, double xrot, double yrot, double jump_count, double last_jump_speed, double efficiency) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.xrot = xrot;
            this.yrot = yrot;
            this.jump_count = jump_count;
            this.last_jump_speed = last_jump_speed;
            this.efficiency = efficiency;
        }
    }

    public static class Replay {
        public String replay_id;
        public String map_name;
        public String player_name;
        // L3: stable player identity. Legacy replays load with player_uuid == null/"" (name fallback).
        public String player_uuid = "";
        public double time;
        public long saved_at;
        // Anticheat flags raised during this run ("" = clean), e.g. "Speed x2, Fly x1".
        public String ac_flags = "";
        // Why the run was invalidated ("" = valid). An invalidated run is kept (evidence, history) but is never
        // played back, never offered as anyone's personal best or world record and never promoted to one.
        public String invalidated = "";
        /**
         * The frames: always set in the legacy store; in the v2 store only on a run that was just handed to
         * {@link #saveReplay} (stored runs have null here; use {@link #loadFrames} / {@link #frameCount}).
         */
        public List<ReplayEntry> replayEntries;

        public Replay() {
        }

        public Replay(String map_name, String player_name, double time, List<ReplayEntry> replayEntries) {
            this(map_name, player_name, "", time, replayEntries);
        }

        public Replay(String map_name, String player_name, String player_uuid, double time, List<ReplayEntry> replayEntries) {
            this(UUID.randomUUID().toString(), map_name, player_name, player_uuid, time, System.currentTimeMillis(), replayEntries);
        }

        public Replay(String replay_id, String map_name, String player_name, double time, long saved_at, List<ReplayEntry> replayEntries) {
            this(replay_id, map_name, player_name, "", time, saved_at, replayEntries);
        }

        public Replay(String replay_id, String map_name, String player_name, String player_uuid, double time, long saved_at, List<ReplayEntry> replayEntries) {
            this.replay_id = replay_id;
            this.map_name = map_name;
            this.player_name = player_name;
            this.player_uuid = player_uuid == null ? "" : player_uuid;
            this.time = time;
            this.saved_at = saved_at;
            this.replayEntries = replayEntries;
        }
    }

    public static StoreMode storeMode() {
        return mode;
    }

    /** The v2 store, or null (legacy store, or the v2 store failed to open). Server thread. */
    public static ReplayStore store() {
        return mode == StoreMode.V2 ? store : null;
    }

    public static int deleteReplays(String mapName) {
        return deleteReplaysForMap(mapName);
    }

    public static int deleteReplaysForMap(String mapName) {
        if (mapName == null || mapName.isBlank()) {
            return 0;
        }
        if (Minehop.replayList != null) {
            int originalSize = Minehop.replayList.size();
            Minehop.replayList.removeIf(replay -> replay != null && mapName.equals(replay.map_name));
            return originalSize - Minehop.replayList.size();
        }
        return 0;
    }

    /** L3 identity match for replays: UUID when both have one, with legacy-name fallback. */
    public static boolean replayBelongsTo(Replay replay, String playerName, String playerUuid) {
        if (replay == null) {
            return false;
        }
        if (playerUuid != null && !playerUuid.isBlank()) {
            if (playerUuid.equals(replay.player_uuid)) {
                return true;
            }
            boolean legacy = replay.player_uuid == null || replay.player_uuid.isBlank();
            return legacy && playerName != null && playerName.equals(normalizePlayerName(replay.player_name));
        }
        return playerName != null && playerName.equals(normalizePlayerName(replay.player_name));
    }

    public static int deleteReplayForPlayer(String mapName, String playerName) {
        return deleteReplayForPlayer(mapName, playerName, "");
    }

    public static int deleteReplayForPlayer(String mapName, String playerName, String playerUuid) {
        if (mapName == null || mapName.isBlank()) {
            return 0;
        }
        if (Minehop.replayList != null) {
            int originalSize = Minehop.replayList.size();
            Minehop.replayList.removeIf(replay ->
                    replay != null
                            && mapName.equals(replay.map_name)
                            && replayBelongsTo(replay, playerName, playerUuid)
            );
            return originalSize - Minehop.replayList.size();
        }
        return 0;
    }

    public static int deleteReplaysForPlayer(String playerName) {
        return deleteReplaysForPlayer(playerName, "");
    }

    public static int deleteReplaysForPlayer(String playerName, String playerUuid) {
        if (Minehop.replayList != null) {
            int originalSize = Minehop.replayList.size();
            Minehop.replayList.removeIf(replay ->
                    replay != null
                            && replayBelongsTo(replay, playerName, playerUuid)
            );
            return originalSize - Minehop.replayList.size();
        }
        return 0;
    }

    public static Replay getReplay(String mapName) {
        if (mapName == null || mapName.isBlank()) {
            return null;
        }
        DataManager.RecordData recordData = DataManager.getRecord(mapName);
        if (recordData != null && recordData.name != null && !recordData.name.isBlank()) {
            return getReplayForRecord(recordData);
        }
        return null;
    }

    /**
     * The replay backing the named player's personal best on the map (see {@link #getPersonalBestReplay}).
     * Name-only lookup, kept for callers that have nothing better; prefer the UUID-aware methods.
     */
    @Deprecated
    public static Replay getReplay(String mapName, String playerName) {
        if (mapName == null || mapName.isBlank() || playerName == null || playerName.isBlank()) {
            return null;
        }
        return getReplayForRecord(DataManager.getPersonalRecord(playerName, mapName));
    }

    /**
     * The replay backing the player's CURRENT personal best on the map: the same player (UUID first; the name
     * only for legacy rows without one) and exactly the PB time. Null when there is no PB there or no saved run
     * matches it. Slower runs, invalidated runs and orphans (replays whose PB row is gone, e.g. after a times
     * purge, including the old impossible 0.0x s ones) are therefore never offered as anyone's PB.
     */
    public static Replay getPersonalBestReplay(String mapName, String playerName, String playerUuid) {
        if (mapName == null || mapName.isBlank()) {
            return null;
        }
        return getReplayForRecord(DataManager.getPersonalRecord(playerName, playerUuid, mapName));
    }

    /**
     * The PB row of a player named by a command argument. An online player is matched by UUID; otherwise the
     * row whose stored name matches, ignoring case (the fastest one if several accounts used that name).
     */
    public static DataManager.RecordData findPersonalRecordByName(MinecraftServer server, String mapName, String playerName) {
        if (mapName == null || mapName.isBlank() || playerName == null || playerName.isBlank() || Minehop.personalRecordList == null) {
            return null;
        }
        net.minecraft.server.level.ServerPlayer online = server == null ? null : server.getPlayerList().getPlayerByName(playerName);
        if (online != null) {
            return DataManager.getPersonalRecord(online.getScoreboardName(), online.getStringUUID(), mapName);
        }
        DataManager.RecordData best = null;
        for (DataManager.RecordData row : Minehop.personalRecordList) {
            if (row == null || !mapName.equals(row.map_name) || row.name == null || !row.name.equalsIgnoreCase(playerName)) {
                continue;
            }
            if (best == null || row.time < best.time) {
                best = row;
            }
        }
        return best;
    }

    /** True if the replay can be played back: it has frames that can be loaded and was not invalidated. */
    public static boolean isPlayable(Replay replay) {
        return replay != null && frameCount(replay) > 0 && unavailableReason(replay).isEmpty()
                && (replay.invalidated == null || replay.invalidated.isBlank());
    }

    /** Number of recorded frames (without loading them), including pre- and post-run frames. */
    public static int frameCount(Replay replay) {
        if (replay == null) {
            return 0;
        }
        PendingSave pending = pending(replay);
        if (pending != null) {
            return pending.knownFrames;
        }
        ReplayStore v2 = store();
        ReplayStore.Entry entry = v2 == null ? null : v2.entry(replay);
        if (entry != null) {
            return entry.frames();
        }
        return replay.replayEntries == null ? 0 : replay.replayEntries.size();
    }

    /** Why the run's frames can't be loaded (a corrupt, quarantined or missing file), or "" if they can. */
    public static String unavailableReason(Replay replay) {
        ReplayStore v2 = store();
        ReplayStore.Entry entry = v2 == null || replay == null ? null : v2.entry(replay);
        return entry == null ? "" : entry.unavailable();
    }

    /** {minX, minY, minZ, maxX, maxY, maxZ} of the run's route, or null if unknown. */
    public static double[] bounds(Replay replay) {
        if (pending(replay) != null) {
            return null;
        }
        ReplayStore v2 = store();
        ReplayStore.Entry entry = v2 == null || replay == null ? null : v2.entry(replay);
        if (entry != null) {
            return entry.bounds();
        }
        if (replay == null || replay.replayEntries == null) {
            return null;
        }
        double[] bounds = null;
        for (ReplayEntry frame : replay.replayEntries) {
            if (frame == null || !Double.isFinite(frame.x) || !Double.isFinite(frame.y) || !Double.isFinite(frame.z)) {
                continue;
            }
            if (bounds == null) {
                bounds = new double[]{frame.x, frame.y, frame.z, frame.x, frame.y, frame.z};
            } else {
                bounds[0] = Math.min(bounds[0], frame.x);
                bounds[1] = Math.min(bounds[1], frame.y);
                bounds[2] = Math.min(bounds[2], frame.z);
                bounds[3] = Math.max(bounds[3], frame.x);
                bounds[4] = Math.max(bounds[4], frame.y);
                bounds[5] = Math.max(bounds[5], frame.z);
            }
        }
        return bounds;
    }

    /**
     * The run's frames if they are in memory now (legacy store, the frame cache, a finish still being written), else
     * null; never touches the disk. Use {@link #loadFrames} to load them.
     */
    public static ReplayFrames framesIfLoaded(Replay replay) {
        if (replay == null || pending(replay) != null) {
            return null;
        }
        ReplayStore v2 = store();
        ReplayFrames frames = v2 == null ? null : v2.cached(replay);
        if (frames != null) {
            return frames;
        }
        return replay.replayEntries != null && !replay.replayEntries.isEmpty() ? toFrames(replay.replayEntries) : null;
    }

    /**
     * Gets the run's frames and hands them to {@code callback} on the server thread: right away if they are in memory,
     * otherwise once the replay store's reader thread has loaded them (the server thread never reads or decodes a
     * replay file). Null if they can't be loaded. Server thread only.
     */
    public static void loadFrames(Replay replay, Consumer<ReplayFrames> callback) {
        PendingSave pending = pending(replay);
        if (pending != null) {
            pending.waiting.add(callback); // within a second, when its post-run frames are complete
            return;
        }
        ReplayFrames frames = framesIfLoaded(replay);
        ReplayStore v2 = store();
        if (frames != null || v2 == null || replay == null) {
            callback.accept(frames);
            return;
        }
        v2.load(replay, callback);
    }

    /**
     * The run's MHRP file in the v2 store if it is written and readable, else null (the legacy store, a finish whose
     * file is still being written, a missing or quarantined file): replay streaming then encodes the frames instead.
     */
    public static Path writtenFile(Replay replay) {
        if (replay == null || pending(replay) != null) {
            return null;
        }
        ReplayStore v2 = store();
        return v2 == null ? null : v2.writtenFile(replay);
    }

    /**
     * The header a streamed copy of the run carries when its file is encoded from frames (see {@link #writtenFile}):
     * its identity, time and frame layout, none of the server-only fields (MhrpHeader#forClients).
     */
    public static net.nerdorg.minehop.replays.storage.MhrpHeader streamHeader(Replay replay, ReplayFrames frames) {
        net.nerdorg.minehop.replays.storage.MhrpHeader header = new net.nerdorg.minehop.replays.storage.MhrpHeader();
        header.replayId = replay.replay_id == null ? "" : replay.replay_id;
        header.mapName = replay.map_name == null ? "" : replay.map_name;
        header.playerUuid = replay.player_uuid == null ? "" : replay.player_uuid;
        header.playerName = replay.player_name == null ? "" : replay.player_name;
        header.time = replay.time;
        header.savedAt = replay.saved_at;
        header.preFrames = frames.preFrames();
        header.postFrames = frames.postFrames();
        header.flags = frames.tickStream() ? net.nerdorg.minehop.replays.storage.MhrpHeader.FLAG_TICK_STREAM : 0;
        return header;
    }

    /** A column copy of recorded frames (what the v2 store keeps and writes). */
    public static ReplayFrames toFrames(List<ReplayEntry> entries) {
        if (entries == null) {
            return ReplayFrames.EMPTY;
        }
        ReplayFrames.Builder builder = ReplayFrames.builder(entries.size());
        for (ReplayEntry entry : entries) {
            if (entry == null) {
                continue;
            }
            builder.add(entry.x, entry.y, entry.z, (float) entry.yrot, (float) entry.xrot, (int) Math.round(entry.jump_count),
                    (float) entry.last_jump_speed, (float) entry.efficiency, 0);
        }
        return builder.build();
    }

    /** One frame as a ReplayEntry (what ghosts, spectating and the path tools read). */
    public static ReplayEntry entryAt(ReplayFrames frames, int index) {
        return new ReplayEntry(frames.x(index), frames.y(index), frames.z(index), frames.pitch(index), frames.yaw(index),
                frames.jumpCount(index), frames.lastJumpSpeed(index), frames.efficiency(index));
    }

    /** The frames as a read-only list of ReplayEntry (each get creates the entry), e.g. for ReplayPathSimplifier. */
    public static List<ReplayEntry> asEntries(ReplayFrames frames) {
        return new java.util.AbstractList<>() {
            @Override
            public ReplayEntry get(int index) {
                return entryAt(frames, index);
            }

            @Override
            public int size() {
                return frames.size();
            }
        };
    }

    /**
     * Every PB on the map that has a playable replay backing it, fastest first, as (row, replay) pairs. One pass
     * over the replay list, so command suggestions can call it.
     */
    public static List<java.util.Map.Entry<DataManager.RecordData, Replay>> watchablePersonalBests(String mapName) {
        List<java.util.Map.Entry<DataManager.RecordData, Replay>> result = new ArrayList<>();
        if (mapName == null || mapName.isBlank() || Minehop.personalRecordList == null || Minehop.replayList == null) {
            return result;
        }
        List<Replay> onMap = new ArrayList<>();
        for (Replay replay : Minehop.replayList) {
            if (replay != null && mapName.equals(replay.map_name) && replay.player_name != null && isPlayable(replay)) {
                onMap.add(replay);
            }
        }
        for (DataManager.RecordData row : Minehop.personalRecordList) {
            if (row == null || !mapName.equals(row.map_name) || row.name == null || row.name.isBlank()) {
                continue;
            }
            Replay match = matchRecord(row, onMap);
            if (match != null) {
                result.add(java.util.Map.entry(row, match));
            }
        }
        result.sort(java.util.Comparator.comparingDouble(entry -> entry.getKey().time));
        return result;
    }

    public static Replay getReplayForRecord(DataManager.RecordData recordData) {
        if (recordData == null || recordData.map_name == null || recordData.map_name.isBlank() || recordData.name == null || recordData.name.isBlank()) {
            return null;
        }

        return Minehop.replayList == null ? null : matchRecord(recordData, Minehop.replayList);
    }

    /** The newest playable replay among {@code replays} that is the run of this PB/WR row (same player, same time). */
    private static Replay matchRecord(DataManager.RecordData recordData, List<Replay> replays) {
        Replay bestExactMatch = null;
        for (Replay replay : replays) {
            if (isRunOf(recordData, replay) && (bestExactMatch == null || replay.saved_at > bestExactMatch.saved_at)) {
                bestExactMatch = replay;
            }
        }
        return bestExactMatch;
    }

    /** True if the replay is a playable recording of the run that set this PB/WR row. */
    private static boolean isRunOf(DataManager.RecordData recordData, Replay replay) {
        if (replay == null || replay.map_name == null || replay.player_name == null || !recordData.map_name.equals(replay.map_name)) {
            return false;
        }
        if (!isPlayable(replay)) {
            return false;
        }
        // Prefer UUID identity (survives name changes) with legacy-name fallback.
        boolean samePlayer = recordData.uuid != null && !recordData.uuid.isBlank()
                && replay.player_uuid != null && !replay.player_uuid.isBlank()
                ? recordData.uuid.equals(replay.player_uuid)
                : recordData.name.equals(normalizePlayerName(replay.player_name));
        return samePlayer && timesMatch(replay.time, recordData.time);
    }

    /**
     * The replay of every map's current world record ({@link #getReplay(String)} for all maps at once): one pass
     * over the records and one over the replays.
     */
    public static java.util.Map<String, Replay> worldRecordReplays() {
        java.util.Map<String, DataManager.RecordData> records = new java.util.HashMap<>();
        if (Minehop.recordList != null) {
            for (DataManager.RecordData row : Minehop.recordList) {
                if (row == null || row.map_name == null || row.name == null || row.name.isBlank()) {
                    continue;
                }
                DataManager.RecordData existing = records.get(row.map_name);
                if (existing == null || row.time < existing.time) {
                    records.put(row.map_name, row);
                }
            }
        }
        java.util.Map<String, Replay> result = new java.util.HashMap<>();
        if (records.isEmpty() || Minehop.replayList == null) {
            return result;
        }
        for (Replay replay : Minehop.replayList) {
            DataManager.RecordData row = replay == null || replay.map_name == null ? null : records.get(replay.map_name);
            if (row == null || !isRunOf(row, replay)) {
                continue;
            }
            Replay current = result.get(replay.map_name);
            if (current == null || replay.saved_at > current.saved_at) {
                result.put(replay.map_name, replay);
            }
        }
        return result;
    }

    public static void saveRecordReplay(ServerLevel world, Replay replay) {
        saveReplay(world, replay);
    }

    public static void savePersonalBestReplay(ServerLevel world, Replay replay) {
        saveReplay(world, replay);
    }

    public static void saveReplay(ServerLevel world, Replay replay) {
        saveReplay(world, replay, Double.NaN, -1L);
    }

    /**
     * Stores a finished run (a copy of it: the caller's frame list is not kept). In the v2 store this costs the server
     * thread one column copy of the frames; the file is encoded and written by the store's writer thread.
     *
     * @param serverTime  the server-measured run time in seconds (NaN if unknown), kept in the run's file
     * @param clientTicks client ticks the run took (-1 if unknown), kept in the run's file
     */
    public static void saveReplay(ServerLevel world, Replay replay, double serverTime, long clientTicks) {
        long start = System.nanoTime();
        if (world == null || replay == null || replay.map_name == null || replay.map_name.isBlank()) {
            return;
        }
        if (replay.player_name == null || replay.player_name.isBlank()) {
            return;
        }
        if (replay.replayEntries == null || replay.replayEntries.isEmpty()) {
            return;
        }
        if (!Double.isFinite(replay.time) || replay.time <= 0.0D) {
            return;
        }

        if (Minehop.replayList == null) {
            Minehop.replayList = new ArrayList<>();
        }

        String id = replay.replay_id == null || replay.replay_id.isBlank() ? UUID.randomUUID().toString() : replay.replay_id;
        long savedAt = replay.saved_at > 0L ? replay.saved_at : System.currentTimeMillis();
        if (mode == StoreMode.V2) {
            if (store == null) {
                Minehop.LOGGER.error("The replay store is not available; {}'s run on {} was not saved", replay.player_name, replay.map_name);
                return;
            }
            Replay stored = new Replay(id, replay.map_name, replay.player_name, replay.player_uuid, replay.time, savedAt, null);
            stored.ac_flags = replay.ac_flags == null ? "" : replay.ac_flags;
            stored.invalidated = replay.invalidated == null ? "" : replay.invalidated;
            Minehop.replayList.add(stored);
            // A shallow copy (recorded frames are never changed afterwards); the store's writer thread turns it into
            // columns, encodes and writes it, so this costs the same for a 20-minute run as for a 20-second one.
            store.addRun(stored, new ArrayList<>(replay.replayEntries), serverTime, clientTicks, 0);
            long nanos = System.nanoTime() - start;
            store.stats().lastFinishNanos = nanos;
            store.stats().maxFinishNanos = Math.max(store.stats().maxFinishNanos, nanos);
            return;
        }

        Replay stored = new Replay(
                id,
                replay.map_name,
                replay.player_name,
                replay.player_uuid,
                replay.time,
                savedAt,
                copyReplayEntries(replay.replayEntries)
        );
        stored.ac_flags = replay.ac_flags == null ? "" : replay.ac_flags;
        stored.invalidated = replay.invalidated == null ? "" : replay.invalidated;
        Minehop.replayList.add(stored);

        saveRecordReplaysAsync(world, Minehop.replayList);
    }

    /**
     * Starts saving a finished run whose frames are still being recorded (see {@link PendingSave}): its metadata goes
     * into the run list now, so the finish (PB, WR, ghost) is handled at once, and the frames are stored when the
     * recorder completes them. {@code knownFrames}: frames recorded up to the finish (shown until then). Null if the run
     * can't be saved.
     *
     * @param serverTime  the server-measured run time in seconds (NaN if unknown), kept in the run's file
     * @param clientTicks client ticks the run took (-1 if unknown), kept in the run's file
     * @param headerFlags MhrpHeader flags for the run's file
     */
    public static PendingSave beginSave(ServerLevel world, Replay replay, double serverTime, long clientTicks, int headerFlags,
                                        int knownFrames) {
        if (world == null || replay == null || replay.map_name == null || replay.map_name.isBlank()
                || replay.player_name == null || replay.player_name.isBlank()
                || !Double.isFinite(replay.time) || replay.time <= 0.0D || knownFrames <= 0) {
            return null;
        }
        if (mode == StoreMode.V2 && store == null) {
            Minehop.LOGGER.error("The replay store is not available; {}'s run on {} was not saved", replay.player_name, replay.map_name);
            return null;
        }
        if (Minehop.replayList == null) {
            Minehop.replayList = new ArrayList<>();
        }
        String id = replay.replay_id == null || replay.replay_id.isBlank() ? UUID.randomUUID().toString() : replay.replay_id;
        long savedAt = replay.saved_at > 0L ? replay.saved_at : System.currentTimeMillis();
        Replay stored = new Replay(id, replay.map_name, replay.player_name, replay.player_uuid, replay.time, savedAt, null);
        stored.ac_flags = replay.ac_flags == null ? "" : replay.ac_flags;
        stored.invalidated = replay.invalidated == null ? "" : replay.invalidated;
        PendingSave pending = new PendingSave(stored, world, serverTime, clientTicks, headerFlags, knownFrames);
        PENDING_RUNS.put(id, pending);
        Minehop.replayList.add(stored);
        return pending;
    }

    /** True if the run's frames are still being completed (see {@link #beginSave}). */
    public static boolean isSavePending(Replay replay) {
        return pending(replay) != null;
    }

    private static PendingSave pending(Replay replay) {
        if (replay == null || replay.replay_id == null || PENDING_RUNS.isEmpty()) {
            return null;
        }
        PendingSave pending = PENDING_RUNS.get(replay.replay_id);
        return pending != null && pending.stored == replay ? pending : null;
    }

    /**
     * Legacy store: synchronous atomic, backed-up, version-enveloped write of the whole store (see JsonStorage) on
     * the calling thread, waiting for a background write in progress. V2 store: persists status changes and removals
     * and waits up to 30 s for the store's writer. Minehop itself only calls this at shutdown.
     *
     * @deprecated nothing needs to save the whole replay list any more; use {@link #saveRecordReplaysAsync}.
     */
    @Deprecated
    public static void saveRecordReplays(ServerLevel world, List<Replay> replays) {
        saveRecordReplaysChecked(world, replays);
    }

    /**
     * Same as {@link #saveRecordReplays}, returning false if the write failed.
     *
     * @deprecated see {@link #saveRecordReplays}.
     */
    @Deprecated
    public static boolean saveRecordReplaysChecked(ServerLevel world, List<Replay> replays) {
        if (world == null) {
            return false;
        }
        if (mode == StoreMode.V2) {
            if (store == null) {
                return false;
            }
            store.sync(replays == null ? Minehop.replayList : replays);
            return store.flush(30_000L);
        }
        List<Replay> safeReplays = replays == null ? new ArrayList<>() : replays;
        return writeStore(storePath(world), SAVE_SEQUENCE.incrementAndGet(), safeReplays);
    }

    /**
     * Persists changes to the run list and returns immediately (no file IO on the calling thread). Must be called on
     * the server thread, which owns {@code replays}. Legacy store: queues a save of the whole store (a shallow copy of
     * the list; saved replays are never modified afterwards); saves queued while one is pending coalesce into the
     * newest and a failed write is retried. V2 store: removed runs are moved out of the store and changed statuses
     * (invalidations) written, by the store's writer thread.
     */
    public static void saveRecordReplaysAsync(ServerLevel world, List<Replay> replays) {
        if (world == null) {
            return;
        }
        if (mode == StoreMode.V2) {
            if (store != null) {
                store.sync(replays == null ? Minehop.replayList : replays);
            }
            return;
        }
        queueLegacySave(world, replays);
    }

    private static long queueLegacySave(ServerLevel world, List<Replay> replays) {
        long sequence = SAVE_SEQUENCE.incrementAndGet();
        LegacySave save = new LegacySave(storePath(world), sequence,
                replays == null ? new ArrayList<>() : new ArrayList<>(replays));
        if (PENDING_SAVE.getAndSet(save) == null) {
            WRITER.execute(ReplayManager::drainPendingSave);
        }
        return sequence;
    }

    private static void drainPendingSave() {
        LEGACY_WRITING.set(true);
        try {
            LegacySave save = PENDING_SAVE.getAndSet(null);
            if (save == null) {
                return;
            }
            boolean written = false;
            try {
                written = writeStore(save.file(), save.sequence(), save.snapshot());
            } catch (Throwable error) {
                Minehop.LOGGER.error("Background replay save failed", error);
            }
            if (written || JsonStorage.isLoadFailed(save.file())) {
                return; // done, or saving is disabled for this file (logged by JsonStorage); retrying can't help
            }
            // Keep it pending unless something newer was queued meanwhile, and try again later.
            if (PENDING_SAVE.compareAndSet(null, save)) {
                Minehop.LOGGER.warn("Replay save failed; retrying in {} s", RETRY_DELAY_SECONDS);
                WRITER.schedule(ReplayManager::drainPendingSave, RETRY_DELAY_SECONDS, TimeUnit.SECONDS);
            }
        } finally {
            LEGACY_WRITING.set(false);
        }
    }

    /** Every write of the store goes through here: one at a time, never replacing a newer state with an older one. */
    private static boolean writeStore(Path file, long sequence, List<Replay> replays) {
        synchronized (WRITE_LOCK) {
            Long written = WRITTEN_SEQUENCE.get(file);
            if (written != null && written >= sequence) {
                return true; // a newer state of the store is already on disk
            }
            if (!JsonStorage.writeAtomic(file, SCHEMA_VERSION, replays)) {
                return false;
            }
            WRITTEN_SEQUENCE.put(file, sequence);
            return true;
        }
    }

    private static Path storePath(ServerLevel world) {
        return legacyStorePath(world.getServer());
    }

    /** The legacy (version 1) store file, minehop_replays.json at the world root. */
    public static Path legacyStorePath(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve(REPLAYS_FILE).toAbsolutePath().normalize();
    }

    // ------------------------------------------------------------------------------------------
    // Legacy store hand-over (LegacyMigration)
    // ------------------------------------------------------------------------------------------

    /** True if the legacy store was read without errors this session (the precondition for migrating it). */
    public static boolean legacyLoadedCompletely() {
        return mode == StoreMode.LEGACY && legacyLoadedCompletely;
    }

    /** True if the legacy writer has nothing queued, scheduled for a retry or in progress. */
    public static boolean legacyWriterIdle() {
        return PENDING_SAVE.get() == null && !LEGACY_WRITING.get();
    }

    /**
     * Switches this session to the v2 store (LegacyMigration, on the server thread, with the legacy writer idle):
     * from now on saves go to {@code v2} only.
     */
    public static void switchToV2(ReplayStore v2) {
        store = v2;
        mode = StoreMode.V2;
    }

    /** Undoes a {@link #switchToV2} made for a check that failed (nothing was written to the v2 store). */
    public static void switchBackToLegacy() {
        store = null;
        mode = StoreMode.LEGACY;
    }

    public static List<Replay> loadRecordReplays(ServerLevel world) {
        Path worldDir = world.getServer().getWorldPath(LevelResource.ROOT);
        // Crash-proof read with .corrupt quarantine + .bak fallback; null = no data yet.
        return JsonStorage.readData(worldDir.resolve(REPLAYS_FILE), replayListType);
    }

    public static void register() {
        // Once per server, not per dimension: the replay file lives at the server root (see DataManager).
        Services.EVENTS.onServerLevelLoad(((server, world) -> {
            if (world.dimension() != net.minecraft.world.level.Level.OVERWORLD) {
                return;
            }
            openStore(server, world);
            // L3: backfill UUIDs onto legacy replays via the user cache, then persist if changed.
            if (backfillReplayUuids(server)) {
                saveRecordReplaysAsync(world, Minehop.replayList);
            }
        }));

        Services.EVENTS.onServerStarted(LegacyMigration::onServerStarted);
        Services.EVENTS.onServerTickEnd(LegacyMigration::tick);
        Services.EVENTS.onServerStopping(LegacyMigration::onServerStopping);

        Services.EVENTS.onServerLevelUnload(((server, world) -> {
            if (world.dimension() != net.minecraft.world.level.Level.OVERWORLD) {
                return;
            }
            if (!PENDING_RUNS.isEmpty()) {
                // The recorder completes every pending run when the server starts stopping; this is a safety net.
                Minehop.LOGGER.warn("Replay store: {} finished run(s) were still being recorded at shutdown; their replays are lost",
                        PENDING_RUNS.size());
                for (PendingSave pending : new ArrayList<>(PENDING_RUNS.values())) {
                    pending.complete(null);
                }
            }
            if (mode == StoreMode.V2) {
                if (store != null) {
                    store.sync(Minehop.replayList);
                    store.close();
                }
                store = null;
                return;
            }
            saveRecordReplaysChecked(world, Minehop.replayList);
        }));
    }

    /**
     * Picks the world's store: the v2 store once it exists (its marker is written last by the migration) or when
     * there is no legacy store to migrate; otherwise the legacy store, which LegacyMigration then converts in the
     * background after the server has started.
     */
    private static void openStore(MinecraftServer server, ServerLevel world) {
        store = null;
        legacyLoadedCompletely = false;
        Minehop.replayList = new ArrayList<>();
        Path root = ReplayStore.root(server);
        Path legacy = legacyStorePath(server);
        boolean legacyExists = Files.exists(legacy) || Files.exists(legacy.resolveSibling(REPLAYS_FILE + ".bak"));
        boolean v2Active = ReplayStore.isActive(root);
        if (v2Active || !legacyExists) {
            mode = StoreMode.V2;
            try {
                ReplayStore.OpenReport report = new ReplayStore.OpenReport();
                store = ReplayStore.open(server, root, report);
                if (!v2Active) {
                    ReplayStore.Marker marker = new ReplayStore.Marker();
                    marker.created_at = System.currentTimeMillis();
                    marker.created_by = "new world (no legacy replay store)";
                    store.writeMarker(marker);
                }
                Minehop.replayList = store.runs();
                Minehop.LOGGER.info("Replay store: {}", report);
                if (legacyExists) {
                    Minehop.LOGGER.info("Replay store: {} (the old store) is kept as it is and no longer used", REPLAYS_FILE);
                }
            } catch (Exception e) {
                store = null;
                Minehop.LOGGER.error("The replay store in {} could not be opened; replays are unavailable and not saved this session", root, e);
            }
            return;
        }

        mode = StoreMode.LEGACY;
        long loadStart = System.nanoTime();
        List<Replay> newReplayList = loadRecordReplays(world);
        // Only a store that loaded completely may be migrated (a null list from an existing file is a failed load).
        legacyLoadedCompletely = newReplayList != null && !JsonStorage.isLoadFailed(legacy);
        if (newReplayList != null) {
            for (Replay replay : newReplayList) {
                if (replay == null || replay.map_name == null || replay.map_name.isBlank()) {
                    continue;
                }
                if (replay.replayEntries == null || replay.replayEntries.isEmpty()) {
                    continue;
                }
                if (!Double.isFinite(replay.time) || replay.time <= 0.0D) {
                    continue;
                }
                if (replay.replay_id == null || replay.replay_id.isBlank()) {
                    replay.replay_id = UUID.randomUUID().toString();
                }
                if (replay.saved_at <= 0L) {
                    replay.saved_at = System.currentTimeMillis();
                }
                replay.player_name = normalizePlayerName(replay.player_name);
                if (replay.player_uuid == null) {
                    replay.player_uuid = "";
                }
                if (replay.invalidated == null) {
                    replay.invalidated = "";
                }
                Minehop.replayList.add(replay);
            }
        }
        Minehop.LOGGER.info("Replay store: {} runs loaded from {} (legacy store) in {} ms", Minehop.replayList.size(),
                REPLAYS_FILE, (System.nanoTime() - loadStart) / 1_000_000L);
    }

    public static List<ReplayEntry> copyReplayEntries(List<ReplayEntry> source) {
        List<ReplayEntry> copied = new ArrayList<>();
        if (source == null) {
            return copied;
        }
        for (ReplayEntry entry : source) {
            if (entry == null) {
                continue;
            }
            copied.add(new ReplayEntry(
                    entry.x,
                    entry.y,
                    entry.z,
                    entry.xrot,
                    entry.yrot,
                    entry.jump_count,
                    entry.last_jump_speed,
                    entry.efficiency
            ));
        }
        return copied;
    }

    private static boolean backfillReplayUuids(MinecraftServer server) {
        if (server == null || Minehop.replayList == null) {
            return false;
        }
        // Same cache-only lookup as the record backfill: no Mojang round trips on the server thread.
        java.util.Map<String, String> cachedUuids = net.nerdorg.minehop.data.DataManager.loadCachedUuidsByName(server);
        if (cachedUuids.isEmpty()) {
            return false;
        }
        boolean changed = false;
        for (Replay replay : Minehop.replayList) {
            if (replay == null || replay.player_name == null || replay.player_name.isBlank()) {
                continue;
            }
            if (replay.player_uuid != null && !replay.player_uuid.isBlank()) {
                continue;
            }
            String uuid = cachedUuids.get(replay.player_name.toLowerCase(java.util.Locale.ROOT));
            if (uuid != null) {
                replay.player_uuid = uuid;
                changed = true;
            }
        }
        return changed;
    }

    private static String normalizePlayerName(String rawName) {
        return rawName == null ? "" : rawName;
    }

    private static boolean timesMatch(double first, double second) {
        return Math.abs(first - second) <= 0.00001D;
    }
}
