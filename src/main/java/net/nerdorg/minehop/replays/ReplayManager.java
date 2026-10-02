package net.nerdorg.minehop.replays;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;
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

public class ReplayManager {
    private static final Type replayListType = new TypeToken<List<Replay>>(){}.getType();
    private static final String REPLAYS_FILE = "minehop_replays.json";
    // Bump when the on-disk Replay shape changes; migrate in loadRecordReplays/register.
    private static final int SCHEMA_VERSION = 1;

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

    public static Replay getReplay(String mapName, String playerName) {
        if (mapName == null || mapName.isBlank() || playerName == null || playerName.isBlank()) {
            return null;
        }
        Replay bestMatch = null;
        if (Minehop.replayList != null) {
            for (Replay replay : Minehop.replayList) {
                if (replay == null || replay.map_name == null || replay.replayEntries == null || replay.replayEntries.isEmpty()) {
                    continue;
                }
                if (mapName.equals(replay.map_name) && playerName.equals(normalizePlayerName(replay.player_name))) {
                    if (bestMatch == null || replay.time < bestMatch.time || (timesMatch(replay.time, bestMatch.time) && replay.saved_at > bestMatch.saved_at)) {
                        bestMatch = replay;
                    }
                }
            }
        }
        return bestMatch;
    }

    public static Replay getReplayForRecord(DataManager.RecordData recordData) {
        if (recordData == null || recordData.map_name == null || recordData.map_name.isBlank() || recordData.name == null || recordData.name.isBlank()) {
            return null;
        }

        Replay bestExactMatch = null;
        if (Minehop.replayList != null) {
            for (Replay replay : Minehop.replayList) {
                if (replay == null || replay.map_name == null || replay.player_name == null || replay.replayEntries == null || replay.replayEntries.isEmpty()) {
                    continue;
                }
                if (!recordData.map_name.equals(replay.map_name)) {
                    continue;
                }
                // Prefer UUID identity (survives name changes) with legacy-name fallback.
                boolean samePlayer = recordData.uuid != null && !recordData.uuid.isBlank()
                        && replay.player_uuid != null && !replay.player_uuid.isBlank()
                        ? recordData.uuid.equals(replay.player_uuid)
                        : recordData.name.equals(normalizePlayerName(replay.player_name));
                if (!samePlayer) {
                    continue;
                }
                if (timesMatch(replay.time, recordData.time)) {
                    if (bestExactMatch == null || replay.saved_at > bestExactMatch.saved_at) {
                        bestExactMatch = replay;
                    }
                    continue;
                }
            }
        }
        return bestExactMatch;
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
        Minehop.replayList.add(stored);

        saveRecordReplays(world, Minehop.replayList);
    }

    /** Atomic, backed-up, version-enveloped write (see JsonStorage). Signature kept for other mods. */
    public static void saveRecordReplays(ServerLevel world, List<Replay> replays) {
        saveRecordReplaysChecked(world, replays);
    }

    /** Same as {@link #saveRecordReplays}, returning false if the write failed. */
    public static boolean saveRecordReplaysChecked(ServerLevel world, List<Replay> replays) {
        if (world == null) {
            return false;
        }
        List<Replay> safeReplays = replays == null ? new ArrayList<>() : replays;
        MinecraftServer server = world.getServer();
        Path worldDir = server.getWorldPath(LevelResource.ROOT);
        return JsonStorage.writeAtomic(worldDir.resolve(REPLAYS_FILE), SCHEMA_VERSION, safeReplays);
    }

    public static List<Replay> loadRecordReplays(ServerLevel world) {
        Path worldDir = world.getServer().getWorldPath(LevelResource.ROOT);
        // Crash-proof read with .corrupt quarantine + .bak fallback; null = no data yet.
        return JsonStorage.readData(worldDir.resolve(REPLAYS_FILE), replayListType);
    }

    public static void register() {
        // Once per server, not per dimension: the replay file lives at the server root (see DataManager).
        ServerLevelEvents.LOAD.register(((server, world) -> {
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
                    Minehop.replayList.add(replay);
                }
            }
            // L3: backfill UUIDs onto legacy replays via the user cache, then persist if changed.
            if (backfillReplayUuids(server)) {
                saveRecordReplays(world, Minehop.replayList);
            }
        }));

        ServerLevelEvents.UNLOAD.register(((server, world) -> {
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
