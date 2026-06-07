package net.nerdorg.minehop.util;

import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.nerdorg.minehop.data.DataManager;
import net.nerdorg.minehop.entity.custom.EndEntity;
import net.nerdorg.minehop.entity.custom.ResetEntity;
import net.nerdorg.minehop.entity.custom.StartEntity;
import net.nerdorg.minehop.entity.custom.Zone;
import net.nerdorg.minehop.item.custom.BoundsStickItem;
import net.nerdorg.minehop.networking.PacketHandler;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ZonePlacementManager {
    private static final int MAX_MAP_NAME_LENGTH = 128;
    private static final Map<UUID, EditState> EDIT_STATES = new HashMap<>();

    private ZonePlacementManager() {
    }

    public static void openEditor(ServerPlayerEntity player, Zone zone) {
        if (player == null || zone == null) {
            return;
        }
        if (!UserPlotManager.canEditEntity(player, zone)) {
            Logger.logFailure(player, "You can only edit zones in your own plot.");
            return;
        }
        if (!(player.getWorld() instanceof ServerWorld playerWorld) || zone.getWorld() != playerWorld) {
            Logger.logFailure(player, "Could not edit that zone in this world.");
            return;
        }

        ZoneKind kind = ZoneKind.of(zone);
        if (kind == ZoneKind.UNKNOWN) {
            return;
        }

        EDIT_STATES.put(
                player.getUuid(),
                new EditState(
                        playerWorld.getRegistryKey().getValue().toString(),
                        zone.getUuid(),
                        kind
                )
        );

        int checkpointIndex = zone instanceof ResetEntity resetEntity ? resetEntity.getCheckIndex() : 0;
        boolean preserveSpeed = zone instanceof ResetEntity resetEntity && resetEntity.isPreserveSpeed();
        boolean preserveSpeedEditable = kind == ZoneKind.RESET;
        PacketHandler.openZoneStickSettings(
                player,
                kind.serializedName,
                zone.getPairedMap(),
                checkpointIndex,
                kind == ZoneKind.RESET,
                preserveSpeed,
                preserveSpeedEditable
        );
        Logger.logActionBar(player, "Editing " + kind.displayName + " zone.");
    }

    public static void applyOptionsFromGui(
            ServerPlayerEntity player,
            String rawMapName,
            int checkpointIndex,
            boolean applyBounds,
            boolean preserveSpeed
    ) {
        if (player == null) {
            return;
        }

        EditState editState = EDIT_STATES.get(player.getUuid());
        if (editState == null) {
            Logger.logFailure(player, "No zone edit is active.");
            return;
        }

        Zone zone = resolveZone(player, editState);
        if (zone == null) {
            EDIT_STATES.remove(player.getUuid());
            Logger.logFailure(player, "The selected zone no longer exists.");
            return;
        }
        if (!UserPlotManager.canEditEntity(player, zone)) {
            Logger.logFailure(player, "You can only edit zones in your own plot.");
            return;
        }

        String mapName = sanitizeMapName(rawMapName);
        if (mapName == null) {
            Logger.logFailure(player, "Map name must be 1-" + MAX_MAP_NAME_LENGTH + " chars and cannot contain '~'.");
            return;
        }
        if (DataManager.getMap(mapName) == null) {
            Logger.logFailure(player, "There is no map called " + mapName + ".");
            return;
        }
        DataManager.MapData mapData = DataManager.getMap(mapName);
        if (!player.hasPermissionLevel(4) && !UserPlotManager.canManageMapByName(player, mapName)) {
            Logger.logFailure(player, "You can only pair zones to your own plot map.");
            return;
        }

        zone.setPairedMap(mapName);

        if (zone instanceof ResetEntity resetEntity) {
            int safeCheckpoint = Math.max(0, checkpointIndex);
            if (!validateResetCheckpointTarget(player, mapData, safeCheckpoint)) {
                return;
            }
            resetEntity.setCheckIndex(safeCheckpoint);
            resetEntity.setPreserveSpeed(preserveSpeed);
        }

        if (applyBounds) {
            BlockPos[] positions = BoundsStickItem.playerPositions.get(player.getNameForScoreboard());
            if (positions == null || positions.length < 2 || positions[0] == null || positions[1] == null) {
                Logger.logFailure(player, "Set both zone corners with the zone stick first.");
                return;
            }
            if (!player.hasPermissionLevel(4) && player.getWorld() instanceof ServerWorld serverWorld) {
                BlockPos maxExclusive = positions[1].toImmutable();
                BlockPos maxInclusive = new BlockPos(
                        maxExclusive.getX() - 1,
                        maxExclusive.getY() - 1,
                        maxExclusive.getZ() - 1
                );
                if (!UserPlotManager.canBuildAt(player, serverWorld, positions[0].toImmutable())
                        || !UserPlotManager.canBuildAt(player, serverWorld, maxInclusive)) {
                    Logger.logFailure(player, "Zone bounds must stay inside your own plot.");
                    return;
                }
            }
            setZoneCorners(zone, positions[0], positions[1]);
        }

        Logger.logSuccess(player, "Updated " + editState.zoneKind.displayName + " zone.");
    }

    public static void deleteEditedZone(ServerPlayerEntity player) {
        if (player == null) {
            return;
        }
        EditState editState = EDIT_STATES.remove(player.getUuid());
        if (editState == null) {
            Logger.logFailure(player, "No zone edit is active.");
            return;
        }

        Zone zone = resolveZone(player, editState);
        if (zone == null) {
            Logger.logFailure(player, "The selected zone no longer exists.");
            return;
        }
        if (!UserPlotManager.canEditEntity(player, zone)) {
            Logger.logFailure(player, "You can only edit zones in your own plot.");
            return;
        }
        if (zone.getWorld() instanceof ServerWorld serverWorld) {
            zone.kill(serverWorld);
        } else {
            zone.remove(Entity.RemovalReason.KILLED);
        }
        Logger.logSuccess(player, "Deleted " + editState.zoneKind.displayName + " zone.");
    }

    public static void cancelEditing(ServerPlayerEntity player) {
        if (player == null) {
            return;
        }
        if (EDIT_STATES.remove(player.getUuid()) != null) {
            Logger.logActionBar(player, "Zone edit canceled.");
        }
    }

    private static Zone resolveZone(ServerPlayerEntity player, EditState editState) {
        if (player == null || editState == null) {
            return null;
        }
        for (ServerWorld serverWorld : player.getServer().getWorlds()) {
            String worldKey = serverWorld.getRegistryKey().getValue().toString();
            if (!worldKey.equals(editState.worldKey)) {
                continue;
            }
            Entity entity = serverWorld.getEntity(editState.zoneUuid);
            if (entity instanceof Zone zone && zone.isAlive() && !zone.isRemoved()) {
                if (ZoneKind.of(zone) == editState.zoneKind) {
                    return zone;
                }
            }
        }
        return null;
    }

    private static void setZoneCorners(Zone zone, BlockPos first, BlockPos second) {
        if (zone instanceof StartEntity startEntity) {
            startEntity.setCorner1(first.toImmutable());
            startEntity.setCorner2(second.toImmutable());
            return;
        }
        if (zone instanceof EndEntity endEntity) {
            endEntity.setCorner1(first.toImmutable());
            endEntity.setCorner2(second.toImmutable());
            return;
        }
        if (zone instanceof ResetEntity resetEntity) {
            resetEntity.setCorner1(first.toImmutable());
            resetEntity.setCorner2(second.toImmutable());
        }
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
        return trimmed;
    }

    private static boolean validateResetCheckpointTarget(ServerPlayerEntity player, DataManager.MapData mapData, int checkpointIndex) {
        if (mapData == null || checkpointIndex <= 0) {
            return true;
        }
        int checkpointCount = mapData.checkpointPositions == null ? 0 : mapData.checkpointPositions.size();
        if (checkpointCount <= 0) {
            Logger.logFailure(player, "Map '" + mapData.name + "' has no checkpoints. Use Start Spawn for this reset zone or add checkpoints first.");
            return false;
        }
        if (checkpointIndex > checkpointCount) {
            Logger.logFailure(player, "Checkpoint " + checkpointIndex + " does not exist on '" + mapData.name + "' (current count: " + checkpointCount + ").");
            return false;
        }
        return true;
    }

    private enum ZoneKind {
        START("start", "start"),
        END("end", "end"),
        RESET("reset", "reset"),
        UNKNOWN("unknown", "zone");

        private final String serializedName;
        private final String displayName;

        ZoneKind(String serializedName, String displayName) {
            this.serializedName = serializedName;
            this.displayName = displayName;
        }

        private static ZoneKind of(Zone zone) {
            if (zone instanceof StartEntity) {
                return START;
            }
            if (zone instanceof EndEntity) {
                return END;
            }
            if (zone instanceof ResetEntity) {
                return RESET;
            }
            return UNKNOWN;
        }
    }

    private static final class EditState {
        private final String worldKey;
        private final UUID zoneUuid;
        private final ZoneKind zoneKind;

        private EditState(String worldKey, UUID zoneUuid, ZoneKind zoneKind) {
            this.worldKey = worldKey == null ? "" : worldKey;
            this.zoneUuid = zoneUuid;
            this.zoneKind = zoneKind == null ? ZoneKind.UNKNOWN : zoneKind;
        }
    }
}
