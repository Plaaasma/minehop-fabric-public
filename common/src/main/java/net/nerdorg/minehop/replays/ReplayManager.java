package net.nerdorg.minehop.replays;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.nerdorg.minehop.platform.Services;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.data.DataManager;
import net.nerdorg.minehop.util.JsonStorage;

import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class ReplayManager {
    private static final Type replayListType = new TypeToken<List<Replay>>(){}.getType();
    private static final String REPLAYS_FILE = "minehop_replays.json";
    // Bump when the on-disk Replay shape changes; migrate in loadRecordReplays/register.
    private static final int SCHEMA_VERSION = 1;

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
    private static final AtomicReference<PendingSave> PENDING_SAVE = new AtomicReference<>();

    private record PendingSave(Path file, long sequence, List<Replay> snapshot) {
    }

    public static class SSJEntry {
        public double jump_count;
        public double last_jump_speed;
        public double efficiency;

        public SSJEntry(double jump_count, double last_jump_speed, double efficiency) {
            this.jump_count = jump_count;
            this.last_jump_speed = last_jump_speed;
            this.efficiency = efficiency;
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

    /** True if the replay can be played back: it has frames and was not invalidated. */
    public static boolean isPlayable(Replay replay) {
        return replay != null && replay.replayEntries != null && !replay.replayEntries.isEmpty()
                && (replay.invalidated == null || replay.invalidated.isBlank());
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
        if (replay == null || replay.map_name == null || replay.player_name == null || !isPlayable(replay)) {
            return false;
        }
        if (!recordData.map_name.equals(replay.map_name)) {
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

        Replay stored = new Replay(
                replay.replay_id == null || replay.replay_id.isBlank() ? UUID.randomUUID().toString() : replay.replay_id,
                replay.map_name,
                replay.player_name,
                replay.player_uuid,
                replay.time,
                replay.saved_at > 0L ? replay.saved_at : System.currentTimeMillis(),
                copyReplayEntries(replay.replayEntries)
        );
        stored.ac_flags = replay.ac_flags == null ? "" : replay.ac_flags;
        stored.invalidated = replay.invalidated == null ? "" : replay.invalidated;
        Minehop.replayList.add(stored);

        saveRecordReplaysAsync(world, Minehop.replayList);
    }

    /**
     * Synchronous atomic, backed-up, version-enveloped write (see JsonStorage) on the calling thread. Waits for a
     * background write in progress. Signature kept for other mods; Minehop itself only uses it at shutdown.
     */
    public static void saveRecordReplays(ServerLevel world, List<Replay> replays) {
        saveRecordReplaysChecked(world, replays);
    }

    /** Same as {@link #saveRecordReplays}, returning false if the write failed. */
    public static boolean saveRecordReplaysChecked(ServerLevel world, List<Replay> replays) {
        if (world == null) {
            return false;
        }
        List<Replay> safeReplays = replays == null ? new ArrayList<>() : replays;
        return writeStore(storePath(world), SAVE_SEQUENCE.incrementAndGet(), safeReplays);
    }

    /**
     * Queue a save of the store and return immediately (no file IO on the calling thread). Must be called on the
     * server thread, which owns {@code replays}: the list is copied here (a shallow copy; saved replays are never
     * modified afterwards), so later changes don't race the writer. Saves queued while one is pending coalesce
     * into the newest; a failed write is retried.
     */
    public static void saveRecordReplaysAsync(ServerLevel world, List<Replay> replays) {
        if (world == null) {
            return;
        }
        PendingSave save = new PendingSave(storePath(world), SAVE_SEQUENCE.incrementAndGet(),
                replays == null ? new ArrayList<>() : new ArrayList<>(replays));
        if (PENDING_SAVE.getAndSet(save) == null) {
            WRITER.execute(ReplayManager::drainPendingSave);
        }
    }

    private static void drainPendingSave() {
        PendingSave save = PENDING_SAVE.getAndSet(null);
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
        return world.getServer().getWorldPath(LevelResource.ROOT).resolve(REPLAYS_FILE).toAbsolutePath().normalize();
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
            Minehop.replayList = new ArrayList<>();
            List<Replay> newReplayList = loadRecordReplays(world);
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
            // L3: backfill UUIDs onto legacy replays via the user cache, then persist if changed.
            if (backfillReplayUuids(server)) {
                saveRecordReplaysAsync(world, Minehop.replayList);
            }
        }));

        Services.EVENTS.onServerLevelUnload(((server, world) -> {
            if (world.dimension() != net.minecraft.world.level.Level.OVERWORLD) {
                return;
            }
            saveRecordReplays(world, Minehop.replayList);
        }));
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
