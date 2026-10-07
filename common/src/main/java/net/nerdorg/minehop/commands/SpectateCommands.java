package net.nerdorg.minehop.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.nerdorg.minehop.platform.Services;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.data.DataManager;
import net.nerdorg.minehop.networking.PacketHandler;
import net.nerdorg.minehop.replays.ReplayManager;
import net.nerdorg.minehop.spectate.SpectateSessions;
import net.nerdorg.minehop.util.Logger;
import net.nerdorg.minehop.util.ZoneUtil;

import java.nio.charset.StandardCharsets;

/**
 * /spec, /spectate and /unspec. Who is watching whom, and keeping them there, is SpectateSessions' job.
 */
public class SpectateCommands {
    private static final int PATH_COOLDOWN_TICKS = 100;
    private static final java.util.Map<java.util.UUID, Long> LAST_PATH_REQUEST = new java.util.HashMap<>();

    public static void register() {
        Services.EVENTS.onRegisterCommands((dispatcher, registryAccess, environment) -> dispatcher.register(
            LiteralArgumentBuilder.<CommandSourceStack>literal("spec")
                .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("entity", StringArgumentType.string())
                    .suggests((context, builder) -> {
                        suggestTargets(context, builder);
                        suggestSavedReplayNames(context, builder);
                        return builder.buildFuture();
                    })
                    .executes(context -> {
                        handleSpectateReplay(context, false);
                        return Command.SINGLE_SUCCESS;
                    })
                    .then(LiteralArgumentBuilder.<CommandSourceStack>literal("path")
                            .executes(context -> {
                                handleSpectateReplay(context, true);
                                return Command.SINGLE_SUCCESS;
                            })
                    )
                )
            ));
        Services.EVENTS.onRegisterCommands((dispatcher, registryAccess, environment) -> dispatcher.register(
            LiteralArgumentBuilder.<CommandSourceStack>literal("spectate")
                .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("entity", StringArgumentType.string())
                    .suggests((context, builder) -> {
                        suggestTargets(context, builder);
                        suggestSavedReplayNames(context, builder);
                        return builder.buildFuture();
                    })
                    .executes(context -> {
                        handleSpectateReplay(context, false);
                        return Command.SINGLE_SUCCESS;
                    })
                    .then(LiteralArgumentBuilder.<CommandSourceStack>literal("path")
                            .executes(context -> {
                                handleSpectateReplay(context, true);
                                return Command.SINGLE_SUCCESS;
                            })
                    )
                )
        ));
        Services.EVENTS.onRegisterCommands((dispatcher, registryAccess, environment) -> dispatcher.register(
            LiteralArgumentBuilder.<CommandSourceStack>literal("unspec")
                .executes(context -> {
                    handleUnSpectate(context);
                    return Command.SINGLE_SUCCESS;
                })

        ));
    }

    /** /unspec: ends a spectate session and puts the player back as they were (game mode, place, map). */
    private static void handleUnSpectate(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer serverPlayerEntity = context.getSource().getPlayer();
        if (serverPlayerEntity == null) {
            return;
        }
        PacketHandler.clearReplayPath(serverPlayerEntity);
        if (!SpectateSessions.stop(serverPlayerEntity, "No longer spectating.")) {
            // Not in a session: nothing to undo. (This used to force adventure mode on anyone, e.g. plot builders.)
            Logger.logFailure(serverPlayerEntity, "You are not spectating.");
        }
    }

    /**
     * /spec {player | map_replay | map_replay_player}. Spectating is allowed from anywhere: the session sends the
     * viewer back to where they were afterwards, so it can't be used to travel, and it follows the target across
     * maps and dimensions.
     */
    private static void handleSpectateReplay(CommandContext<CommandSourceStack> context, boolean pathMode) throws CommandSyntaxException {
        ServerPlayer serverPlayerEntity = context.getSource().getPlayer();
        String nameString = new String(context.getArgument("entity", String.class).getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
        if (serverPlayerEntity == null) {
            return;
        }

        if (pathMode) {
            handleReplayPath(context, serverPlayerEntity, nameString);
            return;
        }

        String cooldown = SpectateSessions.cooldownMessage(serverPlayerEntity);
        if (cooldown != null) {
            Logger.logFailure(serverPlayerEntity, cooldown);
            return;
        }

        ServerPlayer playerEntity = context.getSource().getServer().getPlayerList().getPlayerByName(nameString);
        if (playerEntity != null) {
            if (playerEntity == serverPlayerEntity) {
                Logger.logFailure(serverPlayerEntity, "You cannot spectate yourself.");
            }
            else if (playerEntity.isCreative() || playerEntity.isSpectator()) {
                Logger.logFailure(serverPlayerEntity, "You cannot spectate another spectator.");
            }
            else {
                SpectateSessions.startPlayer(serverPlayerEntity, playerEntity);
                if (SpectateSessions.isSpectating(serverPlayerEntity)) {
                    Logger.logSuccess(serverPlayerEntity, "Now spectating " + playerEntity.getScoreboardName() + ". Use /unspec to stop spectating.");
                }
            }
            return;
        }

        if (nameString.endsWith("_replay")) {
            String mapName = nameString.substring(0, nameString.length() - "_replay".length());
            if (SpectateSessions.startWorldRecord(serverPlayerEntity, mapName)) {
                Logger.logSuccess(serverPlayerEntity, "Now spectating the world record on " + mapName + ". Use /unspec to stop spectating.");
                return;
            }
        }

        // "{map}_replay_{player}": that player's personal best, from its start.
        for (DataManager.MapData mapData : Minehop.mapList) {
            if (mapData == null || mapData.name == null || !nameString.startsWith(mapData.name + "_replay_")) {
                continue;
            }
            ReplayManager.Replay replay = resolveReplayFromName(context.getSource().getServer(), mapData.name, nameString);
            if (replay != null) {
                SpectateSessions.startPersonalBest(serverPlayerEntity, mapData.name, replay);
                if (SpectateSessions.isSpectating(serverPlayerEntity)) {
                    Logger.logSuccess(serverPlayerEntity, "Now watching " + replay.player_name + "'s personal best on " + mapData.name
                            + " (" + String.format("%.5f", replay.time) + "). Use /unspec to stop watching.");
                }
                return;
            }
        }

        Logger.logFailure(serverPlayerEntity, "Entity not found.");
    }

    /**
     * /spec {replay} path: draws a watchable replay's route for the player. Up to 4096 points per request (see
     * ReplayPathSimplifier) and one request per {@link #PATH_COOLDOWN_TICKS} per player.
     */
    private static void handleReplayPath(CommandContext<CommandSourceStack> context, ServerPlayer viewer, String requestedName) {
        long now = context.getSource().getServer().getTickCount();
        Long last = LAST_PATH_REQUEST.get(viewer.getUUID());
        if (last != null && now - last < PATH_COOLDOWN_TICKS && now >= last) {
            Logger.logFailure(viewer, "Please wait a few seconds before rendering another path.");
            return;
        }
        LAST_PATH_REQUEST.put(viewer.getUUID(), now);
        ResolvedReplayPath resolved = resolveReplayPath(context, viewer, requestedName);
        if (resolved == null || resolved.replay == null || resolved.replay.replayEntries == null || resolved.replay.replayEntries.size() < 2) {
            Logger.logFailure(viewer, "No saved replay path found for " + requestedName + ".");
            return;
        }

        int pointsSent = PacketHandler.sendReplayPath(viewer, resolved.replay.replayEntries);
        if (pointsSent < 2) {
            Logger.logFailure(viewer, "No valid replay path points found for " + requestedName + ".");
            return;
        }

        Logger.logSuccess(
                viewer,
                "Rendered path for " + resolved.displayName + " on " + resolved.mapName + " (" + pointsSent + " points). Use /unspec or spectate normally to clear it."
        );
    }

    private static ResolvedReplayPath resolveReplayPath(CommandContext<CommandSourceStack> context, ServerPlayer viewer, String requestedName) {
        if (context == null || viewer == null || requestedName == null || requestedName.isBlank()) {
            return null;
        }

        if (requestedName.endsWith("_replay")) {
            String mapName = requestedName.substring(0, requestedName.length() - "_replay".length());
            if (DataManager.getMap(mapName) != null) {
                return new ResolvedReplayPath(mapName, "the world record", ReplayManager.getReplay(mapName));
            }
        }
        for (DataManager.MapData mapData : Minehop.mapList) {
            if (mapData != null && mapData.name != null && requestedName.startsWith(mapData.name + "_replay_")) {
                ReplayManager.Replay replay = resolveReplayFromName(context.getSource().getServer(), mapData.name, requestedName);
                if (replay != null) {
                    return new ResolvedReplayPath(mapData.name, replay.player_name + "'s personal best", replay);
                }
            }
        }

        // A plain player name: their personal best on the map the viewer is on.
        String currentMap = ZoneUtil.getCurrentMapName(viewer);
        if (currentMap == null || currentMap.isBlank()) {
            Logger.logFailure(viewer, "Please teleport to the map before rendering a replay path.");
            return null;
        }

        ReplayManager.Replay replay = resolveReplayFromName(context.getSource().getServer(), currentMap, requestedName);
        if (replay == null) {
            return null;
        }
        return new ResolvedReplayPath(currentMap, replay.player_name + "'s personal best", replay);
    }

    /**
     * A watchable replay named by a command argument: "{map}_replay" is the WR, "{map}_replay_{player}" or a
     * player name is that player's current PB (UUID first). Runs that back no current PB/WR are never returned.
     */
    private static ReplayManager.Replay resolveReplayFromName(net.minecraft.server.MinecraftServer server, String currentMap, String requestedName) {
        if (currentMap == null || currentMap.isBlank() || requestedName == null || requestedName.isBlank()) {
            return null;
        }

        if (requestedName.equals(currentMap + "_replay")) {
            return ReplayManager.getReplay(currentMap);
        }

        String replayPrefix = currentMap + "_replay_";
        if (requestedName.startsWith(replayPrefix)) {
            String sanitizedPlayerName = requestedName.substring(replayPrefix.length());
            for (java.util.Map.Entry<net.nerdorg.minehop.data.DataManager.RecordData, ReplayManager.Replay> entry : ReplayManager.watchablePersonalBests(currentMap)) {
                if (sanitizedPlayerName.equals(sanitizeForScoreboard(entry.getKey().name))) {
                    return entry.getValue();
                }
            }
            return null;
        }

        return ReplayManager.getReplayForRecord(ReplayManager.findPersonalRecordByName(server, currentMap, requestedName));
    }

    private static String sanitizeForScoreboard(String raw) {
        if (raw == null || raw.isBlank()) {
            return "player";
        }
        StringBuilder sanitized = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if ((c >= 'A' && c <= 'Z')
                    || (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9')
                    || c == '_') {
                sanitized.append(c);
            } else {
                sanitized.append('_');
            }
        }
        return sanitized.toString();
    }

    /** Spectatable players (not creative/spectator) and the maps' world-record ghosts. */
    private static void suggestTargets(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        for (ServerPlayer player : context.getSource().getServer().getPlayerList().getPlayers()) {
            if (!player.isCreative() && !player.isSpectator()) {
                builder.suggest(player.getScoreboardName(), new LiteralMessage(player.getScoreboardName()));
            }
        }
        for (String mapName : net.nerdorg.minehop.replays.ReplayGhosts.worldRecordMaps()) {
            builder.suggest(mapName + "_replay", new LiteralMessage(mapName + " world record"));
        }
    }

    private static void suggestSavedReplayNames(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        if (context == null || builder == null || Minehop.replayList == null) {
            return;
        }
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) {
            return;
        }
        String currentMap = ZoneUtil.getCurrentMapName(player);
        if (currentMap == null || currentMap.isBlank()) {
            return;
        }

        if (ReplayManager.getReplay(currentMap) != null) {
            builder.suggest(currentMap + "_replay", new LiteralMessage("world record replay"));
        }
        // Only replays that back a current PB; orphaned/invalidated runs are not offered.
        for (java.util.Map.Entry<net.nerdorg.minehop.data.DataManager.RecordData, ReplayManager.Replay> entry : ReplayManager.watchablePersonalBests(currentMap)) {
            String playerName = entry.getKey().name;
            builder.suggest(playerName, new LiteralMessage(playerName + "'s personal best"));
            builder.suggest(currentMap + "_replay_" + sanitizeForScoreboard(playerName), new LiteralMessage(playerName + "'s personal best"));
        }
    }

    private record ResolvedReplayPath(String mapName, String displayName, ReplayManager.Replay replay) {
    }
}
