package net.nerdorg.minehop.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
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
import net.nerdorg.minehop.networking.HandshakeHandler;
import net.nerdorg.minehop.networking.ReplayProtocol;
import net.nerdorg.minehop.networking.payloads.ReplayControlPayload;
import net.nerdorg.minehop.replays.ReplayGhosts;
import net.nerdorg.minehop.replays.ReplayManager;
import net.nerdorg.minehop.replays.ReplayStreaming;
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
                        // Playback controls for replays the player's own client plays (1.1.7+); the client has keys
                        // for them too. Every control is a command so it can be driven by scripts (execute as ...).
                        .then(LiteralArgumentBuilder.<CommandSourceStack>literal("pause")
                                .executes(context -> control(context.getSource(), ReplayProtocol.CONTROL_PAUSE, 1.0D, "Paused.")))
                        .then(LiteralArgumentBuilder.<CommandSourceStack>literal("resume")
                                .executes(context -> control(context.getSource(), ReplayProtocol.CONTROL_PAUSE, 0.0D, "Playing.")))
                        .then(LiteralArgumentBuilder.<CommandSourceStack>literal("speed")
                                .then(RequiredArgumentBuilder.<CommandSourceStack, Float>argument("speed",
                                                FloatArgumentType.floatArg(ReplayProtocol.MIN_SPEED, ReplayProtocol.MAX_SPEED))
                                        .executes(context -> {
                                            float speed = FloatArgumentType.getFloat(context, "speed");
                                            return control(context.getSource(), ReplayProtocol.CONTROL_SPEED, speed,
                                                    String.format(java.util.Locale.ROOT, "Speed %.2fx.", speed));
                                        })))
                        .then(LiteralArgumentBuilder.<CommandSourceStack>literal("seek")
                                .then(RequiredArgumentBuilder.<CommandSourceStack, Double>argument("seconds", DoubleArgumentType.doubleArg(-60.0D, 1.0E6D))
                                        .executes(context -> {
                                            double seconds = DoubleArgumentType.getDouble(context, "seconds");
                                            return control(context.getSource(), ReplayProtocol.CONTROL_SEEK, seconds,
                                                    String.format(java.util.Locale.ROOT, "Seeking to %.2f s.", seconds));
                                        })))
                        .then(LiteralArgumentBuilder.<CommandSourceStack>literal("skip")
                                .then(RequiredArgumentBuilder.<CommandSourceStack, Double>argument("seconds", DoubleArgumentType.doubleArg(-1.0E6D, 1.0E6D))
                                        .executes(context -> {
                                            double seconds = DoubleArgumentType.getDouble(context, "seconds");
                                            return control(context.getSource(), ReplayProtocol.CONTROL_SEEK_BY, seconds,
                                                    String.format(java.util.Locale.ROOT, "Skipping %+.2f s.", seconds));
                                        })))
                        .then(LiteralArgumentBuilder.<CommandSourceStack>literal("stop")
                                .executes(context -> stopWatching(context.getSource())))
                        .then(LiteralArgumentBuilder.<CommandSourceStack>literal("race")
                                .executes(context -> race(context.getSource(), ReplayProtocol.RACE_TOGGLE, "Toggling the race ghost."))
                                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("on")
                                        .executes(context -> race(context.getSource(), ReplayProtocol.RACE_ON, "Race ghost on.")))
                                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("off")
                                        .executes(context -> race(context.getSource(), ReplayProtocol.RACE_OFF, "Race ghost off.")))
                                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("pb")
                                        .executes(context -> race(context.getSource(), ReplayProtocol.RACE_PB, "Racing your personal best.")))
                                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("wr")
                                        .executes(context -> race(context.getSource(), ReplayProtocol.RACE_WR, "Racing the world record."))))
                        .then(LiteralArgumentBuilder.<CommandSourceStack>literal("ghosts")
                                .requires(source -> source.hasPermission(4))
                                .executes(context -> listGhosts(context.getSource()))
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

    private static final String UPDATE_MESSAGE = "Update Minehop to " + net.nerdorg.minehop.Minehop.MOD_VERSION_STRING
            + " or newer to control replays (pause, speed, seek) and race your personal best.";

    /** /replay pause|resume|speed|seek|skip: sent to the client playing the player's replay. */
    private static int control(CommandSourceStack source, byte action, double value, String feedback) {
        ServerPlayer viewer = source.getPlayer();
        if (viewer == null) {
            source.sendFailure(net.minecraft.network.chat.Component.literal("Only players can control a replay."));
            return 0;
        }
        if (SpectateSessions.sendClientReplayControl(viewer, action, value)) {
            Logger.logSuccess(viewer, feedback);
            return Command.SINGLE_SUCCESS;
        }
        if (SpectateSessions.isSpectating(viewer) && !HandshakeHandler.supportsClientReplays(viewer)) {
            Logger.logFailure(viewer, UPDATE_MESSAGE);
        } else {
            Logger.logFailure(viewer, "You are not watching a replay. Use /replay watch or /spec <map>_replay.");
        }
        return 0;
    }

    /** /replay stop: stops watching (any spectate session, like /unspec). */
    private static int stopWatching(CommandSourceStack source) {
        ServerPlayer viewer = source.getPlayer();
        if (viewer == null) {
            return 0;
        }
        if (!SpectateSessions.stop(viewer, "No longer watching.")) {
            Logger.logFailure(viewer, "You are not watching a replay.");
            return 0;
        }
        return Command.SINGLE_SUCCESS;
    }

    /** /replay race [on|off|pb|wr]: the race ghost is the client's setting; the command is passed on to it. */
    private static int race(CommandSourceStack source, int mode, String feedback) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            return 0;
        }
        if (!HandshakeHandler.supportsClientReplays(player)) {
            Logger.logFailure(player, UPDATE_MESSAGE);
            return 0;
        }
        ReplayStreaming.send(player, new ReplayControlPayload(ReplayProtocol.CONTROL_RACE, mode));
        Logger.logSuccess(player, feedback);
        return Command.SINGLE_SUCCESS;
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
        text.append("; ").append(net.nerdorg.minehop.replays.storage.LegacyMigration.status());
        text.append("\n").append(ReplayStreaming.status());
        for (String line : SpectateSessions.describeSessions(source.getServer())) {
            text.append("\nsession: ").append(line);
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
