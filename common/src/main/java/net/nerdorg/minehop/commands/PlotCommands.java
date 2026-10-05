package net.nerdorg.minehop.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.nerdorg.minehop.platform.Services;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.data.DataManager;
import net.nerdorg.minehop.networking.payloads.MapCreatorActionPayload;
import net.nerdorg.minehop.util.Logger;
import net.nerdorg.minehop.util.MapCreationManager;
import net.nerdorg.minehop.util.UserPlotManager;

import java.util.function.IntSupplier;

public final class PlotCommands {
    private PlotCommands() {
    }

    public static void register() {
        Services.EVENTS.onRegisterCommands((dispatcher, registryAccess, environment) -> dispatcher.register(
                LiteralArgumentBuilder.<CommandSourceStack>literal("plot")
                        .then(LiteralArgumentBuilder.<CommandSourceStack>literal("create")
                                .then(com.mojang.brigadier.builder.RequiredArgumentBuilder.<CommandSourceStack, String>argument("map_name", StringArgumentType.word())
                                        .executes(context -> safeExecute(
                                                context.getSource(),
                                                "create_default",
                                                () -> UserPlotManager.createPlot(
                                                        requirePlayer(context.getSource()),
                                                        StringArgumentType.getString(context, "map_name"),
                                                        "minecraft:grass_block",
                                                        ""
                                                )
                                        ))
                                        .then(com.mojang.brigadier.builder.RequiredArgumentBuilder.<CommandSourceStack, String>argument("ground_block", StringArgumentType.word())
                                                .executes(context -> safeExecute(
                                                        context.getSource(),
                                                        "create_ground",
                                                        () -> UserPlotManager.createPlot(
                                                                requirePlayer(context.getSource()),
                                                                StringArgumentType.getString(context, "map_name"),
                                                                StringArgumentType.getString(context, "ground_block"),
                                                                ""
                                                        )
                                                ))
                                                .then(com.mojang.brigadier.builder.RequiredArgumentBuilder.<CommandSourceStack, String>argument("description", StringArgumentType.greedyString())
                                                        .executes(context -> safeExecute(
                                                                context.getSource(),
                                                                "create_full",
                                                                () -> UserPlotManager.createPlot(
                                                                        requirePlayer(context.getSource()),
                                                                        StringArgumentType.getString(context, "map_name"),
                                                                        StringArgumentType.getString(context, "ground_block"),
                                                                        StringArgumentType.getString(context, "description")
                                                                )
                                                        ))
                                                )
                                        )
                                )
                        )
                        .then(LiteralArgumentBuilder.<CommandSourceStack>literal("home")
                                .executes(context -> safeExecute(
                                        context.getSource(),
                                        "home",
                                        () -> UserPlotManager.teleportHome(requirePlayer(context.getSource()))
                                ))
                        )
                        .then(LiteralArgumentBuilder.<CommandSourceStack>literal("play")
                                .executes(context -> safeExecute(
                                        context.getSource(),
                                        "play",
                                        () -> UserPlotManager.setOwnedPlotGameMode(requirePlayer(context.getSource()), GameType.ADVENTURE)
                                ))
                        )
                        .then(LiteralArgumentBuilder.<CommandSourceStack>literal("build")
                                .executes(context -> safeExecute(
                                        context.getSource(),
                                        "build",
                                        () -> UserPlotManager.setOwnedPlotGameMode(requirePlayer(context.getSource()), GameType.CREATIVE)
                                ))
                        )
                        .then(LiteralArgumentBuilder.<CommandSourceStack>literal("gamemode")
                                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("creative")
                                        .executes(context -> safeExecute(
                                                context.getSource(),
                                                "gamemode_creative",
                                                () -> UserPlotManager.setOwnedPlotGameMode(requirePlayer(context.getSource()), GameType.CREATIVE)
                                        ))
                                )
                                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("adventure")
                                        .executes(context -> safeExecute(
                                                context.getSource(),
                                                "gamemode_adventure",
                                                () -> UserPlotManager.setOwnedPlotGameMode(requirePlayer(context.getSource()), GameType.ADVENTURE)
                                        ))
                                )
                                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("survival")
                                        .executes(context -> safeExecute(
                                                context.getSource(),
                                                "gamemode_survival",
                                                () -> UserPlotManager.setOwnedPlotGameMode(requirePlayer(context.getSource()), GameType.SURVIVAL)
                                        ))
                                )
                        )
                        .then(LiteralArgumentBuilder.<CommandSourceStack>literal("setspawn")
                                .executes(context -> safeExecute(
                                        context.getSource(),
                                        "setspawn",
                                        () -> runOwnedMapAction(
                                                requirePlayer(context.getSource()),
                                                MapCreatorActionPayload.ACTION_SET_SPAWN,
                                                0
                                        )
                                ))
                        )
                        .then(LiteralArgumentBuilder.<CommandSourceStack>literal("zone")
                                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("start")
                                        .executes(context -> safeExecute(
                                                context.getSource(),
                                                "zone_start",
                                                () -> runOwnedMapAction(
                                                        requirePlayer(context.getSource()),
                                                        MapCreatorActionPayload.ACTION_ADD_START_ZONE,
                                                        0
                                                )
                                        ))
                                )
                                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("end")
                                        .executes(context -> safeExecute(
                                                context.getSource(),
                                                "zone_end",
                                                () -> runOwnedMapAction(
                                                        requirePlayer(context.getSource()),
                                                        MapCreatorActionPayload.ACTION_ADD_END_ZONE,
                                                        0
                                                )
                                        ))
                                )
                                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("reset")
                                        .executes(context -> safeExecute(
                                                context.getSource(),
                                                "zone_reset_default",
                                                () -> runOwnedMapAction(
                                                        requirePlayer(context.getSource()),
                                                        MapCreatorActionPayload.ACTION_ADD_RESET_ZONE,
                                                        0
                                                )
                                        ))
                                        .then(com.mojang.brigadier.builder.RequiredArgumentBuilder.<CommandSourceStack, Integer>argument("checkpoint_index", IntegerArgumentType.integer(0))
                                                .executes(context -> safeExecute(
                                                        context.getSource(),
                                                        "zone_reset_indexed",
                                                        () -> runOwnedMapAction(
                                                                requirePlayer(context.getSource()),
                                                                MapCreatorActionPayload.ACTION_ADD_RESET_ZONE,
                                                                IntegerArgumentType.getInteger(context, "checkpoint_index")
                                                        )
                                                ))
                                        )
                                )
                        )
                        .then(LiteralArgumentBuilder.<CommandSourceStack>literal("description")
                                .then(com.mojang.brigadier.builder.RequiredArgumentBuilder.<CommandSourceStack, String>argument("text", StringArgumentType.greedyString())
                                        .executes(context -> safeExecute(
                                                context.getSource(),
                                                "description",
                                                () -> UserPlotManager.setDescription(
                                                        requirePlayer(context.getSource()),
                                                        StringArgumentType.getString(context, "text")
                                                )
                                        ))
                                )
                        )
                        .then(LiteralArgumentBuilder.<CommandSourceStack>literal("ground")
                                .then(com.mojang.brigadier.builder.RequiredArgumentBuilder.<CommandSourceStack, String>argument("block_id", StringArgumentType.word())
                                        .executes(context -> safeExecute(
                                                context.getSource(),
                                                "ground",
                                                () -> UserPlotManager.setGround(
                                                        requirePlayer(context.getSource()),
                                                        StringArgumentType.getString(context, "block_id")
                                                )
                                        ))
                                )
                        )
                        .then(LiteralArgumentBuilder.<CommandSourceStack>literal("delete")
                                .executes(context -> safeExecute(
                                        context.getSource(),
                                        "delete",
                                        () -> UserPlotManager.deleteOwnedPlot(requirePlayer(context.getSource()))
                                ))
                        )
                        .executes(context -> safeExecute(
                                context.getSource(),
                                "root",
                                () -> {
                                    ServerPlayer player = requirePlayer(context.getSource());
                                    if (player == null) {
                                        return 0;
                                    }
                                    context.getSource().sendSuccess(
                                            () -> Component.literal(
                                                    "Plot commands: /plot create <map_name> [ground_block] [description], /plot home, /plot play, /plot build, /plot gamemode <creative|adventure|survival>, /plot setspawn, /plot zone <start|end|reset [checkpoint_index]>, /plot description <text>, /plot ground <block_id>, /plot delete."
                                            ),
                                            false
                                    );
                                    return Command.SINGLE_SUCCESS;
                                }
                        ))
        ));
    }

    private static ServerPlayer requirePlayer(CommandSourceStack source) {
        if (source.getEntity() instanceof ServerPlayer player) {
            return player;
        }
        source.sendFailure(Component.literal("This command can only be used by a player."));
        return null;
    }

    private static int runOwnedMapAction(ServerPlayer player, String action, int checkpointIndex) {
        if (player == null) {
            return 0;
        }
        DataManager.MapData mapData = UserPlotManager.getAnyOwnedPlot(player);
        if (mapData == null || mapData.name == null || mapData.name.isBlank()) {
            Logger.logFailure(player, "You do not have a custom map yet. Create one with /plot create <map_name>.");
            return 0;
        }
        MapCreationManager.handleAction(
                player,
                action,
                mapData.name,
                mapData.difficulty,
                mapData.arena,
                mapData.hns,
                mapData.surf,
                mapData.kz,
                mapData.movement_override,
                mapData.movement_sv_friction,
                mapData.movement_sv_accelerate,
                mapData.movement_sv_airaccelerate,
                mapData.movement_sv_maxairspeed,
                mapData.movement_sv_jump_impulse,
                mapData.movement_speed_mul,
                mapData.movement_sv_gravity,
                mapData.movement_sv_stopspeed,
                mapData.movement_speed_coefficient,
                mapData.movement_speed_cap,
                mapData.movement_auto_step_up,
                mapData.movement_css_crouch_jump,
                mapData.movement_disable_sprint,
                mapData.movement_fall_damage,
                Math.max(0, checkpointIndex)
        );
        return Command.SINGLE_SUCCESS;
    }

    private static int safeExecute(CommandSourceStack source, String action, IntSupplier execute) {
        try {
            return execute.getAsInt();
        } catch (Throwable throwable) {
            Minehop.LOGGER.error("Plot command failed during action '{}'", action, throwable);
            source.sendFailure(Component.literal("Plot command failed. Check latest.log for details."));
            return 0;
        }
    }
}
