package net.nerdorg.minehop.commands;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.ai.TargetPredicate;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
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
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                LiteralArgumentBuilder.<ServerCommandSource>literal("zone")
                        .requires(source -> source.hasPermissionLevel(4))
                        .then(LiteralArgumentBuilder.<ServerCommandSource>literal("kill")
                                .executes(context -> {
                                    handleKill(context);
                                    return Command.SINGLE_SUCCESS;
                                })
                        )
                        .then(LiteralArgumentBuilder.<ServerCommandSource>literal("add")
                                .then(LiteralArgumentBuilder.<ServerCommandSource>literal("reset")
                                        .then(RequiredArgumentBuilder.<ServerCommandSource, String>argument("map_name", StringArgumentType.string())
                                                .then(RequiredArgumentBuilder.<ServerCommandSource, Integer>argument("check_index", IntegerArgumentType.integer())
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
                                .then(LiteralArgumentBuilder.<ServerCommandSource>literal("start")
                                        .then(RequiredArgumentBuilder.<ServerCommandSource, String>argument("map_name", StringArgumentType.string())
                                                .executes(context -> {
                                                    handleAddStart(context);
                                                    return Command.SINGLE_SUCCESS;
                                                })
                                        )
                                )
                                .then(LiteralArgumentBuilder.<ServerCommandSource>literal("end")
                                        .then(RequiredArgumentBuilder.<ServerCommandSource, String>argument("map_name", StringArgumentType.string())
                                                .executes(context -> {
                                                    handleAddEnd(context);
                                                    return Command.SINGLE_SUCCESS;
                                                })
                                        )
                                )
                        )
        ));
    }

    private static void handleKill(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity serverPlayerEntity = context.getSource().getPlayer();
        ServerWorld serverWorld = serverPlayerEntity.getServerWorld();
        List<Zone> zoneEntities = new ArrayList<>();
        for (Entity entity : serverWorld.iterateEntities()) {
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
            closestEntity.kill();
            Logger.logSuccess(serverPlayerEntity, "Killed nearest zone entity.");
        }
        else {
            Logger.logFailure(serverPlayerEntity, "Couldn't find zone entity within 10 blocks.");
        }
    }

    private static void handleAddResetCustom(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity serverPlayerEntity = context.getSource().getPlayer();
        String name = StringArgumentType.getString(context, "map_name");
        int check_index = IntegerArgumentType.getInteger(context, "check_index");
        DataManager.MapData pairedMap = DataManager.getMap(name);
        if (pairedMap != null) {
            ServerWorld serverWorld = context.getSource().getWorld();
            if (BoundsStickItem.playerPositions.containsKey(serverPlayerEntity.getNameForScoreboard())) {
                BlockPos[] setPositions = BoundsStickItem.playerPositions.get(serverPlayerEntity.getNameForScoreboard());
                if (setPositions[0] != null && setPositions[1] != null) {
                    ResetEntity resetEntity = ModEntities.RESET_ENTITY.spawn(serverWorld, setPositions[0], SpawnReason.NATURAL);
                    resetEntity.setCorner1(setPositions[0]);
                    resetEntity.setCorner2(setPositions[1]);
                    resetEntity.setPairedMap(name);
                    resetEntity.setCheckIndex(check_index);
                    for (ServerPlayerEntity worldPlayer : serverWorld.getPlayers()) {
                        PacketHandler.updateZone(worldPlayer, resetEntity.getId(), setPositions[0], setPositions[1], name, check_index);
                    }
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

    private static void handleAddReset(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity serverPlayerEntity = context.getSource().getPlayer();
        String name = StringArgumentType.getString(context, "map_name");
        DataManager.MapData pairedMap = DataManager.getMap(name);
        if (pairedMap != null) {
            ServerWorld serverWorld = context.getSource().getWorld();
            if (BoundsStickItem.playerPositions.containsKey(serverPlayerEntity.getNameForScoreboard())) {
                BlockPos[] setPositions = BoundsStickItem.playerPositions.get(serverPlayerEntity.getNameForScoreboard());
                if (setPositions[0] != null && setPositions[1] != null) {
                    ResetEntity resetEntity = ModEntities.RESET_ENTITY.spawn(serverWorld, setPositions[0], SpawnReason.NATURAL);
                    resetEntity.setCorner1(setPositions[0]);
                    resetEntity.setCorner2(setPositions[1]);
                    resetEntity.setPairedMap(name);
                    for (ServerPlayerEntity worldPlayer : serverWorld.getPlayers()) {
                        PacketHandler.updateZone(worldPlayer, resetEntity.getId(), setPositions[0], setPositions[1], name, 0);
                    }
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

    private static void handleAddStart(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity serverPlayerEntity = context.getSource().getPlayer();
        String name = StringArgumentType.getString(context, "map_name");
        DataManager.MapData pairedMap = DataManager.getMap(name);
        if (pairedMap != null) {
            ServerWorld serverWorld = context.getSource().getWorld();
            if (BoundsStickItem.playerPositions.containsKey(serverPlayerEntity.getNameForScoreboard())) {
                BlockPos[] setPositions = BoundsStickItem.playerPositions.get(serverPlayerEntity.getNameForScoreboard());
                if (setPositions[0] != null && setPositions[1] != null) {
                    StartEntity startEntity = ModEntities.START_ENTITY.spawn(serverWorld, setPositions[0], SpawnReason.NATURAL);
                    startEntity.setCorner1(setPositions[0]);
                    startEntity.setCorner2(setPositions[1]);
                    startEntity.setPairedMap(name);
                    for (ServerPlayerEntity worldPlayer : serverWorld.getPlayers()) {
                        PacketHandler.updateZone(worldPlayer, startEntity.getId(), setPositions[0], setPositions[1], name, 0);
                    }
                    Logger.logSuccess(serverPlayerEntity, "Creating start zone from " + setPositions[0].toShortString() + " to " + setPositions[1].toShortString());
                } else {
                    Logger.logFailure(serverPlayerEntity, "You haven't set both corner positions.");
                }
            } else {
                Logger.logFailure(serverPlayerEntity, "Not holding bounds stick");
            }
        }
        else {
            Logger.logFailure(serverPlayerEntity, "There is no map called " + name + ".");
        }
    }

    private static void handleAddEnd(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity serverPlayerEntity = context.getSource().getPlayer();
        String name = StringArgumentType.getString(context, "map_name");
        DataManager.MapData pairedMap = DataManager.getMap(name);
        if (pairedMap != null) {
            ServerWorld serverWorld = context.getSource().getWorld();
            if (BoundsStickItem.playerPositions.containsKey(serverPlayerEntity.getNameForScoreboard())) {
                BlockPos[] setPositions = BoundsStickItem.playerPositions.get(serverPlayerEntity.getNameForScoreboard());
                if (setPositions[0] != null && setPositions[1] != null) {
                    EndEntity endEntity = ModEntities.END_ENTITY.spawn(serverWorld, setPositions[0], SpawnReason.NATURAL);
                    endEntity.setCorner1(setPositions[0]);
                    endEntity.setCorner2(setPositions[1]);
                    endEntity.setPairedMap(name);
                    for (ServerPlayerEntity worldPlayer : serverWorld.getPlayers()) {
                        PacketHandler.updateZone(worldPlayer, endEntity.getId(), setPositions[0], setPositions[1], name, 0);
                    }
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
