package net.nerdorg.minehop.commands;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.nerdorg.minehop.platform.Services;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.data.DataManager;
import net.nerdorg.minehop.entity.ModEntities;
import net.nerdorg.minehop.entity.custom.EndEntity;
import net.nerdorg.minehop.entity.custom.ResetEntity;
import net.nerdorg.minehop.entity.custom.StartEntity;
import net.nerdorg.minehop.entity.custom.Zone;
import net.nerdorg.minehop.item.ModItems;
import net.nerdorg.minehop.item.custom.BoundsStickItem;
import net.nerdorg.minehop.networking.PacketHandler;
import net.nerdorg.minehop.util.Logger;
import net.nerdorg.minehop.util.StringFormatting;

import java.util.ArrayList;
import java.util.List;

public class ZoneManagementCommands {
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public static void register() {
        Services.EVENTS.onRegisterCommands((dispatcher, registryAccess, environment) -> dispatcher.register(
                LiteralArgumentBuilder.<CommandSourceStack>literal("zone")
                        .requires(source -> source.hasPermission(4))
                        .then(LiteralArgumentBuilder.<CommandSourceStack>literal("kill")
                                .executes(context -> {
                                    handleKill(context);
                                    return Command.SINGLE_SUCCESS;
                                })
                        )
                        .then(LiteralArgumentBuilder.<CommandSourceStack>literal("add")
                                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("reset")
                                        .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("map_name", StringArgumentType.string())
                                                .suggests((context, builder) -> {
                                                    for (DataManager.MapData mapData : Minehop.mapList) {
                                                        builder.suggest(mapData.name, new LiteralMessage(mapData.name));
                                                    }
                                                    return builder.buildFuture();
                                                })
                                                .then(RequiredArgumentBuilder.<CommandSourceStack, Integer>argument("check_index", IntegerArgumentType.integer())
                                                        .executes(context -> {
                                                            handleAddResetCustom(context);
                                                            return Command.SINGLE_SUCCESS;
                                                        })
                                                )
                                                .executes(context -> {
                                                    handleAddReset(context);
                                                    return Command.SINGLE_SUCCESS;
                                                })
                                        )
                                )
                                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("start")
                                        .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("map_name", StringArgumentType.string())
                                                .suggests((context, builder) -> {
                                                    for (DataManager.MapData mapData : Minehop.mapList) {
                                                        builder.suggest(mapData.name, new LiteralMessage(mapData.name));
                                                    }
                                                    return builder.buildFuture();
                                                })
                                                .executes(context -> {
                                                    handleAddStart(context);
                                                    return Command.SINGLE_SUCCESS;
                                                })
                                        )
                                )
                                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("end")
                                        .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("map_name", StringArgumentType.string())
                                                .suggests((context, builder) -> {
                                                    for (DataManager.MapData mapData : Minehop.mapList) {
                                                        builder.suggest(mapData.name, new LiteralMessage(mapData.name));
                                                    }
                                                    return builder.buildFuture();
                                                })
                                                .executes(context -> {
                                                    handleAddEnd(context);
                                                    return Command.SINGLE_SUCCESS;
                                                })
                                        )
                                )
                        )
        ));
    }

    private static void handleKill(CommandContext<CommandSourceStack> context) {
        ServerPlayer serverPlayerEntity = context.getSource().getPlayer();
        ServerLevel serverWorld = serverPlayerEntity.serverLevel();
        List<Zone> zoneEntities = new ArrayList<>();
        for (Entity entity : serverWorld.getAllEntities()) {
            if (entity instanceof Zone zone) {
                zoneEntities.add(zone);
            }
        }
        double closestDistance = Double.POSITIVE_INFINITY;
        Zone closestEntity = null;
        for (Zone zoneEntity : zoneEntities) {
            double distance = zoneEntity.distanceTo(serverPlayerEntity);
            if (distance < closestDistance) {
                closestEntity = zoneEntity;
                closestDistance = distance;
            }
        }
        if (closestEntity != null) {
            closestEntity.kill(serverWorld);
            Logger.logSuccess(serverPlayerEntity, "Killed nearest zone entity.");
        }
        else {
            Logger.logFailure(serverPlayerEntity, "Couldn't find zone entity within 10 blocks.");
        }
    }

    private static void handleAddResetCustom(CommandContext<CommandSourceStack> context) {
        ServerPlayer serverPlayerEntity = context.getSource().getPlayer();
        String name = StringArgumentType.getString(context, "map_name");
        int check_index = IntegerArgumentType.getInteger(context, "check_index");
        DataManager.MapData pairedMap = DataManager.getMap(name);
        if (pairedMap != null) {
            ServerLevel serverWorld = context.getSource().getLevel();
            if (BoundsStickItem.playerPositions.containsKey(serverPlayerEntity.getScoreboardName())) {
                BlockPos[] setPositions = BoundsStickItem.playerPositions.get(serverPlayerEntity.getScoreboardName());
                if (setPositions[0] != null && setPositions[1] != null) {
                    ResetEntity resetEntity = ModEntities.RESET_ENTITY.get().spawn(serverWorld, setPositions[0], EntitySpawnReason.NATURAL);
                    resetEntity.setCorner1(setPositions[0]);
                    resetEntity.setCorner2(setPositions[1]);
                    resetEntity.setPairedMap(name);
                    resetEntity.setCheckIndex(check_index);
                    for (ServerPlayer worldPlayer : serverWorld.players()) {
                        PacketHandler.updateZone(worldPlayer, resetEntity.getId(), setPositions[0], setPositions[1], name, check_index);
                    }
                    BoundsStickItem.clearSelection(serverPlayerEntity);
                    Logger.logSuccess(serverPlayerEntity, "Creating reset zone from " + setPositions[0].toShortString() + " to " + setPositions[1].toShortString());
                } else {
                    Logger.logFailure(serverPlayerEntity, "You haven't set both corner positions.");
                }
            } else {
                Logger.logFailure(serverPlayerEntity, "You need to set positions first.");
            }
        }
        else {
            Logger.logFailure(serverPlayerEntity, "There is no map called " + name + ".");
        }
    }

    private static void handleAddReset(CommandContext<CommandSourceStack> context) {
        ServerPlayer serverPlayerEntity = context.getSource().getPlayer();
        String name = StringArgumentType.getString(context, "map_name");
        DataManager.MapData pairedMap = DataManager.getMap(name);
        if (pairedMap != null) {
            ServerLevel serverWorld = context.getSource().getLevel();
            if (BoundsStickItem.playerPositions.containsKey(serverPlayerEntity.getScoreboardName())) {
                BlockPos[] setPositions = BoundsStickItem.playerPositions.get(serverPlayerEntity.getScoreboardName());
                if (setPositions[0] != null && setPositions[1] != null) {
                    ResetEntity resetEntity = ModEntities.RESET_ENTITY.get().spawn(serverWorld, setPositions[0], EntitySpawnReason.NATURAL);
                    resetEntity.setCorner1(setPositions[0]);
                    resetEntity.setCorner2(setPositions[1]);
                    resetEntity.setPairedMap(name);
                    for (ServerPlayer worldPlayer : serverWorld.players()) {
                        PacketHandler.updateZone(worldPlayer, resetEntity.getId(), setPositions[0], setPositions[1], name, 0);
                    }
                    BoundsStickItem.clearSelection(serverPlayerEntity);
                    Logger.logSuccess(serverPlayerEntity, "Creating reset zone from " + setPositions[0].toShortString() + " to " + setPositions[1].toShortString());
                } else {
                    Logger.logFailure(serverPlayerEntity, "You haven't set both corner positions.");
                }
            } else {
                Logger.logFailure(serverPlayerEntity, "You need to set positions first.");
            }
        }
        else {
            Logger.logFailure(serverPlayerEntity, "There is no map called " + name + ".");
        }
    }

    private static void handleAddStart(CommandContext<CommandSourceStack> context) {
        ServerPlayer serverPlayerEntity = context.getSource().getPlayer();
        String name = StringArgumentType.getString(context, "map_name");
        DataManager.MapData pairedMap = DataManager.getMap(name);
        if (pairedMap != null) {
            ServerLevel serverWorld = context.getSource().getLevel();
            if (BoundsStickItem.playerPositions.containsKey(serverPlayerEntity.getScoreboardName())) {
                BlockPos[] setPositions = BoundsStickItem.playerPositions.get(serverPlayerEntity.getScoreboardName());
                if (setPositions[0] != null && setPositions[1] != null) {
                    StartEntity startEntity = ModEntities.START_ENTITY.get().spawn(serverWorld, setPositions[0], EntitySpawnReason.NATURAL);
                    startEntity.setCorner1(setPositions[0]);
                    startEntity.setCorner2(setPositions[1]);
                    startEntity.setPairedMap(name);
                    for (ServerPlayer worldPlayer : serverWorld.players()) {
                        PacketHandler.updateZone(worldPlayer, startEntity.getId(), setPositions[0], setPositions[1], name, 0);
                    }
                    BoundsStickItem.clearSelection(serverPlayerEntity);
                    Logger.logSuccess(serverPlayerEntity, "Creating start zone from " + setPositions[0].toShortString() + " to " + setPositions[1].toShortString());
                } else {
                    Logger.logFailure(serverPlayerEntity, "You haven't set both corner positions.");
                }
            } else {
                Logger.logFailure(serverPlayerEntity, "Not holding zone stick");
            }
        }
        else {
            Logger.logFailure(serverPlayerEntity, "There is no map called " + name + ".");
        }
    }

    private static void handleAddEnd(CommandContext<CommandSourceStack> context) {
        ServerPlayer serverPlayerEntity = context.getSource().getPlayer();
        String name = StringArgumentType.getString(context, "map_name");
        DataManager.MapData pairedMap = DataManager.getMap(name);
        if (pairedMap != null) {
            ServerLevel serverWorld = context.getSource().getLevel();
            if (BoundsStickItem.playerPositions.containsKey(serverPlayerEntity.getScoreboardName())) {
                BlockPos[] setPositions = BoundsStickItem.playerPositions.get(serverPlayerEntity.getScoreboardName());
                if (setPositions[0] != null && setPositions[1] != null) {
                    EndEntity endEntity = ModEntities.END_ENTITY.get().spawn(serverWorld, setPositions[0], EntitySpawnReason.NATURAL);
                    endEntity.setCorner1(setPositions[0]);
                    endEntity.setCorner2(setPositions[1]);
                    endEntity.setPairedMap(name);
                    for (ServerPlayer worldPlayer : serverWorld.players()) {
                        PacketHandler.updateZone(worldPlayer, endEntity.getId(), setPositions[0], setPositions[1], name, 0);
                    }
                    BoundsStickItem.clearSelection(serverPlayerEntity);
                    Logger.logSuccess(serverPlayerEntity, "Creating end zone from " + setPositions[0].toShortString() + " to " + setPositions[1].toShortString());
                } else {
                    Logger.logFailure(serverPlayerEntity, "You haven't set both corner positions.");
                }
            } else {
                Logger.logFailure(serverPlayerEntity, "You need to set positions first.");
            }
        }
        else {
            Logger.logFailure(serverPlayerEntity, "There is no map called " + name + ".");
        }
    }
}
