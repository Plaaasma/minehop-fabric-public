package net.nerdorg.minehop.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.util.SurfRampPlacementManager;

import java.util.function.IntSupplier;

public class SurfStickCommands {
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                LiteralArgumentBuilder.<ServerCommandSource>literal("surfstick")
                        .then(LiteralArgumentBuilder.<ServerCommandSource>literal("mode")
                                .then(LiteralArgumentBuilder.<ServerCommandSource>literal("one")
                                        .executes(context -> safeExecute(
                                                context.getSource(),
                                                "mode one",
                                                () -> chooseMode(resolvePlayer(context.getSource()), true)
                                        ))
                                )
                                .then(LiteralArgumentBuilder.<ServerCommandSource>literal("two")
                                        .executes(context -> safeExecute(
                                                context.getSource(),
                                                "mode two",
                                                () -> chooseMode(resolvePlayer(context.getSource()), false)
                                        ))
                                )
                        )
                        .then(LiteralArgumentBuilder.<ServerCommandSource>literal("side")
                                .then(LiteralArgumentBuilder.<ServerCommandSource>literal("my")
                                        .executes(context -> safeExecute(
                                                context.getSource(),
                                                "side my",
                                                () -> SurfRampPlacementManager.chooseSide(resolvePlayer(context.getSource()), true)
                                        ))
                                )
                                .then(LiteralArgumentBuilder.<ServerCommandSource>literal("other")
                                        .executes(context -> safeExecute(
                                                context.getSource(),
                                                "side other",
                                                () -> SurfRampPlacementManager.chooseSide(resolvePlayer(context.getSource()), false)
                                        ))
                                )
                        )
                        .then(LiteralArgumentBuilder.<ServerCommandSource>literal("width")
                                .then(RequiredArgumentBuilder.<ServerCommandSource, Double>argument("value", DoubleArgumentType.doubleArg(0.15D, 64.0D))
                                        .executes(context -> safeExecute(
                                                context.getSource(),
                                                "width",
                                                () -> SurfRampPlacementManager.setWidth(
                                                        resolvePlayer(context.getSource()),
                                                        DoubleArgumentType.getDouble(context, "value")
                                                )
                                        ))
                                )
                        )
                        .then(LiteralArgumentBuilder.<ServerCommandSource>literal("drop")
                                .then(RequiredArgumentBuilder.<ServerCommandSource, Double>argument("value", DoubleArgumentType.doubleArg(0.1D, 64.0D))
                                        .executes(context -> safeExecute(
                                                context.getSource(),
                                                "drop",
                                                () -> SurfRampPlacementManager.setDrop(
                                                        resolvePlayer(context.getSource()),
                                                        DoubleArgumentType.getDouble(context, "value")
                                                )
                                        ))
                                )
                        )
                        .then(LiteralArgumentBuilder.<ServerCommandSource>literal("texture")
                                .then(RequiredArgumentBuilder.<ServerCommandSource, String>argument("block_id", StringArgumentType.string())
                                        .executes(context -> safeExecute(
                                                context.getSource(),
                                                "texture",
                                                () -> SurfRampPlacementManager.setTexture(
                                                        resolvePlayer(context.getSource()),
                                                        StringArgumentType.getString(context, "block_id")
                                                )
                                        ))
                                )
                        )
                        .then(LiteralArgumentBuilder.<ServerCommandSource>literal("clear")
                                .executes(context -> safeExecute(
                                        context.getSource(),
                                        "clear",
                                        () -> SurfRampPlacementManager.clearSelection(resolvePlayer(context.getSource()))
                                ))
                        )
                        .then(LiteralArgumentBuilder.<ServerCommandSource>literal("finish")
                                .executes(context -> safeExecute(
                                        context.getSource(),
                                        "finish",
                                        () -> SurfRampPlacementManager.finishSelection(resolvePlayer(context.getSource()))
                                ))
                        )
                        .executes(context -> safeExecute(
                                context.getSource(),
                                "root",
                                () -> {
                                    ServerPlayerEntity player = resolvePlayer(context.getSource());
                                    if (player == null) {
                                        return 0;
                                    }
                                    context.getSource().sendFeedback(() -> Text.literal("Surf stick: first right click opens settings, then add points and run /surfstick finish."), false);
                                    return 1;
                                }
                        ))
        ));
    }

    private static int chooseMode(ServerPlayerEntity player, boolean oneSided) {
        if (player == null) {
            return Command.SINGLE_SUCCESS;
        }
        return SurfRampPlacementManager.chooseMode(player, oneSided);
    }

    private static ServerPlayerEntity resolvePlayer(ServerCommandSource source) {
        if (source.getEntity() instanceof ServerPlayerEntity player) {
            return player;
        }
        source.sendError(Text.literal("This command can only be used by a player."));
        return null;
    }

    private static int safeExecute(ServerCommandSource source, String action, IntSupplier execute) {
        try {
            return execute.getAsInt();
        } catch (Throwable throwable) {
            Minehop.LOGGER.error("SurfStick command failed during action '{}'", action, throwable);
            source.sendError(Text.literal("Surf stick command failed. Check latest.log for details."));
            return 0;
        }
    }
}
