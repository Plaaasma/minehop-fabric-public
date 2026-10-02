package net.nerdorg.minehop.commands;

import net.nerdorg.minehop.util.PermissionUtil;

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
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.nerdorg.minehop.config.ConfigWrapper;
import net.nerdorg.minehop.util.Logger;

public class GamemodeCommands {
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
            LiteralArgumentBuilder.<CommandSourceStack>literal("gmc")
                .requires(source -> PermissionUtil.hasLevel(source, 4))
                .executes(context -> {
                    handleCreative(context);
                    return Command.SINGLE_SUCCESS;
                })
                .then(RequiredArgumentBuilder.<CommandSourceStack, EntitySelector>argument("player", EntityArgument.player())
                    .executes(context -> {
                        handleCreativeArg(context);
                        return Command.SINGLE_SUCCESS;
                    })
                )
        ));

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
            LiteralArgumentBuilder.<CommandSourceStack>literal("gmsp")
                .requires(source -> PermissionUtil.hasLevel(source, 4))
                .executes(context -> {
                    handleSpectator(context);
                    return Command.SINGLE_SUCCESS;
                })
                .then(RequiredArgumentBuilder.<CommandSourceStack, EntitySelector>argument("player", EntityArgument.player())
                    .executes(context -> {
                        handleSpectatorArg(context);
                        return Command.SINGLE_SUCCESS;
                    })
                )
        ));

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
            LiteralArgumentBuilder.<CommandSourceStack>literal("gms")
                .requires(source -> PermissionUtil.hasLevel(source, 4))
                .executes(context -> {
                    handleSurvival(context);
                    return Command.SINGLE_SUCCESS;
                })
                .then(RequiredArgumentBuilder.<CommandSourceStack, EntitySelector>argument("player", EntityArgument.player())
                    .executes(context -> {
                        handleSurvivalArg(context);
                        return Command.SINGLE_SUCCESS;
                    })
                )
        ));

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
            LiteralArgumentBuilder.<CommandSourceStack>literal("gma")
                .requires(source -> PermissionUtil.hasLevel(source, 4))
                .executes(context -> {
                    handleAdventure(context);
                    return Command.SINGLE_SUCCESS;
                })
                .then(RequiredArgumentBuilder.<CommandSourceStack, EntitySelector>argument("player", EntityArgument.player())
                    .executes(context -> {
                        handleAdventureArg(context);
                        return Command.SINGLE_SUCCESS;
                    })
                )
        ));

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
            LiteralArgumentBuilder.<CommandSourceStack>literal("gm")
                .requires(source -> PermissionUtil.hasLevel(source, 4))
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("c")
                    .executes(context -> {
                        handleCreative(context);
                        return Command.SINGLE_SUCCESS;
                    })
                    .then(RequiredArgumentBuilder.<CommandSourceStack, EntitySelector>argument("player", EntityArgument.player())
                        .executes(context -> {
                            handleCreativeArg(context);
                            return Command.SINGLE_SUCCESS;
                        })
                    )
                )
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("sp")
                    .executes(context -> {
                        handleSpectator(context);
                        return Command.SINGLE_SUCCESS;
                    })
                    .then(RequiredArgumentBuilder.<CommandSourceStack, EntitySelector>argument("player", EntityArgument.player())
                        .executes(context -> {
                            handleSpectatorArg(context);
                            return Command.SINGLE_SUCCESS;
                        })
                    )
                )
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("s")
                    .executes(context -> {
                        handleSurvival(context);
                        return Command.SINGLE_SUCCESS;
                    })
                    .then(RequiredArgumentBuilder.<CommandSourceStack, EntitySelector>argument("player", EntityArgument.player())
                        .executes(context -> {
                            handleSurvivalArg(context);
                            return Command.SINGLE_SUCCESS;
                        })
                    )
                )
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("a")
                    .executes(context -> {
                        handleAdventure(context);
                        return Command.SINGLE_SUCCESS;
                    })
                    .then(RequiredArgumentBuilder.<CommandSourceStack, EntitySelector>argument("player", EntityArgument.player())
                        .executes(context -> {
                            handleAdventureArg(context);
                            return Command.SINGLE_SUCCESS;
                        })
                    )
                )
        ));
    }

    private static void handleCreative(CommandContext<CommandSourceStack> context) {
        ServerPlayer senderEntity = context.getSource().getPlayer();

        senderEntity.setGameMode(GameType.CREATIVE);
        Logger.logSuccess(senderEntity, "Setting gamemode to creative.");
    }

    private static void handleCreativeArg(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer senderEntity = context.getSource().getPlayer();
        ServerPlayer serverPlayerEntity = EntityArgument.getPlayer(context, "player");

        serverPlayerEntity.setGameMode(GameType.CREATIVE);
        Logger.logSuccess(senderEntity, "Setting gamemode to creative for " + serverPlayerEntity.getScoreboardName() + ".");
    }

    private static void handleSpectator(CommandContext<CommandSourceStack> context) {
        ServerPlayer senderEntity = context.getSource().getPlayer();

        senderEntity.setGameMode(GameType.SPECTATOR);
        Logger.logSuccess(senderEntity, "Setting gamemode to spectator.");
    }

    private static void handleSpectatorArg(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer senderEntity = context.getSource().getPlayer();
        ServerPlayer serverPlayerEntity = EntityArgument.getPlayer(context, "player");

        serverPlayerEntity.setGameMode(GameType.SPECTATOR);
        Logger.logSuccess(senderEntity, "Setting gamemode to spectator for " + serverPlayerEntity.getScoreboardName() + ".");
    }

    private static void handleSurvival(CommandContext<CommandSourceStack> context) {
        ServerPlayer senderEntity = context.getSource().getPlayer();

        senderEntity.setGameMode(GameType.SURVIVAL);
        Logger.logSuccess(senderEntity, "Setting gamemode to survival.");
    }

    private static void handleSurvivalArg(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer senderEntity = context.getSource().getPlayer();
        ServerPlayer serverPlayerEntity = EntityArgument.getPlayer(context, "player");

        serverPlayerEntity.setGameMode(GameType.SURVIVAL);
        Logger.logSuccess(senderEntity, "Setting gamemode to survival for " + serverPlayerEntity.getScoreboardName() + ".");
    }

    private static void handleAdventure(CommandContext<CommandSourceStack> context) {
        ServerPlayer senderEntity = context.getSource().getPlayer();

        senderEntity.setGameMode(GameType.ADVENTURE);
        Logger.logSuccess(senderEntity, "Setting gamemode to adventure.");
    }

    private static void handleAdventureArg(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer senderEntity = context.getSource().getPlayer();
        ServerPlayer serverPlayerEntity = EntityArgument.getPlayer(context, "player");

        serverPlayerEntity.setGameMode(GameType.ADVENTURE);
        Logger.logSuccess(senderEntity, "Setting gamemode to adventure for " + serverPlayerEntity.getScoreboardName() + ".");
    }
}
