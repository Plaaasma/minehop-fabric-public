package net.nerdorg.minehop.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.world.GameMode;
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
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                LiteralArgumentBuilder.<ServerCommandSource>literal("plot")
                        .then(LiteralArgumentBuilder.<ServerCommandSource>literal("create")
                                .then(com.mojang.brigadier.builder.RequiredArgumentBuilder.<ServerCommandSource, String>argument("map_name", StringArgumentType.word())
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
                                        .then(com.mojang.brigadier.builder.RequiredArgumentBuilder.<ServerCommandSource, String>argument("ground_block", StringArgumentType.word())
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
                                                .then(com.mojang.brigadier.builder.RequiredArgumentBuilder.<ServerCommandSource, String>argument("description", StringArgumentType.greedyString())
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
                        .then(LiteralArgumentBuilder.<ServerCommandSource>literal("home")
                                .executes(context -> safeExecute(
                                        context.getSource(),
                                        "home",
                                        () -> UserPlotManager.teleportHome(requirePlayer(context.getSource()))
                                ))
                        )
                        .then(LiteralArgumentBuilder.<ServerCommandSource>literal("play")
                                .executes(context -> safeExecute(
                                        context.getSource(),
                                        "play",
                                        () -> UserPlotManager.setOwnedPlotGameMode(requirePlayer(context.getSource()), GameMode.ADVENTURE)
                                ))
                        )
                        .then(LiteralArgumentBuilder.<ServerCommandSource>literal("build")
                                .executes(context -> safeExecute(
                                        context.getSource(),
                                        "build",
                                        () -> UserPlotManager.setOwnedPlotGameMode(requirePlayer(context.getSource()), GameMode.CREATIVE)
                                ))
                        )
                        .then(LiteralArgumentBuilder.<ServerCommandSource>literal("gamemode")
                                .then(LiteralArgumentBuilder.<ServerCommandSource>literal("creative")
                                        .executes(context -> safeExecute(
                                                context.getSource(),
                                                "gamemode_creative",
                                                () -> UserPlotManager.setOwnedPlotGameMode(requirePlayer(context.getSource()), GameMode.CREATIVE)
                                        ))
                                )
                                .then(LiteralArgumentBuilder.<ServerCommandSource>literal("adventure")
                                        .executes(context -> safeExecute(
                                                context.getSource(),
                                                "gamemode_adventure",
                                                () -> UserPlotManager.setOwnedPlotGameMode(requirePlayer(context.getSource()), GameMode.ADVENTURE)
                                        ))
                                )
                                .then(LiteralArgumentBuilder.<ServerCommandSource>literal("survival")
                                        .executes(context -> safeExecute(
                                                context.getSource(),
                                                "gamemode_survival",
                                                () -> UserPlotManager.setOwnedPlotGameMode(requirePlayer(context.getSource()), GameMode.SURVIVAL)
                                        ))
                                )
                        )
                        .then(LiteralArgumentBuilder.<ServerCommandSource>literal("setspawn")
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
                        .then(LiteralArgumentBuilder.<ServerCommandSource>literal("zone")
                                .then(LiteralArgumentBuilder.<ServerCommandSource>literal("start")
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
                                .then(LiteralArgumentBuilder.<ServerCommandSource>literal("end")
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
                                .then(LiteralArgumentBuilder.<ServerCommandSource>literal("reset")
                                        .executes(context -> safeExecute(
                                                context.getSource(),
                                                "zone_reset_default",
                                                () -> runOwnedMapAction(
                                                        requirePlayer(context.getSource()),
                                                        MapCreatorActionPayload.ACTION_ADD_RESET_ZONE,
                                                        0
                                                )
                                        ))
                                        .then(com.mojang.brigadier.builder.RequiredArgumentBuilder.<ServerCommandSource, Integer>argument("checkpoint_index", IntegerArgumentType.integer(0))
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
                        .then(LiteralArgumentBuilder.<ServerCommandSource>literal("description")
                                .then(com.mojang.brigadier.builder.RequiredArgumentBuilder.<ServerCommandSource, String>argument("text", StringArgumentType.greedyString())
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
                        .then(LiteralArgumentBuilder.<ServerCommandSource>literal("ground")
                                .then(com.mojang.brigadier.builder.RequiredArgumentBuilder.<ServerCommandSource, String>argument("block_id", StringArgumentType.word())
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
                        .then(LiteralArgumentBuilder.<ServerCommandSource>literal("delete")
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
                                    ServerPlayerEntity player = requirePlayer(context.getSource());
                                    if (player == null) {
                                        return 0;
                                    }
                                    context.getSource().sendFeedback(
                                            () -> Text.literal(
                                                    "Plot commands: /plot create <map_name> [ground_block] [description], /plot home, /plot play, /plot build, /plot gamemode <creative|adventure|survival>, /plot setspawn, /plot zone <start|end|reset [checkpoint_index]>, /plot description <text>, /plot ground <block_id>, /plot delete."
                                            ),
                                            false
                                    );
                                    return Command.SINGLE_SUCCESS;
                                }
                        ))
        ));
    }

    private static ServerPlayerEntity requirePlayer(ServerCommandSource source) {
        if (source.getEntity() instanceof ServerPlayerEntity player) {
            return player;
        }
        source.sendError(Text.literal("This command can only be used by a player."));
        return null;
    }

    private static int runOwnedMapAction(ServerPlayerEntity player, String action, int checkpointIndex) {
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
                mapData.movement_auto_step_up,
                mapData.movement_css_crouch_jump,
                mapData.movement_disable_sprint,
                mapData.movement_fall_damage,
                Math.max(0, checkpointIndex)
        );
        return Command.SINGLE_SUCCESS;
    }

    private static int safeExecute(ServerCommandSource source, String action, IntSupplier execute) {
        try {
            return execute.getAsInt();
        } catch (Throwable throwable) {
            Minehop.LOGGER.error("Plot command failed during action '{}'", action, throwable);
            source.sendError(Text.literal("Plot command failed. Check latest.log for details."));
            return 0;
        }
    }
}
