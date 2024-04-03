package net.nerdorg.minehop.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.EntitySelector;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.GameMode;
import net.nerdorg.minehop.networking.PacketHandler;
import net.nerdorg.minehop.util.Logger;

import java.util.HashMap;
import java.util.List;

public class SpectateCommands {
    public static HashMap<String, List<String>> spectatorList = new HashMap<>();

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
            LiteralArgumentBuilder.<ServerCommandSource>literal("spec")
                .then(RequiredArgumentBuilder.<ServerCommandSource, EntitySelector>argument("player", EntityArgumentType.player())
                    .executes(context -> {
                        handleSpectate(context);
                        return Command.SINGLE_SUCCESS;
                    })
                )
            ));
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
            LiteralArgumentBuilder.<ServerCommandSource>literal("spectate")
                .then(RequiredArgumentBuilder.<ServerCommandSource, EntitySelector>argument("player", EntityArgumentType.player())
                    .executes(context -> {
                        handleSpectate(context);
                        return Command.SINGLE_SUCCESS;
                    })
                )
        ));
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
            LiteralArgumentBuilder.<ServerCommandSource>literal("unspec")
                .executes(context -> {
                    handleUnSpectate(context);
                    return Command.SINGLE_SUCCESS;
                })

        ));
    }

    private static void handleUnSpectate(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity serverPlayerEntity = context.getSource().getPlayer();

        if (!serverPlayerEntity.isSpectator()) {
            Logger.logFailure(serverPlayerEntity, "You are not spectating.");
        }
        else {
            Logger.logSuccess(serverPlayerEntity, "No longer spectating.");
            serverPlayerEntity.changeGameMode(GameMode.ADVENTURE);
            serverPlayerEntity.setCameraEntity(serverPlayerEntity);
            SpawnCommands.handleSpawn(context);
        }
    }

    private static void handleSpectate(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity serverPlayerEntity = context.getSource().getPlayer();

        ServerPlayerEntity targetPlayerEntity = EntityArgumentType.getPlayer(context, "player");

        if (targetPlayerEntity == serverPlayerEntity) {
            Logger.logFailure(serverPlayerEntity, "You cannot spectate yourself.");
        }
        else if (targetPlayerEntity.isCreative() || targetPlayerEntity.isSpectator()) {
            Logger.logFailure(serverPlayerEntity, "You cannot spectate another spectator.");
        }
        else {
            Logger.logSuccess(serverPlayerEntity, "Now spectating " + targetPlayerEntity.getNameForScoreboard());
            serverPlayerEntity.changeGameMode(GameMode.SPECTATOR);
            PacketHandler.sendSpectate(serverPlayerEntity, targetPlayerEntity.getNameForScoreboard());
        }
    }
}
