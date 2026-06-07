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
import net.nerdorg.minehop.config.MinehopConfig;
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
    private static final Type mapRatingListType = new TypeToken<List<MapRatingData>>(){}.getType();

    private static final String folderName = "MineHop_Data";
    public static final String mapListLocation = folderName+"/minehop_maps.json";
    public static final String pbListLocation = folderName+"/minehop_pbs.json";
    public static final String recordsListLocation = folderName+"/minehop_records.json";
    public static final String mapRatingsLocation = folderName+"/minehop_map_ratings.json";

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
        public boolean surf;
        public boolean kz;
        public boolean movement_override;
        public double movement_sv_friction;
        public double movement_sv_accelerate;
        public double movement_sv_airaccelerate;
        public double movement_sv_maxairspeed;
        public double movement_sv_jump_impulse;
        public double movement_speed_mul;
        public double movement_sv_gravity;
        public double movement_sv_stopspeed;
        public double movement_speed_coefficient;
        public boolean movement_auto_step_up;
        public boolean movement_css_crouch_jump;
        public boolean movement_disable_sprint;
        public boolean movement_fall_damage;
        public boolean userMap;
        public int difficulty;
        public int player_count;
        public String ownerUuid;
        public String ownerName;
        public String description;
        public int plotMinX;
        public int plotMinY;
        public int plotMinZ;
        public int plotMaxX;
        public int plotMaxY;
        public int plotMaxZ;
        public String plotGroundBlockId;
        public int play_count;
        public int rating_count;
        public int rating_quality_total;
        public int rating_difficulty_total;

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
            this.userMap = false;
            this.ownerUuid = "";
            this.ownerName = "";
            this.description = "";
            this.plotGroundBlockId = "";
            this.play_count = 0;
            this.rating_count = 0;
            this.rating_quality_total = 0;
            this.rating_difficulty_total = 0;
            this.copyMovementFrom(null, false);
        }

        public MapData(String name, double x, double y, double z, double xrot, double yrot, String worldKey, boolean arena, boolean hns, int difficulty, int player_count) {
            this(name, x, y, z, xrot, yrot, worldKey, arena, hns, false, difficulty, player_count);
        }

        public MapData(String name, double x, double y, double z, double xrot, double yrot, String worldKey, boolean arena, boolean hns, boolean surf, int difficulty, int player_count) {
            this(name, x, y, z, xrot, yrot, worldKey, arena, hns, surf, false, difficulty, player_count);
        }

        public MapData(String name, double x, double y, double z, double xrot, double yrot, String worldKey, boolean arena, boolean hns, boolean surf, boolean kz, int difficulty, int player_count) {
            this(name, x, y, z, xrot, yrot, worldKey, arena, hns, surf, kz, difficulty, player_count, false, "", "", "", 0, 0, 0, 0, 0, 0, "");
        }

        public MapData(
                String name,
                double x,
                double y,
                double z,
                double xrot,
                double yrot,
                String worldKey,
                boolean arena,
                boolean hns,
                boolean surf,
                boolean kz,
                int difficulty,
                int player_count,
                boolean userMap,
                String ownerUuid,
                String ownerName,
                String description,
                int plotMinX,
                int plotMinY,
                int plotMinZ,
                int plotMaxX,
                int plotMaxY,
                int plotMaxZ,
                String plotGroundBlockId
        ) {
            this(
                    name,
                    x,
                    y,
                    z,
                    xrot,
                    yrot,
                    worldKey,
                    arena,
                    hns,
                    surf,
                    kz,
                    difficulty,
                    player_count,
                    userMap,
                    ownerUuid,
                    ownerName,
                    description,
                    plotMinX,
                    plotMinY,
                    plotMinZ,
                    plotMaxX,
                    plotMaxY,
                    plotMaxZ,
                    plotGroundBlockId,
                    0,
                    0,
                    0,
                    0
            );
        }

        public MapData(
                String name,
                double x,
                double y,
                double z,
                double xrot,
                double yrot,
                String worldKey,
                boolean arena,
                boolean hns,
                boolean surf,
                boolean kz,
                int difficulty,
                int player_count,
                boolean userMap,
                String ownerUuid,
                String ownerName,
                String description,
                int plotMinX,
                int plotMinY,
                int plotMinZ,
                int plotMaxX,
                int plotMaxY,
                int plotMaxZ,
                String plotGroundBlockId,
                int playCount,
                int ratingCount,
                int ratingQualityTotal,
                int ratingDifficultyTotal
        ) {
            this.name = name;
            this.x = x;
            this.y = y;
            this.z = z;
            this.xrot = xrot;
            this.yrot = yrot;
            this.worldKey = worldKey;
            this.arena = arena;
            this.hns = hns;
            this.surf = surf;
            this.kz = kz;
            this.difficulty = difficulty;
            this.player_count = player_count;
            this.userMap = userMap;
            this.ownerUuid = ownerUuid == null ? "" : ownerUuid;
            this.ownerName = ownerName == null ? "" : ownerName;
            this.description = description == null ? "" : description;
            this.plotMinX = plotMinX;
            this.plotMinY = plotMinY;
            this.plotMinZ = plotMinZ;
            this.plotMaxX = plotMaxX;
            this.plotMaxY = plotMaxY;
            this.plotMaxZ = plotMaxZ;
            this.plotGroundBlockId = plotGroundBlockId == null ? "" : plotGroundBlockId;
            this.play_count = Math.max(0, playCount);
            this.rating_count = Math.max(0, ratingCount);
            this.rating_quality_total = Math.max(0, ratingQualityTotal);
            this.rating_difficulty_total = Math.max(0, ratingDifficultyTotal);
            this.copyMovementFrom(null, false);
        }

        public void copyMovementFrom(MinehopConfig config, boolean overrideEnabled) {
            MinehopConfig source = config == null ? new MinehopConfig() : config;
            this.movement_override = overrideEnabled;
            this.movement_sv_friction = source.movement.sv_friction;
            this.movement_sv_accelerate = source.movement.sv_accelerate;
            this.movement_sv_airaccelerate = source.movement.sv_airaccelerate;
            this.movement_sv_maxairspeed = source.movement.sv_maxairspeed;
            this.movement_sv_jump_impulse = source.movement.sv_jump_impulse;
            this.movement_speed_mul = source.movement.speed_mul;
            this.movement_sv_gravity = source.movement.sv_gravity;
            this.movement_sv_stopspeed = source.movement.sv_stopspeed;
            this.movement_speed_coefficient = source.movement.speed_coefficient;
            this.movement_auto_step_up = source.movement.auto_step_up;
            this.movement_css_crouch_jump = source.movement.css_crouch_jump;
            this.movement_disable_sprint = source.movement.disable_sprint;
            this.movement_fall_damage = source.fall_damage;
        }

        public void applyMovementSettings(
                boolean overrideEnabled,
                double svFriction,
                double svAccelerate,
                double svAiraccelerate,
                double svMaxairspeed,
                double svJumpImpulse,
                double speedMul,
                double svGravity,
                double svStopspeed,
                double speedCoefficient,
                boolean autoStepUp,
                boolean cssCrouchJump,
                boolean disableSprint,
                boolean fallDamage
        ) {
            this.movement_override = overrideEnabled;
            this.movement_sv_friction = clampFinite(svFriction, 0.0D, 20.0D, 4.0D);
            this.movement_sv_accelerate = clampFinite(svAccelerate, 0.0D, 100.0D, 10.0D);
            this.movement_sv_airaccelerate = clampFinite(svAiraccelerate, 0.0D, 10000.0D, 100.0D);
            this.movement_sv_maxairspeed = clampFinite(svMaxairspeed, 0.0D, 1000.0D, 30.0D);
            this.movement_sv_jump_impulse = clampFinite(svJumpImpulse, 0.0D, 1000.0D, 300.0D);
            this.movement_speed_mul = clampFinite(speedMul, 0.0D, 10.0D, 3.25D);
            this.movement_sv_gravity = clampFinite(svGravity, 0.0D, 4000.0D, 800.0D);
            this.movement_sv_stopspeed = clampFinite(svStopspeed, 0.0D, 1000.0D, 75.0D);
            this.movement_speed_coefficient = clampFinite(speedCoefficient, 0.0D, 10.0D, 1.0D);
            this.movement_auto_step_up = autoStepUp;
            this.movement_css_crouch_jump = cssCrouchJump;
            this.movement_disable_sprint = disableSprint;
            this.movement_fall_damage = fallDamage;
        }
    }

    public static class MapRatingData {
        public String map_name;
        public String voter_uuid;
        public int quality_rating;
        public int difficulty_rating;

        public MapRatingData() {
        }

        public MapRatingData(String mapName, String voterUuid, int qualityRating, int difficultyRating) {
            this.map_name = mapName == null ? "" : mapName;
            this.voter_uuid = voterUuid == null ? "" : voterUuid;
            this.quality_rating = clampRating(qualityRating);
            this.difficulty_rating = clampRating(difficultyRating);
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
            Minehop.mapRatingList = new ArrayList<>();
            Minehop.recordList = new ArrayList<>();
            Minehop.personalRecordList = new ArrayList<>();
            List<DataManager.MapData> newMapList = DataManager.loadData(world, mapListLocation, mapListType);
            List<DataManager.MapRatingData> newMapRatingList = DataManager.loadData(world, mapRatingsLocation, mapRatingListType);
            List<DataManager.RecordData> newPersonalRecordList = DataManager.loadData(world, pbListLocation, recordListType);
            List<DataManager.RecordData> newRecordList = DataManager.loadData(world, recordsListLocation, recordListType);
            if (newMapList != null) {
                Minehop.mapList = deduplicateMapListByName(newMapList);
            }
            if (newMapRatingList != null) {
                Minehop.mapRatingList = newMapRatingList;
            }
            if (newPersonalRecordList != null) {
                Minehop.personalRecordList = newPersonalRecordList;
            }
            if (newRecordList != null) {
                Minehop.recordList = newRecordList;
            }
            recalculateMapRatings();
        }));

        ServerWorldEvents.UNLOAD.register(((server, world) -> {
            DataManager.saveData(world, mapListLocation, Minehop.mapList);
            DataManager.saveData(world, mapRatingsLocation, Minehop.mapRatingList);
            DataManager.saveData(world, pbListLocation, Minehop.personalRecordList);
            DataManager.saveData(world, recordsListLocation, Minehop.recordList);
        }));
    }

    public static boolean setMapRating(String mapName, String voterUuid, int qualityRating, int difficultyRating) {
        if (mapName == null || mapName.isBlank() || voterUuid == null || voterUuid.isBlank()) {
            return false;
        }
        MapData mapData = getMap(mapName);
        if (mapData == null || !mapData.userMap) {
            return false;
        }
        if (voterUuid.equals(mapData.ownerUuid)) {
            return false;
        }
        if (Minehop.mapRatingList == null) {
            Minehop.mapRatingList = new ArrayList<>();
        }

        int clampedQuality = clampRating(qualityRating);
        int clampedDifficulty = clampRating(difficultyRating);

        for (MapRatingData ratingData : Minehop.mapRatingList) {
            if (ratingData == null) {
                continue;
            }
            if (mapName.equals(ratingData.map_name) && voterUuid.equals(ratingData.voter_uuid)) {
                ratingData.quality_rating = clampedQuality;
                ratingData.difficulty_rating = clampedDifficulty;
                return true;
            }
        }

        Minehop.mapRatingList.add(new MapRatingData(mapName, voterUuid, clampedQuality, clampedDifficulty));
        return true;
    }

    public static void removeRatingsForMap(String mapName) {
        if (mapName == null || mapName.isBlank() || Minehop.mapRatingList == null) {
            return;
        }
        Minehop.mapRatingList.removeIf(rating -> rating != null && mapName.equals(rating.map_name));
        recalculateMapRatings();
    }

    public static void recalculateMapRatings() {
        if (Minehop.mapList != null) {
            for (MapData mapData : Minehop.mapList) {
                if (mapData == null) {
                    continue;
                }
                mapData.rating_count = 0;
                mapData.rating_quality_total = 0;
                mapData.rating_difficulty_total = 0;
            }
        }

        if (Minehop.mapRatingList == null || Minehop.mapRatingList.isEmpty()) {
            return;
        }

        for (MapRatingData ratingData : Minehop.mapRatingList) {
            if (ratingData == null || ratingData.map_name == null || ratingData.map_name.isBlank()) {
                continue;
            }
            MapData mapData = getMap(ratingData.map_name);
            if (mapData == null || !mapData.userMap) {
                continue;
            }
            mapData.rating_count += 1;
            mapData.rating_quality_total += clampRating(ratingData.quality_rating);
            mapData.rating_difficulty_total += clampRating(ratingData.difficulty_rating);
        }
    }

    public static int clampRating(int raw) {
        return Math.max(1, Math.min(5, raw));
    }

    public static double clampFinite(double raw, double min, double max, double fallback) {
        if (!Double.isFinite(raw)) {
            return fallback;
        }
        return Math.max(min, Math.min(max, raw));
    }

    public static RecordData removePersonalRecordsForPlayer(String mapName, String playerName) {
        if (mapName == null || playerName == null || Minehop.personalRecordList == null) {
            return null;
        }
        RecordData removed = null;
        Iterator<RecordData> iterator = Minehop.personalRecordList.iterator();
        while (iterator.hasNext()) {
            RecordData recordData = iterator.next();
            if (recordData == null) {
                continue;
            }
            if (mapName.equals(recordData.map_name) && playerName.equals(recordData.name)) {
                if (removed == null) {
                    removed = recordData;
                }
                iterator.remove();
            }
        }
        return removed;
    }

    public static RecordData removeRecordsForPlayer(String mapName, String playerName) {
        if (mapName == null || playerName == null || Minehop.recordList == null) {
            return null;
        }
        RecordData removed = null;
        Iterator<RecordData> iterator = Minehop.recordList.iterator();
        while (iterator.hasNext()) {
            RecordData recordData = iterator.next();
            if (recordData == null) {
                continue;
            }
            if (mapName.equals(recordData.map_name) && playerName.equals(recordData.name)) {
                if (removed == null) {
                    removed = recordData;
                }
                iterator.remove();
            }
        }
        return removed;
    }

    public static RecordData removePersonalRecords(String mapName) {
        if (mapName == null || Minehop.personalRecordList == null) {
            return null;
        }
        RecordData removed = null;
        Iterator<RecordData> iterator = Minehop.personalRecordList.iterator();
        while (iterator.hasNext()) {
            RecordData recordData = iterator.next();
            if (recordData == null) {
                continue;
            }
            if (mapName.equals(recordData.map_name)) {
                if (removed == null) {
                    removed = recordData;
                }
                iterator.remove();
            }
        }
        return removed;
    }

    public static RecordData removeRecords(String mapName) {
        if (mapName == null || Minehop.recordList == null) {
            return null;
        }
        RecordData removed = null;
        Iterator<RecordData> iterator = Minehop.recordList.iterator();
        while (iterator.hasNext()) {
            RecordData recordData = iterator.next();
            if (recordData == null) {
                continue;
            }
            if (mapName.equals(recordData.map_name)) {
                if (removed == null) {
                    removed = recordData;
                }
                iterator.remove();
            }
        }
        return removed;
    }

    public static void upsertPersonalRecord(String playerName, String mapName, double time) {
        if (playerName == null || mapName == null) {
            return;
        }
        if (Minehop.personalRecordList == null) {
            Minehop.personalRecordList = new ArrayList<>();
        }
        Iterator<RecordData> iterator = Minehop.personalRecordList.iterator();
        while (iterator.hasNext()) {
            RecordData recordData = iterator.next();
            if (recordData == null) {
                continue;
            }
            if (playerName.equals(recordData.name) && mapName.equals(recordData.map_name)) {
                iterator.remove();
            }
        }
        Minehop.personalRecordList.add(new RecordData(playerName, mapName, time));
    }

    public static void upsertRecord(String playerName, String mapName, double time) {
        if (playerName == null || mapName == null) {
            return;
        }
        if (Minehop.recordList == null) {
            Minehop.recordList = new ArrayList<>();
        }
        Iterator<RecordData> iterator = Minehop.recordList.iterator();
        while (iterator.hasNext()) {
            RecordData recordData = iterator.next();
            if (recordData == null) {
                continue;
            }
            if (mapName.equals(recordData.map_name)) {
                iterator.remove();
            }
        }
        Minehop.recordList.add(new RecordData(playerName, mapName, time));
    }

    public static RecordData rebuildRecordForMap(String mapName) {
        if (mapName == null || mapName.isBlank()) {
            return null;
        }
        removeRecords(mapName);

        RecordData bestPersonal = null;
        if (Minehop.personalRecordList != null) {
            for (RecordData personalRecord : Minehop.personalRecordList) {
                if (personalRecord == null) {
                    continue;
                }
                if (!mapName.equals(personalRecord.map_name)) {
                    continue;
                }
                if (bestPersonal == null || personalRecord.time < bestPersonal.time) {
                    bestPersonal = personalRecord;
                }
            }
        }

        if (bestPersonal != null) {
            upsertRecord(bestPersonal.name, bestPersonal.map_name, bestPersonal.time);
            return getRecord(mapName);
        }
        return null;
    }

    public static RecordData getPersonalRecord(String playerName, String mapName) {
        if (playerName == null || mapName == null || Minehop.personalRecordList == null) {
            return null;
        }
        RecordData best = null;
        for (RecordData recordData : Minehop.personalRecordList) {
            if (recordData == null) {
                continue;
            }
            if (playerName.equals(recordData.name) && mapName.equals(recordData.map_name)) {
                if (best == null || recordData.time < best.time) {
                    best = recordData;
                }
            }
        }
        return best;
    }

    public static RecordData getRecord(String mapName) {
        if (mapName == null || Minehop.recordList == null) {
            return null;
        }
        RecordData best = null;
        for (RecordData recordData : Minehop.recordList) {
            if (recordData == null) {
                continue;
            }
            if (mapName.equals(recordData.map_name)) {
                if (best == null || recordData.time < best.time) {
                    best = recordData;
                }
            }
        }
        return best;
    }

    public static RecordData getRecordFromName(String mapName, String playerName) {
        if (mapName == null || playerName == null || Minehop.recordList == null) {
            return null;
        }
        RecordData best = null;
        for (RecordData recordData : Minehop.recordList) {
            if (recordData == null) {
                continue;
            }
            if (playerName.equals(recordData.name) && mapName.equals(recordData.map_name)) {
                if (best == null || recordData.time < best.time) {
                    best = recordData;
                }
            }
        }
        return best;
    }

    public static RecordData getAnyRecordFromName(String playerName) {
        if (playerName == null || Minehop.recordList == null) {
            return null;
        }
        for (RecordData recordData : Minehop.recordList) {
            if (recordData == null) {
                continue;
            }
            if (playerName.equals(recordData.name)) {
                return recordData;
            }
        }
        return null;
    }

    public static MapData getMap(String mapName) {
        if (mapName == null || Minehop.mapList == null) {
            return null;
        }

        String query = mapName.trim();
        if (query.isEmpty()) {
            return null;
        }

        MapData best = null;
        int bestScore = Integer.MIN_VALUE;

        for (MapData mapData : Minehop.mapList) {
            if (mapData == null || mapData.name == null) {
                continue;
            }
            String candidate = mapData.name.trim();
            if (!candidate.equalsIgnoreCase(query)) {
                continue;
            }

            int checkpointCount = mapData.checkpointPositions == null ? 0 : mapData.checkpointPositions.size();
            int score = checkpointCount;
            if (candidate.equals(query)) {
                score += 10_000;
            }

            if (score > bestScore) {
                bestScore = score;
                best = mapData;
            }
        }

        return best;
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

    private static List<MapData> deduplicateMapListByName(List<MapData> maps) {
        if (maps == null || maps.isEmpty()) {
            return maps == null ? new ArrayList<>() : maps;
        }

        Map<String, MapData> byCanonicalName = new LinkedHashMap<>();
        for (MapData candidate : maps) {
            if (candidate == null || candidate.name == null) {
                continue;
            }
            String trimmed = candidate.name.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            sanitizeMapDataFields(candidate);
            String key = trimmed.toLowerCase(Locale.ROOT);
            MapData existing = byCanonicalName.get(key);
            if (existing == null) {
                candidate.name = trimmed;
                byCanonicalName.put(key, candidate);
                continue;
            }

            int existingCheckpoints = existing.checkpointPositions == null ? 0 : existing.checkpointPositions.size();
            int candidateCheckpoints = candidate.checkpointPositions == null ? 0 : candidate.checkpointPositions.size();

            if (candidateCheckpoints > existingCheckpoints) {
                candidate.name = trimmed;
                byCanonicalName.put(key, candidate);
            }
        }
        return new ArrayList<>(byCanonicalName.values());
    }

    private static void sanitizeMapDataFields(MapData mapData) {
        if (mapData == null) {
            return;
        }
        if (mapData.ownerUuid == null) {
            mapData.ownerUuid = "";
        }
        if (mapData.ownerName == null) {
            mapData.ownerName = "";
        }
        if (mapData.description == null) {
            mapData.description = "";
        }
        if (mapData.plotGroundBlockId == null) {
            mapData.plotGroundBlockId = "";
        }
        if (mapData.worldKey == null) {
            mapData.worldKey = "";
        }
        if (mapData.userMap) {
            if (mapData.ownerUuid.isBlank()) {
                mapData.userMap = false;
            }
            if (mapData.plotMaxX <= mapData.plotMinX || mapData.plotMaxZ <= mapData.plotMinZ) {
                mapData.userMap = false;
            }
        }
        if (mapData.play_count < 0) mapData.play_count = 0;
        if (mapData.rating_count < 0) mapData.rating_count = 0;
        if (mapData.rating_quality_total < 0) mapData.rating_quality_total = 0;
        if (mapData.rating_difficulty_total < 0) mapData.rating_difficulty_total = 0;
        if (mapData.player_count < 0) mapData.player_count = 0;
        sanitizeMapMovementFields(mapData);
    }

    public static void sanitizeMapMovementFields(MapData mapData) {
        if (mapData == null) {
            return;
        }
        MinehopConfig defaults = new MinehopConfig();
        mapData.movement_sv_friction = clampFinite(mapData.movement_sv_friction, 0.0D, 20.0D, defaults.movement.sv_friction);
        mapData.movement_sv_accelerate = clampFinite(mapData.movement_sv_accelerate, 0.0D, 100.0D, defaults.movement.sv_accelerate);
        mapData.movement_sv_airaccelerate = clampFinite(mapData.movement_sv_airaccelerate, 0.0D, 10000.0D, defaults.movement.sv_airaccelerate);
        mapData.movement_sv_maxairspeed = clampFinite(mapData.movement_sv_maxairspeed, 0.0D, 1000.0D, defaults.movement.sv_maxairspeed);
        mapData.movement_sv_jump_impulse = clampFinite(mapData.movement_sv_jump_impulse, 0.0D, 1000.0D, defaults.movement.sv_jump_impulse);
        mapData.movement_speed_mul = clampFinite(mapData.movement_speed_mul, 0.0D, 10.0D, defaults.movement.speed_mul);
        mapData.movement_sv_gravity = clampFinite(mapData.movement_sv_gravity, 0.0D, 4000.0D, defaults.movement.sv_gravity);
        mapData.movement_sv_stopspeed = clampFinite(mapData.movement_sv_stopspeed, 0.0D, 1000.0D, defaults.movement.sv_stopspeed);
        mapData.movement_speed_coefficient = clampFinite(mapData.movement_speed_coefficient, 0.0D, 10.0D, defaults.movement.speed_coefficient);
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
