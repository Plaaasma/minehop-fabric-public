package net.nerdorg.minehop.commands;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
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
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.block.entity.BoostBlockEntity;
import net.nerdorg.minehop.config.ConfigWrapper;
import net.nerdorg.minehop.config.MinehopConfig;
import net.nerdorg.minehop.util.Logger;
import net.nerdorg.minehop.util.StringFormatting;

public class ConfigCommands {
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
            LiteralArgumentBuilder.<ServerCommandSource>literal("minehop")
                .then(LiteralArgumentBuilder.<ServerCommandSource>literal("config")
                    .requires(source -> source.hasPermissionLevel(4))
                    .then(LiteralArgumentBuilder.<ServerCommandSource>literal("reload")
                        .executes(context -> {
                            handleReload(context);
                            return Command.SINGLE_SUCCESS;
                        })
                    )
                    .then(LiteralArgumentBuilder.<ServerCommandSource>literal("list")
                        .executes(context -> {
                            handleList(context);
                            return Command.SINGLE_SUCCESS;
                        })
                    )
                    .then(LiteralArgumentBuilder.<ServerCommandSource>literal("set")
                        .then(LiteralArgumentBuilder.<ServerCommandSource>literal("toggle_fall_dmg")
                                .executes(context -> {
                                    handleToggleFallDamage(context);
                                    return Command.SINGLE_SUCCESS;
                                })
                        )
                        .then(LiteralArgumentBuilder.<ServerCommandSource>literal("toggle_enabled")
                                .executes(context -> {
                                    handleToggleEnabled(context);
                                    return Command.SINGLE_SUCCESS;
                                })
                        )
                        .then(LiteralArgumentBuilder.<ServerCommandSource>literal("toggle_auto_step_up")
                                .executes(context -> {
                                    handleToggleAutoStepUp(context);
                                    return Command.SINGLE_SUCCESS;
                                })
                        )
                        .then(LiteralArgumentBuilder.<ServerCommandSource>literal("toggle_css_crouch_jump")
                                .executes(context -> {
                                    handleToggleCssCrouchJump(context);
                                    return Command.SINGLE_SUCCESS;
                                })
                        )
                        .then(LiteralArgumentBuilder.<ServerCommandSource>literal("speed_coefficient")
                                .then(RequiredArgumentBuilder.<ServerCommandSource, Double>argument("speed_coefficient", DoubleArgumentType.doubleArg())
                                        .executes(context -> {
                                            handleSetSpeedCoeff(context);
                                            return Command.SINGLE_SUCCESS;
                                        })
                                )
                        )
                        .then(LiteralArgumentBuilder.<ServerCommandSource>literal("sv_friction")
                                .then(RequiredArgumentBuilder.<ServerCommandSource, Double>argument("sv_friction", DoubleArgumentType.doubleArg())
                                        .executes(context -> {
                                            handleSetFriction(context);
                                            return Command.SINGLE_SUCCESS;
                                        })
                                )
                        )
                        .then(LiteralArgumentBuilder.<ServerCommandSource>literal("sv_accelerate")
                                .then(RequiredArgumentBuilder.<ServerCommandSource, Double>argument("sv_accelerate", DoubleArgumentType.doubleArg())
                                        .executes(context -> {
                                            handleSetAccelerate(context);
                                            return Command.SINGLE_SUCCESS;
                                        })
                                )
                        )
                        .then(LiteralArgumentBuilder.<ServerCommandSource>literal("sv_airaccelerate")
                                .then(RequiredArgumentBuilder.<ServerCommandSource, Double>argument("sv_airaccelerate", DoubleArgumentType.doubleArg())
                                        .executes(context -> {
                                            handleSetAirAccelerate(context);
                                            return Command.SINGLE_SUCCESS;
                                        })
                                )
                        )
                        .then(LiteralArgumentBuilder.<ServerCommandSource>literal("sv_maxairspeed")
                                .then(RequiredArgumentBuilder.<ServerCommandSource, Double>argument("sv_maxairspeed", DoubleArgumentType.doubleArg())
                                        .executes(context -> {
                                            handleSetMaxAirSpeed(context);
                                            return Command.SINGLE_SUCCESS;
                                        })
                                )
                        )
                        .then(LiteralArgumentBuilder.<ServerCommandSource>literal("sv_jump_impulse")
                                .then(RequiredArgumentBuilder.<ServerCommandSource, Double>argument("sv_jump_impulse", DoubleArgumentType.doubleArg())
                                        .executes(context -> {
                                            handleSetJumpImpulse(context);
                                            return Command.SINGLE_SUCCESS;
                                        })
                                )
                        )
                        .then(LiteralArgumentBuilder.<ServerCommandSource>literal("speed_mul")
                                .then(RequiredArgumentBuilder.<ServerCommandSource, Double>argument("speed_mul", DoubleArgumentType.doubleArg())
                                        .executes(context -> {
                                            handleSetSpeedMul(context);
                                            return Command.SINGLE_SUCCESS;
                                        })
                                )
                        )
                        .then(LiteralArgumentBuilder.<ServerCommandSource>literal("sv_gravity")
                            .then(RequiredArgumentBuilder.<ServerCommandSource, Double>argument("sv_gravity", DoubleArgumentType.doubleArg())
                                .executes(context -> {
                                    handleSetGravity(context);
                                    return Command.SINGLE_SUCCESS;
                                })
                            )
                        )
                    )
                )
            ));
    }

    private static void handleToggleFallDamage(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity serverPlayerEntity = context.getSource().getPlayer();
        MinehopConfig config = ConfigWrapper.config;

        config.fall_damage = !config.fall_damage;

        ConfigWrapper.saveConfig(config);

        Logger.logSuccess(serverPlayerEntity, "Set fall damage to " + config.fall_damage);
    }

    private static void handleToggleEnabled(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity serverPlayerEntity = context.getSource().getPlayer();
        MinehopConfig config = ConfigWrapper.config;

        config.enabled = !config.enabled;

        ConfigWrapper.saveConfig(config);

        Logger.logSuccess(serverPlayerEntity, "Set enabled to " + config.enabled);
    }

    private static void handleToggleAutoStepUp(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity serverPlayerEntity = context.getSource().getPlayer();
        MinehopConfig config = ConfigWrapper.config;

        config.movement.auto_step_up = !config.movement.auto_step_up;

        ConfigWrapper.saveConfig(config);

        Logger.logSuccess(serverPlayerEntity, "Set auto_step_up to " + config.movement.auto_step_up);
    }

    private static void handleToggleCssCrouchJump(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity serverPlayerEntity = context.getSource().getPlayer();
        MinehopConfig config = ConfigWrapper.config;

        config.movement.css_crouch_jump = !config.movement.css_crouch_jump;

        ConfigWrapper.saveConfig(config);

        Logger.logSuccess(serverPlayerEntity, "Set css_crouch_jump to " + config.movement.css_crouch_jump);
    }

    private static void handleSetSpeedCoeff(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity serverPlayerEntity = context.getSource().getPlayer();
        MinehopConfig config = ConfigWrapper.config;

        config.movement.speed_coefficient = DoubleArgumentType.getDouble(context, "speed_coefficient");

        ConfigWrapper.saveConfig(config);

        Logger.logSuccess(serverPlayerEntity, "Set speed_coefficient to " + config.movement.speed_coefficient);
    }

    private static void handleSetFriction(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity serverPlayerEntity = context.getSource().getPlayer();
        MinehopConfig config = ConfigWrapper.config;

        config.movement.sv_friction = DoubleArgumentType.getDouble(context, "sv_friction");

        ConfigWrapper.saveConfig(config);

        Logger.logSuccess(serverPlayerEntity, "Set sv_friction to " + config.movement.sv_friction);
    }

    private static void handleSetAccelerate(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity serverPlayerEntity = context.getSource().getPlayer();
        MinehopConfig config = ConfigWrapper.config;

        config.movement.sv_accelerate = DoubleArgumentType.getDouble(context, "sv_accelerate");

        ConfigWrapper.saveConfig(config);

        Logger.logSuccess(serverPlayerEntity, "Set sv_accelerate to " + config.movement.sv_accelerate);
    }

    private static void handleSetAirAccelerate(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity serverPlayerEntity = context.getSource().getPlayer();
        MinehopConfig config = ConfigWrapper.config;

        config.movement.sv_airaccelerate = DoubleArgumentType.getDouble(context, "sv_airaccelerate");

        ConfigWrapper.saveConfig(config);

        Logger.logSuccess(serverPlayerEntity, "Set sv_airaccelerate to " + config.movement.sv_airaccelerate);
    }

    private static void handleSetMaxAirSpeed(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity serverPlayerEntity = context.getSource().getPlayer();
        MinehopConfig config = ConfigWrapper.config;

        config.movement.sv_maxairspeed = DoubleArgumentType.getDouble(context, "sv_maxairspeed");

        ConfigWrapper.saveConfig(config);

        Logger.logSuccess(serverPlayerEntity, "Set sv_maxairspeed to " + config.movement.sv_maxairspeed);
    }

    private static void handleSetSpeedMul(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity serverPlayerEntity = context.getSource().getPlayer();
        MinehopConfig config = ConfigWrapper.config;

        config.movement.speed_mul = DoubleArgumentType.getDouble(context, "speed_mul");

        ConfigWrapper.saveConfig(config);

        Logger.logSuccess(serverPlayerEntity, "Set speed_mul to " + config.movement.speed_mul);
    }

    private static void handleSetJumpImpulse(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity serverPlayerEntity = context.getSource().getPlayer();
        MinehopConfig config = ConfigWrapper.config;

        config.movement.sv_jump_impulse = DoubleArgumentType.getDouble(context, "sv_jump_impulse");

        ConfigWrapper.saveConfig(config);

        Logger.logSuccess(serverPlayerEntity, "Set sv_jump_impulse to " + config.movement.sv_jump_impulse);
    }

    private static void handleSetGravity(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity serverPlayerEntity = context.getSource().getPlayer();
        MinehopConfig config = ConfigWrapper.config;

        config.movement.sv_gravity = DoubleArgumentType.getDouble(context, "sv_gravity");

        ConfigWrapper.saveConfig(config);

        Logger.logSuccess(serverPlayerEntity, "Set sv_gravity to " + config.movement.sv_gravity);
    }

    private static void handleList(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity serverPlayerEntity = context.getSource().getPlayer();

        Logger.logSuccess(serverPlayerEntity, "Config Settings \\/\n" + StringFormatting.limitDecimals(gson.toJson(ConfigWrapper.config)));
    }

    private static void handleReload(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity serverPlayerEntity = context.getSource().getPlayer();

        ConfigWrapper.loadConfig();
        Logger.logSuccess(serverPlayerEntity, "Reloading config.");
    }
}
