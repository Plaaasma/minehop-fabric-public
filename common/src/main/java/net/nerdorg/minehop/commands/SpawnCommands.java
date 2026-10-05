package net.nerdorg.minehop.commands;

import net.nerdorg.minehop.util.PermissionUtil;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.nerdorg.minehop.platform.Services;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.data.DataManager;
import net.nerdorg.minehop.entity.ModEntities;
import net.nerdorg.minehop.entity.custom.EndEntity;
import net.nerdorg.minehop.entity.custom.ResetEntity;
import net.nerdorg.minehop.entity.custom.StartEntity;
import net.nerdorg.minehop.item.custom.BoundsStickItem;
import net.nerdorg.minehop.networking.PacketHandler;
import net.nerdorg.minehop.util.Logger;
import net.nerdorg.minehop.util.UserPlotManager;
import net.nerdorg.minehop.util.ZoneUtil;

import java.util.List;

public class SpawnCommands {
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public static void register() {
        Services.EVENTS.onRegisterCommands((dispatcher, registryAccess, environment) -> dispatcher.register(
            LiteralArgumentBuilder.<CommandSourceStack>literal("spawn")
                .executes(context -> {
                    handleSpawn(context);
                    return Command.SINGLE_SUCCESS;
                })
            ));

        Services.EVENTS.onRegisterCommands((dispatcher, registryAccess, environment) -> dispatcher.register(
                LiteralArgumentBuilder.<CommandSourceStack>literal("delspawn").requires(source -> PermissionUtil.hasLevel(source, 4))
                        .executes(context -> {
                            removeSpawn(context);
                            return Command.SINGLE_SUCCESS;
                        })
        ));
    }

    public static void handleSpawn(CommandContext<CommandSourceStack> context) {
        ServerPlayer serverPlayerEntity = context.getSource().getPlayer();
        if (serverPlayerEntity == null) {
            return;
        }
        String name = "spawn";
        DataManager.MapData pairedMap = DataManager.getMap(name);
        if (pairedMap != null) {
            if (!serverPlayerEntity.isSpectator()) {
                UserPlotManager.consumeForcedCreativeState(serverPlayerEntity);
                if (!serverPlayerEntity.isCreative()) {
                    serverPlayerEntity.getInventory().clearContent();
                }
                ServerLevel foundWorld = null;
                if (pairedMap.worldKey != null && !pairedMap.worldKey.isBlank()) {
                    for (ServerLevel svrWorld : context.getSource().getServer().getAllLevels()) {
                        if (svrWorld.dimension().toString().equals(pairedMap.worldKey)) {
                            foundWorld = svrWorld;
                            break;
                        }
                    }
                }
                if (foundWorld == null) {
                    foundWorld = context.getSource().getServer().overworld();
                }
                if (foundWorld != null) {
                    serverPlayerEntity.teleport(ZoneUtil.makeTeleportTarget(
                            foundWorld,
                            new Vec3(pairedMap.x, pairedMap.y, pairedMap.z),
                            (float) pairedMap.yrot,
                            (float) pairedMap.xrot
                    ));
                    Minehop.timerManager.remove(serverPlayerEntity.getScoreboardName());
                    Logger.logSuccess(serverPlayerEntity, "Teleporting to spawn.");
                    if (SpectateCommands.spectatorList.containsKey(serverPlayerEntity.getScoreboardName())) {
                        List<String> spectators = SpectateCommands.spectatorList.get(serverPlayerEntity.getScoreboardName());
                        for (String spectator : spectators) {
                            ServerPlayer spectatorPlayer = context.getSource().getServer().getPlayerList().getPlayerByName(spectator);
                            if (spectatorPlayer == null) {
                                continue;
                            }
                            UserPlotManager.consumeForcedCreativeState(spectatorPlayer);
                            if (!spectatorPlayer.isCreative()) {
                                spectatorPlayer.getInventory().clearContent();
                            }
                            spectatorPlayer.teleport(ZoneUtil.makeTeleportTarget(serverPlayerEntity.level(), new Vec3(serverPlayerEntity.getX(), serverPlayerEntity.getY(), serverPlayerEntity.getZ()), serverPlayerEntity.getYRot(), serverPlayerEntity.getXRot()));
                            spectatorPlayer.setCamera(serverPlayerEntity);
                        }
                    }
                }
            }
        }
        else {
            Logger.logFailure(serverPlayerEntity, "Spawn hasn't been set.");
        }
    }

    private static void removeSpawn(CommandContext<CommandSourceStack> context) {
        ServerPlayer serverPlayerEntity = context.getSource().getPlayer();
        String name = "spawn";
        DataManager.MapData pairedMap = DataManager.getMap(name);
        if (pairedMap != null) {
            Minehop.mapList.removeIf(data -> data != null && name.equals(data.name));
            ServerLevel world = context.getSource().getLevel();
            if (world != null) {
                DataManager.saveData(world, DataManager.mapListLocation, Minehop.mapList);
            }
            Logger.logSuccess(serverPlayerEntity, "Deleting spawn.");
        }
        else {
            Logger.logFailure(serverPlayerEntity, "Spawn hasn't been set.");
        }
    }
}
