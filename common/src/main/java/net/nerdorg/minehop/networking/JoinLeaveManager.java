package net.nerdorg.minehop.networking;

import net.nerdorg.minehop.platform.Services;
import net.minecraft.server.level.ServerLevel;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.data.DataManager;
import net.nerdorg.minehop.spectate.SpectateSessions;
import net.nerdorg.minehop.util.SurfRampPlacementManager;
import net.nerdorg.minehop.util.UserPlotManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class JoinLeaveManager {
    /**
     * Moves to make once a joining player is fully in the world. A dimension change from inside the join event runs
     * before vanilla has finished placing the player in the level they logged out in, so the player ended up added to
     * both levels: the leftover entity came back as "Force-added player with duplicate UUID" on the next join, and the
     * disconnect after that crashed the server (DistanceManager#removePlayer NPE). Server thread only.
     */
    private record PendingMove(UUID player, ServerLevel level, Vec3 position, float yRot, float xRot, int afterTick) {
    }

    private static final List<PendingMove> PENDING_MOVES = new ArrayList<>();

    /** Teleports the player once their join has completed (at the end of the next server tick). */
    public static void moveAfterJoin(ServerPlayer player, ServerLevel level, Vec3 position, float yRot, float xRot) {
        MinecraftServer server = player.getServer();
        int tick = server == null ? 0 : server.getTickCount();
        PENDING_MOVES.removeIf(move -> move.player().equals(player.getUUID()));
        PENDING_MOVES.add(new PendingMove(player.getUUID(), level, position, yRot, xRot, tick));
    }

    private static void applyPendingMoves(MinecraftServer server) {
        if (PENDING_MOVES.isEmpty()) {
            return;
        }
        int tick = server.getTickCount();
        PENDING_MOVES.removeIf(move -> {
            if (tick <= move.afterTick()) {
                return false;
            }
            ServerPlayer player = server.getPlayerList().getPlayer(move.player());
            if (player != null && !player.isRemoved()) {
                net.nerdorg.minehop.util.ZoneUtil.teleportTo(player, net.nerdorg.minehop.util.ZoneUtil.makeTeleportTarget(move.level(), move.position(), move.yRot(), move.xRot()));
            }
            return true;
        });
    }

    public static void register() {
        Services.EVENTS.onServerTickEnd(JoinLeaveManager::applyPendingMoves);
        Services.EVENTS.onGameMessage(((server, message, overlay) -> {

        }));

        Services.NETWORK.onPlayConnectionDisconnect(((networkHandler, server) -> {
            if (networkHandler.player != null) {
                // A spectating player gets their game mode and position back before they are saved.
                SpectateSessions.onDisconnect(networkHandler.player);
                PENDING_MOVES.removeIf(move -> move.player().equals(networkHandler.player.getUUID()));
                net.nerdorg.minehop.replays.ReplayStreaming.onDisconnect(networkHandler.player);
                net.nerdorg.minehop.replays.RunStats.forget(networkHandler.player);
                net.nerdorg.minehop.commands.SpectateCommands.forget(networkHandler.player.getUUID());
                PacketHandler.clearRunState(networkHandler.player, server);
                // A finished run's replay keeps the post-run frames recorded so far.
                net.nerdorg.minehop.replays.RunRecorder.onDisconnect(networkHandler.player);
                net.nerdorg.minehop.replays.RunClock.forget(networkHandler.player);
                SurfRampPlacementManager.onPlayerDisconnect(networkHandler.player.getUUID());
                UserPlotManager.onPlayerDisconnect(networkHandler.player);
                net.nerdorg.minehop.util.PacketRateLimiter.clear(networkHandler.player.getUUID());
            }
        }));

        Services.NETWORK.onPlayConnectionJoin(((networkHandler, server) -> {
            // A fresh session never continues an earlier run (or its replay recording).
            PacketHandler.clearRunState(networkHandler.player, server);
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
                            // them at the lobby's coordinates in that dimension's void (instant death on rejoin). The
                            // dimension change waits until the join has completed (see PENDING_MOVES).
                            moveAfterJoin(networkHandler.player, foundWorld, new Vec3(mapData.x, mapData.y, mapData.z),
                                    networkHandler.player.getYRot(), networkHandler.player.getXRot());
                        }
                    }
                }
            }
            SpectateSessions.onJoin(networkHandler.player);
        }));
    }
}
