package net.nerdorg.minehop.replays;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.Vec3d;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.data.DataManager;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

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
        public String map_name;
        public String player_name;
        public double time;
        public List<ReplayEntry> replayEntries;

        public Replay(String map_name, String player_name, double time, List<ReplayEntry> replayEntries) {
            this.map_name = map_name;
            this.player_name = player_name;
            this.time = time;
            this.replayEntries = replayEntries;
        }
    }

    public static void deleteReplays(String mapName) {
        if (Minehop.replayList != null) {
            Minehop.replayList.removeIf(replay -> replay.map_name.equals(mapName));
        }
    }

    public static Replay getReplay(String mapName) {
        if (Minehop.replayList != null) {
            for (Replay replay : Minehop.replayList) {
                if (replay.map_name.equals(mapName)) {
                    return replay;
                }
            }
        }

        return null;
    }

    public static void saveRecordReplay(ServerWorld world, Replay replay) {
        Gson gson = new Gson();
        deleteReplays(replay.map_name);
        Minehop.replayList.add(replay);

        if (Minehop.replayList != null) {
            String jsonData = gson.toJson(Minehop.replayList);

            MinecraftServer server = world.getServer();
            Path worldDir = server.getSavePath(WorldSavePath.ROOT);

            try {
                Files.write(worldDir.resolve("minehop_replays.json"), jsonData.getBytes());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void saveRecordReplays(ServerWorld world, List<Replay> replays) {
        Gson gson = new Gson();
        String jsonData = gson.toJson(replays);

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
                Minehop.replayList = newReplayList;
            }
        }));

        ServerWorldEvents.UNLOAD.register(((server, world) -> {
            saveRecordReplays(world, Minehop.replayList);
        }));
    }
}
