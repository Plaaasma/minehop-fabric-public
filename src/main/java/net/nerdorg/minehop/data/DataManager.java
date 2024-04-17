package net.nerdorg.minehop.data;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.mojang.datafixers.util.Pair;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Formatting;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.Vec3d;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.networking.PacketHandler;
import net.nerdorg.minehop.util.Logger;

import javax.xml.crypto.Data;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class DataManager {
    private static final Type mapListType = new TypeToken<List<MapData>>(){}.getType();
    private static final Type recordListType = new TypeToken<List<RecordData>>(){}.getType();

    private static final String folderName = "MineHop_Data";
    public static final String mapListLocation = folderName+"/minehop_maps.json";
    public static final String pbListLocation = folderName+"/minehop_pbs.json";
    public static final String recordsListLocation = folderName+"/minehop_records.json";

    public static class MapData {
        public String name;
        public double x;
        public double y;
        public double z;
        public double xrot;
        public double yrot;
        public String worldKey;
        public List<List<Vec3d>> checkpointPositions;
        public boolean arena;
        public boolean hns;
        public int difficulty;
        public int player_count;

        public MapData() {
        }

        public MapData(String name, double x, double y, double z, double xrot, double yrot, String worldKey) {
            this.name = name;
            this.x = x;
            this.y = y;
            this.z = z;
            this.xrot = xrot;
            this.yrot = yrot;
            this.worldKey = worldKey;
        }

        public MapData(String name, double x, double y, double z, double xrot, double yrot, String worldKey, boolean arena, boolean hns, int difficulty, int player_count) {
            this.name = name;
            this.x = x;
            this.y = y;
            this.z = z;
            this.xrot = xrot;
            this.yrot = yrot;
            this.worldKey = worldKey;
            this.arena = arena;
            this.hns = hns;
            this.difficulty = difficulty;
            this.player_count = player_count;
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

    public static void register() {
        ServerWorldEvents.LOAD.register(((server, world) -> {
            Minehop.mapList = new ArrayList<>();
            Minehop.recordList = new ArrayList<>();
            Minehop.personalRecordList = new ArrayList<>();
            List<DataManager.MapData> newMapList = DataManager.loadData(world, mapListLocation, mapListType);
            List<DataManager.RecordData> newPersonalRecordList = DataManager.loadData(world, pbListLocation, recordListType);
            List<DataManager.RecordData> newRecordList = DataManager.loadData(world, recordsListLocation, recordListType);
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
            DataManager.saveData(world, mapListLocation, Minehop.mapList);
            DataManager.saveData(world, pbListLocation, Minehop.personalRecordList);
            DataManager.saveData(world, recordsListLocation, Minehop.recordList);
        }));
    }

    public static RecordData removePersonalRecordsForPlayer(String mapName, String playerName) {
        if (Minehop.personalRecordList != null) {
            for (RecordData recordData : Minehop.personalRecordList) {
                if (recordData.map_name.equals(mapName) && recordData.name.equals(playerName)) {
                    Minehop.personalRecordList.remove(recordData);
                    return recordData;
                }
            }
        }

        return null;
    }

    public static RecordData removeRecordsForPlayer(String mapName, String playerName) {
        if (Minehop.recordList != null) {
            for (RecordData recordData : Minehop.recordList) {
                if (recordData.map_name.equals(mapName) && recordData.name.equals(playerName)) {
                    Minehop.recordList.remove(recordData);
                    double lowestTime = Double.MAX_VALUE;
                    RecordData recordToAdd = null;
                    for (RecordData newRecordData : Minehop.recordList) {
                        if (newRecordData.map_name.equals(mapName)) {
                            if (newRecordData.time < lowestTime) {
                                lowestTime = newRecordData.time;
                                recordToAdd = newRecordData;
                            }
                        }
                    }
                    if (recordToAdd != null) {
                        Minehop.recordList.add(recordToAdd);
                    }
                    return recordData;
                }
            }
        }

        return null;
    }

    public static RecordData removePersonalRecords(String mapName) {
        if (Minehop.personalRecordList != null) {
            for (RecordData recordData : Minehop.personalRecordList) {
                if (recordData.map_name.equals(mapName)) {
                    Minehop.personalRecordList.remove(recordData);
                    return recordData;
                }
            }
        }

        return null;
    }

    public static RecordData removeRecords(String mapName) {
        if (Minehop.recordList != null) {
            for (RecordData recordData : Minehop.recordList) {
                if (recordData.map_name.equals(mapName)) {
                    Minehop.recordList.remove(recordData);
                    return recordData;
                }
            }
        }

        return null;
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

    public static RecordData getRecordFromName(String mapName, String playerName) {
        if (Minehop.recordList != null) {
            for (RecordData recordData : Minehop.recordList) {
                if (recordData.name.equals(playerName) && recordData.map_name.equals(mapName)) {
                    return recordData;
                }
            }
        }

        return null;
    }

    public static RecordData getAnyRecordFromName(String playerName) {
        if (Minehop.recordList != null) {
            for (RecordData recordData : Minehop.recordList) {
                if (recordData.name.equals(playerName)) {
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

    public static void resetPlayerCounts() {
        if (Minehop.mapList != null) {
            List<MapData> newMapList = new ArrayList<>();
            for (MapData mapData : Minehop.mapList) {
                mapData.player_count = 0;
                newMapList.add(mapData);
            }
            Minehop.mapList = newMapList;
        }
    }

    public static <T> void saveData(ServerWorld world, String location, List<T> data) {


        Gson gson = new Gson();
        String jsonData = gson.toJson(data);

        MinecraftServer server = world.getServer();
        Path worldDir = server.getSavePath(WorldSavePath.ROOT);

        folderCheck(worldDir);

        try {
            Files.write(worldDir.resolve(location), jsonData.getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static <T> List<T> loadData(ServerWorld world, String location, Type recordListType) {



        Path worldDir = world.getServer().getSavePath(WorldSavePath.ROOT);
        folderCheck(worldDir);

        try {
            String jsonData = new String(Files.readAllBytes(worldDir.resolve(location)));
            Gson gson = new Gson();
            return gson.fromJson(jsonData, recordListType); // Replace Object.class with your data type
        } catch (IOException e) {
            e.printStackTrace();
            return null; // Handle the case where the data doesn't exist yet
        }
    }

    private static void folderCheck(Path path){
        Path folderPath = Paths.get(folderName);
        try {
            Files.createDirectories(path.resolve(folderPath));
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
    }
}