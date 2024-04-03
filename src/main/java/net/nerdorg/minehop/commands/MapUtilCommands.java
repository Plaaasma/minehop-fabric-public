package net.nerdorg.minehop.commands;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.entity.Entity;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.data.DataManager;
import net.nerdorg.minehop.entity.custom.EndEntity;
import net.nerdorg.minehop.entity.custom.ResetEntity;
import net.nerdorg.minehop.entity.custom.StartEntity;
import net.nerdorg.minehop.entity.custom.Zone;
import net.nerdorg.minehop.networking.PacketHandler;
import net.nerdorg.minehop.util.Logger;
import net.nerdorg.minehop.util.StringFormatting;

import java.util.*;
import java.util.stream.Collectors;

public class MapUtilCommands {
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
            LiteralArgumentBuilder.<ServerCommandSource>literal("map")
            .then(RequiredArgumentBuilder.<ServerCommandSource, String>argument("map_name", StringArgumentType.string())
                .executes(context -> {
                    handleTeleport(context);
                    return Command.SINGLE_SUCCESS;
                })
            )
            .then(LiteralArgumentBuilder.<ServerCommandSource>literal("restart")
                .executes(context -> {
                    handleRestart(context);
                    return Command.SINGLE_SUCCESS;
                })
            )
            .then(LiteralArgumentBuilder.<ServerCommandSource>literal("list")
                .executes(context -> {
                    handleList(context);
                    return Command.SINGLE_SUCCESS;
                })
            )
            .then(LiteralArgumentBuilder.<ServerCommandSource>literal("checkpoint")
                .then(LiteralArgumentBuilder.<ServerCommandSource>literal("add")
                    .then(RequiredArgumentBuilder.<ServerCommandSource, String>argument("map_name", StringArgumentType.string())
                        .executes(context -> {
                            handleAddCheckpoint(context);
                            return Command.SINGLE_SUCCESS;
                        })
                    )
                )
            )
            .then(LiteralArgumentBuilder.<ServerCommandSource>literal("top")
                .then(RequiredArgumentBuilder.<ServerCommandSource, String>argument("map_name", StringArgumentType.string())
                    .executes(context -> {
                        handleListTop(context);
                        return Command.SINGLE_SUCCESS;
                    })
                )
            )
            .then(LiteralArgumentBuilder.<ServerCommandSource>literal("add")
                .requires(source -> source.hasPermissionLevel(4))
                .then(RequiredArgumentBuilder.<ServerCommandSource, String>argument("add_name", StringArgumentType.string())
                    .executes(context -> {
                        handleAdd(context);
                        return Command.SINGLE_SUCCESS;
                    })
                )
            )
            .then(LiteralArgumentBuilder.<ServerCommandSource>literal("invalidate")
                .requires(source -> source.hasPermissionLevel(4))
                .then(RequiredArgumentBuilder.<ServerCommandSource, String>argument("map_name", StringArgumentType.string())
                    .executes(context -> {
                        handleInvalidate(context);
                        return Command.SINGLE_SUCCESS;
                    })
                )
            )
            .then(LiteralArgumentBuilder.<ServerCommandSource>literal("remove")
                .requires(source -> source.hasPermissionLevel(4))
                .then(RequiredArgumentBuilder.<ServerCommandSource, String>argument("remove_name", StringArgumentType.string())
                    .executes(context -> {
                        handleRemove(context);
                        return Command.SINGLE_SUCCESS;
                    })
                )
            )
            .then(LiteralArgumentBuilder.<ServerCommandSource>literal("info")
                .requires(source -> source.hasPermissionLevel(4))
                .then(RequiredArgumentBuilder.<ServerCommandSource, String>argument("search_name", StringArgumentType.string())
                    .executes(context -> {
                        handleInfo(context);
                        return Command.SINGLE_SUCCESS;
                    })
                )
            )
        ));
    }

    private static void handleAddCheckpoint(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity serverPlayerEntity = context.getSource().getPlayer();

        String name = StringArgumentType.getString(context, "map_name");

        DataManager.MapData mapToAddTo = null;

        for (Object object : Minehop.mapList) {
            if (object instanceof DataManager.MapData mapData) {
                if (mapData.name.equals(name)) {
                    mapToAddTo = mapData;
                    break;
                }
            }
        }

        if (mapToAddTo != null) {
            if (mapToAddTo.checkpointPositions == null) {
                mapToAddTo.checkpointPositions = new ArrayList<>();
            }
            Logger.logSuccess(serverPlayerEntity, "Added checkpoint " + (mapToAddTo.checkpointPositions.size() + 1) + " to " + name);
            Minehop.mapList.remove(mapToAddTo);
            mapToAddTo.checkpointPositions.add(new ArrayList<>(Arrays.asList(serverPlayerEntity.getPos(), new Vec3d(serverPlayerEntity.getRotationClient().x, serverPlayerEntity.getRotationClient().y, 0))));
            Minehop.mapList.add(mapToAddTo);
            DataManager.saveMapData(context.getSource().getWorld(), Minehop.mapList);
        }
        else {
            Logger.logFailure(serverPlayerEntity, "The map " + name + " does not exist.");
        }
    }

    private static void handleRestart(CommandContext<ServerCommandSource> context) {
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
        if (closestEntity == null) {
            Logger.logFailure(serverPlayerEntity, "Error finding nearest map.");
        }
        else {
            DataManager.MapData currentMapData = null;
            String mapName = "";

            if (closestEntity instanceof ResetEntity resetEntity) {
                mapName = resetEntity.getPairedMap();
            }
            else if (closestEntity instanceof StartEntity startEntity) {
                mapName = startEntity.getPairedMap();
            }
            else if (closestEntity instanceof EndEntity endEntity) {
                mapName = endEntity.getPairedMap();
            }
            for (Object object : Minehop.mapList) {
                if (object instanceof DataManager.MapData mapData) {
                    if (mapData.name.equals(mapName)) {
                        currentMapData = mapData;
                        break;
                    }
                }
            }
            if (currentMapData != null) {
                if (currentMapData.worldKey == null || currentMapData.worldKey.equals("")) {
                    Minehop.mapList.remove(currentMapData);
                    currentMapData.worldKey = context.getSource().getServer().getOverworld().getRegistryKey().toString();
                    Minehop.mapList.add(currentMapData);
                    DataManager.saveMapData(context.getSource().getWorld(), Minehop.mapList);
                }
                Minehop.timerManager.remove(serverPlayerEntity.getNameForScoreboard());
                ServerWorld foundWorld = null;
                for (ServerWorld svrWorld : context.getSource().getServer().getWorlds()) {
                    if (svrWorld.getRegistryKey().toString().equals(currentMapData.worldKey)) {
                        foundWorld = svrWorld;
                        break;
                    }
                }
                if (foundWorld != null) {
                    serverPlayerEntity.teleport(foundWorld, currentMapData.x, currentMapData.y, currentMapData.z, (float) currentMapData.yrot, (float) currentMapData.xrot);
                    if (SpectateCommands.spectatorList.containsKey(serverPlayerEntity.getNameForScoreboard())) {
                        List<String> spectators = SpectateCommands.spectatorList.get(serverPlayerEntity.getNameForScoreboard());
                        for (String spectator : spectators) {
                            if (!spectator.equals(serverPlayerEntity.getNameForScoreboard())) {
                                ServerPlayerEntity spectatorPlayer = context.getSource().getServer().getPlayerManager().getPlayer(spectator);
                                PacketHandler.sendSpectate(spectatorPlayer, serverPlayerEntity.getNameForScoreboard());
                            }
                        }
                    }
                }
            }
            else {
                Logger.logFailure(serverPlayerEntity, "Error finding nearest map.");
            }
        }
    }

    private static void handleTeleport(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity serverPlayerEntity = context.getSource().getPlayer();

        String name = StringArgumentType.getString(context, "map_name");

        DataManager.MapData tpData = null;

        for (Object object : Minehop.mapList) {
            if (object instanceof DataManager.MapData mapData) {
                if (mapData.name.equals(name)) {
                    tpData = mapData;
                    break;
                }
            }
        }

        if (tpData != null) {
            if (tpData.worldKey == null || tpData.worldKey.equals("")) {
                Minehop.mapList.remove(tpData);
                tpData.worldKey = context.getSource().getServer().getOverworld().getRegistryKey().toString();
                Minehop.mapList.add(tpData);
                DataManager.saveMapData(context.getSource().getWorld(), Minehop.mapList);
            }
            Logger.logSuccess(serverPlayerEntity, "Teleporting to " + name);
            ServerWorld foundWorld = null;
            for (ServerWorld serverWorld : context.getSource().getServer().getWorlds()) {
                if (serverWorld.getRegistryKey().toString().equals(tpData.worldKey)) {
                    foundWorld = serverWorld;
                    break;
                }
            }
            if (foundWorld != null) {
                serverPlayerEntity.teleport(foundWorld, tpData.x, tpData.y, tpData.z, (float) tpData.yrot, (float) tpData.xrot);
                if (SpectateCommands.spectatorList.containsKey(serverPlayerEntity.getNameForScoreboard())) {
                    List<String> spectators = SpectateCommands.spectatorList.get(serverPlayerEntity.getNameForScoreboard());
                    for (String spectator : spectators) {
                        if (!spectator.equals(serverPlayerEntity.getNameForScoreboard())) {
                            ServerPlayerEntity spectatorPlayer = context.getSource().getServer().getPlayerManager().getPlayer(spectator);
                            PacketHandler.sendSpectate(spectatorPlayer, serverPlayerEntity.getNameForScoreboard());
                        }
                    }
                }
            }
        }
        else {
            Logger.logFailure(serverPlayerEntity, "The map " + name + " does not exist.");
        }
    }

    private static void handleAdd(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity serverPlayerEntity = context.getSource().getPlayer();

        String name = StringArgumentType.getString(context, "add_name");
        double spawn_x = serverPlayerEntity.getX();
        double spawn_y = serverPlayerEntity.getY();
        double spawn_z = serverPlayerEntity.getZ();
        double spawn_xrot = serverPlayerEntity.getPitch();
        double spawn_yrot = serverPlayerEntity.getYaw();

        DataManager.MapData mapData = new DataManager.MapData(name, spawn_x, spawn_y, spawn_z, spawn_xrot, spawn_yrot, serverPlayerEntity.getWorld().getRegistryKey().toString());
        Minehop.mapList.add(mapData);
        DataManager.saveMapData(context.getSource().getWorld(), Minehop.mapList);

        Logger.logSuccess(serverPlayerEntity, "Created map \\/\n" + StringFormatting.limitDecimals(gson.toJson(mapData)));


    }

    private static void handleInvalidate(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity serverPlayerEntity = context.getSource().getPlayer();

        String name = StringArgumentType.getString(context, "map_name");

        DataManager.MapData invalidateData = null;

        for (Object object : Minehop.mapList) {
            if (object instanceof DataManager.MapData mapData) {
                if (mapData.name.equals(name)) {
                    invalidateData = mapData;
                    break;
                }
            }
        }

        Minehop.personalRecordList = new ArrayList<>();
        DataManager.savePersonalRecordData(context.getSource().getWorld(), new ArrayList<>());

        Minehop.recordList = new ArrayList<>();
        DataManager.saveRecordData(context.getSource().getWorld(), new ArrayList<>());

        if (invalidateData != null) {
            Logger.logSuccess(serverPlayerEntity, "Invalidated times for map \\/\n" + StringFormatting.limitDecimals(gson.toJson(invalidateData)));
        }
        else {
            Logger.logFailure(serverPlayerEntity, "The map " + name + " does not exist.");
        }
    }

    private static void handleRemove(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity serverPlayerEntity = context.getSource().getPlayer();

        String name = StringArgumentType.getString(context, "remove_name");

        DataManager.MapData removedData = null;

        for (Object object : Minehop.mapList) {
            if (object instanceof DataManager.MapData mapData) {
                if (mapData.name.equals(name)) {
                    removedData = mapData;
                    Minehop.mapList.remove(mapData);
                    DataManager.saveMapData(context.getSource().getWorld(), Minehop.mapList);
                    break;
                }
            }
        }

        if (removedData != null) {
            Logger.logSuccess(serverPlayerEntity, "Removed map \\/\n" + StringFormatting.limitDecimals(gson.toJson(removedData)));
        }
        else {
            Logger.logFailure(serverPlayerEntity, "The map " + name + " does not exist.");
        }
    }

    private static void handleListTop(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity serverPlayerEntity = context.getSource().getPlayer();
        String name = StringArgumentType.getString(context, "map_name");

        List<DataManager.RecordData> recordDataList = new ArrayList<>();
        for (DataManager.RecordData recordData : Minehop.personalRecordList) {
            if (recordData.map_name.equals(name)) {
                recordDataList.add(recordData);
            }
        }

        HashMap<String, Double> formattedMap = new HashMap<>();
        for (DataManager.RecordData recordData : recordDataList) {
            formattedMap.put(recordData.name, recordData.time);
        }

        LinkedHashMap<String, Double> sortedRecordDataList = formattedMap.entrySet()
                .stream()
                .sorted(HashMap.Entry.<String, Double>comparingByValue())
                .collect(Collectors.toMap(
                        HashMap.Entry::getKey,
                        HashMap.Entry::getValue,
                        (oldValue, newValue) -> oldValue, LinkedHashMap::new));

        Logger.logSuccess(serverPlayerEntity, "Top Map Times for " + name + " \\/\n" + StringFormatting.limitDecimals(gson.toJson(sortedRecordDataList)));
    }

    private static void handleList(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity serverPlayerEntity = context.getSource().getPlayer();

        List<String> mapNameList = new ArrayList<>();

        for (Object object : Minehop.mapList) {
            if (object instanceof DataManager.MapData mapData) {
                if (!mapData.name.equals("spawn")) {
                    mapNameList.add(mapData.name);
                }
            }
        }

        Logger.logSuccess(serverPlayerEntity, "Map Names \\/\n" + StringFormatting.limitDecimals(gson.toJson(mapNameList)) + "\nUse /map \"map_name\" in order to teleport.");
    }

    private static void handleInfo(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity serverPlayerEntity = context.getSource().getPlayer();

        String name = StringArgumentType.getString(context, "search_name");

        DataManager.MapData foundData = null;

        for (Object object : Minehop.mapList) {
            if (object instanceof DataManager.MapData mapData) {
                if (mapData.name.equals(name)) {
                    foundData = mapData;
                    break;
                }
            }
        }

        if (foundData != null) {
            Logger.logSuccess(serverPlayerEntity, "Map Info \\/\n" + StringFormatting.limitDecimals(gson.toJson(foundData)));
        }
        else {
            Logger.logFailure(serverPlayerEntity, "The map " + name + " does not exist.");
        }
    }
}
