package net.nerdorg.minehop.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.nerdorg.minehop.platform.Services;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.util.SurfRampPlacementManager;

import java.util.function.IntSupplier;

public class SurfStickCommands {
    public static void register() {
        Services.EVENTS.onRegisterCommands((dispatcher, registryAccess, environment) -> dispatcher.register(
                LiteralArgumentBuilder.<CommandSourceStack>literal("surfstick")
                        .then(LiteralArgumentBuilder.<CommandSourceStack>literal("mode")
                                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("one")
                                        .executes(context -> safeExecute(
                                                context.getSource(),
                                                "mode one",
                                                () -> chooseMode(resolvePlayer(context.getSource()), true)
                                        ))
                                )
                                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("two")
                                        .executes(context -> safeExecute(
                                                context.getSource(),
                                                "mode two",
                                                () -> chooseMode(resolvePlayer(context.getSource()), false)
                                        ))
                                )
                        )
                        .then(LiteralArgumentBuilder.<CommandSourceStack>literal("side")
                                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("my")
                                        .executes(context -> safeExecute(
                                                context.getSource(),
                                                "side my",
                                                () -> SurfRampPlacementManager.chooseSide(resolvePlayer(context.getSource()), true)
                                        ))
                                )
                                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("other")
                                        .executes(context -> safeExecute(
                                                context.getSource(),
                                                "side other",
                                                () -> SurfRampPlacementManager.chooseSide(resolvePlayer(context.getSource()), false)
                                        ))
                                )
                        )
                        .then(LiteralArgumentBuilder.<CommandSourceStack>literal("width")
                                .then(RequiredArgumentBuilder.<CommandSourceStack, Double>argument("value", DoubleArgumentType.doubleArg(0.15D, 64.0D))
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
                        .then(LiteralArgumentBuilder.<CommandSourceStack>literal("drop")
                                .then(RequiredArgumentBuilder.<CommandSourceStack, Double>argument("value", DoubleArgumentType.doubleArg(0.1D, 64.0D))
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
                        .then(LiteralArgumentBuilder.<CommandSourceStack>literal("texture")
                                .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("block_id", StringArgumentType.string())
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
                        .then(LiteralArgumentBuilder.<CommandSourceStack>literal("clear")
                                .executes(context -> safeExecute(
                                        context.getSource(),
                                        "clear",
                                        () -> SurfRampPlacementManager.clearSelection(resolvePlayer(context.getSource()))
                                ))
                        )
                        .then(LiteralArgumentBuilder.<CommandSourceStack>literal("finish")
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
                                    ServerPlayer player = resolvePlayer(context.getSource());
                                    if (player == null) {
                                        return 0;
                                    }
                                    context.getSource().sendSuccess(() -> Component.literal("Surf stick: first right click opens settings, then add points and run /surfstick finish."), false);
                                    return 1;
                                }
                        ))
        ));
    }

    private static int chooseMode(ServerPlayer player, boolean oneSided) {
        if (player == null) {
            return Command.SINGLE_SUCCESS;
        }
        return SurfRampPlacementManager.chooseMode(player, oneSided);
    }

    private static ServerPlayer resolvePlayer(CommandSourceStack source) {
        if (source.getEntity() instanceof ServerPlayer player) {
            return player;
        }
        source.sendFailure(Component.literal("This command can only be used by a player."));
        return null;
    }

    private static int safeExecute(CommandSourceStack source, String action, IntSupplier execute) {
        try {
            return execute.getAsInt();
        } catch (Throwable throwable) {
            Minehop.LOGGER.error("SurfStick command failed during action '{}'", action, throwable);
            source.sendFailure(Component.literal("Surf stick command failed. Check latest.log for details."));
            return 0;
        }
    }
}
