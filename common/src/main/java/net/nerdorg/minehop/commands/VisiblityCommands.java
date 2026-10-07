package net.nerdorg.minehop.commands;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.nerdorg.minehop.platform.Services;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.nerdorg.minehop.block.entity.BoostBlockEntity;
import net.nerdorg.minehop.networking.PacketHandler;
import net.nerdorg.minehop.util.Logger;

public class VisiblityCommands {
    public static void register() {
        Services.EVENTS.onRegisterCommands((dispatcher, registryAccess, environment) -> dispatcher.register(
            LiteralArgumentBuilder.<CommandSourceStack>literal("hide")
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("self")
                    .executes(context -> {
                        handleHideSelf(context);
                        return Command.SINGLE_SUCCESS;
                    })
                )
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("replay")
                        .executes(context -> {
                            handleHideReplays(context);
                            return Command.SINGLE_SUCCESS;
                        })
                )
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("race")
                        .executes(context -> {
                            handleHideRace(context);
                            return Command.SINGLE_SUCCESS;
                        })
                )
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("others")
                    .executes(context -> {
                        handleHideOthers(context);
                        return Command.SINGLE_SUCCESS;
                    })
                )
            ));
    }

    private static void handleHideReplays(CommandContext<CommandSourceStack> context) {
        ServerPlayer serverPlayerEntity = context.getSource().getPlayer();
        PacketHandler.sendReplayVToggle(serverPlayerEntity);
        Logger.logSuccess(serverPlayerEntity, "Toggling visibility for replay models.");
    }

    private static void handleHideRace(CommandContext<CommandSourceStack> context) {
        ServerPlayer serverPlayerEntity = context.getSource().getPlayer();
        if (serverPlayerEntity == null) {
            return;
        }
        if (!net.nerdorg.minehop.networking.HandshakeHandler.supportsClientReplays(serverPlayerEntity)) {
            Logger.logFailure(serverPlayerEntity, "Update Minehop to " + net.nerdorg.minehop.Minehop.MOD_VERSION_STRING
                    + " or newer to race your personal best.");
            return;
        }
        net.nerdorg.minehop.replays.ReplayStreaming.send(serverPlayerEntity,
                new net.nerdorg.minehop.networking.payloads.ReplayControlPayload(net.nerdorg.minehop.networking.ReplayProtocol.CONTROL_HIDE_RACE, 0.0D));
        Logger.logSuccess(serverPlayerEntity, "Toggling visibility for the race ghost.");
    }

    private static void handleHideOthers(CommandContext<CommandSourceStack> context) {
        ServerPlayer serverPlayerEntity = context.getSource().getPlayer();
        PacketHandler.sendOtherVToggle(serverPlayerEntity);
        Logger.logSuccess(serverPlayerEntity, "Toggling visibility for other player models.");
    }

    private static void handleHideSelf(CommandContext<CommandSourceStack> context) {
        ServerPlayer serverPlayerEntity = context.getSource().getPlayer();
        PacketHandler.sendSelfVToggle(serverPlayerEntity);
        Logger.logSuccess(serverPlayerEntity, "Toggling visibility for hand and status bars.");
    }
}
