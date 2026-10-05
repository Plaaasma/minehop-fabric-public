package net.nerdorg.minehop.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.nerdorg.minehop.platform.Services;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.data.DataManager;
import net.nerdorg.minehop.entity.ModEntities;
import net.nerdorg.minehop.entity.custom.ReplayEntity;
import net.nerdorg.minehop.networking.PacketHandler;
import net.nerdorg.minehop.replays.ReplayManager;
import net.nerdorg.minehop.util.Logger;
import net.nerdorg.minehop.util.ZoneUtil;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class ReplayCommands {
    public static void register() {
        Services.EVENTS.onRegisterCommands((dispatcher, registryAccess, environment) -> dispatcher.register(
                LiteralArgumentBuilder.<CommandSourceStack>literal("replay")
                        .then(LiteralArgumentBuilder.<CommandSourceStack>literal("watch")
                                .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("map_name", StringArgumentType.string())
                                        .suggests((context, builder) -> {
                                            for (DataManager.MapData mapData : Minehop.mapList) {
                                                if (mapData != null && mapData.name != null) {
                                                    builder.suggest(mapData.name, new LiteralMessage(mapData.name));
                                                }
                                            }
                                            return builder.buildFuture();
                                        })
                                        .executes(context -> {
                                            handleWatchReplay(context, null);
                                            return Command.SINGLE_SUCCESS;
                                        })
                                        .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("player_name", StringArgumentType.string())
                                                .suggests((context, builder) -> {
                                                    String mapName = StringArgumentType.getString(context, "map_name");
                                                    Set<String> playerNames = collectReplayPlayerNames(mapName);
                                                    for (String playerName : playerNames) {
                                                        builder.suggest(playerName, new LiteralMessage(playerName));
                                                    }
                                                    return builder.buildFuture();
                                                })
                                                .executes(context -> {
                                                    handleWatchReplay(context, StringArgumentType.getString(context, "player_name"));
                                                    return Command.SINGLE_SUCCESS;
                                                })
                                        )
                                )
                        )
                        .then(LiteralArgumentBuilder.<CommandSourceStack>literal("remove")
                                .requires(source -> source.hasPermission(4))
                                .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("map_name", StringArgumentType.string())
                                        .suggests((context, builder) -> {
                                            for (DataManager.MapData mapData : Minehop.mapList) {
                                                if (mapData != null && mapData.name != null) {
                                                    builder.suggest(mapData.name, new LiteralMessage(mapData.name));
                                                }
                                            }
                                            return builder.buildFuture();
                                        })
                                        .executes(context -> {
                                            handleRemoveReplay(context);
                                            return Command.SINGLE_SUCCESS;
                                        })
                                )
                        )
                        .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("map_name", StringArgumentType.string())
                                .requires(source -> source.hasPermission(4))
                                .suggests((context, builder) -> {
                                    for (DataManager.MapData mapData : Minehop.mapList) {
                                        if (mapData != null && mapData.name != null) {
                                            builder.suggest(mapData.name, new LiteralMessage(mapData.name));
                                        }
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(context -> {
                                    handleAddReplay(context);
                                    return Command.SINGLE_SUCCESS;
                                })
                        )
        ));
    }

    private static void handleAddReplay(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer sender = context.getSource().getPlayer();
        String mapName = StringArgumentType.getString(context, "map_name");

        ReplayManager.Replay replay = ReplayManager.getReplay(mapName);
        if (replay == null) {
            Logger.logFailure(sender, "No replay found for map " + mapName + ".");
            return;
        }

        DataManager.MapData mapData = DataManager.getMap(mapName);
        if (mapData == null) {
            Logger.logFailure(sender, "Map " + mapName + " was not found.");
            return;
        }

        ServerLevel foundWorld = resolveMapWorld(context.getSource(), mapData);
        if (foundWorld == null) {
            Logger.logFailure(sender, "Could not resolve world for map " + mapName + ".");
            return;
        }

        removeReplayEntities(foundWorld, mapName, "");
        ReplayEntity replayEntity = ModEntities.REPLAY_ENTITY.get().spawn(
                foundWorld,
                new BlockPos((int) mapData.x, (int) mapData.y, (int) mapData.z),
                MobSpawnType.NATURAL
        );
        if (replayEntity == null) {
            Logger.logFailure(sender, "Failed to spawn replay entity.");
            return;
        }
        replayEntity.setReplay(mapName, "", false, false);
        Logger.logSuccess(sender, "Spawned WR replay entity for " + mapName + ".");
    }

    private static void handleRemoveReplay(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer sender = context.getSource().getPlayer();
        String mapName = StringArgumentType.getString(context, "map_name");

        DataManager.MapData mapData = DataManager.getMap(mapName);
        if (mapData == null) {
            Logger.logFailure(sender, "Map " + mapName + " was not found.");
            return;
        }

        ServerLevel foundWorld = resolveMapWorld(context.getSource(), mapData);
        if (foundWorld == null) {
            Logger.logFailure(sender, "Could not resolve world for map " + mapName + ".");
            return;
        }

        int removed = removeReplayEntities(foundWorld, mapName, "");
        if (removed <= 0) {
            Logger.logFailure(sender, "No WR replay entity was found for " + mapName + ".");
            return;
        }
        Logger.logSuccess(sender, "Removed WR replay entity for " + mapName + ".");
    }

    private static void handleWatchReplay(CommandContext<CommandSourceStack> context, String explicitTargetPlayer) throws CommandSyntaxException {
        ServerPlayer viewer = context.getSource().getPlayer();
        String requestedMapName = StringArgumentType.getString(context, "map_name");
        DataManager.MapData mapData = DataManager.getMap(requestedMapName);
        if (mapData == null) {
            Logger.logFailure(viewer, "Map " + requestedMapName + " was not found.");
            return;
        }
        String mapName = mapData.name;
        String targetPlayer = explicitTargetPlayer;
        if (targetPlayer == null || targetPlayer.isBlank()) {
            targetPlayer = viewer.getScoreboardName();
        }

        ReplayManager.Replay replay = ReplayManager.getReplay(mapName, targetPlayer);
        if (replay == null) {
            Logger.logFailure(viewer, "No saved replay found for " + targetPlayer + " on " + mapName + ".");
            return;
        }

        ServerLevel foundWorld = resolveMapWorld(context.getSource(), mapData);
        if (foundWorld == null) {
            Logger.logFailure(viewer, "Could not resolve world for map " + mapName + ".");
            return;
        }

        ReplayEntity replayEntity = findReplayEntity(foundWorld, mapName, targetPlayer);
        if (replayEntity == null) {
            replayEntity = ModEntities.REPLAY_ENTITY.get().spawn(
                    foundWorld,
                    new BlockPos((int) mapData.x, (int) mapData.y, (int) mapData.z),
                    MobSpawnType.NATURAL
            );
            if (replayEntity == null) {
                Logger.logFailure(viewer, "Failed to spawn replay viewer entity.");
                return;
            }
            replayEntity.setReplay(mapName, targetPlayer, true, true);
        }

        beginSpectatingReplay(viewer, replayEntity);
        Logger.logSuccess(viewer, "Now watching " + targetPlayer + "'s fastest replay on " + mapName + " (" + String.format("%.5f", replay.time) + ").");
    }

    private static void beginSpectatingReplay(ServerPlayer viewer, ReplayEntity replayEntity) {
        if (viewer == null || replayEntity == null) {
            return;
        }
        removeViewerFromPreviousSpectate(viewer);
        PacketHandler.clearReplayPath(viewer);
        viewer.setCamera(viewer);
        viewer.setGameMode(GameType.SPECTATOR);
        if (!viewer.isCreative()) {
            viewer.getInventory().clearContent();
        }
        ZoneUtil.teleportTo(viewer, ZoneUtil.makeTeleportTarget(
                (ServerLevel) replayEntity.level(),
                new Vec3(replayEntity.getX(), replayEntity.getY(), replayEntity.getZ()),
                replayEntity.getYRot(),
                replayEntity.getXRot()
        ));
        viewer.setCamera(replayEntity);
        SpectateCommands.addSpectator(replayEntity.getScoreboardName(), viewer.getScoreboardName());
    }

    private static void removeViewerFromPreviousSpectate(ServerPlayer viewer) {
        if (viewer == null || viewer.getCamera() == null) {
            return;
        }
        String oldTargetName = viewer.getCamera().getScoreboardName();
        List<String> oldSpectators = SpectateCommands.spectatorList.get(oldTargetName);
        if (oldSpectators == null) {
            return;
        }
        oldSpectators.remove(viewer.getScoreboardName());
        if (oldSpectators.size() <= 1) {
            SpectateCommands.spectatorList.remove(oldTargetName);
        }
    }

    private static ReplayEntity findReplayEntity(ServerLevel world, String mapName, String replayPlayerName) {
        if (world == null || mapName == null || mapName.isBlank() || replayPlayerName == null || replayPlayerName.isBlank()) {
            return null;
        }
        for (Entity entity : world.getAllEntities()) {
            if (!(entity instanceof ReplayEntity replayEntity)) {
                continue;
            }
            if (!mapName.equals(replayEntity.getMapName())) {
                continue;
            }
            if (!replayPlayerName.equals(replayEntity.getReplayPlayerName())) {
                continue;
            }
            if (replayEntity.shouldRenderHead()) {
                continue;
            }
            return replayEntity;
        }
        return null;
    }

    private static ReplayEntity findWorldRecordReplayEntity(ServerLevel world, String mapName) {
        if (world == null || mapName == null || mapName.isBlank()) {
            return null;
        }
        for (Entity entity : world.getAllEntities()) {
            if (!(entity instanceof ReplayEntity replayEntity)) {
                continue;
            }
            // A killed entity lingers while dying; reusing it would leave the map without a replay.
            if (!replayEntity.isAlive() || replayEntity.isRemoved()) {
                continue;
            }
            if (!mapName.equals(replayEntity.getMapName())) {
                continue;
            }
            if (!replayEntity.getReplayPlayerName().isBlank()) {
                continue;
            }
            return replayEntity;
        }
        return null;
    }

    private static int removeReplayEntities(ServerLevel world, String mapName, String replayPlayerName) {
        if (world == null || mapName == null || mapName.isBlank()) {
            return 0;
        }
        List<ReplayEntity> toRemove = new ArrayList<>();
        for (Entity entity : world.getAllEntities()) {
            if (!(entity instanceof ReplayEntity replayEntity)) {
                continue;
            }
            if (!mapName.equals(replayEntity.getMapName())) {
                continue;
            }
            if (!replayPlayerName.equals(replayEntity.getReplayPlayerName())) {
                continue;
            }
            toRemove.add(replayEntity);
        }
        for (ReplayEntity replayEntity : toRemove) {
            SpectateCommands.spectatorList.remove(replayEntity.getScoreboardName());
            replayEntity.kill();
        }
        return toRemove.size();
    }

    /**
     * Removes the in-world world-record replay for a map (used when the map no longer has a WR replay)
     * and returns the names of the players who were spectating it, so they can be sent back.
     */
    public static List<String> removeWorldRecordReplayEntities(MinecraftServer server, String mapName) {
        List<String> spectators = new ArrayList<>();
        if (server == null || mapName == null || mapName.isBlank()) {
            return spectators;
        }
        for (ServerLevel world : server.getAllLevels()) {
            List<ReplayEntity> toRemove = new ArrayList<>();
            for (Entity entity : world.getAllEntities()) {
                if (entity instanceof ReplayEntity replayEntity
                        && replayEntity.isAlive() && !replayEntity.isRemoved()
                        && mapName.equals(replayEntity.getMapName())
                        && replayEntity.getReplayPlayerName().isBlank()) {
                    toRemove.add(replayEntity);
                }
            }
            for (ReplayEntity replayEntity : toRemove) {
                List<String> watching = SpectateCommands.spectatorList.remove(replayEntity.getScoreboardName());
                if (watching != null) {
                    spectators.addAll(watching);
                }
                replayEntity.kill();
            }
        }
        return spectators;
    }

    private static Set<String> collectReplayPlayerNames(String mapName) {
        Set<String> playerNames = new LinkedHashSet<>();
        if (mapName == null || mapName.isBlank()) {
            return playerNames;
        }
        if (Minehop.replayList == null) {
            return playerNames;
        }
        for (ReplayManager.Replay replay : Minehop.replayList) {
            if (replay == null || replay.map_name == null || replay.player_name == null || replay.player_name.isBlank()) {
                continue;
            }
            if (mapName.equals(replay.map_name)) {
                playerNames.add(replay.player_name);
            }
        }
        for (DataManager.RecordData recordData : Minehop.personalRecordList) {
            if (recordData == null || recordData.map_name == null || recordData.name == null) {
                continue;
            }
            if (mapName.equals(recordData.map_name)) {
                playerNames.add(recordData.name);
            }
        }
        return playerNames;
    }

    private static ServerLevel resolveMapWorld(CommandSourceStack source, DataManager.MapData mapData) {
        if (source == null || mapData == null) {
            return null;
        }
        return resolveMapWorld(source.getServer(), mapData);
    }

    private static ServerLevel resolveMapWorld(MinecraftServer server, DataManager.MapData mapData) {
        if (server == null || mapData == null) {
            return null;
        }
        String worldKey = mapData.worldKey;
        for (ServerLevel serverWorld : server.getAllLevels()) {
            if (serverWorld.dimension().toString().equals(worldKey)) {
                return serverWorld;
            }
        }
        return server.overworld();
    }

    public static boolean ensureWorldRecordReplayEntity(MinecraftServer server, String mapName) {
        if (server == null || mapName == null || mapName.isBlank()) {
            return false;
        }

        DataManager.MapData mapData = DataManager.getMap(mapName);
        if (mapData == null) {
            return false;
        }
        ReplayManager.Replay replay = ReplayManager.getReplay(mapName);
        if (replay == null) {
            return false;
        }

        ServerLevel world = resolveMapWorld(server, mapData);
        if (world == null) {
            return false;
        }

        ReplayEntity existing = findWorldRecordReplayEntity(world, mapName);
        if (existing != null) {
            existing.setReplay(mapName, "", false, false);
            return true;
        }

        ReplayEntity replayEntity = ModEntities.REPLAY_ENTITY.get().spawn(
                world,
                new BlockPos((int) mapData.x, (int) mapData.y, (int) mapData.z),
                MobSpawnType.NATURAL
        );
        if (replayEntity == null) {
            return false;
        }
        replayEntity.setReplay(mapName, "", false, false);
        return true;
    }
}
