package net.nerdorg.minehop.networking;

import net.nerdorg.minehop.platform.Services;
import net.minecraft.server.level.ServerLevel;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.commands.SpectateCommands;
import net.nerdorg.minehop.data.DataManager;
import net.nerdorg.minehop.util.SurfRampPlacementManager;
import net.nerdorg.minehop.util.UserPlotManager;

import java.util.ArrayList;
import java.util.List;

public class JoinLeaveManager {
    public static void register() {
        Services.EVENTS.onGameMessage(((server, message, overlay) -> {

        }));

        Services.NETWORK.onPlayConnectionDisconnect(((networkHandler, server) -> {
            if (networkHandler.player != null) {
                if (networkHandler.player.getCamera() != null) {
                    if (SpectateCommands.spectatorList.containsKey(networkHandler.player.getCamera().getScoreboardName())) {
                        List<String> spectators = SpectateCommands.spectatorList.get(networkHandler.player.getCamera().getScoreboardName());
                        if (spectators.contains(networkHandler.player.getScoreboardName())) {
                            spectators.remove(networkHandler.player.getScoreboardName());
                        }
                    }
                }
                SurfRampPlacementManager.onPlayerDisconnect(networkHandler.player.getUUID());
                UserPlotManager.onPlayerDisconnect(networkHandler.player);
                net.nerdorg.minehop.util.PacketRateLimiter.clear(networkHandler.player.getUUID());
            }
        }));

        Services.NETWORK.onPlayConnectionJoin(((networkHandler, server) -> {
            if (!networkHandler.player.hasPermissions(4)) {
                DataManager.MapData mapData = DataManager.getMap("spawn");
                if (mapData != null) {
                    if (mapData.worldKey == null || mapData.worldKey.equals("")) {
                        Minehop.mapList.remove(mapData);
                        mapData.worldKey = server.overworld().dimension().toString();
                        Minehop.mapList.add(mapData);
                        DataManager.saveData(networkHandler.player.serverLevel(), DataManager.mapListLocation, Minehop.mapList);
                    }
                    ServerLevel foundWorld = null;
                    for (ServerLevel serverWorld : server.getAllLevels()) {
                        if (serverWorld.dimension().toString().equals(mapData.worldKey)) {
                            foundWorld = serverWorld;
                            break;
                        }
                    }
                    if (foundWorld != null) {
                        networkHandler.player.getInventory().clearContent();
                        if (networkHandler.player.serverLevel() == foundWorld) {
                            networkHandler.player.teleportTo(
                                    mapData.x,
                                    mapData.y,
                                    mapData.z
                            );
                        } else {
                            // Logged out in another dimension (a map built in its own dimension, a plot): move them to
                            // the spawn map's world. teleportTo(x, y, z) stays in the current dimension, which put
                            // them at the lobby's coordinates in that dimension's void (instant death on rejoin).
                            networkHandler.player.teleport(net.nerdorg.minehop.util.ZoneUtil.makeTeleportTarget(
                                    foundWorld,
                                    new net.minecraft.world.phys.Vec3(mapData.x, mapData.y, mapData.z),
                                    networkHandler.player.getYRot(),
                                    networkHandler.player.getXRot()
                            ));
                        }
                    }
                }
            }
        }));
    }
}
