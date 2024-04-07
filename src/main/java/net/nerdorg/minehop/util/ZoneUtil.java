package net.nerdorg.minehop.util;

import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.data.DataManager;
import net.nerdorg.minehop.entity.custom.EndEntity;
import net.nerdorg.minehop.entity.custom.ResetEntity;
import net.nerdorg.minehop.entity.custom.StartEntity;
import net.nerdorg.minehop.entity.custom.Zone;

import java.util.ArrayList;
import java.util.List;

public class ZoneUtil {
    public static String getCurrentMapName(Entity target_entity) {
        Vec3d targetPos = target_entity.getPos();

        String closestMapName = "";
        double closestMapDistance = Double.POSITIVE_INFINITY;
        for (DataManager.MapData mapData : Minehop.mapList) {
            Vec3d mapPos = new Vec3d(mapData.x, mapData.y, mapData.z);
            double mapDistance = mapPos.distanceTo(targetPos);
            if (mapDistance < closestMapDistance) {
                closestMapDistance = mapDistance;
                closestMapName = mapData.name;
            }
        }
        if (closestMapName.equals("")) {
            return null;
        }
        else {
            return closestMapName;
        }
    }

    public static DataManager.MapData getCurrentMap(Entity target_entity) {
        Vec3d targetPos = target_entity.getPos();

        DataManager.MapData closestMap = null;
        double closestMapDistance = Double.POSITIVE_INFINITY;
        for (DataManager.MapData mapData : Minehop.mapList) {
            Vec3d mapPos = new Vec3d(mapData.x, mapData.y, mapData.z);
            double mapDistance = mapPos.distanceTo(targetPos);
            if (mapDistance < closestMapDistance) {
                closestMapDistance = mapDistance;
                closestMap = mapData;
            }
        }
        return closestMap;
    }
}
