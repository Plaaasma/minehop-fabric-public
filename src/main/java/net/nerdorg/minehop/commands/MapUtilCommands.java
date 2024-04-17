package net.nerdorg.minehop.commands;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.EntitySelector;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
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
import net.nerdorg.minehop.item.ModItems;
import net.nerdorg.minehop.networking.PacketHandler;
import net.nerdorg.minehop.util.Logger;
import net.nerdorg.minehop.util.StringFormatting;
import net.nerdorg.minehop.util.ZoneUtil;

import javax.xml.crypto.Data;
import java.util.*;
import java.util.stream.Collectors;

public class MapUtilCommands {
    private static Random random = new Random();
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
            .then(LiteralArgumentBuilder.<ServerCommandSource>literal("top")
                .then(RequiredArgumentBuilder.<ServerCommandSource, String>argument("map_name", StringArgumentType.string())
                    .executes(context -> {
                        handleListTop(context);
                        return Command.SINGLE_SUCCESS;
                    })
                )
            )
            .then(LiteralArgumentBuilder.<ServerCommandSource>literal("manage")
            .requires(source -> source.hasPermissionLevel(4))
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
                .then(LiteralArgumentBuilder.<ServerCommandSource>literal("add")
                    .then(RequiredArgumentBuilder.<ServerCommandSource, String>argument("add_name", StringArgumentType.string())
                        .executes(context -> {
                            handleAdd(context);
                            return Command.SINGLE_SUCCESS;
                        })
                    )
                )
                .then(LiteralArgumentBuilder.<ServerCommandSource>literal("invalidate")
                    .then(RequiredArgumentBuilder.<ServerCommandSource, String>argument("map_name", StringArgumentType.string())
                        .executes(context -> {
                            handleInvalidate(context);
                            return Command.SINGLE_SUCCESS;
                        })
                    )
                )
                .then(LiteralArgumentBuilder.<ServerCommandSource>literal("invalidate_player")
                    .then(RequiredArgumentBuilder.<ServerCommandSource, String>argument("map_name", StringArgumentType.string())
                        .then(RequiredArgumentBuilder.<ServerCommandSource, String>argument("player_name", StringArgumentType.string())
                            .executes(context -> {
                                handleInvalidatePlayer(context);
                                return Command.SINGLE_SUCCESS;
                            })
                        )
                    )
                )
                .then(LiteralArgumentBuilder.<ServerCommandSource>literal("remove")
                    .then(RequiredArgumentBuilder.<ServerCommandSource, String>argument("remove_name", StringArgumentType.string())
                        .executes(context -> {
                            handleRemove(context);
                            return Command.SINGLE_SUCCESS;
                        })
                    )
                )
                .then(LiteralArgumentBuilder.<ServerCommandSource>literal("setspawn")
                        .then(RequiredArgumentBuilder.<ServerCommandSource, String>argument("map_name", StringArgumentType.string())
                                .executes(context -> {
                                    handleSetMapSpawn(context);
                                    return Command.SINGLE_SUCCESS;
                                })
                        )
                )
                .then(LiteralArgumentBuilder.<ServerCommandSource>literal("arena")
                        .then(RequiredArgumentBuilder.<ServerCommandSource, String>argument("map_name", StringArgumentType.string())
                                .executes(context -> {
                                    handleToggleArena(context);
                                    return Command.SINGLE_SUCCESS;
                                })
                        )
                )
                .then(LiteralArgumentBuilder.<ServerCommandSource>literal("hns")
                        .then(RequiredArgumentBuilder.<ServerCommandSource, String>argument("map_name", StringArgumentType.string())
                                .executes(context -> {
                                    handleToggleHNS(context);
                                    return Command.SINGLE_SUCCESS;
                                })
                        )
                )
                .then(LiteralArgumentBuilder.<ServerCommandSource>literal("info")
                    .then(RequiredArgumentBuilder.<ServerCommandSource, String>argument("search_name", StringArgumentType.string())
                        .executes(context -> {
                            handleInfo(context);
                            return Command.SINGLE_SUCCESS;
                        })
                    )
                )
            )
            .executes(context -> {
                handleOpenMapScreen(context);
                return Command.SINGLE_SUCCESS;
            })
        ));
    }

    private static void handleOpenMapScreen(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity serverPlayerEntity = context.getSource().getPlayer();
        PacketHandler.sendOpenMapScreen(serverPlayerEntity, "Balls");
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
            DataManager.saveData(context.getSource().getWorld(), DataManager.mapListLocation, Minehop.mapList);
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
                    DataManager.saveData(context.getSource().getWorld(), DataManager.mapListLocation, Minehop.mapList);
                }
                Minehop.timerManager.remove(serverPlayerEntity.getEntityName());
                ServerWorld foundWorld = null;
                for (ServerWorld svrWorld : context.getSource().getServer().getWorlds()) {
                    if (svrWorld.getRegistryKey().toString().equals(currentMapData.worldKey)) {
                        foundWorld = svrWorld;
                        break;
                    }
                }
                if (foundWorld != null) {
                    if (!serverPlayerEntity.isSpectator()) {
                        if (!serverPlayerEntity.isCreative()) {
                            serverPlayerEntity.getInventory().clear();
                        }
                        serverPlayerEntity.teleport(foundWorld, currentMapData.x, currentMapData.y, currentMapData.z, (float) currentMapData.yrot, (float) currentMapData.xrot);
                        if (SpectateCommands.spectatorList.containsKey(serverPlayerEntity.getEntityName())) {
                            List<String> spectators = SpectateCommands.spectatorList.get(serverPlayerEntity.getEntityName());
                            for (String spectator : spectators) {
                                if (!spectator.equals(serverPlayerEntity.getEntityName())) {
                                    ServerPlayerEntity spectatorPlayer = context.getSource().getServer().getPlayerManager().getPlayer(spectator);
                                    if (!spectatorPlayer.isCreative()) {
                                        spectatorPlayer.getInventory().clear();
                                    }
                                    spectatorPlayer.teleport(serverPlayerEntity.getX(), serverPlayerEntity.getY(), serverPlayerEntity.getZ());
                                    spectatorPlayer.setCameraEntity(serverPlayerEntity);
                                }
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
                DataManager.saveData(context.getSource().getWorld(), DataManager.mapListLocation, Minehop.mapList);
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
                Vec3d targetPos = new Vec3d(tpData.x, tpData.y, tpData.z);
                Vec3d rotPos = new Vec3d(tpData.xrot, tpData.yrot, 0);
                if (tpData.arena) {
                    List<Vec3d> spawnCheck = new ArrayList<>();
                    spawnCheck.add(new Vec3d(tpData.x, tpData.y, tpData.z));
                    spawnCheck.add(new Vec3d(tpData.xrot, tpData.yrot, 0));

                    List<List<Vec3d>> checkpointPositions = new ArrayList<>();
                    if (tpData.checkpointPositions != null) {
                        checkpointPositions.addAll(tpData.checkpointPositions);
                    }
                    checkpointPositions.add(spawnCheck);

                    List<Vec3d> randomCheckpoint = checkpointPositions.get(random.nextInt(0, checkpointPositions.size()));
                    targetPos = randomCheckpoint.get(0);
                    rotPos = randomCheckpoint.get(1);
                }

                if (!serverPlayerEntity.isSpectator()) {
                    if (!serverPlayerEntity.isCreative()) {
                        serverPlayerEntity.getInventory().clear();
                    }
                    serverPlayerEntity.teleport(foundWorld, targetPos.getX(), targetPos.getY(), targetPos.getZ(), (float) rotPos.getY(), (float) rotPos.getX());
                    if (SpectateCommands.spectatorList.containsKey(serverPlayerEntity.getEntityName())) {
                        List<String> spectators = SpectateCommands.spectatorList.get(serverPlayerEntity.getEntityName());
                        for (String spectator : spectators) {
                            if (!spectator.equals(serverPlayerEntity.getEntityName())) {
                                ServerPlayerEntity spectatorPlayer = context.getSource().getServer().getPlayerManager().getPlayer(spectator);
                                if (!spectatorPlayer.isCreative()) {
                                    spectatorPlayer.getInventory().clear();
                                }
                                spectatorPlayer.teleport(serverPlayerEntity.getX(), serverPlayerEntity.getY(), serverPlayerEntity.getZ());
                                spectatorPlayer.setCameraEntity(serverPlayerEntity);
                            }
                        }
                    }
                    if (tpData.arena) {
                        for (int slotNum = 1; slotNum < serverPlayerEntity.getInventory().size(); slotNum++) {
                            serverPlayerEntity.getInventory().setStack(slotNum, new ItemStack(Items.AIR));
                        }
                        serverPlayerEntity.getInventory().setStack(0, new ItemStack(ModItems.INSTAGIB_GUN));
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
        DataManager.saveData(context.getSource().getWorld(), DataManager.mapListLocation, Minehop.mapList);

        Logger.logSuccess(serverPlayerEntity, "Created map \\/\n" + StringFormatting.limitDecimals(gson.toJson(mapData)));


    }

    private static void handleInvalidatePlayer(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity senderEntity = context.getSource().getPlayer();
        String playerName = StringArgumentType.getString(context, "player_name");

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

        if (invalidateData != null) {
            if (DataManager.getPersonalRecord(playerName, invalidateData.name) != null) {
                DataManager.removePersonalRecordsForPlayer(name, playerName);
                DataManager.saveData(context.getSource().getWorld(), DataManager.pbListLocation, Minehop.personalRecordList);

                DataManager.removeRecordsForPlayer(name, playerName);
                DataManager.saveData(context.getSource().getWorld(), DataManager.recordsListLocation, Minehop.recordList);
                Logger.logSuccess(senderEntity, "Invalidated times for player " + playerName + " on map \\/\n" + StringFormatting.limitDecimals(gson.toJson(invalidateData)));
            }
            else {
                Logger.logFailure(senderEntity, playerName + " does not have a time on " + invalidateData.name);
            }
        }
        else {
            Logger.logFailure(senderEntity, "The map " + name + " does not exist.");
        }
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

        DataManager.removePersonalRecords(name);
        DataManager.saveData(context.getSource().getWorld(), DataManager.pbListLocation, Minehop.personalRecordList);

        DataManager.removeRecords(name);
        DataManager.saveData(context.getSource().getWorld(), DataManager.recordsListLocation, Minehop.recordList);

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
                    DataManager.saveData(context.getSource().getWorld(), DataManager.mapListLocation, Minehop.mapList);
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

    private static void handleSetMapSpawn(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity serverPlayerEntity = context.getSource().getPlayer();

        String name = StringArgumentType.getString(context, "map_name");

        double spawn_x = serverPlayerEntity.getX();
        double spawn_y = serverPlayerEntity.getY();
        double spawn_z = serverPlayerEntity.getZ();
        double spawn_xrot = serverPlayerEntity.getPitch();
        double spawn_yrot = serverPlayerEntity.getYaw();

        DataManager.MapData spawnData = null;

        for (Object object : Minehop.mapList) {
            if (object instanceof DataManager.MapData mapData) {
                if (mapData.name.equals(name)) {
                    spawnData = mapData;
                    Minehop.mapList.remove(mapData);
                    break;
                }
            }
        }

        if (spawnData != null) {
            spawnData.x = spawn_x;
            spawnData.y = spawn_y;
            spawnData.z = spawn_z;
            spawnData.xrot = spawn_xrot;
            spawnData.yrot = spawn_yrot;
            Minehop.mapList.add(spawnData);
            DataManager.saveData(context.getSource().getWorld(), DataManager.mapListLocation, Minehop.mapList);

            Logger.logSuccess(serverPlayerEntity, "Set map spawn \\/\n" + StringFormatting.limitDecimals(gson.toJson(spawnData)));
        }
        else {
            Logger.logSuccess(serverPlayerEntity, "There is no map called " + name + ".");
        }
    }

    private static void handleToggleArena(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity serverPlayerEntity = context.getSource().getPlayer();

        String name = StringArgumentType.getString(context, "map_name");

        DataManager.MapData toggleData = null;

        for (Object object : Minehop.mapList) {
            if (object instanceof DataManager.MapData mapData) {
                if (mapData.name.equals(name)) {
                    toggleData = mapData;
                    Minehop.mapList.remove(mapData);
                    break;
                }
            }
        }

        if (toggleData != null) {
            toggleData.arena = !toggleData.arena;
            Minehop.mapList.add(toggleData);
            DataManager.saveData(context.getSource().getWorld(), DataManager.mapListLocation, Minehop.mapList);

            Logger.logSuccess(serverPlayerEntity, "Toggled arena mode to " + toggleData.arena);
        }
        else {
            Logger.logSuccess(serverPlayerEntity, "There is no map called " + name + ".");
        }
    }

    private static void handleToggleHNS(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity serverPlayerEntity = context.getSource().getPlayer();

        String name = StringArgumentType.getString(context, "map_name");

        DataManager.MapData toggleData = null;

        for (Object object : Minehop.mapList) {
            if (object instanceof DataManager.MapData mapData) {
                if (mapData.name.equals(name)) {
                    toggleData = mapData;
                    Minehop.mapList.remove(mapData);
                    break;
                }
            }
        }

        if (toggleData != null) {
            toggleData.hns = !toggleData.hns;
            Minehop.mapList.add(toggleData);
            DataManager.saveData(context.getSource().getWorld(), DataManager.mapListLocation, Minehop.mapList);

            Logger.logSuccess(serverPlayerEntity, "Toggled hns mode to " + toggleData.hns);
        }
        else {
            Logger.logSuccess(serverPlayerEntity, "There is no map called " + name + ".");
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
