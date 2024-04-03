package net.nerdorg.minehop.commands;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.nerdorg.minehop.block.entity.BoostBlockEntity;
import net.nerdorg.minehop.util.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class VanillaCommands {
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
            LiteralArgumentBuilder.<ServerCommandSource>literal("me")
                .requires(source -> source.hasPermissionLevel(4))
                .executes(context -> {
                    handleCommandOverride(context);
                    return Command.SINGLE_SUCCESS;
                })
        ));
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                LiteralArgumentBuilder.<ServerCommandSource>literal("list")
                        .requires(source -> source.hasPermissionLevel(4))
                        .executes(context -> {
                            handleCommandOverride(context);
                            return Command.SINGLE_SUCCESS;
                        })
        ));
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                LiteralArgumentBuilder.<ServerCommandSource>literal("random")
                        .requires(source -> source.hasPermissionLevel(4))
                        .executes(context -> {
                            handleCommandOverride(context);
                            return Command.SINGLE_SUCCESS;
                        })
        ));
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                LiteralArgumentBuilder.<ServerCommandSource>literal("seed")
                        .requires(source -> source.hasPermissionLevel(4))
                        .executes(context -> {
                            handleCommandOverride(context);
                            return Command.SINGLE_SUCCESS;
                        })
        ));
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                LiteralArgumentBuilder.<ServerCommandSource>literal("teammsg")
                        .requires(source -> source.hasPermissionLevel(4))
                        .executes(context -> {
                            handleCommandOverride(context);
                            return Command.SINGLE_SUCCESS;
                        })
        ));
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                LiteralArgumentBuilder.<ServerCommandSource>literal("tm")
                        .requires(source -> source.hasPermissionLevel(4))
                        .executes(context -> {
                            handleCommandOverride(context);
                            return Command.SINGLE_SUCCESS;
                        })
        ));
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                LiteralArgumentBuilder.<ServerCommandSource>literal("trigger")
                        .requires(source -> source.hasPermissionLevel(4))
                        .executes(context -> {
                            handleCommandOverride(context);
                            return Command.SINGLE_SUCCESS;
                        })
        ));
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                LiteralArgumentBuilder.<ServerCommandSource>literal("test")
                        .requires(source -> source.hasPermissionLevel(4))
                        .executes(context -> {
                            handleCommandOverride(context);
                            return Command.SINGLE_SUCCESS;
                        })
        ));
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
            LiteralArgumentBuilder.<ServerCommandSource>literal("help")
                .executes(context -> {
                    handleHelp(context);
                    return Command.SINGLE_SUCCESS;
                })
        ));
    }

    private static void handleHelp(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity serverPlayerEntity = context.getSource().getPlayer();

        Logger.logSuccess(serverPlayerEntity, """
                Use /map and all of it's sub commands to list maps, see the world records, and go to maps.
                
                You can do /hide self | others in order to toggle hiding your hand and status bars or to toggle hiding other players.
                
                You can use /spec <player> in order to spectate a player and /unspec to stop spectating.
                
                Thank you for playing with minehop! Please submit maps in the discord (/discord)!
                """);
    }

    private static void handleCommandOverride(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity serverPlayerEntity = context.getSource().getPlayer();

        Logger.logFailure(serverPlayerEntity, "This command was overridden by minehop and is now useless and dumb.");
    }
}
