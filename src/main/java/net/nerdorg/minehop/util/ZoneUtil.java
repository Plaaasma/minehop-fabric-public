package net.nerdorg.minehop.util;

import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.nerdorg.minehop.data.DataManager;
import net.nerdorg.minehop.entity.custom.EndEntity;
import net.nerdorg.minehop.entity.custom.ResetEntity;
import net.nerdorg.minehop.entity.custom.StartEntity;
import net.nerdorg.minehop.entity.custom.Zone;

import java.util.ArrayList;
import java.util.List;

public class ZoneUtil {
    public static String getCurrentMapName(ServerPlayerEntity serverPlayerEntity, ServerWorld serverWorld) {
        String mapName = "";

        List<Zone> zoneEntities = new ArrayList<>();
        for (Entity entity : serverWorld.iterateEntities()) {
            if (entity instanceof Zone zone) {
                zoneEntities.add(zone);
            }
        }
        double closestDistance = Double.POSITIVE_INFINITY;
        Zone closestEntity = null;
        for (Zone zoneEntity : zoneEntities) {
            double distance = zoneEntity.distanceTo(serverPlayerEntity);
            if (distance < closestDistance) {
                closestEntity = zoneEntity;
                closestDistance = distance;
            }
        }
        if (closestEntity == null) {
            Logger.logFailure(serverPlayerEntity, "Error finding nearest map.");
        }
        else {
            DataManager.MapData currentMapData = null;

            if (closestEntity instanceof ResetEntity resetEntity) {
                mapName = resetEntity.getPairedMap();
            } else if (closestEntity instanceof StartEntity startEntity) {
                mapName = startEntity.getPairedMap();
            } else if (closestEntity instanceof EndEntity endEntity) {
                mapName = endEntity.getPairedMap();
            }
        }

        if (mapName.equals("")) {
            return null;
        }
        else {
            return mapName;
        }
    }
}
