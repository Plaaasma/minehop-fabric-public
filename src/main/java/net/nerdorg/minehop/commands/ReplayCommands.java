package net.nerdorg.minehop.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.entity.Entity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.data.DataManager;
import net.nerdorg.minehop.entity.ModEntities;
import net.nerdorg.minehop.entity.custom.ReplayEntity;
import net.nerdorg.minehop.replays.ReplayManager;
import net.nerdorg.minehop.util.Logger;
import net.nerdorg.minehop.util.ZoneUtil;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class ReplayCommands {
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                LiteralArgumentBuilder.<ServerCommandSource>literal("replay")
                        .then(LiteralArgumentBuilder.<ServerCommandSource>literal("watch")
                                .then(RequiredArgumentBuilder.<ServerCommandSource, String>argument("map_name", StringArgumentType.string())
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
                                        .then(RequiredArgumentBuilder.<ServerCommandSource, String>argument("player_name", StringArgumentType.string())
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
                        .then(LiteralArgumentBuilder.<ServerCommandSource>literal("remove")
                                .requires(source -> source.hasPermissionLevel(4))
                                .then(RequiredArgumentBuilder.<ServerCommandSource, String>argument("map_name", StringArgumentType.string())
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
                        .then(RequiredArgumentBuilder.<ServerCommandSource, String>argument("map_name", StringArgumentType.string())
                                .requires(source -> source.hasPermissionLevel(4))
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

    private static void handleAddReplay(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity sender = context.getSource().getPlayer();
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

        ServerWorld foundWorld = resolveMapWorld(context.getSource(), mapData);
        if (foundWorld == null) {
            Logger.logFailure(sender, "Could not resolve world for map " + mapName + ".");
            return;
        }

        removeReplayEntities(foundWorld, mapName, "");
        ReplayEntity replayEntity = ModEntities.REPLAY_ENTITY.spawn(
                foundWorld,
                new BlockPos((int) mapData.x, (int) mapData.y, (int) mapData.z),
                SpawnReason.NATURAL
        );
        if (replayEntity == null) {
            Logger.logFailure(sender, "Failed to spawn replay entity.");
            return;
        }
        replayEntity.setReplay(mapName, "", false, false);
        Logger.logSuccess(sender, "Spawned WR replay entity for " + mapName + ".");
    }

    private static void handleRemoveReplay(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity sender = context.getSource().getPlayer();
        String mapName = StringArgumentType.getString(context, "map_name");

        DataManager.MapData mapData = DataManager.getMap(mapName);
        if (mapData == null) {
            Logger.logFailure(sender, "Map " + mapName + " was not found.");
            return;
        }

        ServerWorld foundWorld = resolveMapWorld(context.getSource(), mapData);
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

    private static void handleWatchReplay(CommandContext<ServerCommandSource> context, String explicitTargetPlayer) throws CommandSyntaxException {
        ServerPlayerEntity viewer = context.getSource().getPlayer();
        String requestedMapName = StringArgumentType.getString(context, "map_name");
        DataManager.MapData mapData = DataManager.getMap(requestedMapName);
        if (mapData == null) {
            Logger.logFailure(viewer, "Map " + requestedMapName + " was not found.");
            return;
        }
        String mapName = mapData.name;
        String targetPlayer = explicitTargetPlayer;
        if (targetPlayer == null || targetPlayer.isBlank()) {
            targetPlayer = viewer.getNameForScoreboard();
        }

        ReplayManager.Replay replay = ReplayManager.getReplay(mapName, targetPlayer);
        if (replay == null) {
            Logger.logFailure(viewer, "No saved replay found for " + targetPlayer + " on " + mapName + ".");
            return;
        }

        ServerWorld foundWorld = resolveMapWorld(context.getSource(), mapData);
        if (foundWorld == null) {
            Logger.logFailure(viewer, "Could not resolve world for map " + mapName + ".");
            return;
        }

        ReplayEntity replayEntity = findReplayEntity(foundWorld, mapName, targetPlayer);
        if (replayEntity == null) {
            replayEntity = ModEntities.REPLAY_ENTITY.spawn(
                    foundWorld,
                    new BlockPos((int) mapData.x, (int) mapData.y, (int) mapData.z),
                    SpawnReason.NATURAL
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

    private static void beginSpectatingReplay(ServerPlayerEntity viewer, ReplayEntity replayEntity) {
        if (viewer == null || replayEntity == null) {
            return;
        }
        removeViewerFromPreviousSpectate(viewer);
        viewer.setCameraEntity(viewer);
        viewer.changeGameMode(GameMode.SPECTATOR);
        if (!viewer.isCreative()) {
            viewer.getInventory().clear();
        }
        viewer.teleportTo(ZoneUtil.makeTeleportTarget(
                (ServerWorld) replayEntity.getWorld(),
                new Vec3d(replayEntity.getX(), replayEntity.getY(), replayEntity.getZ()),
                replayEntity.getYaw(),
                replayEntity.getPitch()
        ));
        viewer.setCameraEntity(replayEntity);
        SpectateCommands.addSpectator(replayEntity.getNameForScoreboard(), viewer.getNameForScoreboard());
    }

    private static void removeViewerFromPreviousSpectate(ServerPlayerEntity viewer) {
        if (viewer == null || viewer.getCameraEntity() == null) {
            return;
        }
        String oldTargetName = viewer.getCameraEntity().getNameForScoreboard();
        List<String> oldSpectators = SpectateCommands.spectatorList.get(oldTargetName);
        if (oldSpectators == null) {
            return;
        }
        oldSpectators.remove(viewer.getNameForScoreboard());
        if (oldSpectators.size() <= 1) {
            SpectateCommands.spectatorList.remove(oldTargetName);
        }
    }

    private static ReplayEntity findReplayEntity(ServerWorld world, String mapName, String replayPlayerName) {
        if (world == null || mapName == null || mapName.isBlank() || replayPlayerName == null || replayPlayerName.isBlank()) {
            return null;
        }
        for (Entity entity : world.iterateEntities()) {
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

    private static ReplayEntity findWorldRecordReplayEntity(ServerWorld world, String mapName) {
        if (world == null || mapName == null || mapName.isBlank()) {
            return null;
        }
        for (Entity entity : world.iterateEntities()) {
            if (!(entity instanceof ReplayEntity replayEntity)) {
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

    private static int removeReplayEntities(ServerWorld world, String mapName, String replayPlayerName) {
        if (world == null || mapName == null || mapName.isBlank()) {
            return 0;
        }
        List<ReplayEntity> toRemove = new ArrayList<>();
        for (Entity entity : world.iterateEntities()) {
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
            SpectateCommands.spectatorList.remove(replayEntity.getNameForScoreboard());
            replayEntity.kill(world);
        }
        return toRemove.size();
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

    private static ServerWorld resolveMapWorld(ServerCommandSource source, DataManager.MapData mapData) {
        if (source == null || mapData == null) {
            return null;
        }
        return resolveMapWorld(source.getServer(), mapData);
    }

    private static ServerWorld resolveMapWorld(MinecraftServer server, DataManager.MapData mapData) {
        if (server == null || mapData == null) {
            return null;
        }
        String worldKey = mapData.worldKey;
        for (ServerWorld serverWorld : server.getWorlds()) {
            if (serverWorld.getRegistryKey().toString().equals(worldKey)) {
                return serverWorld;
            }
        }
        return server.getOverworld();
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

        ServerWorld world = resolveMapWorld(server, mapData);
        if (world == null) {
            return false;
        }

        ReplayEntity existing = findWorldRecordReplayEntity(world, mapName);
        if (existing != null) {
            existing.setReplay(mapName, "", false, false);
            return true;
        }

        ReplayEntity replayEntity = ModEntities.REPLAY_ENTITY.spawn(
                world,
                new BlockPos((int) mapData.x, (int) mapData.y, (int) mapData.z),
                SpawnReason.NATURAL
        );
        if (replayEntity == null) {
            return false;
        }
        replayEntity.setReplay(mapName, "", false, false);
        return true;
    }
}
