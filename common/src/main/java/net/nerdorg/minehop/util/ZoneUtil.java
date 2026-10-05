package net.nerdorg.minehop.util;

import com.mojang.datafixers.util.Pair;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.anticheat.AntiCheatManager;
import net.nerdorg.minehop.data.DataManager;
import net.nerdorg.minehop.entity.custom.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ZoneUtil {
    public static String getCurrentMapName(Entity target_entity) {
        if (Minehop.playerMapLocation.containsKey(target_entity.getStringUUID())) {
            return Minehop.playerMapLocation.get(target_entity.getStringUUID()).getPairedMap();
        }

        return null;
    }

    public static GamemodeEntity getGamemodeEntity(String map_name, ServerLevel serverWorld) {
        for (Entity entity : serverWorld.getAllEntities()) {
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

    public static DataManager.MapData getCurrentMapForPlayerCount(ServerPlayer player) {
        if (player == null || !(player.level() instanceof ServerLevel serverWorld)) {
            return null;
        }

        DataManager.MapData zoneTracked = getCurrentMap(player);
        if (zoneTracked != null) {
            return zoneTracked;
        }

        DataManager.MapData insidePlot = resolveUserPlotByPosition(serverWorld, player.position());
        if (insidePlot != null) {
            return insidePlot;
        }

        return resolveBySpawnProximity(serverWorld, player.position(), 8.0D);
    }

    // 1.21.1: DimensionTransition / ServerPlayer#changeDimension (TeleportTransition / Entity#teleport since 1.21.2);
    // a same-level transition is a plain connection teleport there.
    public static DimensionTransition makeTeleportTarget(ServerLevel serverWorld, Vec3 targetLocation, float yaw, float pitch) {
        return new DimensionTransition(serverWorld, new Vec3(targetLocation.x(), targetLocation.y(), targetLocation.z()), Vec3.ZERO, yaw, pitch, (playerEntity) -> {
            if (playerEntity instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                AntiCheatManager.markAuthorizedTeleport(serverPlayer);
            }
        });
    }

    private static DataManager.MapData resolveUserPlotByPosition(ServerLevel world, Vec3 pos) {
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

    private static DataManager.MapData resolveBySpawnProximity(ServerLevel world, Vec3 pos, double radius) {
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

    private static boolean isInWorld(DataManager.MapData mapData, ServerLevel world) {
        if (mapData == null || world == null) {
            return false;
        }
        if (mapData.worldKey == null || mapData.worldKey.isBlank()) {
            return world.dimension() == world.getServer().overworld().dimension();
        }
        return mapData.worldKey.equals(world.dimension().toString());
    }

    private static boolean isInsidePlotBounds(DataManager.MapData mapData, Vec3 pos) {
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
