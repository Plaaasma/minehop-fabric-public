package net.nerdorg.minehop.data;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.WorldSavePath;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.networking.PacketHandler;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class DataManager {
    private static Type mapListType = new TypeToken<List<MapData>>(){}.getType();
    private static Type recordListType = new TypeToken<List<RecordData>>(){}.getType();

    public static class MapData {
        public String name;
        public double x;
        public double y;
        public double z;
        public double xrot;
        public double yrot;

        public MapData() {
        }

        public MapData(String name, double x, double y, double z, double xrot, double yrot) {
            this.name = name;
            this.x = x;
            this.y = y;
            this.z = z;
            this.xrot = xrot;
            this.yrot = yrot;
        }
    }

    public static class RecordData {
        public String name;
        public String map_name;
        public double time;

        public RecordData() {

        }

        public RecordData(String name, String map_name, double time) {
            this.name = name;
            this.map_name = map_name;
            this.time = time;
        }
    }

    public static RecordData getPersonalRecord(String playerName, String mapName) {
        if (Minehop.personalRecordList != null) {
            for (RecordData recordData : Minehop.personalRecordList) {
                if (recordData.name.equals(playerName) && recordData.map_name.equals(mapName)) {
                    return recordData;
                }
            }
        }

        return null;
    }

    public static RecordData getRecord(String mapName) {
        if (Minehop.recordList != null) {
            for (RecordData recordData : Minehop.recordList) {
                if (recordData.map_name.equals(mapName)) {
                    return recordData;
                }
            }
        }

        return null;
    }

    public static MapData getMap(String mapName) {
        if (Minehop.mapList != null) {
            for (MapData mapData : Minehop.mapList) {
                if (mapData.name.equals(mapName)) {
                    return mapData;
                }
            }
        }

        return null;
    }

    public static void register() {
        ServerWorldEvents.LOAD.register(((server, world) -> {
            Minehop.mapList = new ArrayList<>();
            Minehop.recordList = new ArrayList<>();
            Minehop.personalRecordList = new ArrayList<>();
            List<DataManager.MapData> newMapList = DataManager.loadMapData(world);
            List<DataManager.RecordData> newPersonalRecordList = DataManager.loadPersonalRecordData(world);
            List<DataManager.RecordData> newRecordList = DataManager.loadRecordData(world);
            if (newMapList != null) {
                Minehop.mapList = newMapList;
            }
            if (newPersonalRecordList != null) {
                Minehop.personalRecordList = newPersonalRecordList;
            }
            if (newRecordList != null) {
                Minehop.recordList = newRecordList;
            }
        }));

        ServerWorldEvents.UNLOAD.register(((server, world) -> {
            DataManager.saveMapData(world, Minehop.mapList);
            DataManager.savePersonalRecordData(world, Minehop.personalRecordList);
            DataManager.saveRecordData(world, Minehop.recordList);
        }));
    }

    public static void savePersonalRecordData(ServerWorld world, List<RecordData> data) {
        Gson gson = new Gson();
        String jsonData = gson.toJson(data);

        MinecraftServer server = world.getServer();
        Path worldDir = server.getSavePath(WorldSavePath.ROOT);

        try {
            Files.write(worldDir.resolve("minehop_pbs.json"), jsonData.getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void saveRecordData(ServerWorld world, List<RecordData> data) {
        Gson gson = new Gson();
        String jsonData = gson.toJson(data);

        MinecraftServer server = world.getServer();
        Path worldDir = server.getSavePath(WorldSavePath.ROOT);

        try {
            Files.write(worldDir.resolve("minehop_records.json"), jsonData.getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void saveMapData(ServerWorld world, List<MapData> data) {
        Gson gson = new Gson();
        String jsonData = gson.toJson(data);

        MinecraftServer server = world.getServer();
        Path worldDir = server.getSavePath(WorldSavePath.ROOT);

        try {
            Files.write(worldDir.resolve("minehop_maps.json"), jsonData.getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static List<RecordData> loadPersonalRecordData(ServerWorld world) {
        Path worldDir = world.getServer().getSavePath(WorldSavePath.ROOT);
        Path filePath = worldDir.resolve("minehop_pbs.json");

        try {
            String jsonData = new String(Files.readAllBytes(filePath));
            Gson gson = new Gson();
            return gson.fromJson(jsonData, recordListType); // Replace Object.class with your data type
        } catch (IOException e) {
            e.printStackTrace();
            return null; // Handle the case where the data doesn't exist yet
        }
    }

    public static List<RecordData> loadRecordData(ServerWorld world) {
        Path worldDir = world.getServer().getSavePath(WorldSavePath.ROOT);
        Path filePath = worldDir.resolve("minehop_records.json");

        try {
            String jsonData = new String(Files.readAllBytes(filePath));
            Gson gson = new Gson();
            return gson.fromJson(jsonData, recordListType); // Replace Object.class with your data type
        } catch (IOException e) {
            e.printStackTrace();
            return null; // Handle the case where the data doesn't exist yet
        }
    }

    public static List<MapData> loadMapData(ServerWorld world) {
        Path worldDir = world.getServer().getSavePath(WorldSavePath.ROOT);
        Path filePath = worldDir.resolve("minehop_maps.json");

        try {
            String jsonData = new String(Files.readAllBytes(filePath));
            Gson gson = new Gson();
            return gson.fromJson(jsonData, mapListType); // Replace Object.class with your data type
        } catch (IOException e) {
            e.printStackTrace();
            return null; // Handle the case where the data doesn't exist yet
        }
    }
}
