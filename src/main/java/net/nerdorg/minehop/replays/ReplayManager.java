package net.nerdorg.minehop.replays;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.WorldSavePath;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.data.DataManager;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ReplayManager {
    private static final Type replayListType = new TypeToken<List<Replay>>(){}.getType();

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
        public double time;
        public long saved_at;
        public List<ReplayEntry> replayEntries;

        public Replay() {
        }

        public Replay(String map_name, String player_name, double time, List<ReplayEntry> replayEntries) {
            this(UUID.randomUUID().toString(), map_name, player_name, time, System.currentTimeMillis(), replayEntries);
        }

        public Replay(String replay_id, String map_name, String player_name, double time, long saved_at, List<ReplayEntry> replayEntries) {
            this.replay_id = replay_id;
            this.map_name = map_name;
            this.player_name = player_name;
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

    public static int deleteReplayForPlayer(String mapName, String playerName) {
        if (mapName == null || mapName.isBlank() || playerName == null || playerName.isBlank()) {
            return 0;
        }
        if (Minehop.replayList != null) {
            int originalSize = Minehop.replayList.size();
            Minehop.replayList.removeIf(replay ->
                    replay != null
                            && mapName.equals(replay.map_name)
                            && playerName.equals(normalizePlayerName(replay.player_name))
            );
            return originalSize - Minehop.replayList.size();
        }
        return 0;
    }

    public static int deleteReplaysForPlayer(String playerName) {
        if (playerName == null || playerName.isBlank()) {
            return 0;
        }
        if (Minehop.replayList != null) {
            int originalSize = Minehop.replayList.size();
            Minehop.replayList.removeIf(replay ->
                    replay != null
                            && playerName.equals(normalizePlayerName(replay.player_name))
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
                if (!recordData.map_name.equals(replay.map_name) || !recordData.name.equals(normalizePlayerName(replay.player_name))) {
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

    public static void saveRecordReplay(ServerWorld world, Replay replay) {
        saveReplay(world, replay);
    }

    public static void savePersonalBestReplay(ServerWorld world, Replay replay) {
        saveReplay(world, replay);
    }

    public static void saveReplay(ServerWorld world, Replay replay) {
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

        Minehop.replayList.add(new Replay(
                replay.replay_id == null || replay.replay_id.isBlank() ? UUID.randomUUID().toString() : replay.replay_id,
                replay.map_name,
                replay.player_name,
                replay.time,
                replay.saved_at > 0L ? replay.saved_at : System.currentTimeMillis(),
                copyReplayEntries(replay.replayEntries)
        ));

        saveRecordReplays(world, Minehop.replayList);
    }

    public static void saveRecordReplays(ServerWorld world, List<Replay> replays) {
        if (world == null) {
            return;
        }
        Gson gson = new Gson();
        List<Replay> safeReplays = replays == null ? new ArrayList<>() : replays;
        String jsonData = gson.toJson(safeReplays);

        MinecraftServer server = world.getServer();
        Path worldDir = server.getSavePath(WorldSavePath.ROOT);

        try {
            Files.write(worldDir.resolve("minehop_replays.json"), jsonData.getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static List<Replay> loadRecordReplays(ServerWorld world) {
        Path worldDir = world.getServer().getSavePath(WorldSavePath.ROOT);
        Path filePath = worldDir.resolve("minehop_replays.json");

        try {
            String jsonData = new String(Files.readAllBytes(filePath));
            Gson gson = new Gson();
            return gson.fromJson(jsonData, replayListType); // Replace Object.class with your data type
        } catch (IOException e) {
            e.printStackTrace();
            return null; // Handle the case where the data doesn't exist yet
        }
    }

    public static void register() {
        ServerWorldEvents.LOAD.register(((server, world) -> {
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
                    Minehop.replayList.add(replay);
                }
            }
        }));

        ServerWorldEvents.UNLOAD.register(((server, world) -> {
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

    private static String normalizePlayerName(String rawName) {
        return rawName == null ? "" : rawName;
    }

    private static boolean timesMatch(double first, double second) {
        return Math.abs(first - second) <= 0.00001D;
    }
}
