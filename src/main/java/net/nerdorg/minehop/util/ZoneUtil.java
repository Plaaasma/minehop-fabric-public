package net.nerdorg.minehop.util;

import com.mojang.datafixers.util.Pair;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.TeleportTarget;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.anticheat.AntiCheatManager;
import net.nerdorg.minehop.data.DataManager;
import net.nerdorg.minehop.entity.custom.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ZoneUtil {
    public static String getCurrentMapName(Entity target_entity) {
        if (Minehop.playerMapLocation.containsKey(target_entity.getUuidAsString())) {
            return Minehop.playerMapLocation.get(target_entity.getUuidAsString()).getPairedMap();
        }

        return null;
    }

    public static GamemodeEntity getGamemodeEntity(String map_name, ServerWorld serverWorld) {
        for (Entity entity : serverWorld.iterateEntities()) {
            if (entity instanceof GamemodeEntity gamemodeEntity) {
                if (Objects.equals(gamemodeEntity.getPairedMap(), map_name)) {
                    return gamemodeEntity;
                }
            }
        }

        return null;
    }

    public static DataManager.MapData getCurrentMap(Entity target_entity) {
        return DataManager.getMap(getCurrentMapName(target_entity));
    }

    public static DataManager.MapData getCurrentMapForPlayerCount(ServerPlayerEntity player) {
        if (player == null || !(player.getWorld() instanceof ServerWorld serverWorld)) {
            return null;
        }

        DataManager.MapData zoneTracked = getCurrentMap(player);
        if (zoneTracked != null) {
            return zoneTracked;
        }

        DataManager.MapData insidePlot = resolveUserPlotByPosition(serverWorld, player.getPos());
        if (insidePlot != null) {
            return insidePlot;
        }

        return resolveBySpawnProximity(serverWorld, player.getPos(), 8.0D);
    }

    public static TeleportTarget makeTeleportTarget(ServerWorld serverWorld, Vec3d targetLocation, float yaw, float pitch) {
        return new TeleportTarget(serverWorld, new Vec3d(targetLocation.getX(), targetLocation.getY(), targetLocation.getZ()), Vec3d.ZERO, yaw, pitch, (playerEntity) -> {
            if (playerEntity instanceof net.minecraft.server.network.ServerPlayerEntity serverPlayer) {
                AntiCheatManager.markAuthorizedTeleport(serverPlayer);
            }
        });
    }

    private static DataManager.MapData resolveUserPlotByPosition(ServerWorld world, Vec3d pos) {
        for (DataManager.MapData mapData : Minehop.mapList) {
            if (mapData == null || !mapData.userMap) {
                continue;
            }
            if (!isInWorld(mapData, world)) {
                continue;
            }
            if (isInsidePlotBounds(mapData, pos)) {
                return mapData;
            }
        }
        return null;
    }

    private static DataManager.MapData resolveBySpawnProximity(ServerWorld world, Vec3d pos, double radius) {
        double radiusSq = radius * radius;
        double bestDistSq = Double.MAX_VALUE;
        DataManager.MapData best = null;

        for (DataManager.MapData mapData : Minehop.mapList) {
            if (mapData == null) {
                continue;
            }
            if (!isInWorld(mapData, world)) {
                continue;
            }
            double dx = pos.x - mapData.x;
            double dy = pos.y - mapData.y;
            double dz = pos.z - mapData.z;
            double distSq = dx * dx + dy * dy + dz * dz;
            if (distSq <= radiusSq && distSq < bestDistSq) {
                bestDistSq = distSq;
                best = mapData;
            }
        }

        return best;
    }

    private static boolean isInWorld(DataManager.MapData mapData, ServerWorld world) {
        if (mapData == null || world == null) {
            return false;
        }
        if (mapData.worldKey == null || mapData.worldKey.isBlank()) {
            return world.getRegistryKey() == world.getServer().getOverworld().getRegistryKey();
        }
        return mapData.worldKey.equals(world.getRegistryKey().toString());
    }

    private static boolean isInsidePlotBounds(DataManager.MapData mapData, Vec3d pos) {
        if (mapData == null || pos == null || !mapData.userMap) {
            return false;
        }
        if (mapData.plotMaxX <= mapData.plotMinX || mapData.plotMaxZ <= mapData.plotMinZ) {
            return false;
        }
        return pos.x >= mapData.plotMinX
                && pos.x < mapData.plotMaxX
                && pos.z >= mapData.plotMinZ
                && pos.z < mapData.plotMaxZ;
    }
}
