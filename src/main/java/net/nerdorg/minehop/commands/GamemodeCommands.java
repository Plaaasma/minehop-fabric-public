package net.nerdorg.minehop.commands;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.EntitySelector;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.network.packet.s2c.play.GameStateChangeS2CPacket;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.GameMode;
import net.nerdorg.minehop.config.ConfigWrapper;
import net.nerdorg.minehop.util.Logger;

public class GamemodeCommands {
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
            LiteralArgumentBuilder.<ServerCommandSource>literal("gmc")
                .requires(source -> source.hasPermissionLevel(4))
                .executes(context -> {
                    handleCreative(context);
                    return Command.SINGLE_SUCCESS;
                })
                .then(RequiredArgumentBuilder.<ServerCommandSource, EntitySelector>argument("player", EntityArgumentType.player())
                    .executes(context -> {
                        handleCreativeArg(context);
                        return Command.SINGLE_SUCCESS;
                    })
                )
        ));

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
            LiteralArgumentBuilder.<ServerCommandSource>literal("gmsp")
                .requires(source -> source.hasPermissionLevel(4))
                .executes(context -> {
                    handleSpectator(context);
                    return Command.SINGLE_SUCCESS;
                })
                .then(RequiredArgumentBuilder.<ServerCommandSource, EntitySelector>argument("player", EntityArgumentType.player())
                    .executes(context -> {
                        handleSpectatorArg(context);
                        return Command.SINGLE_SUCCESS;
                    })
                )
        ));

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
            LiteralArgumentBuilder.<ServerCommandSource>literal("gms")
                .requires(source -> source.hasPermissionLevel(4))
                .executes(context -> {
                    handleSurvival(context);
                    return Command.SINGLE_SUCCESS;
                })
                .then(RequiredArgumentBuilder.<ServerCommandSource, EntitySelector>argument("player", EntityArgumentType.player())
                    .executes(context -> {
                        handleSurvivalArg(context);
                        return Command.SINGLE_SUCCESS;
                    })
                )
        ));

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
            LiteralArgumentBuilder.<ServerCommandSource>literal("gma")
                .requires(source -> source.hasPermissionLevel(4))
                .executes(context -> {
                    handleAdventure(context);
                    return Command.SINGLE_SUCCESS;
                })
                .then(RequiredArgumentBuilder.<ServerCommandSource, EntitySelector>argument("player", EntityArgumentType.player())
                    .executes(context -> {
                        handleAdventureArg(context);
                        return Command.SINGLE_SUCCESS;
                    })
                )
        ));

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
            LiteralArgumentBuilder.<ServerCommandSource>literal("gm")
                .requires(source -> source.hasPermissionLevel(4))
                .then(LiteralArgumentBuilder.<ServerCommandSource>literal("c")
                    .executes(context -> {
                        handleCreative(context);
                        return Command.SINGLE_SUCCESS;
                    })
                    .then(RequiredArgumentBuilder.<ServerCommandSource, EntitySelector>argument("player", EntityArgumentType.player())
                        .executes(context -> {
                            handleCreativeArg(context);
                            return Command.SINGLE_SUCCESS;
                        })
                    )
                )
                .then(LiteralArgumentBuilder.<ServerCommandSource>literal("sp")
                    .executes(context -> {
                        handleSpectator(context);
                        return Command.SINGLE_SUCCESS;
                    })
                    .then(RequiredArgumentBuilder.<ServerCommandSource, EntitySelector>argument("player", EntityArgumentType.player())
                        .executes(context -> {
                            handleSpectatorArg(context);
                            return Command.SINGLE_SUCCESS;
                        })
                    )
                )
                .then(LiteralArgumentBuilder.<ServerCommandSource>literal("s")
                    .executes(context -> {
                        handleSurvival(context);
                        return Command.SINGLE_SUCCESS;
                    })
                    .then(RequiredArgumentBuilder.<ServerCommandSource, EntitySelector>argument("player", EntityArgumentType.player())
                        .executes(context -> {
                            handleSurvivalArg(context);
                            return Command.SINGLE_SUCCESS;
                        })
                    )
                )
                .then(LiteralArgumentBuilder.<ServerCommandSource>literal("a")
                    .executes(context -> {
                        handleAdventure(context);
                        return Command.SINGLE_SUCCESS;
                    })
                    .then(RequiredArgumentBuilder.<ServerCommandSource, EntitySelector>argument("player", EntityArgumentType.player())
                        .executes(context -> {
                            handleAdventureArg(context);
                            return Command.SINGLE_SUCCESS;
                        })
                    )
                )
        ));
    }

    private static void handleCreative(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity senderEntity = context.getSource().getPlayer();

        senderEntity.changeGameMode(GameMode.CREATIVE);
        Logger.logSuccess(senderEntity, "Setting gamemode to creative.");
    }

    private static void handleCreativeArg(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity senderEntity = context.getSource().getPlayer();
        ServerPlayerEntity serverPlayerEntity = EntityArgumentType.getPlayer(context, "player");

        serverPlayerEntity.changeGameMode(GameMode.CREATIVE);
        Logger.logSuccess(senderEntity, "Setting gamemode to creative for " + serverPlayerEntity.getNameForScoreboard() + ".");
    }

    private static void handleSpectator(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity senderEntity = context.getSource().getPlayer();

        senderEntity.changeGameMode(GameMode.SPECTATOR);
        Logger.logSuccess(senderEntity, "Setting gamemode to spectator.");
    }

    private static void handleSpectatorArg(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity senderEntity = context.getSource().getPlayer();
        ServerPlayerEntity serverPlayerEntity = EntityArgumentType.getPlayer(context, "player");

        serverPlayerEntity.changeGameMode(GameMode.SPECTATOR);
        Logger.logSuccess(senderEntity, "Setting gamemode to spectator for " + serverPlayerEntity.getNameForScoreboard() + ".");
    }

    private static void handleSurvival(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity senderEntity = context.getSource().getPlayer();

        senderEntity.changeGameMode(GameMode.SURVIVAL);
        Logger.logSuccess(senderEntity, "Setting gamemode to survival.");
    }

    private static void handleSurvivalArg(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity senderEntity = context.getSource().getPlayer();
        ServerPlayerEntity serverPlayerEntity = EntityArgumentType.getPlayer(context, "player");

        serverPlayerEntity.changeGameMode(GameMode.SURVIVAL);
        Logger.logSuccess(senderEntity, "Setting gamemode to survival for " + serverPlayerEntity.getNameForScoreboard() + ".");
    }

    private static void handleAdventure(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity senderEntity = context.getSource().getPlayer();

        senderEntity.changeGameMode(GameMode.ADVENTURE);
        Logger.logSuccess(senderEntity, "Setting gamemode to adventure.");
    }

    private static void handleAdventureArg(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity senderEntity = context.getSource().getPlayer();
        ServerPlayerEntity serverPlayerEntity = EntityArgumentType.getPlayer(context, "player");

        serverPlayerEntity.changeGameMode(GameMode.ADVENTURE);
        Logger.logSuccess(senderEntity, "Setting gamemode to adventure for " + serverPlayerEntity.getNameForScoreboard() + ".");
    }
}
