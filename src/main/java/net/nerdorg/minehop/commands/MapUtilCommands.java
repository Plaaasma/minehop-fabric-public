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
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.EntitySelector;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.anticheat.AntiCheatManager;
import net.nerdorg.minehop.config.ConfigWrapper;
import net.nerdorg.minehop.data.DataManager;
import net.nerdorg.minehop.entity.ModEntities;
import net.nerdorg.minehop.entity.custom.*;
import net.nerdorg.minehop.item.ModItems;
import net.nerdorg.minehop.networking.PacketHandler;
import net.nerdorg.minehop.replays.ReplayManager;
import net.nerdorg.minehop.util.DescriptionCensor;
import net.nerdorg.minehop.util.Logger;
import net.nerdorg.minehop.util.MapCreationManager;
import net.nerdorg.minehop.util.StringFormatting;
import net.nerdorg.minehop.util.UserPlotManager;
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
            .then(LiteralArgumentBuilder.<ServerCommandSource>literal("edit")
                .then(RequiredArgumentBuilder.<ServerCommandSource, String>argument("map_name", StringArgumentType.string())
                    .suggests((context, builder) -> {
                        for (DataManager.MapData mapData : Minehop.mapList) {
                            if (mapData != null && mapData.name != null) {
                                builder.suggest(mapData.name, new LiteralMessage(mapData.name));
                            }
                        }
                        return builder.buildFuture();
                    })
                    .executes(context -> {
                        handleEditMap(context);
                        return Command.SINGLE_SUCCESS;
                    })
                )
            )
            .then(LiteralArgumentBuilder.<ServerCommandSource>literal("rate")
                .then(RequiredArgumentBuilder.<ServerCommandSource, String>argument("map_name", StringArgumentType.string())
                    .suggests((context, builder) -> {
                        for (DataManager.MapData mapData : Minehop.mapList) {
                            if (mapData != null && mapData.userMap && mapData.name != null) {
                                builder.suggest(mapData.name, new LiteralMessage(mapData.name));
                            }
                        }
                        return builder.buildFuture();
                    })
                    .then(RequiredArgumentBuilder.<ServerCommandSource, Integer>argument("good_rating", IntegerArgumentType.integer(1, 5))
                        .then(RequiredArgumentBuilder.<ServerCommandSource, Integer>argument("difficulty_rating", IntegerArgumentType.integer(1, 5))
                            .executes(context -> {
                                handleRateMap(context);
                                return Command.SINGLE_SUCCESS;
                            })
                        )
                    )
                )
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
                    .suggests((context, builder) -> {
                        for (DataManager.MapData mapData : Minehop.mapList) {
                            builder.suggest(mapData.name, new LiteralMessage(mapData.name));
                        }
                        return builder.buildFuture();
                    })
                    .executes(context -> {
                        handleListTop(context);
                        return Command.SINGLE_SUCCESS;
                    })
                )
            )
            .then(LiteralArgumentBuilder.<ServerCommandSource>literal("plotmanage")
                .executes(context -> {
                    handleOpenMapManageScreen(context);
                    return Command.SINGLE_SUCCESS;
                })
            )
            .then(LiteralArgumentBuilder.<ServerCommandSource>literal("manage")
            .requires(source -> source.hasPermissionLevel(4))
                .executes(context -> {
                    handleOpenMapManageScreen(context);
                    return Command.SINGLE_SUCCESS;
                })
                .then(LiteralArgumentBuilder.<ServerCommandSource>literal("checkpoint")
                    .then(LiteralArgumentBuilder.<ServerCommandSource>literal("add")
                        .then(RequiredArgumentBuilder.<ServerCommandSource, String>argument("map_name", StringArgumentType.string())
                            .suggests((context, builder) -> {
                                for (DataManager.MapData mapData : Minehop.mapList) {
                                    builder.suggest(mapData.name, new LiteralMessage(mapData.name));
                                }
                                return builder.buildFuture();
                            })
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
                    .then(LiteralArgumentBuilder.<ServerCommandSource>literal("times")
                        .then(LiteralArgumentBuilder.<ServerCommandSource>literal("player")
                            .then(RequiredArgumentBuilder.<ServerCommandSource, String>argument("player_name", StringArgumentType.string())
                                .suggests((context, builder) -> {
                                    suggestKnownPlayerNames(context.getSource(), builder);
                                    return builder.buildFuture();
                                })
                                .executes(context -> {
                                    handleInvalidateAllTimesForPlayer(context);
                                    return Command.SINGLE_SUCCESS;
                                })
                            )
                        )
                        .then(LiteralArgumentBuilder.<ServerCommandSource>literal("map")
                            .then(RequiredArgumentBuilder.<ServerCommandSource, String>argument("map_name", StringArgumentType.string())
                                .suggests((context, builder) -> {
                                    suggestMapNames(builder);
                                    return builder.buildFuture();
                                })
                                .executes(context -> {
                                    handleInvalidateTimesForMap(context);
                                    return Command.SINGLE_SUCCESS;
                                })
                            )
                        )
                    )
                    .then(LiteralArgumentBuilder.<ServerCommandSource>literal("replays")
                        .then(LiteralArgumentBuilder.<ServerCommandSource>literal("player")
                            .then(RequiredArgumentBuilder.<ServerCommandSource, String>argument("player_name", StringArgumentType.string())
                                .suggests((context, builder) -> {
                                    suggestKnownPlayerNames(context.getSource(), builder);
                                    return builder.buildFuture();
                                })
                                .executes(context -> {
                                    handleInvalidateReplaysForPlayer(context);
                                    return Command.SINGLE_SUCCESS;
                                })
                            )
                        )
                        .then(LiteralArgumentBuilder.<ServerCommandSource>literal("map")
                            .then(RequiredArgumentBuilder.<ServerCommandSource, String>argument("map_name", StringArgumentType.string())
                                .suggests((context, builder) -> {
                                    suggestMapNames(builder);
                                    return builder.buildFuture();
                                })
                                .executes(context -> {
                                    handleInvalidateReplaysForMap(context);
                                    return Command.SINGLE_SUCCESS;
                                })
                            )
                        )
                    )
                    .then(LiteralArgumentBuilder.<ServerCommandSource>literal("run")
                        .then(RequiredArgumentBuilder.<ServerCommandSource, String>argument("replay_id", StringArgumentType.string())
                            .executes(context -> LeaderboardCommands.invalidateRun(context, StringArgumentType.getString(context, "replay_id")))
                        )
                    )
                    .then(RequiredArgumentBuilder.<ServerCommandSource, String>argument("map_name", StringArgumentType.string())
                        .suggests((context, builder) -> {
                            for (DataManager.MapData mapData : Minehop.mapList) {
                                builder.suggest(mapData.name, new LiteralMessage(mapData.name));
                            }
                            return builder.buildFuture();
                        })
                        .executes(context -> {
                            handleInvalidate(context);
                            return Command.SINGLE_SUCCESS;
                        })
                    )
                )
                .then(LiteralArgumentBuilder.<ServerCommandSource>literal("history")
                    .then(RequiredArgumentBuilder.<ServerCommandSource, String>argument("player", StringArgumentType.string())
                        .suggests((context, builder) -> {
                            suggestKnownPlayerNames(context.getSource(), builder);
                            return builder.buildFuture();
                        })
                        .executes(context -> LeaderboardCommands.history(context, StringArgumentType.getString(context, "player"), null))
                        .then(RequiredArgumentBuilder.<ServerCommandSource, String>argument("map_name", StringArgumentType.string())
                            .suggests((context, builder) -> {
                                suggestMapNames(builder);
                                return builder.buildFuture();
                            })
                            .executes(context -> LeaderboardCommands.history(context, StringArgumentType.getString(context, "player"),
                                    canonicalMapName(StringArgumentType.getString(context, "map_name"))))
                        )
                    )
                )
                .then(LiteralArgumentBuilder.<ServerCommandSource>literal("flagged")
                    .executes(context -> LeaderboardCommands.flagged(context, null))
                    .then(RequiredArgumentBuilder.<ServerCommandSource, String>argument("map_name", StringArgumentType.string())
                        .suggests((context, builder) -> {
                            suggestMapNames(builder);
                            return builder.buildFuture();
                        })
                        .executes(context -> LeaderboardCommands.flagged(context, canonicalMapName(StringArgumentType.getString(context, "map_name"))))
                    )
                )
                .then(LiteralArgumentBuilder.<ServerCommandSource>literal("ban")
                    .then(RequiredArgumentBuilder.<ServerCommandSource, String>argument("player", StringArgumentType.string())
                        .suggests((context, builder) -> {
                            suggestKnownPlayerNames(context.getSource(), builder);
                            return builder.buildFuture();
                        })
                        .executes(context -> LeaderboardCommands.ban(context, StringArgumentType.getString(context, "player"), ""))
                        .then(RequiredArgumentBuilder.<ServerCommandSource, String>argument("reason", StringArgumentType.greedyString())
                            .executes(context -> LeaderboardCommands.ban(context, StringArgumentType.getString(context, "player"),
                                    StringArgumentType.getString(context, "reason")))
                        )
                    )
                )
                .then(LiteralArgumentBuilder.<ServerCommandSource>literal("unban")
                    .then(RequiredArgumentBuilder.<ServerCommandSource, String>argument("player", StringArgumentType.string())
                        .executes(context -> LeaderboardCommands.unban(context, StringArgumentType.getString(context, "player")))
                    )
                )
                .then(LiteralArgumentBuilder.<ServerCommandSource>literal("bans")
                    .executes(LeaderboardCommands::listBans)
                )
                .then(LiteralArgumentBuilder.<ServerCommandSource>literal("reconcile_ranks")
                    .executes(LeaderboardCommands::reconcileRanks)
                )
                .then(LiteralArgumentBuilder.<ServerCommandSource>literal("invalidate_player")
                    .then(RequiredArgumentBuilder.<ServerCommandSource, String>argument("map_name", StringArgumentType.string())
                        .suggests((context, builder) -> {
                            for (DataManager.MapData mapData : Minehop.mapList) {
                                builder.suggest(mapData.name, new LiteralMessage(mapData.name));
                            }
                            return builder.buildFuture();
                        })
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
                        .suggests((context, builder) -> {
                            for (DataManager.MapData mapData : Minehop.mapList) {
                                builder.suggest(mapData.name, new LiteralMessage(mapData.name));
                            }
                            return builder.buildFuture();
                        })
                        .executes(context -> {
                            handleRemove(context);
                            return Command.SINGLE_SUCCESS;
                        })
                    )
                )
                .then(LiteralArgumentBuilder.<ServerCommandSource>literal("setspawn")
                        .then(RequiredArgumentBuilder.<ServerCommandSource, String>argument("map_name", StringArgumentType.string())
                                .suggests((context, builder) -> {
                                    for (DataManager.MapData mapData : Minehop.mapList) {
                                        builder.suggest(mapData.name, new LiteralMessage(mapData.name));
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(context -> {
                                    handleSetMapSpawn(context);
                                    return Command.SINGLE_SUCCESS;
                                })
                        )
                )
                .then(LiteralArgumentBuilder.<ServerCommandSource>literal("difficulty")
                        .then(RequiredArgumentBuilder.<ServerCommandSource, String>argument("map_name", StringArgumentType.string())
                                .suggests((context, builder) -> {
                                    for (DataManager.MapData mapData : Minehop.mapList) {
                                        builder.suggest(mapData.name, new LiteralMessage(mapData.name));
                                    }
                                    return builder.buildFuture();
                                })
                                .then(RequiredArgumentBuilder.<ServerCommandSource, Integer>argument("difficulty", IntegerArgumentType.integer())
                                        .suggests((context, builder) -> {
                                            builder.suggest(0, new LiteralMessage("Beginner"));
                                            builder.suggest(1, new LiteralMessage("Easy"));
                                            builder.suggest(2, new LiteralMessage("Moderate"));
                                            builder.suggest(3, new LiteralMessage("Challenging"));
                                            builder.suggest(4, new LiteralMessage("Extremely Hard"));
                                            builder.suggest(5, new LiteralMessage("Impossible"));
                                            return builder.buildFuture();
                                        })
                                        .executes(context -> {
                                            handleSetDifficulty(context);
                                            return Command.SINGLE_SUCCESS;
                                        })
                                )
                        )
                )
                .then(LiteralArgumentBuilder.<ServerCommandSource>literal("arena")
                        .then(RequiredArgumentBuilder.<ServerCommandSource, String>argument("map_name", StringArgumentType.string())
                                .suggests((context, builder) -> {
                                    for (DataManager.MapData mapData : Minehop.mapList) {
                                        builder.suggest(mapData.name, new LiteralMessage(mapData.name));
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(context -> {
                                    handleToggleArena(context);
                                    return Command.SINGLE_SUCCESS;
                                })
                        )
                )
                .then(LiteralArgumentBuilder.<ServerCommandSource>literal("hns")
                        .then(RequiredArgumentBuilder.<ServerCommandSource, String>argument("map_name", StringArgumentType.string())
                                .suggests((context, builder) -> {
                                    for (DataManager.MapData mapData : Minehop.mapList) {
                                        builder.suggest(mapData.name, new LiteralMessage(mapData.name));
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(context -> {
                                    handleToggleHNS(context);
                                    return Command.SINGLE_SUCCESS;
                                })
                        )
                )
                .then(LiteralArgumentBuilder.<ServerCommandSource>literal("kz")
                        .then(RequiredArgumentBuilder.<ServerCommandSource, String>argument("map_name", StringArgumentType.string())
                                .suggests((context, builder) -> {
                                    for (DataManager.MapData mapData : Minehop.mapList) {
                                        builder.suggest(mapData.name, new LiteralMessage(mapData.name));
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(context -> {
                                    handleToggleKZ(context);
                                    return Command.SINGLE_SUCCESS;
                                })
                        )
                )
                .then(LiteralArgumentBuilder.<ServerCommandSource>literal("preservespeed")
                        .then(RequiredArgumentBuilder.<ServerCommandSource, String>argument("map_name", StringArgumentType.string())
                                .suggests((context, builder) -> {
                                    for (DataManager.MapData mapData : Minehop.mapList) {
                                        builder.suggest(mapData.name, new LiteralMessage(mapData.name));
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(context -> {
                                    handleTogglePreserveSpeed(context);
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
        ConfigWrapper.refreshPlayerCounts(context.getSource().getServer());
        PacketHandler.sendMaps(serverPlayerEntity);
        PacketHandler.sendOpenMapScreen(serverPlayerEntity, "Map Browser");
    }

    private static void handleOpenMapManageScreen(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity serverPlayerEntity = context.getSource().getPlayer();
        if (serverPlayerEntity == null) {
            return;
        }
        if (!UserPlotManager.canOpenMapManager(serverPlayerEntity)) {
            Logger.logFailure(serverPlayerEntity, "Open map creator by standing in your plot (or use operator permissions).");
            return;
        }
        MapCreationManager.openGui(serverPlayerEntity);
    }

    private static void handleEditMap(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity serverPlayerEntity = context.getSource().getPlayer();
        if (serverPlayerEntity == null) {
            return;
        }

        String mapName = StringArgumentType.getString(context, "map_name");
        DataManager.MapData mapData = DataManager.getMap(mapName);
        if (mapData == null) {
            Logger.logFailure(serverPlayerEntity, "The map " + mapName + " does not exist.");
            return;
        }

        if (!serverPlayerEntity.hasPermissionLevel(4) && !UserPlotManager.canManageMap(serverPlayerEntity, mapData)) {
            Logger.logFailure(serverPlayerEntity, "You can only edit your own user plot map from inside your plot.");
            return;
        }

        MapCreationManager.openGuiForMap(serverPlayerEntity, mapData.name);
    }

    private static void handleRateMap(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity serverPlayerEntity = context.getSource().getPlayer();
        if (serverPlayerEntity == null) {
            return;
        }

        String mapName = StringArgumentType.getString(context, "map_name");
        int goodRating = IntegerArgumentType.getInteger(context, "good_rating");
        int difficultyRating = IntegerArgumentType.getInteger(context, "difficulty_rating");

        DataManager.MapData mapData = DataManager.getMap(mapName);
        if (mapData == null) {
            Logger.logFailure(serverPlayerEntity, "The map " + mapName + " does not exist.");
            return;
        }
        if (!mapData.userMap) {
            Logger.logFailure(serverPlayerEntity, "Only user maps can be community-rated.");
            return;
        }
        if (mapData.ownerUuid != null && mapData.ownerUuid.equals(serverPlayerEntity.getUuidAsString())) {
            Logger.logFailure(serverPlayerEntity, "You cannot rate your own map.");
            return;
        }

        DataManager.setMapRating(
                mapName,
                serverPlayerEntity.getUuidAsString(),
                goodRating,
                difficultyRating
        );
        DataManager.recalculateMapRatings();

        ServerWorld world = serverPlayerEntity.getServerWorld();
        DataManager.saveData(world, DataManager.mapListLocation, Minehop.mapList);
        DataManager.saveData(world, DataManager.mapRatingsLocation, Minehop.mapRatingList);

        syncMapsForAll(context.getSource().getServer());

        double avgGood = mapData.rating_count > 0
                ? (double) mapData.rating_quality_total / (double) mapData.rating_count
                : 0.0D;
        double avgDiff = mapData.rating_count > 0
                ? (double) mapData.rating_difficulty_total / (double) mapData.rating_count
                : 0.0D;
        Logger.logSuccess(
                serverPlayerEntity,
                "Rated " + mapName + " | score " + goodRating + "/5, difficulty " + difficultyRating + "/5 "
                        + "(community avg: " + String.format(java.util.Locale.ROOT, "%.2f", avgGood)
                        + "/5, diff " + String.format(java.util.Locale.ROOT, "%.2f", avgDiff)
                        + "/5 from " + mapData.rating_count + " ratings)."
        );
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



        assert serverPlayerEntity != null;
        Zone closestEntity = Minehop.playerMapLocation.get(serverPlayerEntity.getUuidAsString());




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
                        UserPlotManager.consumeForcedCreativeState(serverPlayerEntity);
                        if (!serverPlayerEntity.isCreative()) {
                            serverPlayerEntity.getInventory().clear();
                        }
                        ZoneUtil.teleportTo(serverPlayerEntity, ZoneUtil.makeTeleportTarget(foundWorld, new Vec3d(currentMapData.x, currentMapData.y, currentMapData.z), (float) currentMapData.yrot, (float) currentMapData.xrot));
                        if (SpectateCommands.spectatorList.containsKey(serverPlayerEntity.getEntityName())) {
                            List<String> spectators = SpectateCommands.spectatorList.get(serverPlayerEntity.getEntityName());
                            for (String spectator : spectators) {
                                if (!spectator.equals(serverPlayerEntity.getEntityName())) {
                                    ServerPlayerEntity spectatorPlayer = context.getSource().getServer().getPlayerManager().getPlayer(spectator);
                                    if (spectatorPlayer == null) {
                                        continue;
                                    }
                                    UserPlotManager.consumeForcedCreativeState(spectatorPlayer);
                                    if (!spectatorPlayer.isCreative()) {
                                        spectatorPlayer.getInventory().clear();
                                    }
                                    ZoneUtil.teleportTo(spectatorPlayer, ZoneUtil.makeTeleportTarget(serverPlayerEntity.getServerWorld(), new Vec3d(serverPlayerEntity.getX(), serverPlayerEntity.getY(), serverPlayerEntity.getZ()), serverPlayerEntity.getYaw(), serverPlayerEntity.getPitch()));
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
            if (tpData.userMap) {
                tpData.play_count += 1;
            }
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
                if (tpData.hns || tpData.arena) {
                    GamemodeEntity gamemodeEntity = ZoneUtil.getGamemodeEntity(tpData.name, foundWorld);
                    if (gamemodeEntity == null) {
                        gamemodeEntity = ModEntities.GAMEMODE_ENTITY.spawn(foundWorld, new BlockPos((int) tpData.x, (int) tpData.y, (int) tpData.z), SpawnReason.NATURAL);
                        gamemodeEntity.setPairedMap(tpData.name);
                    }
                    Minehop.playerMapLocation.put(serverPlayerEntity.getUuidAsString(), gamemodeEntity);
                }
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
                    UserPlotManager.consumeForcedCreativeState(serverPlayerEntity);
                    if (!serverPlayerEntity.isCreative()) {
                        serverPlayerEntity.getInventory().clear();
                    }
                    ZoneUtil.teleportTo(serverPlayerEntity, ZoneUtil.makeTeleportTarget(foundWorld, targetPos, (float) rotPos.getY(), (float) rotPos.getX()));
                    if (SpectateCommands.spectatorList.containsKey(serverPlayerEntity.getEntityName())) {
                        List<String> spectators = SpectateCommands.spectatorList.get(serverPlayerEntity.getEntityName());
                        for (String spectator : spectators) {
                            if (!spectator.equals(serverPlayerEntity.getEntityName())) {
                                ServerPlayerEntity spectatorPlayer = context.getSource().getServer().getPlayerManager().getPlayer(spectator);
                                if (spectatorPlayer == null) {
                                    continue;
                                }
                                UserPlotManager.consumeForcedCreativeState(spectatorPlayer);
                                if (!spectatorPlayer.isCreative()) {
                                    spectatorPlayer.getInventory().clear();
                                }
                                ZoneUtil.teleportTo(spectatorPlayer, ZoneUtil.makeTeleportTarget(serverPlayerEntity.getServerWorld(), new Vec3d(serverPlayerEntity.getX(), serverPlayerEntity.getY(), serverPlayerEntity.getZ()), serverPlayerEntity.getYaw(), serverPlayerEntity.getPitch()));
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

        String rawName = StringArgumentType.getString(context, "add_name");
        String name = rawName == null ? "" : rawName.trim();
        if (name.isEmpty() || name.length() > 128 || name.contains("~") || name.contains("\n") || name.contains("\r")) {
            Logger.logFailure(serverPlayerEntity, "Map name must be 1-128 chars and cannot contain '~'.");
            return;
        }
        name = DescriptionCensor.censorProfanity(name).trim();
        if (name.isEmpty()) {
            Logger.logFailure(serverPlayerEntity, "Map name cannot be blank.");
            return;
        }
        double spawn_x = serverPlayerEntity.getX();
        double spawn_y = serverPlayerEntity.getY();
        double spawn_z = serverPlayerEntity.getZ();
        double spawn_xrot = serverPlayerEntity.getPitch();
        double spawn_yrot = serverPlayerEntity.getYaw();

        DataManager.MapData mapData = new DataManager.MapData(name, spawn_x, spawn_y, spawn_z, spawn_xrot, spawn_yrot, serverPlayerEntity.getWorld().getRegistryKey().toString());
        mapData.copyMovementFrom(ConfigWrapper.config, false);
        Minehop.mapList.add(mapData);
        DataManager.saveData(context.getSource().getWorld(), DataManager.mapListLocation, Minehop.mapList);

        Logger.logSuccess(serverPlayerEntity, "Created map \\/\n" + StringFormatting.limitDecimals(gson.toJson(mapData)));


    }

    private static String canonicalMapName(String requested) {
        DataManager.MapData mapData = DataManager.getMap(requested);
        return mapData != null ? mapData.name : requested;
    }

    // All invalidation goes through LeaderboardIntegrity (UUID-safe identity, WR promotion, replays,
    // record_holder rank, WR replay entity, client sync, audit log).
    private static void handleInvalidatePlayer(CommandContext<ServerCommandSource> context) {
        LeaderboardCommands.invalidatePlayer(context, StringArgumentType.getString(context, "player_name"),
                canonicalMapName(StringArgumentType.getString(context, "map_name")), true, true);
    }

    private static void handleInvalidate(CommandContext<ServerCommandSource> context) {
        LeaderboardCommands.invalidateMap(context, canonicalMapName(StringArgumentType.getString(context, "map_name")), true, true);
    }

    private static void handleInvalidateAllTimesForPlayer(CommandContext<ServerCommandSource> context) {
        LeaderboardCommands.invalidatePlayer(context, StringArgumentType.getString(context, "player_name"), null, true, true);
    }

    private static void handleInvalidateTimesForMap(CommandContext<ServerCommandSource> context) {
        LeaderboardCommands.invalidateMap(context, canonicalMapName(StringArgumentType.getString(context, "map_name")), true, true);
    }

    private static void handleInvalidateReplaysForPlayer(CommandContext<ServerCommandSource> context) {
        LeaderboardCommands.invalidatePlayer(context, StringArgumentType.getString(context, "player_name"), null, false, true);
    }

    private static void handleInvalidateReplaysForMap(CommandContext<ServerCommandSource> context) {
        LeaderboardCommands.invalidateMap(context, canonicalMapName(StringArgumentType.getString(context, "map_name")), false, true);
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
                    DataManager.removeRatingsForMap(name);
                    DataManager.saveData(context.getSource().getWorld(), DataManager.mapListLocation, Minehop.mapList);
                    DataManager.saveData(context.getSource().getWorld(), DataManager.mapRatingsLocation, Minehop.mapRatingList);
                    break;
                }
            }
        }

        if (removedData != null) {
            // Cascade: a removed map keeps no times, WR, replays or WR replay entity (and its holder
            // loses record_holder unless they hold another WR). Re-adding the name starts clean.
            net.nerdorg.minehop.data.LeaderboardIntegrity.Report cascade = net.nerdorg.minehop.data.LeaderboardIntegrity.purgeMap(
                    context.getSource().getServer(), name, true, true, context.getSource().getName(), "map removed");
            Logger.logSuccess(serverPlayerEntity, "Removed map (and " + cascade.summary() + ") \\/\n" + StringFormatting.limitDecimals(gson.toJson(removedData)));
        }
        else {
            Logger.logFailure(serverPlayerEntity, "The map " + name + " does not exist.");
        }
    }

    private static void handleSetDifficulty(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity serverPlayerEntity = context.getSource().getPlayer();

        String name = StringArgumentType.getString(context, "map_name");
        int difficulty = IntegerArgumentType.getInteger(context, "difficulty");

        DataManager.MapData difficultyData = null;

        for (Object object : Minehop.mapList) {
            if (object instanceof DataManager.MapData mapData) {
                if (mapData.name.equals(name)) {
                    difficultyData = mapData;
                    Minehop.mapList.remove(mapData);
                    break;
                }
            }
        }

        if (difficultyData != null) {
            difficultyData.difficulty = difficulty;
            Minehop.mapList.add(difficultyData);
            DataManager.saveData(context.getSource().getWorld(), DataManager.mapListLocation, Minehop.mapList);

            Logger.logSuccess(serverPlayerEntity, "Set map difficulty \\/\n" + StringFormatting.limitDecimals(gson.toJson(difficultyData)));
        }
        else {
            Logger.logSuccess(serverPlayerEntity, "There is no map called " + name + ".");
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

    private static void handleTogglePreserveSpeed(CommandContext<ServerCommandSource> context) {
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
            toggleData.preserve_speed = !toggleData.preserve_speed;
            Minehop.mapList.add(toggleData);
            DataManager.saveData(context.getSource().getWorld(), DataManager.mapListLocation, Minehop.mapList);

            Logger.logSuccess(serverPlayerEntity, "Toggled preserve speed on reset to " + toggleData.preserve_speed + " for " + name + ".");
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

    private static void handleToggleKZ(CommandContext<ServerCommandSource> context) {
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
            toggleData.kz = !toggleData.kz;
            Minehop.mapList.add(toggleData);
            DataManager.saveData(context.getSource().getWorld(), DataManager.mapListLocation, Minehop.mapList);

            Logger.logSuccess(serverPlayerEntity, "Toggled kz mode to " + toggleData.kz);
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

    private static void suggestMapNames(SuggestionsBuilder builder) {
        if (Minehop.mapList == null) {
            return;
        }
        for (DataManager.MapData mapData : Minehop.mapList) {
            if (mapData != null && mapData.name != null && !mapData.name.isBlank()) {
                builder.suggest(mapData.name, new LiteralMessage(mapData.name));
            }
        }
    }

    private static void suggestKnownPlayerNames(ServerCommandSource source, SuggestionsBuilder builder) {
        for (String playerName : collectKnownPlayerNames(source)) {
            builder.suggest(playerName, new LiteralMessage(playerName));
        }
    }

    private static Set<String> collectKnownPlayerNames(ServerCommandSource source) {
        Set<String> playerNames = new LinkedHashSet<>();
        if (source != null && source.getServer() != null) {
            for (ServerPlayerEntity player : source.getServer().getPlayerManager().getPlayerList()) {
                if (player != null && player.getEntityName() != null && !player.getEntityName().isBlank()) {
                    playerNames.add(player.getEntityName());
                }
            }
        }
        if (Minehop.personalRecordList != null) {
            for (DataManager.RecordData recordData : Minehop.personalRecordList) {
                if (recordData != null && recordData.name != null && !recordData.name.isBlank()) {
                    playerNames.add(recordData.name);
                }
            }
        }
        if (Minehop.recordList != null) {
            for (DataManager.RecordData recordData : Minehop.recordList) {
                if (recordData != null && recordData.name != null && !recordData.name.isBlank()) {
                    playerNames.add(recordData.name);
                }
            }
        }
        if (Minehop.replayList != null) {
            for (ReplayManager.Replay replay : Minehop.replayList) {
                if (replay != null && replay.player_name != null && !replay.player_name.isBlank()) {
                    playerNames.add(replay.player_name);
                }
            }
        }
        return playerNames;
    }

    private static void syncMapsForAll(net.minecraft.server.MinecraftServer server) {
        if (server == null) {
            return;
        }
        for (ServerPlayerEntity worldPlayer : server.getPlayerManager().getPlayerList()) {
            PacketHandler.sendMaps(worldPlayer);
        }
    }

}
