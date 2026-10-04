package net.nerdorg.minehop.commands;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.nerdorg.minehop.config.ConfigWrapper;
import net.nerdorg.minehop.config.MinehopConfig;
import net.nerdorg.minehop.util.Logger;
import net.nerdorg.minehop.util.StringFormatting;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.function.Function;

public class ConfigCommands {
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            // Build `set` dynamically from the config fields via reflection so every option
            // (booleans, doubles, ints) appears automatically with tab-completion — no per-field
            // wiring needed when a new config setting is added.
            LiteralArgumentBuilder<CommandSourceStack> setNode = LiteralArgumentBuilder.literal("set");
            appendConfigFields(setNode, MinehopConfig.class, config -> config);
            appendConfigFields(setNode, MinehopConfig.MovementSettings.class, config -> config.movement);

            dispatcher.register(
                LiteralArgumentBuilder.<CommandSourceStack>literal("minehop")
                    .then(LiteralArgumentBuilder.<CommandSourceStack>literal("config")
                        .requires(source -> source.hasPermission(4))
                        .then(LiteralArgumentBuilder.<CommandSourceStack>literal("reload")
                            .executes(context -> {
                                handleReload(context);
                                return Command.SINGLE_SUCCESS;
                            })
                        )
                        .then(LiteralArgumentBuilder.<CommandSourceStack>literal("list")
                            .executes(context -> {
                                handleList(context);
                                return Command.SINGLE_SUCCESS;
                            })
                        )
                        .then(setNode)
                    )
            );
        });
    }

    private static void appendConfigFields(
            LiteralArgumentBuilder<CommandSourceStack> setNode,
            Class<?> clazz,
            Function<MinehopConfig, Object> targetGetter
    ) {
        for (Field field : clazz.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            String name = field.getName();
            // Internal migration bookkeeping — not user-settable.
            if (name.equals("source_units_migrated") || name.equals("source_movement_version")) {
                continue;
            }
            try {
                field.setAccessible(true);
            } catch (Exception ignored) {
            }
            Class<?> type = field.getType();
            if (type == boolean.class || type == Boolean.class) {
                setNode.then(LiteralArgumentBuilder.<CommandSourceStack>literal(name)
                        .then(RequiredArgumentBuilder.<CommandSourceStack, Boolean>argument("value", BoolArgumentType.bool())
                                .executes(context -> applyBoolean(context, targetGetter, field, name))));
            } else if (type == double.class || type == Double.class) {
                setNode.then(LiteralArgumentBuilder.<CommandSourceStack>literal(name)
                        .then(RequiredArgumentBuilder.<CommandSourceStack, Double>argument("value", DoubleArgumentType.doubleArg())
                                .executes(context -> applyDouble(context, targetGetter, field, name))));
            } else if (type == int.class || type == Integer.class) {
                setNode.then(LiteralArgumentBuilder.<CommandSourceStack>literal(name)
                        .then(RequiredArgumentBuilder.<CommandSourceStack, Integer>argument("value", IntegerArgumentType.integer())
                                .executes(context -> applyInt(context, targetGetter, field, name))));
            }
            // String fields (e.g. discord tokens) and nested objects (jHud, movement) are skipped.
        }
    }

    private static int applyBoolean(CommandContext<CommandSourceStack> context, Function<MinehopConfig, Object> targetGetter, Field field, String name) {
        MinehopConfig config = ConfigWrapper.config;
        boolean value = BoolArgumentType.getBool(context, "value");
        return applyValue(context, config, targetGetter, field, name, value);
    }

    private static int applyDouble(CommandContext<CommandSourceStack> context, Function<MinehopConfig, Object> targetGetter, Field field, String name) {
        MinehopConfig config = ConfigWrapper.config;
        double value = DoubleArgumentType.getDouble(context, "value");
        return applyValue(context, config, targetGetter, field, name, value);
    }

    private static int applyInt(CommandContext<CommandSourceStack> context, Function<MinehopConfig, Object> targetGetter, Field field, String name) {
        MinehopConfig config = ConfigWrapper.config;
        int value = IntegerArgumentType.getInteger(context, "value");
        return applyValue(context, config, targetGetter, field, name, value);
    }

    private static int applyValue(CommandContext<CommandSourceStack> context, MinehopConfig config, Function<MinehopConfig, Object> targetGetter, Field field, String name, Object value) {
        ServerPlayer player = context.getSource().getPlayer();
        try {
            field.set(targetGetter.apply(config), value);
        } catch (IllegalAccessException | IllegalArgumentException e) {
            Logger.logFailure(player, "Could not set " + name + ".");
            return 0;
        }
        ConfigWrapper.saveConfig(config);
        Logger.logSuccess(player, "Set " + name + " to " + value);
        return Command.SINGLE_SUCCESS;
    }

    private static void handleList(CommandContext<CommandSourceStack> context) {
        ServerPlayer serverPlayerEntity = context.getSource().getPlayer();

        Logger.logSuccess(serverPlayerEntity, "Config Settings \\/\n" + StringFormatting.limitDecimals(gson.toJson(ConfigWrapper.config)));
    }

    private static void handleReload(CommandContext<CommandSourceStack> context) {
        ServerPlayer serverPlayerEntity = context.getSource().getPlayer();

        ConfigWrapper.loadConfig();
        Logger.logSuccess(serverPlayerEntity, "Reloading config.");
    }
}
