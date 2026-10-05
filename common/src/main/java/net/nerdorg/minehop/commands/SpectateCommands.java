package net.nerdorg.minehop.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.nerdorg.minehop.platform.Services;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.entity.custom.ReplayEntity;
import net.nerdorg.minehop.networking.PacketHandler;
import net.nerdorg.minehop.replays.ReplayManager;
import net.nerdorg.minehop.util.Logger;
import net.nerdorg.minehop.util.ZoneUtil;
import org.apache.commons.collections4.MapUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

public class SpectateCommands {
    public static HashMap<String, List<String>> spectatorList = new HashMap<>();

    public static void register() {
        Services.EVENTS.onRegisterCommands((dispatcher, registryAccess, environment) -> dispatcher.register(
            LiteralArgumentBuilder.<CommandSourceStack>literal("spec")
                .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("entity", StringArgumentType.string())
                    .suggests((context, builder) -> {
                        // Iterate over all entities and add your custom entities to the suggestions
                        Iterable<Entity> entities = context.getSource().getLevel().getAllEntities();
                        for (Entity entity : entities) {
                            if (entity instanceof ReplayEntity) {
                                builder.suggest(entity.getScoreboardName(), new LiteralMessage(entity.getName().getString()));
                            }
                            else if (entity instanceof Player) {
                                if (!((Player) entity).isCreative() && !entity.isSpectator()) {
                                    builder.suggest(entity.getScoreboardName(), new LiteralMessage(entity.getName().getString()));
                                }
                            }
                        }
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
                        // Iterate over all entities and add your custom entities to the suggestions
                        Iterable<Entity> entities = context.getSource().getLevel().getAllEntities();
                        for (Entity entity : entities) {
                            if (entity instanceof ReplayEntity) {
                                builder.suggest(entity.getScoreboardName(), new LiteralMessage(entity.getName().getString()));
                            }
                            else if (entity instanceof Player) {
                                builder.suggest(entity.getScoreboardName(), new LiteralMessage(entity.getName().getString()));
                            }
                        }
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

    private static void handleUnSpectate(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer serverPlayerEntity = context.getSource().getPlayer();

        removeFromCurrentSpectateTarget(serverPlayerEntity);

        if (Minehop.timerManager.containsKey(serverPlayerEntity.getScoreboardName())) {
            Minehop.timerManager.remove(serverPlayerEntity.getScoreboardName());
        }

        serverPlayerEntity.setCamera(serverPlayerEntity);
        PacketHandler.clearReplayPath(serverPlayerEntity);

        if (!serverPlayerEntity.isSpectator()) {
            Logger.logFailure(serverPlayerEntity, "You are not spectating.");
        }
        else {
            Logger.logSuccess(serverPlayerEntity, "No longer spectating.");
            SpawnCommands.handleSpawn(context);
        }

        serverPlayerEntity.setGameMode(GameType.ADVENTURE);
    }

    private static void handleSpectateReplay(CommandContext<CommandSourceStack> context, boolean pathMode) throws CommandSyntaxException {
        ServerPlayer serverPlayerEntity = context.getSource().getPlayer();
        String nameString = new String(context.getArgument("entity", String.class).getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);

        if (pathMode) {
            handleReplayPath(context, serverPlayerEntity, nameString);
            return;
        }

        removeFromCurrentSpectateTarget(serverPlayerEntity);
        PacketHandler.clearReplayPath(serverPlayerEntity);

        if (Minehop.timerManager.containsKey(serverPlayerEntity.getScoreboardName())) {
            Minehop.timerManager.remove(serverPlayerEntity.getScoreboardName());
        }

        if (Minehop.timerManager.containsKey(serverPlayerEntity.getScoreboardName())) {
            Minehop.timerManager.remove(serverPlayerEntity.getScoreboardName());
        }

        Entity entity = context.getSource().getServer().getPlayerList().getPlayerByName(nameString);
        if (entity == null) {
            Iterable<Entity> entities = context.getSource().getLevel().getAllEntities();
            for (Entity iterEntity : entities) {
                if (iterEntity instanceof ReplayEntity) {
                    if (iterEntity.getScoreboardName().equals(nameString)) {
                        entity = iterEntity;
                        break;
                    }
                }
            }
            if (entity == null) {
                Logger.logFailure(serverPlayerEntity, "Entity not found.");
                return;
            }
        }

        if (entity instanceof ReplayEntity replayEntity) {
            String mapName = ZoneUtil.getCurrentMapName(serverPlayerEntity);
            String replayName = replayEntity.getScoreboardName();
            if (mapName != null) {
                if (replayName.startsWith(mapName)) {
                    serverPlayerEntity.setCamera(serverPlayerEntity);
                    Logger.logSuccess(serverPlayerEntity, "Now spectating " + replayEntity.getScoreboardName() + ". Use /unspec to stop spectating.");
                    serverPlayerEntity.setGameMode(GameType.SPECTATOR);
                    if (!serverPlayerEntity.isCreative()) {
                        serverPlayerEntity.getInventory().clearContent();
                    }
                    serverPlayerEntity.changeDimension(ZoneUtil.makeTeleportTarget((ServerLevel) replayEntity.level(), new Vec3(replayEntity.getX(), replayEntity.getY(), replayEntity.getZ()), replayEntity.getYRot(), replayEntity.getXRot()));
                    serverPlayerEntity.setCamera(replayEntity);
                    addSpectator(replayEntity.getScoreboardName(), serverPlayerEntity.getScoreboardName());
                } else {
                    Logger.logSuccess(serverPlayerEntity, "Please teleport to the map before viewing it's replay.");
                }
            }
            else {
                Logger.logSuccess(serverPlayerEntity, "Please teleport to the map before viewing it's replay.");
            }
        }
        else if (entity instanceof ServerPlayer playerEntity) {
            if (playerEntity == serverPlayerEntity) {
                Logger.logFailure(serverPlayerEntity, "You cannot spectate yourself.");
            }
            else if (playerEntity.isCreative() || playerEntity.isSpectator()) {
                Logger.logFailure(serverPlayerEntity, "You cannot spectate another spectator.");
            }
            else {
                String mapName = ZoneUtil.getCurrentMapName(serverPlayerEntity);
                String targetMapName = ZoneUtil.getCurrentMapName(playerEntity);
                if (mapName == null || targetMapName == null) {
                    Logger.logFailure(serverPlayerEntity, "Both players must be on a valid map before spectating.");
                    return;
                }
                if (mapName.equals(targetMapName)) {
                    serverPlayerEntity.setCamera(serverPlayerEntity);
                    Logger.logSuccess(serverPlayerEntity, "Now spectating " + playerEntity.getScoreboardName() + ". Use /unspec to stop spectating.");
                    serverPlayerEntity.setGameMode(GameType.SPECTATOR);
                    if (!serverPlayerEntity.isCreative()) {
                        serverPlayerEntity.getInventory().clearContent();
                    }
                    serverPlayerEntity.changeDimension(ZoneUtil.makeTeleportTarget(playerEntity.serverLevel(), new Vec3(playerEntity.getX(), playerEntity.getY(), playerEntity.getZ()), playerEntity.getYRot(), playerEntity.getXRot()));
                    serverPlayerEntity.setCamera(playerEntity);
                    addSpectator(playerEntity.getScoreboardName(), serverPlayerEntity.getScoreboardName());
                }
                else {
                    Logger.logSuccess(serverPlayerEntity, "Please teleport to " + targetMapName + " before spectating this player.");
                }
            }
        }
        else {
            Logger.logFailure(serverPlayerEntity, "You cannot spectate this entity.");
        }
    }

    private static void handleReplayPath(CommandContext<CommandSourceStack> context, ServerPlayer viewer, String requestedName) {
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

        Entity entity = context.getSource().getServer().getPlayerList().getPlayerByName(requestedName);
        if (entity == null) {
            for (Entity iterEntity : context.getSource().getLevel().getAllEntities()) {
                if (iterEntity instanceof ReplayEntity && requestedName.equals(iterEntity.getScoreboardName())) {
                    entity = iterEntity;
                    break;
                }
            }
        }

        if (entity instanceof ReplayEntity replayEntity) {
            String mapName = replayEntity.getMapName();
            if (!isViewerOnMap(viewer, mapName)) {
                Logger.logFailure(viewer, "Please teleport to " + mapName + " before rendering this replay path.");
                return null;
            }
            String replayPlayerName = replayEntity.getReplayPlayerName();
            ReplayManager.Replay replay = replayPlayerName == null || replayPlayerName.isBlank()
                    ? ReplayManager.getReplay(mapName)
                    : ReplayManager.getReplay(mapName, replayPlayerName);
            String displayName = replayPlayerName == null || replayPlayerName.isBlank() ? "the world record" : replayPlayerName + "'s fastest replay";
            return new ResolvedReplayPath(mapName, displayName, replay);
        }

        if (entity instanceof Player) {
            Logger.logFailure(viewer, "Path rendering is only available for saved replays.");
            return null;
        }

        String currentMap = ZoneUtil.getCurrentMapName(viewer);
        if (currentMap == null || currentMap.isBlank()) {
            Logger.logFailure(viewer, "Please teleport to the map before rendering a replay path.");
            return null;
        }

        ReplayManager.Replay replay = resolveReplayFromName(currentMap, requestedName);
        if (replay == null) {
            return null;
        }
        return new ResolvedReplayPath(currentMap, replay.player_name + "'s fastest replay", replay);
    }

    private static ReplayManager.Replay resolveReplayFromName(String currentMap, String requestedName) {
        if (currentMap == null || currentMap.isBlank() || requestedName == null || requestedName.isBlank()) {
            return null;
        }

        if (requestedName.equals(currentMap + "_replay")) {
            return ReplayManager.getReplay(currentMap);
        }

        String replayPrefix = currentMap + "_replay_";
        if (requestedName.startsWith(replayPrefix) && Minehop.replayList != null) {
            String sanitizedPlayerName = requestedName.substring(replayPrefix.length());
            for (ReplayManager.Replay replay : Minehop.replayList) {
                if (replay == null || replay.map_name == null || replay.player_name == null) {
                    continue;
                }
                if (currentMap.equals(replay.map_name) && sanitizedPlayerName.equals(sanitizeForScoreboard(replay.player_name))) {
                    return ReplayManager.getReplay(currentMap, replay.player_name);
                }
            }
        }

        return ReplayManager.getReplay(currentMap, requestedName);
    }

    private static boolean isViewerOnMap(ServerPlayer viewer, String mapName) {
        if (viewer == null || mapName == null || mapName.isBlank()) {
            return false;
        }
        String currentMap = ZoneUtil.getCurrentMapName(viewer);
        return mapName.equals(currentMap);
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
        for (ReplayManager.Replay replay : Minehop.replayList) {
            if (replay == null || replay.map_name == null || replay.player_name == null || replay.player_name.isBlank()) {
                continue;
            }
            if (!currentMap.equals(replay.map_name)) {
                continue;
            }
            builder.suggest(replay.player_name, new LiteralMessage(replay.player_name));
            builder.suggest(currentMap + "_replay_" + sanitizeForScoreboard(replay.player_name), new LiteralMessage(replay.player_name));
        }
    }

    private record ResolvedReplayPath(String mapName, String displayName, ReplayManager.Replay replay) {
    }

    private static void removeFromCurrentSpectateTarget(ServerPlayer spectator) {
        if (spectator == null || spectator.getCamera() == null) {
            return;
        }
        String currentTarget = spectator.getCamera().getScoreboardName();
        List<String> spectators = spectatorList.get(currentTarget);
        if (spectators == null) {
            return;
        }
        spectators.remove(spectator.getScoreboardName());
        if (spectators.size() <= 1) {
            spectatorList.remove(currentTarget);
        }
    }

    public static void addSpectator(String targetName, String spectatorName) {
        if (targetName == null || targetName.isBlank() || spectatorName == null || spectatorName.isBlank()) {
            return;
        }
        List<String> spectators = spectatorList.get(targetName);
        if (spectators == null) {
            spectators = new ArrayList<>();
            spectators.add(targetName);
            spectatorList.put(targetName, spectators);
        } else if (!spectators.contains(targetName)) {
            spectators.add(0, targetName);
        }
        if (!spectators.contains(spectatorName)) {
            spectators.add(spectatorName);
        }
    }
}
