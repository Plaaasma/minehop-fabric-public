package net.nerdorg.minehop.commands;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.Command;
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

    private static void handleAddReset(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity serverPlayerEntity = context.getSource().getPlayer();
        String name = StringArgumentType.getString(context, "map_name");
        DataManager.MapData pairedMap = DataManager.getMap(name);
        if (pairedMap != null) {
            ServerWorld serverWorld = context.getSource().getWorld();
            ItemStack heldItemStack = serverPlayerEntity.getMainHandStack();
            if (heldItemStack.getItem() instanceof BoundsStickItem boundsStickItem) {
                if (boundsStickItem.pos1 != null && boundsStickItem.pos2 != null) {
                    ResetEntity resetEntity = ModEntities.RESET_ENTITY.spawn(serverWorld, boundsStickItem.pos1, SpawnReason.NATURAL);
                    resetEntity.setCorner1(boundsStickItem.pos1);
                    resetEntity.setCorner2(boundsStickItem.pos2);
                    resetEntity.setPairedMap(name);
                    for (ServerPlayerEntity worldPlayer : serverWorld.getPlayers()) {
                        PacketHandler.updateZone(worldPlayer, resetEntity.getId(), boundsStickItem.pos1, boundsStickItem.pos2, name);
                    }
                    Logger.logSuccess(serverPlayerEntity, "Creating reset zone from " + boundsStickItem.pos1.toShortString() + " to " + boundsStickItem.pos2.toShortString());
                } else {
                    Logger.logFailure(serverPlayerEntity, "You haven't set both corner positions.");
                }
            } else {
                Logger.logFailure(serverPlayerEntity, "Not holding bounds stick, fabric is fucking gay so you have to be holding one.");
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
            ItemStack heldItemStack = serverPlayerEntity.getMainHandStack();
            if (heldItemStack.getItem() instanceof BoundsStickItem boundsStickItem) {
                if (boundsStickItem.pos1 != null && boundsStickItem.pos2 != null) {
                    StartEntity startEntity = ModEntities.START_ENTITY.spawn(serverWorld, boundsStickItem.pos1, SpawnReason.NATURAL);
                    startEntity.setCorner1(boundsStickItem.pos1);
                    startEntity.setCorner2(boundsStickItem.pos2);
                    startEntity.setPairedMap(name);
                    for (ServerPlayerEntity worldPlayer : serverWorld.getPlayers()) {
                        PacketHandler.updateZone(worldPlayer, startEntity.getId(), boundsStickItem.pos1, boundsStickItem.pos2, name);
                    }
                    Logger.logSuccess(serverPlayerEntity, "Creating start zone from " + boundsStickItem.pos1.toShortString() + " to " + boundsStickItem.pos2.toShortString());
                } else {
                    Logger.logFailure(serverPlayerEntity, "You haven't set both corner positions.");
                }
            } else {
                Logger.logFailure(serverPlayerEntity, "Not holding bounds stick, fabric is fucking gay so you have to be holding one.");
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
            ItemStack heldItemStack = serverPlayerEntity.getMainHandStack();
            if (heldItemStack.getItem() instanceof BoundsStickItem boundsStickItem) {
                if (boundsStickItem.pos1 != null && boundsStickItem.pos2 != null) {
                    EndEntity endEntity = ModEntities.END_ENTITY.spawn(serverWorld, boundsStickItem.pos1, SpawnReason.NATURAL);
                    endEntity.setCorner1(boundsStickItem.pos1);
                    endEntity.setCorner2(boundsStickItem.pos2);
                    endEntity.setPairedMap(name);
                    for (ServerPlayerEntity worldPlayer : serverWorld.getPlayers()) {
                        PacketHandler.updateZone(worldPlayer, endEntity.getId(), boundsStickItem.pos1, boundsStickItem.pos2, name);
                    }
                    Logger.logSuccess(serverPlayerEntity, "Creating end zone from " + boundsStickItem.pos1.toShortString() + " to " + boundsStickItem.pos2.toShortString());
                } else {
                    Logger.logFailure(serverPlayerEntity, "You haven't set both corner positions.");
                }
            } else {
                Logger.logFailure(serverPlayerEntity, "Not holding bounds stick, fabric is fucking gay so you have to be holding one.");
            }
        }
        else {
            Logger.logFailure(serverPlayerEntity, "There is no map called " + name + ".");
        }
    }
}
