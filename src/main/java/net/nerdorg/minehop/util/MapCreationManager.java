package net.nerdorg.minehop.util;

import net.minecraft.entity.SpawnReason;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.config.ConfigWrapper;
import net.nerdorg.minehop.config.MinehopConfig;
import net.nerdorg.minehop.data.DataManager;
import net.nerdorg.minehop.entity.ModEntities;
import net.nerdorg.minehop.entity.custom.EndEntity;
import net.nerdorg.minehop.entity.custom.ResetEntity;
import net.nerdorg.minehop.entity.custom.StartEntity;
import net.nerdorg.minehop.entity.custom.Zone;
import net.nerdorg.minehop.item.custom.BoundsStickItem;
import net.nerdorg.minehop.networking.PacketHandler;
import net.nerdorg.minehop.networking.payloads.MapCreatorActionPayload;

import java.util.ArrayList;
import java.util.Arrays;

public final class MapCreationManager {
    private static final int MIN_DIFFICULTY = 0;
    private static final int MAX_DIFFICULTY = 5;
    private static final int MAX_MAP_NAME_LENGTH = 128;
    private static final int DEFAULT_DIFFICULTY = 1;

    private MapCreationManager() {
    }

    public static void openGui(ServerPlayerEntity player) {
        openGuiForMap(player, null);
    }

    public static void openGuiForMap(ServerPlayerEntity player, String requestedMapName) {
        if (player == null) {
            return;
        }

        String mapName = requestedMapName == null ? "" : requestedMapName.trim();
        int difficulty = DEFAULT_DIFFICULTY;
        boolean arena = false;
        boolean hns = false;
        boolean surf = false;
        boolean kz = false;
        MinehopConfig movementConfig = ConfigWrapper.config == null ? new MinehopConfig() : ConfigWrapper.config;
        boolean movementOverride = false;
        int checkpointIndex = 0;

        DataManager.MapData initialMap = null;
        if (!mapName.isBlank()) {
            initialMap = DataManager.getMap(mapName);
        }

        if (initialMap == null) {
            Zone currentZone = Minehop.playerMapLocation.get(player.getUuidAsString());
            if (currentZone != null) {
                String pairedMap = currentZone.getPairedMap();
                if (pairedMap != null && !pairedMap.isBlank()) {
                    initialMap = DataManager.getMap(pairedMap);
                }
            }
        }

        if (initialMap == null) {
            DataManager.MapData ownedPlot = UserPlotManager.getOwnedPlotAt(player, player.getPos());
            if (ownedPlot != null) {
                initialMap = ownedPlot;
            }
        }

        if (initialMap != null) {
            mapName = initialMap.name == null ? mapName : initialMap.name;
            difficulty = clampDifficulty(initialMap.difficulty);
            arena = initialMap.arena;
            hns = initialMap.hns;
            surf = initialMap.surf;
            kz = initialMap.kz;
            movementOverride = initialMap.movement_override;
            if (movementOverride) {
                movementConfig = movementConfigFromMap(initialMap);
            }
        }

        PacketHandler.sendOpenMapCreatorScreen(
                player,
                mapName,
                difficulty,
                arena,
                hns,
                surf,
                kz,
                movementOverride,
                movementConfig.movement.sv_friction,
                movementConfig.movement.sv_accelerate,
                movementConfig.movement.sv_airaccelerate,
                movementConfig.movement.sv_maxairspeed,
                movementConfig.movement.sv_jump_impulse,
                movementConfig.movement.speed_mul,
                movementConfig.movement.sv_gravity,
                movementConfig.movement.sv_stopspeed,
                movementConfig.movement.speed_coefficient,
                movementConfig.movement.auto_step_up,
                movementConfig.movement.css_crouch_jump,
                movementConfig.fall_damage,
                checkpointIndex
        );
    }

    public static void handleAction(
            ServerPlayerEntity player,
            String action,
            String rawMapName,
            int difficulty,
            boolean arena,
            boolean hns,
            boolean surf,
            boolean kz,
            boolean movementOverride,
            double movementSvFriction,
            double movementSvAccelerate,
            double movementSvAiraccelerate,
            double movementSvMaxairspeed,
            double movementSvJumpImpulse,
            double movementSpeedMul,
            double movementSvGravity,
            double movementSvStopspeed,
            double movementSpeedCoefficient,
            boolean movementAutoStepUp,
            boolean movementCssCrouchJump,
            boolean movementFallDamage,
            int checkpointIndex
    ) {
        if (player == null) {
            return;
        }
        if (action == null || action.isBlank()) {
            Logger.logFailure(player, "Invalid map creation action.");
            return;
        }

        String mapName = sanitizeMapName(rawMapName);
        if (mapName == null) {
            Logger.logFailure(player, "Map name must be 1-" + MAX_MAP_NAME_LENGTH + " chars and cannot contain '~'.");
            return;
        }

        DataManager.MapData existingMap = DataManager.getMap(mapName);
        if (!player.hasPermissionLevel(4)) {
            if (UserPlotManager.isReservedMapName(mapName)) {
                Logger.logFailure(player, "That map name is reserved.");
                return;
            }
            if (existingMap == null) {
                Logger.logFailure(player, "Create a user plot first with /plot create <name>.");
                return;
            }
            if (!UserPlotManager.canManageMap(player, existingMap)) {
                Logger.logFailure(player, "You can only manage your own user plot map from inside your plot.");
                return;
            }
        }

        switch (action) {
            case MapCreatorActionPayload.ACTION_CREATE_OR_UPDATE ->
                    createOrUpdateMap(
                            player,
                            mapName,
                            clampDifficulty(difficulty),
                            arena,
                            hns,
                            surf,
                            kz,
                            movementOverride,
                            movementSvFriction,
                            movementSvAccelerate,
                            movementSvAiraccelerate,
                            movementSvMaxairspeed,
                            movementSvJumpImpulse,
                            movementSpeedMul,
                            movementSvGravity,
                            movementSvStopspeed,
                            movementSpeedCoefficient,
                            movementAutoStepUp,
                            movementCssCrouchJump,
                            movementFallDamage
                    );
            case MapCreatorActionPayload.ACTION_SET_SPAWN ->
                    setSpawn(player, mapName);
            case MapCreatorActionPayload.ACTION_ADD_CHECKPOINT ->
                    addCheckpoint(player, mapName);
            case MapCreatorActionPayload.ACTION_ADD_START_ZONE ->
                    addStartZone(player, mapName);
            case MapCreatorActionPayload.ACTION_ADD_END_ZONE ->
                    addEndZone(player, mapName);
            case MapCreatorActionPayload.ACTION_ADD_RESET_ZONE ->
                    addResetZone(player, mapName, Math.max(0, checkpointIndex));
            default -> Logger.logFailure(player, "Unsupported map creation action: " + action);
        }
    }

    private static void createOrUpdateMap(
            ServerPlayerEntity player,
            String mapName,
            int difficulty,
            boolean arena,
            boolean hns,
            boolean surf,
            boolean kz,
            boolean movementOverride,
            double movementSvFriction,
            double movementSvAccelerate,
            double movementSvAiraccelerate,
            double movementSvMaxairspeed,
            double movementSvJumpImpulse,
            double movementSpeedMul,
            double movementSvGravity,
            double movementSvStopspeed,
            double movementSpeedCoefficient,
            boolean movementAutoStepUp,
            boolean movementCssCrouchJump,
            boolean movementFallDamage
    ) {
        ServerWorld world = player.getServerWorld();
        DataManager.MapData mapData = DataManager.getMap(mapName);

        if (mapData == null) {
            mapData = new DataManager.MapData(
                    mapName,
                    player.getX(),
                    player.getY(),
                    player.getZ(),
                    player.getPitch(),
                    player.getYaw(),
                    world.getRegistryKey().toString(),
                    arena,
                    hns,
                    surf,
                    kz,
                    difficulty,
                    0
            );
            mapData.applyMovementSettings(
                    movementOverride,
                    movementSvFriction,
                    movementSvAccelerate,
                    movementSvAiraccelerate,
                    movementSvMaxairspeed,
                    movementSvJumpImpulse,
                    movementSpeedMul,
                    movementSvGravity,
                    movementSvStopspeed,
                    movementSpeedCoefficient,
                    movementAutoStepUp,
                    movementCssCrouchJump,
                    movementFallDamage
            );
            Minehop.mapList.add(mapData);
            Logger.logSuccess(player, "Created map '" + mapName + "'.");
        } else {
            mapData.x = player.getX();
            mapData.y = player.getY();
            mapData.z = player.getZ();
            mapData.xrot = player.getPitch();
            mapData.yrot = player.getYaw();
            mapData.worldKey = world.getRegistryKey().toString();
            mapData.difficulty = difficulty;
            mapData.arena = arena;
            mapData.hns = hns;
            mapData.surf = surf;
            mapData.kz = kz;
            mapData.applyMovementSettings(
                    movementOverride,
                    movementSvFriction,
                    movementSvAccelerate,
                    movementSvAiraccelerate,
                    movementSvMaxairspeed,
                    movementSvJumpImpulse,
                    movementSpeedMul,
                    movementSvGravity,
                    movementSvStopspeed,
                    movementSpeedCoefficient,
                    movementAutoStepUp,
                    movementCssCrouchJump,
                    movementFallDamage
            );
            Logger.logSuccess(player, "Updated map '" + mapName + "' at your current location.");
        }

        DataManager.saveData(world, DataManager.mapListLocation, Minehop.mapList);
        syncMaps(world);
    }

    private static void setSpawn(ServerPlayerEntity player, String mapName) {
        DataManager.MapData mapData = DataManager.getMap(mapName);
        if (mapData == null) {
            Logger.logFailure(player, "There is no map called " + mapName + ".");
            return;
        }

        ServerWorld world = player.getServerWorld();
        if (mapData.userMap && !player.hasPermissionLevel(4)) {
            if (!UserPlotManager.canManageMap(player, mapData)) {
                Logger.logFailure(player, "Stand inside your plot to set its spawn.");
                return;
            }
        }
        mapData.x = player.getX();
        mapData.y = player.getY();
        mapData.z = player.getZ();
        mapData.xrot = player.getPitch();
        mapData.yrot = player.getYaw();
        mapData.worldKey = world.getRegistryKey().toString();

        DataManager.saveData(world, DataManager.mapListLocation, Minehop.mapList);
        syncMaps(world);
        Logger.logSuccess(player, "Set spawn for '" + mapName + "' to your current location.");
    }

    private static void addCheckpoint(ServerPlayerEntity player, String mapName) {
        DataManager.MapData mapData = DataManager.getMap(mapName);
        if (mapData == null) {
            Logger.logFailure(player, "There is no map called " + mapName + ".");
            return;
        }

        if (mapData.userMap && !player.hasPermissionLevel(4)) {
            if (!UserPlotManager.canManageMap(player, mapData)) {
                Logger.logFailure(player, "Stand inside your plot to add a checkpoint.");
                return;
            }
        }

        if (mapData.checkpointPositions == null) {
            mapData.checkpointPositions = new ArrayList<>();
        }
        mapData.checkpointPositions.add(
                new ArrayList<>(Arrays.asList(
                        player.getPos(),
                        new Vec3d(player.getPitch(), player.getYaw(), 0.0D)
                ))
        );

        DataManager.saveData(player.getServerWorld(), DataManager.mapListLocation, Minehop.mapList);
        syncMaps(player.getServerWorld());
        Logger.logSuccess(player, "Added checkpoint " + mapData.checkpointPositions.size() + " to '" + mapName + "'.");
    }

    private static void addStartZone(ServerPlayerEntity player, String mapName) {
        DataManager.MapData mapData = DataManager.getMap(mapName);
        if (mapData == null) {
            Logger.logFailure(player, "There is no map called " + mapName + ".");
            return;
        }

        BlockPos[] corners = getBoundsCorners(player);
        if (corners == null) {
            return;
        }

        ServerWorld world = player.getServerWorld();
        StartEntity startEntity = ModEntities.START_ENTITY.spawn(world, corners[0], SpawnReason.NATURAL);
        if (startEntity == null) {
            Logger.logFailure(player, "Failed to create start zone entity.");
            return;
        }
        startEntity.setCorner1(corners[0]);
        startEntity.setCorner2(corners[1]);
        startEntity.setPairedMap(mapName);

        for (ServerPlayerEntity worldPlayer : world.getPlayers()) {
            PacketHandler.updateZone(worldPlayer, startEntity.getId(), corners[0], corners[1], mapName, 0);
        }
        BoundsStickItem.clearSelection(player);
        Logger.logSuccess(player, "Created start zone for '" + mapName + "'.");
    }

    private static void addEndZone(ServerPlayerEntity player, String mapName) {
        DataManager.MapData mapData = DataManager.getMap(mapName);
        if (mapData == null) {
            Logger.logFailure(player, "There is no map called " + mapName + ".");
            return;
        }

        BlockPos[] corners = getBoundsCorners(player);
        if (corners == null) {
            return;
        }

        ServerWorld world = player.getServerWorld();
        EndEntity endEntity = ModEntities.END_ENTITY.spawn(world, corners[0], SpawnReason.NATURAL);
        if (endEntity == null) {
            Logger.logFailure(player, "Failed to create end zone entity.");
            return;
        }
        endEntity.setCorner1(corners[0]);
        endEntity.setCorner2(corners[1]);
        endEntity.setPairedMap(mapName);

        for (ServerPlayerEntity worldPlayer : world.getPlayers()) {
            PacketHandler.updateZone(worldPlayer, endEntity.getId(), corners[0], corners[1], mapName, 0);
        }
        BoundsStickItem.clearSelection(player);
        Logger.logSuccess(player, "Created end zone for '" + mapName + "'.");
    }

    private static void addResetZone(ServerPlayerEntity player, String mapName, int checkpointIndex) {
        DataManager.MapData mapData = DataManager.getMap(mapName);
        if (mapData == null) {
            Logger.logFailure(player, "There is no map called " + mapName + ".");
            return;
        }
        if (!validateResetCheckpointTarget(player, mapData, checkpointIndex)) {
            return;
        }

        BlockPos[] corners = getBoundsCorners(player);
        if (corners == null) {
            return;
        }

        ServerWorld world = player.getServerWorld();
        ResetEntity resetEntity = ModEntities.RESET_ENTITY.spawn(world, corners[0], SpawnReason.NATURAL);
        if (resetEntity == null) {
            Logger.logFailure(player, "Failed to create reset zone entity.");
            return;
        }
        resetEntity.setCorner1(corners[0]);
        resetEntity.setCorner2(corners[1]);
        resetEntity.setPairedMap(mapName);
        resetEntity.setCheckIndex(Math.max(0, checkpointIndex));
        resetEntity.setPreserveSpeed(true);

        for (ServerPlayerEntity worldPlayer : world.getPlayers()) {
            PacketHandler.updateZone(worldPlayer, resetEntity.getId(), corners[0], corners[1], mapName, Math.max(0, checkpointIndex));
        }
        BoundsStickItem.clearSelection(player);
        if (checkpointIndex <= 0) {
            Logger.logSuccess(player, "Created reset zone for '" + mapName + "' targeting start spawn.");
        } else {
            Logger.logSuccess(player, "Created reset zone for '" + mapName + "' targeting checkpoint " + checkpointIndex + ".");
        }
    }

    private static BlockPos[] getBoundsCorners(ServerPlayerEntity player) {
        BlockPos[] corners = BoundsStickItem.playerPositions.get(player.getNameForScoreboard());
        if (corners == null || corners.length < 2 || corners[0] == null || corners[1] == null) {
            Logger.logFailure(player, "Set both zone corners with the zone stick before adding zones.");
            return null;
        }
        if (!player.hasPermissionLevel(4) && player.getWorld() instanceof ServerWorld serverWorld) {
            BlockPos minCorner = corners[0].toImmutable();
            BlockPos maxCornerExclusive = corners[1].toImmutable();
            BlockPos maxCornerInclusive = new BlockPos(
                    maxCornerExclusive.getX() - 1,
                    maxCornerExclusive.getY() - 1,
                    maxCornerExclusive.getZ() - 1
            );
            if (!UserPlotManager.canBuildAt(player, serverWorld, minCorner)
                    || !UserPlotManager.canBuildAt(player, serverWorld, maxCornerInclusive)) {
                Logger.logFailure(player, "Zone bounds must stay inside your own plot.");
                return null;
            }
        }
        return new BlockPos[]{corners[0].toImmutable(), corners[1].toImmutable()};
    }

    private static String sanitizeMapName(String rawName) {
        if (rawName == null) {
            return null;
        }
        String trimmed = rawName.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_MAP_NAME_LENGTH) {
            return null;
        }
        if (trimmed.contains("~") || trimmed.contains("\n") || trimmed.contains("\r")) {
            return null;
        }
        String censored = DescriptionCensor.censorProfanity(trimmed).trim();
        if (censored.isEmpty()) {
            return null;
        }
        return censored;
    }

    private static int clampDifficulty(int value) {
        return Math.max(MIN_DIFFICULTY, Math.min(MAX_DIFFICULTY, value));
    }

    private static MinehopConfig movementConfigFromMap(DataManager.MapData mapData) {
        MinehopConfig config = new MinehopConfig();
        if (mapData == null) {
            return config;
        }
        DataManager.sanitizeMapMovementFields(mapData);
        config.movement.sv_friction = mapData.movement_sv_friction;
        config.movement.sv_accelerate = mapData.movement_sv_accelerate;
        config.movement.sv_airaccelerate = mapData.movement_sv_airaccelerate;
        config.movement.sv_maxairspeed = mapData.movement_sv_maxairspeed;
        config.movement.sv_jump_impulse = mapData.movement_sv_jump_impulse;
        config.movement.speed_mul = mapData.movement_speed_mul;
        config.movement.sv_gravity = mapData.movement_sv_gravity;
        config.movement.sv_stopspeed = mapData.movement_sv_stopspeed;
        config.movement.speed_coefficient = mapData.movement_speed_coefficient;
        config.movement.auto_step_up = mapData.movement_auto_step_up;
        config.movement.css_crouch_jump = mapData.movement_css_crouch_jump;
        config.fall_damage = mapData.movement_fall_damage;
        return config;
    }

    private static boolean validateResetCheckpointTarget(ServerPlayerEntity player, DataManager.MapData mapData, int checkpointIndex) {
        if (mapData == null || checkpointIndex <= 0) {
            return true;
        }

        int checkpointCount = mapData.checkpointPositions == null ? 0 : mapData.checkpointPositions.size();
        if (checkpointCount <= 0) {
            Logger.logFailure(player, "Map '" + mapData.name + "' has no checkpoints. Set Reset Destination to Start Spawn or add checkpoints first.");
            return false;
        }
        if (checkpointIndex > checkpointCount) {
            Logger.logFailure(player, "Checkpoint " + checkpointIndex + " does not exist on '" + mapData.name + "' (current count: " + checkpointCount + ").");
            return false;
        }
        return true;
    }

    private static void syncMaps(ServerWorld world) {
        for (ServerPlayerEntity worldPlayer : world.getPlayers()) {
            PacketHandler.sendMaps(worldPlayer);
        }
    }
}
