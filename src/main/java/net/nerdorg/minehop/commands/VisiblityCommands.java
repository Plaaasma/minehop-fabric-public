package net.nerdorg.minehop.commands;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.nerdorg.minehop.block.entity.BoostBlockEntity;
import net.nerdorg.minehop.networking.PacketHandler;
import net.nerdorg.minehop.util.Logger;

public class VisiblityCommands {
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
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
