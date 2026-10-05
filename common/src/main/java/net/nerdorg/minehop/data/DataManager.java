package net.nerdorg.minehop.data;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.mojang.datafixers.util.Pair;
import net.nerdorg.minehop.platform.Services;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.config.MinehopConfig;
import net.nerdorg.minehop.networking.PacketHandler;
import net.nerdorg.minehop.util.JsonStorage;
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

    // Bump when the on-disk shape of maps/records/PBs/ratings changes; migrate in loadData/register.
    private static final int SCHEMA_VERSION = 1;

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
        public List<List<Vec3>> checkpointPositions;
        public boolean arena;
        public boolean hns;
        public boolean surf;
        public boolean kz;
        public boolean preserve_speed = false;
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
        public double movement_speed_cap;
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
            this.movement_speed_cap = source.movement.speed_cap;
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
                double speedCap,
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
            this.movement_speed_cap = clampFinite(speedCap, 0.0D, 100.0D, 0.0D);
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
        // L3: stable player identity. Legacy records load with uuid == null/"" and fall back to name.
        public String uuid = "";
        public String map_name;
        public double time;
        // Anticheat flags raised during the run that set this time ("" = clean), e.g. "Speed x2".
        public String ac_flags = "";

        public RecordData() {

        }

        public RecordData(String name, String map_name, double time) {
            this(name, "", map_name, time);
        }

        public RecordData(String name, String uuid, String map_name, double time) {
            this.name = name;
            this.uuid = uuid == null ? "" : uuid;
            this.map_name = map_name;
            this.time = time;
        }
    }

    public static void register() {
        // LOAD/UNLOAD fire once PER DIMENSION, but these files live at the server root. Without the
        // overworld gate every dimension reloaded (and on shutdown rewrote) the same files, so the
        // one-generation .bak was always overwritten with the file it was meant to protect.
        Services.EVENTS.onServerLevelLoad(((server, world) -> {
            if (world.dimension() != net.minecraft.world.level.Level.OVERWORLD) {
                return;
            }
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
            // L3: backfill UUIDs onto legacy records via the user cache, then persist if changed.
            if (backfillRecordUuids(server)) {
                saveData(world, pbListLocation, Minehop.personalRecordList);
                saveData(world, recordsListLocation, Minehop.recordList);
            }
        }));

        Services.EVENTS.onServerLevelUnload(((server, world) -> {
            if (world.dimension() != net.minecraft.world.level.Level.OVERWORLD) {
                return;
            }
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

    /**
     * L6: deterministic signature of the run-relevant parts of a map — physics overrides, spawn,
     * checkpoint count and plot bounds/flags. Captured at run start and re-checked at finish so a
     * map edited (or deleted) mid-run can't validate a record. Cosmetic fields (description, ratings,
     * play_count) are intentionally excluded so they can change without invalidating active runs.
     */
    public static long computeRunSignature(MapData map) {
        if (map == null) {
            return 0L;
        }
        long h = 1125899906842597L;
        h = 31L * h + Boolean.hashCode(map.movement_override);
        h = 31L * h + Double.hashCode(map.movement_sv_friction);
        h = 31L * h + Double.hashCode(map.movement_sv_accelerate);
        h = 31L * h + Double.hashCode(map.movement_sv_airaccelerate);
        h = 31L * h + Double.hashCode(map.movement_sv_maxairspeed);
        h = 31L * h + Double.hashCode(map.movement_sv_jump_impulse);
        h = 31L * h + Double.hashCode(map.movement_speed_mul);
        h = 31L * h + Double.hashCode(map.movement_sv_gravity);
        h = 31L * h + Double.hashCode(map.movement_sv_stopspeed);
        h = 31L * h + Double.hashCode(map.movement_speed_coefficient);
        h = 31L * h + Double.hashCode(map.movement_speed_cap);
        h = 31L * h + Boolean.hashCode(map.movement_auto_step_up);
        h = 31L * h + Boolean.hashCode(map.movement_css_crouch_jump);
        h = 31L * h + Boolean.hashCode(map.movement_disable_sprint);
        h = 31L * h + Boolean.hashCode(map.movement_fall_damage);
        h = 31L * h + Double.hashCode(map.x);
        h = 31L * h + Double.hashCode(map.y);
        h = 31L * h + Double.hashCode(map.z);
        h = 31L * h + Double.hashCode(map.xrot);
        h = 31L * h + Double.hashCode(map.yrot);
        h = 31L * h + Boolean.hashCode(map.arena);
        h = 31L * h + Boolean.hashCode(map.hns);
        h = 31L * h + Boolean.hashCode(map.surf);
        h = 31L * h + Boolean.hashCode(map.kz);
        h = 31L * h + (map.checkpointPositions == null ? 0 : map.checkpointPositions.size());
        h = 31L * h + map.plotMinX;
        h = 31L * h + map.plotMinY;
        h = 31L * h + map.plotMinZ;
        h = 31L * h + map.plotMaxX;
        h = 31L * h + map.plotMaxY;
        h = 31L * h + map.plotMaxZ;
        return h;
    }

    public static double clampFinite(double raw, double min, double max, double fallback) {
        if (!Double.isFinite(raw)) {
            return fallback;
        }
        return Math.max(min, Math.min(max, raw));
    }

    /**
     * L3 identity match. When a UUID is supplied, a record belongs to the player if its UUID matches,
     * OR it is a legacy (no-UUID) record whose name matches (pre-migration data). Without a UUID it
     * falls back to name. This avoids nuking a different current player who took the old name.
     */
    public static boolean recordBelongsTo(RecordData recordData, String playerName, String playerUuid) {
        if (recordData == null) {
            return false;
        }
        if (playerUuid != null && !playerUuid.isBlank()) {
            if (playerUuid.equals(recordData.uuid)) {
                return true;
            }
            boolean legacy = recordData.uuid == null || recordData.uuid.isBlank();
            return legacy && playerName != null && playerName.equals(recordData.name);
        }
        return playerName != null && playerName.equals(recordData.name);
    }

    public static RecordData removePersonalRecordsForPlayer(String mapName, String playerName) {
        return removePersonalRecordsForPlayer(mapName, playerName, "");
    }

    public static RecordData removePersonalRecordsForPlayer(String mapName, String playerName, String playerUuid) {
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
            if (mapName.equals(recordData.map_name) && recordBelongsTo(recordData, playerName, playerUuid)) {
                if (removed == null) {
                    removed = recordData;
                }
                iterator.remove();
            }
        }
        return removed;
    }

    public static RecordData removeRecordsForPlayer(String mapName, String playerName) {
        return removeRecordsForPlayer(mapName, playerName, "");
    }

    public static RecordData removeRecordsForPlayer(String mapName, String playerName, String playerUuid) {
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
            if (mapName.equals(recordData.map_name) && recordBelongsTo(recordData, playerName, playerUuid)) {
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
        upsertPersonalRecord(playerName, "", mapName, time);
    }

    public static void upsertPersonalRecord(String playerName, String playerUuid, String mapName, double time) {
        upsertPersonalRecord(playerName, playerUuid, mapName, time, "");
    }

    public static void upsertPersonalRecord(String playerName, String playerUuid, String mapName, double time, String acFlags) {
        if (playerName == null || mapName == null) {
            return;
        }
        if (Minehop.personalRecordList == null) {
            Minehop.personalRecordList = new ArrayList<>();
        }
        boolean haveUuid = playerUuid != null && !playerUuid.isBlank();
        Iterator<RecordData> iterator = Minehop.personalRecordList.iterator();
        while (iterator.hasNext()) {
            RecordData recordData = iterator.next();
            if (recordData == null) {
                continue;
            }
            if (!mapName.equals(recordData.map_name)) {
                continue;
            }
            // Match the same player by UUID when both have one (survives name changes), else by name.
            boolean samePlayer = haveUuid && recordData.uuid != null && !recordData.uuid.isBlank()
                    ? playerUuid.equals(recordData.uuid)
                    : playerName.equals(recordData.name);
            if (samePlayer) {
                iterator.remove();
            }
        }
        RecordData row = new RecordData(playerName, playerUuid, mapName, time);
        row.ac_flags = acFlags == null ? "" : acFlags;
        Minehop.personalRecordList.add(row);
    }

    public static void upsertRecord(String playerName, String mapName, double time) {
        upsertRecord(playerName, "", mapName, time);
    }

    public static void upsertRecord(String playerName, String playerUuid, String mapName, double time) {
        upsertRecord(playerName, playerUuid, mapName, time, "");
    }

    public static void upsertRecord(String playerName, String playerUuid, String mapName, double time, String acFlags) {
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
        RecordData row = new RecordData(playerName, playerUuid, mapName, time);
        row.ac_flags = acFlags == null ? "" : acFlags;
        Minehop.recordList.add(row);
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
            // Keep the holder's UUID: dropping it broke record_holder reconciliation and let a later
            // name-based backfill guess a different player.
            upsertRecord(bestPersonal.name, bestPersonal.uuid, bestPersonal.map_name, bestPersonal.time, bestPersonal.ac_flags);
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

    /** UUID-aware PB lookup: matches by UUID (survives name changes) with legacy-name fallback. */
    public static RecordData getPersonalRecord(String playerName, String playerUuid, String mapName) {
        if (mapName == null || Minehop.personalRecordList == null) {
            return null;
        }
        RecordData best = null;
        for (RecordData recordData : Minehop.personalRecordList) {
            if (recordData == null || !mapName.equals(recordData.map_name)) {
                continue;
            }
            if (recordBelongsTo(recordData, playerName, playerUuid)) {
                if (best == null || recordData.time < best.time) {
                    best = recordData;
                }
            }
        }
        return best;
    }

    /**
     * L3 offline backfill: resolve missing UUIDs on legacy records via the server user cache so
     * UUID-keyed matching works for players who set times before the migration. Best-effort (only
     * names the server has seen are resolvable). Returns true if anything changed.
     */
    public static boolean backfillRecordUuids(MinecraftServer server) {
        if (server == null) {
            return false;
        }
        Map<String, String> cachedUuids = loadCachedUuidsByName(server);
        if (cachedUuids.isEmpty()) {
            return false;
        }
        boolean changed = false;
        changed |= backfillRecordList(Minehop.personalRecordList, cachedUuids);
        changed |= backfillRecordList(Minehop.recordList, cachedUuids);
        return changed;
    }

    private static boolean backfillRecordList(List<RecordData> list, Map<String, String> cachedUuids) {
        if (list == null) {
            return false;
        }
        boolean changed = false;
        for (RecordData recordData : list) {
            if (recordData == null || recordData.name == null || recordData.name.isBlank()) {
                continue;
            }
            if (recordData.uuid != null && !recordData.uuid.isBlank()) {
                continue;
            }
            String uuid = cachedUuids.get(recordData.name.toLowerCase(Locale.ROOT));
            if (uuid != null) {
                recordData.uuid = uuid;
                changed = true;
            }
        }
        return changed;
    }

    /**
     * Lowercase name -> UUID for every account in the server's usercache.json, read straight from the
     * file. Not UserCache.findByName: it treats entries older than a month as misses and then asks
     * Mojang, which blocks the server thread once per name and answers with whoever owns the name
     * NOW. The cached entry is the account that actually joined this server under that name, and
     * nothing here touches the network.
     */
    public static Map<String, String> loadCachedUuidsByName(MinecraftServer server) {
        Map<String, String> byName = new HashMap<>();
        if (server == null) {
            return byName;
        }
        Path file = server.getFile("usercache.json");
        if (!Files.isRegularFile(file)) {
            return byName;
        }
        try (java.io.Reader reader = Files.newBufferedReader(file, java.nio.charset.StandardCharsets.UTF_8)) {
            com.google.gson.JsonElement root = com.google.gson.JsonParser.parseReader(reader);
            if (root == null || !root.isJsonArray()) {
                return byName;
            }
            // Saved most-recently-used first, so the first entry for a name wins.
            for (com.google.gson.JsonElement element : root.getAsJsonArray()) {
                if (!element.isJsonObject()) {
                    continue;
                }
                com.google.gson.JsonObject entry = element.getAsJsonObject();
                if (!entry.has("name") || !entry.has("uuid")) {
                    continue;
                }
                String name = entry.get("name").getAsString();
                String uuid;
                try {
                    uuid = UUID.fromString(entry.get("uuid").getAsString()).toString();
                } catch (IllegalArgumentException e) {
                    continue;
                }
                if (!name.isBlank()) {
                    byName.putIfAbsent(name.toLowerCase(Locale.ROOT), uuid);
                }
            }
        } catch (Exception e) {
            Minehop.LOGGER.warn("Could not read usercache.json for the record UUID backfill", e);
        }
        return byName;
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

    public static RecordData getAnyRecordFromUuid(String playerUuid) {
        if (playerUuid == null || playerUuid.isBlank() || Minehop.recordList == null) {
            return null;
        }
        for (RecordData recordData : Minehop.recordList) {
            if (recordData == null) {
                continue;
            }
            if (playerUuid.equals(recordData.uuid)) {
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

    /**
     * Atomic, backed-up, version-enveloped write (see JsonStorage). Keep this signature (void): the
     * minehop-server companion mod calls it through ServerDataManager and is compiled against it.
     */
    public static <T> void saveData(ServerLevel world, String location, List<T> data) {
        saveDataChecked(world, location, data);
    }

    /** Same as {@link #saveData}, returning false if the write failed. */
    public static <T> boolean saveDataChecked(ServerLevel world, String location, List<T> data) {
        MinecraftServer server = world.getServer();
        Path worldDir = server.getWorldPath(LevelResource.ROOT);
        folderCheck(worldDir);
        return JsonStorage.writeAtomic(worldDir.resolve(location), SCHEMA_VERSION, data == null ? new ArrayList<>() : data);
    }

    public static <T> List<T> loadData(ServerLevel world, String location, Type recordListType) {
        Path worldDir = world.getServer().getWorldPath(LevelResource.ROOT);
        folderCheck(worldDir);
        // Crash-proof read with .corrupt quarantine + .bak fallback; null = no data yet.
        return JsonStorage.readData(worldDir.resolve(location), recordListType);
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
        mapData.movement_speed_cap = clampFinite(mapData.movement_speed_cap, 0.0D, 100.0D, defaults.movement.speed_cap);
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
