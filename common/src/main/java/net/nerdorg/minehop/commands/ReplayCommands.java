package net.nerdorg.minehop.commands;

import net.nerdorg.minehop.util.PermissionUtil;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.nerdorg.minehop.platform.Services;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.data.DataManager;
import net.nerdorg.minehop.replays.ReplayGhosts;
import net.nerdorg.minehop.replays.ReplayManager;
import net.nerdorg.minehop.spectate.SpectateSessions;
import net.nerdorg.minehop.util.Logger;

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
                        .then(LiteralArgumentBuilder.<CommandSourceStack>literal("ghosts")
                                .requires(source -> PermissionUtil.hasLevel(source, 4))
                                .executes(context -> listGhosts(context.getSource()))
                        )
                        .then(LiteralArgumentBuilder.<CommandSourceStack>literal("remove")
                                .requires(source -> PermissionUtil.hasLevel(source, 4))
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
                                .requires(source -> PermissionUtil.hasLevel(source, 4))
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

    /** /replay ghosts (op): every world-record ghost, the run it shows, its frame and whether it is spawned. */
    private static int listGhosts(CommandSourceStack source) {
        StringBuilder text = new StringBuilder("World record ghosts:");
        int spawned = 0;
        List<String> maps = ReplayGhosts.worldRecordMaps();
        for (String mapName : maps) {
            ReplayGhosts.Ghost ghost = ReplayGhosts.worldRecordGhost(mapName);
            if (ghost == null) {
                continue;
            }
            ReplayManager.Replay replay = ghost.replay();
            text.append("\n ").append(mapName).append(": ").append(replay.player_name)
                    .append(String.format(java.util.Locale.ROOT, " %.3fs", replay.time))
                    .append(" frame ").append(ghost.frame()).append('/').append(ReplayManager.frameCount(replay));
            if (ghost.frames() == null) {
                text.append(" (frames not loaded)");
            }
            if (ghost.entity() != null) {
                spawned++;
                text.append(String.format(java.util.Locale.ROOT, " at %.1f %.1f %.1f", ghost.entity().getX(), ghost.entity().getY(), ghost.entity().getZ()));
            } else {
                text.append(" (not spawned: no player near its route)");
            }
        }
        text.append("\n").append(maps.size()).append(" ghost(s), ").append(spawned).append(" spawned; ")
                .append(ReplayGhosts.legacyGhostsRemoved()).append(" saved ghost(s) from older versions removed since start.");
        net.nerdorg.minehop.replays.storage.ReplayStore store = ReplayManager.store();
        text.append("\nReplay store: ").append(ReplayManager.storeMode());
        if (store != null) {
            text.append(String.format(java.util.Locale.ROOT, ", %d runs, frame cache %d runs / %.1f MB, %d write(s) pending",
                    Minehop.replayList.size(), store.cachedRuns(), store.cacheBytes() / 1048576.0D, store.pendingWrites()));
        }
        String message = text.toString();
        source.sendSuccess(() -> net.minecraft.network.chat.Component.literal(message), false);
        return Command.SINGLE_SUCCESS;
    }

    /** /replay {map} (op): show the map's world-record ghost again after /replay remove. */
    private static void handleAddReplay(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer sender = context.getSource().getPlayer();
        String mapName = StringArgumentType.getString(context, "map_name");

        DataManager.MapData mapData = DataManager.getMap(mapName);
        if (mapData == null) {
            Logger.logFailure(sender, "Map " + mapName + " was not found.");
            return;
        }
        if (mapData.replay_ghost_disabled) {
            mapData.replay_ghost_disabled = false;
            DataManager.saveData(context.getSource().getServer().overworld(), DataManager.mapListLocation, Minehop.mapList);
        }
        if (!ReplayGhosts.refreshWorldRecord(context.getSource().getServer(), mapName)) {
            Logger.logFailure(sender, "No world record replay found for map " + mapName + ".");
            return;
        }
        Logger.logSuccess(sender, "World record ghost enabled for " + mapName + " (it shows while players are near its route).");
    }

    /** /replay remove {map} (op): no world-record ghost on this map until /replay {map}. */
    private static void handleRemoveReplay(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer sender = context.getSource().getPlayer();
        String mapName = StringArgumentType.getString(context, "map_name");

        DataManager.MapData mapData = DataManager.getMap(mapName);
        if (mapData == null) {
            Logger.logFailure(sender, "Map " + mapName + " was not found.");
            return;
        }
        boolean hadGhost = ReplayGhosts.worldRecordGhost(mapName) != null;
        if (!mapData.replay_ghost_disabled) {
            mapData.replay_ghost_disabled = true;
            DataManager.saveData(context.getSource().getServer().overworld(), DataManager.mapListLocation, Minehop.mapList);
        }
        net.nerdorg.minehop.data.LeaderboardIntegrity.refreshWorldRecordReplay(context.getSource().getServer(), mapName);
        Logger.logSuccess(sender, (hadGhost ? "Removed the world record ghost of " : "Disabled the world record ghost of ") + mapName + ".");
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
        String cooldown = SpectateSessions.cooldownMessage(viewer);
        if (cooldown != null) {
            Logger.logFailure(viewer, cooldown);
            return;
        }
        // Only a run that backs someone's current PB can be watched (UUID first; see ReplayManager).
        boolean self = explicitTargetPlayer == null || explicitTargetPlayer.isBlank();
        DataManager.RecordData personalBest = self
                ? DataManager.getPersonalRecord(viewer.getScoreboardName(), viewer.getStringUUID(), mapName)
                : ReplayManager.findPersonalRecordByName(context.getSource().getServer(), mapName, explicitTargetPlayer);
        ReplayManager.Replay replay = ReplayManager.getReplayForRecord(personalBest);
        String requestedName = self ? viewer.getScoreboardName() : explicitTargetPlayer;
        if (replay == null) {
            Logger.logFailure(viewer, "No saved replay of " + requestedName + "'s personal best on " + mapName + ".");
            return;
        }
        String targetPlayer = personalBest.name;

        // A ghost of the viewer's own plays the run from its start; the session restores the viewer afterwards.
        SpectateSessions.startPersonalBest(viewer, mapName, replay);
        if (SpectateSessions.isSpectating(viewer)) {
            Logger.logSuccess(viewer, "Now watching " + targetPlayer + "'s personal best on " + mapName + " ("
                    + String.format("%.5f", replay.time) + "). Use /unspec to stop watching.");
        }
    }

    private static Set<String> collectReplayPlayerNames(String mapName) {
        // Players whose current PB on the map has a playable replay; orphaned and invalidated runs are not offered.
        Set<String> playerNames = new LinkedHashSet<>();
        for (java.util.Map.Entry<DataManager.RecordData, ReplayManager.Replay> entry : ReplayManager.watchablePersonalBests(mapName)) {
            playerNames.add(entry.getKey().name);
        }
        return playerNames;
    }

    /** Points the map's world-record ghost at the current WR replay (the registry spawns it near players). */
    public static boolean ensureWorldRecordReplayEntity(MinecraftServer server, String mapName) {
        return ReplayGhosts.refreshWorldRecord(server, mapName);
    }
}
