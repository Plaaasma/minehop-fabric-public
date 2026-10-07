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
import net.nerdorg.minehop.platform.Services;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
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
        Services.EVENTS.onRegisterCommands((dispatcher, registryAccess, environment) -> dispatcher.register(
            LiteralArgumentBuilder.<CommandSourceStack>literal("map")
            .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("map_name", StringArgumentType.string())
                .executes(context -> {
                    handleTeleport(context);
                    return Command.SINGLE_SUCCESS;
                })
            )
            .then(LiteralArgumentBuilder.<CommandSourceStack>literal("edit")
                .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("map_name", StringArgumentType.string())
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
            .then(LiteralArgumentBuilder.<CommandSourceStack>literal("rate")
                .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("map_name", StringArgumentType.string())
                    .suggests((context, builder) -> {
                        for (DataManager.MapData mapData : Minehop.mapList) {
                            if (mapData != null && mapData.userMap && mapData.name != null) {
                                builder.suggest(mapData.name, new LiteralMessage(mapData.name));
                            }
                        }
                        return builder.buildFuture();
                    })
                    .then(RequiredArgumentBuilder.<CommandSourceStack, Integer>argument("good_rating", IntegerArgumentType.integer(1, 5))
                        .then(RequiredArgumentBuilder.<CommandSourceStack, Integer>argument("difficulty_rating", IntegerArgumentType.integer(1, 5))
                            .executes(context -> {
                                handleRateMap(context);
                                return Command.SINGLE_SUCCESS;
                            })
                        )
                    )
                )
            )
            .then(LiteralArgumentBuilder.<CommandSourceStack>literal("restart")
                .executes(context -> {
                    handleRestart(context);
                    return Command.SINGLE_SUCCESS;
                })
            )
            .then(LiteralArgumentBuilder.<CommandSourceStack>literal("list")
                .executes(context -> {
                    handleList(context);
                    return Command.SINGLE_SUCCESS;
                })
            )
            .then(LiteralArgumentBuilder.<CommandSourceStack>literal("top")
                .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("map_name", StringArgumentType.string())
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
            .then(LiteralArgumentBuilder.<CommandSourceStack>literal("plotmanage")
                .executes(context -> {
                    handleOpenMapManageScreen(context);
                    return Command.SINGLE_SUCCESS;
                })
            )
            .then(LiteralArgumentBuilder.<CommandSourceStack>literal("manage")
            .requires(source -> source.hasPermission(4))
                .executes(context -> {
                    handleOpenMapManageScreen(context);
                    return Command.SINGLE_SUCCESS;
                })
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("checkpoint")
                    .then(LiteralArgumentBuilder.<CommandSourceStack>literal("add")
                        .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("map_name", StringArgumentType.string())
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
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("add")
                    .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("add_name", StringArgumentType.string())
                        .executes(context -> {
                            handleAdd(context);
                            return Command.SINGLE_SUCCESS;
                        })
                    )
                )
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("invalidate")
                    .then(LiteralArgumentBuilder.<CommandSourceStack>literal("times")
                        .then(LiteralArgumentBuilder.<CommandSourceStack>literal("player")
                            .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("player_name", StringArgumentType.string())
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
                        .then(LiteralArgumentBuilder.<CommandSourceStack>literal("map")
                            .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("map_name", StringArgumentType.string())
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
                    .then(LiteralArgumentBuilder.<CommandSourceStack>literal("replays")
                        .then(LiteralArgumentBuilder.<CommandSourceStack>literal("player")
                            .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("player_name", StringArgumentType.string())
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
                        .then(LiteralArgumentBuilder.<CommandSourceStack>literal("map")
                            .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("map_name", StringArgumentType.string())
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
                    .then(LiteralArgumentBuilder.<CommandSourceStack>literal("run")
                        .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("replay_id", StringArgumentType.string())
                            .executes(context -> LeaderboardCommands.invalidateRun(context, StringArgumentType.getString(context, "replay_id")))
                        )
                    )
                    .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("map_name", StringArgumentType.string())
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
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("history")
                    .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("player", StringArgumentType.string())
                        .suggests((context, builder) -> {
                            suggestKnownPlayerNames(context.getSource(), builder);
                            return builder.buildFuture();
                        })
                        .executes(context -> LeaderboardCommands.history(context, StringArgumentType.getString(context, "player"), null))
                        .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("map_name", StringArgumentType.string())
                            .suggests((context, builder) -> {
                                suggestMapNames(builder);
                                return builder.buildFuture();
                            })
                            .executes(context -> LeaderboardCommands.history(context, StringArgumentType.getString(context, "player"),
                                    canonicalMapName(StringArgumentType.getString(context, "map_name"))))
                        )
                    )
                )
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("flagged")
                    .executes(context -> LeaderboardCommands.flagged(context, null))
                    .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("map_name", StringArgumentType.string())
                        .suggests((context, builder) -> {
                            suggestMapNames(builder);
                            return builder.buildFuture();
                        })
                        .executes(context -> LeaderboardCommands.flagged(context, canonicalMapName(StringArgumentType.getString(context, "map_name"))))
                    )
                )
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("ban")
                    .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("player", StringArgumentType.string())
                        .suggests((context, builder) -> {
                            suggestKnownPlayerNames(context.getSource(), builder);
                            return builder.buildFuture();
                        })
                        .executes(context -> LeaderboardCommands.ban(context, StringArgumentType.getString(context, "player"), ""))
                        .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("reason", StringArgumentType.greedyString())
                            .executes(context -> LeaderboardCommands.ban(context, StringArgumentType.getString(context, "player"),
                                    StringArgumentType.getString(context, "reason")))
                        )
                    )
                )
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("unban")
                    .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("player", StringArgumentType.string())
                        .executes(context -> LeaderboardCommands.unban(context, StringArgumentType.getString(context, "player")))
                    )
                )
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("bans")
                    .executes(LeaderboardCommands::listBans)
                )
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("reconcile_ranks")
                    .executes(LeaderboardCommands::reconcileRanks)
                )
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("invalidate_player")
                    .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("map_name", StringArgumentType.string())
                        .suggests((context, builder) -> {
                            for (DataManager.MapData mapData : Minehop.mapList) {
                                builder.suggest(mapData.name, new LiteralMessage(mapData.name));
                            }
                            return builder.buildFuture();
                        })
                        .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("player_name", StringArgumentType.string())
                            .executes(context -> {
                                handleInvalidatePlayer(context);
                                return Command.SINGLE_SUCCESS;
                            })
                        )
                    )
                )
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("remove")
                    .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("remove_name", StringArgumentType.string())
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
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("setspawn")
                        .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("map_name", StringArgumentType.string())
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
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("difficulty")
                        .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("map_name", StringArgumentType.string())
                                .suggests((context, builder) -> {
                                    for (DataManager.MapData mapData : Minehop.mapList) {
                                        builder.suggest(mapData.name, new LiteralMessage(mapData.name));
                                    }
                                    return builder.buildFuture();
                                })
                                .then(RequiredArgumentBuilder.<CommandSourceStack, Integer>argument("difficulty", IntegerArgumentType.integer())
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
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("arena")
                        .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("map_name", StringArgumentType.string())
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
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("hns")
                        .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("map_name", StringArgumentType.string())
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
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("kz")
                        .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("map_name", StringArgumentType.string())
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
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("preservespeed")
                        .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("map_name", StringArgumentType.string())
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
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("set")
                        .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("map_name", StringArgumentType.string())
                                .suggests((context, builder) -> {
                                    suggestMapNames(builder);
                                    return builder.buildFuture();
                                })
                                .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("field", StringArgumentType.word())
                                        .suggests((context, builder) -> {
                                            for (String field : SETTABLE_MAP_FIELDS) {
                                                builder.suggest(field);
                                            }
                                            return builder.buildFuture();
                                        })
                                        .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("value", StringArgumentType.greedyString())
                                                .executes(MapUtilCommands::handleSetField)
                                        )
                                )
                        )
                )
                .then(LiteralArgumentBuilder.<CommandSourceStack>literal("info")
                    .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("search_name", StringArgumentType.string())
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

    private static void handleOpenMapScreen(CommandContext<CommandSourceStack> context) {
        ServerPlayer serverPlayerEntity = context.getSource().getPlayer();
        ConfigWrapper.refreshPlayerCounts(context.getSource().getServer());
        PacketHandler.sendMaps(serverPlayerEntity);
        PacketHandler.sendOpenMapScreen(serverPlayerEntity, "Map Browser");
    }

    private static void handleOpenMapManageScreen(CommandContext<CommandSourceStack> context) {
        ServerPlayer serverPlayerEntity = context.getSource().getPlayer();
        if (serverPlayerEntity == null) {
            return;
        }
        if (!UserPlotManager.canOpenMapManager(serverPlayerEntity)) {
            Logger.logFailure(serverPlayerEntity, "Open map creator by standing in your plot (or use operator permissions).");
            return;
        }
        MapCreationManager.openGui(serverPlayerEntity);
    }

    private static void handleEditMap(CommandContext<CommandSourceStack> context) {
        ServerPlayer serverPlayerEntity = context.getSource().getPlayer();
        if (serverPlayerEntity == null) {
            return;
        }

        String mapName = StringArgumentType.getString(context, "map_name");
        DataManager.MapData mapData = DataManager.getMap(mapName);
        if (mapData == null) {
            Logger.logFailure(serverPlayerEntity, "The map " + mapName + " does not exist.");
            return;
        }

        if (!serverPlayerEntity.hasPermissions(4) && !UserPlotManager.canManageMap(serverPlayerEntity, mapData)) {
            Logger.logFailure(serverPlayerEntity, "You can only edit your own user plot map from inside your plot.");
            return;
        }

        MapCreationManager.openGuiForMap(serverPlayerEntity, mapData.name);
    }

    private static void handleRateMap(CommandContext<CommandSourceStack> context) {
        ServerPlayer serverPlayerEntity = context.getSource().getPlayer();
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
        if (mapData.ownerUuid != null && mapData.ownerUuid.equals(serverPlayerEntity.getStringUUID())) {
            Logger.logFailure(serverPlayerEntity, "You cannot rate your own map.");
            return;
        }

        DataManager.setMapRating(
                mapName,
                serverPlayerEntity.getStringUUID(),
                goodRating,
                difficultyRating
        );
        DataManager.recalculateMapRatings();

        ServerLevel world = serverPlayerEntity.serverLevel();
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

    private static void handleAddCheckpoint(CommandContext<CommandSourceStack> context) {
        ServerPlayer serverPlayerEntity = context.getSource().getPlayer();

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
            consoleReply(context, serverPlayerEntity, "Added checkpoint " + (mapToAddTo.checkpointPositions.size() + 1) + " to " + name, true);
            Minehop.mapList.remove(mapToAddTo);
            Vec3 checkpointPos = serverPlayerEntity != null ? serverPlayerEntity.position() : context.getSource().getPosition();
            net.minecraft.world.phys.Vec2 checkpointRot = serverPlayerEntity != null ? serverPlayerEntity.getRotationVector() : context.getSource().getRotation();
            mapToAddTo.checkpointPositions.add(new ArrayList<>(Arrays.asList(checkpointPos, new Vec3(checkpointRot.x, checkpointRot.y, 0))));
            Minehop.mapList.add(mapToAddTo);
            DataManager.saveData(context.getSource().getLevel(), DataManager.mapListLocation, Minehop.mapList);
        }
        else {
            Logger.logFailure(serverPlayerEntity, "The map " + name + " does not exist.");
            consoleReply(context, serverPlayerEntity, "The map " + name + " does not exist.", false);
        }
    }

    private static void handleRestart(CommandContext<CommandSourceStack> context) {
        ServerPlayer serverPlayerEntity = context.getSource().getPlayer();



        assert serverPlayerEntity != null;
        Zone closestEntity = Minehop.playerMapLocation.get(serverPlayerEntity.getStringUUID());




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
                    currentMapData.worldKey = context.getSource().getServer().overworld().dimension().toString();
                    Minehop.mapList.add(currentMapData);
                    DataManager.saveData(context.getSource().getLevel(), DataManager.mapListLocation, Minehop.mapList);
                }
                Minehop.timerManager.remove(serverPlayerEntity.getScoreboardName());
                ServerLevel foundWorld = null;
                for (ServerLevel svrWorld : context.getSource().getServer().getAllLevels()) {
                    if (svrWorld.dimension().toString().equals(currentMapData.worldKey)) {
                        foundWorld = svrWorld;
                        break;
                    }
                }
                if (foundWorld != null) {
                    if (!serverPlayerEntity.isSpectator()) {
                        UserPlotManager.consumeForcedCreativeState(serverPlayerEntity);
                        if (!serverPlayerEntity.isCreative()) {
                            serverPlayerEntity.getInventory().clearContent();
                        }
                        ZoneUtil.teleportTo(serverPlayerEntity, ZoneUtil.makeTeleportTarget(foundWorld, new Vec3(currentMapData.x, currentMapData.y, currentMapData.z), (float) currentMapData.yrot, (float) currentMapData.xrot));
                        // Spectators follow on their own (SpectateSessions), also into another dimension.
                    }
                }
            }
            else {
                Logger.logFailure(serverPlayerEntity, "Error finding nearest map.");
            }
        }
    }

    private static void handleTeleport(CommandContext<CommandSourceStack> context) {
        ServerPlayer serverPlayerEntity = context.getSource().getPlayer();

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
                tpData.worldKey = context.getSource().getServer().overworld().dimension().toString();
                Minehop.mapList.add(tpData);
                DataManager.saveData(context.getSource().getLevel(), DataManager.mapListLocation, Minehop.mapList);
            }
            Logger.logSuccess(serverPlayerEntity, "Teleporting to " + name);
            ServerLevel foundWorld = null;
            for (ServerLevel serverWorld : context.getSource().getServer().getAllLevels()) {
                if (serverWorld.dimension().toString().equals(tpData.worldKey)) {
                    foundWorld = serverWorld;
                    break;
                }
            }
            if (foundWorld != null) {
                if (tpData.hns || tpData.arena) {
                    GamemodeEntity gamemodeEntity = ZoneUtil.getGamemodeEntity(tpData.name, foundWorld);
                    if (gamemodeEntity == null) {
                        gamemodeEntity = ModEntities.GAMEMODE_ENTITY.get().spawn(foundWorld, new BlockPos((int) tpData.x, (int) tpData.y, (int) tpData.z), MobSpawnType.NATURAL);
                        gamemodeEntity.setPairedMap(tpData.name);
                    }
                    Minehop.playerMapLocation.put(serverPlayerEntity.getStringUUID(), gamemodeEntity);
                }
                Vec3 targetPos = new Vec3(tpData.x, tpData.y, tpData.z);
                Vec3 rotPos = new Vec3(tpData.xrot, tpData.yrot, 0);
                if (tpData.arena) {
                    List<Vec3> spawnCheck = new ArrayList<>();
                    spawnCheck.add(new Vec3(tpData.x, tpData.y, tpData.z));
                    spawnCheck.add(new Vec3(tpData.xrot, tpData.yrot, 0));

                    List<List<Vec3>> checkpointPositions = new ArrayList<>();
                    if (tpData.checkpointPositions != null) {
                        checkpointPositions.addAll(tpData.checkpointPositions);
                    }
                    checkpointPositions.add(spawnCheck);

                    List<Vec3> randomCheckpoint = checkpointPositions.get(random.nextInt(0, checkpointPositions.size()));
                    targetPos = randomCheckpoint.get(0);
                    rotPos = randomCheckpoint.get(1);
                }

                if (!serverPlayerEntity.isSpectator()) {
                    UserPlotManager.consumeForcedCreativeState(serverPlayerEntity);
                    if (!serverPlayerEntity.isCreative()) {
                        serverPlayerEntity.getInventory().clearContent();
                    }
                    ZoneUtil.teleportTo(serverPlayerEntity, ZoneUtil.makeTeleportTarget(foundWorld, targetPos, (float) rotPos.y(), (float) rotPos.x()));
                    // Spectators follow on their own (SpectateSessions), also into another dimension.
                    if (tpData.arena) {
                        for (int slotNum = 1; slotNum < serverPlayerEntity.getInventory().getContainerSize(); slotNum++) {
                            serverPlayerEntity.getInventory().setItem(slotNum, new ItemStack(Items.AIR));
                        }
                        serverPlayerEntity.getInventory().setItem(0, new ItemStack(ModItems.INSTAGIB_GUN.get()));
                    }
                }
            }
        }
        else {
            Logger.logFailure(serverPlayerEntity, "The map " + name + " does not exist.");
        }
    }

    private static void handleAdd(CommandContext<CommandSourceStack> context) {
        ServerPlayer serverPlayerEntity = context.getSource().getPlayer();

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
        if (serverPlayerEntity == null && DataManager.getMap(name) != null) {
            // Console builds are scripted: never create a second entry under an existing name.
            consoleReply(context, null, "The map " + name + " already exists.", false);
            return;
        }
        CommandSourceStack source = context.getSource();
        double spawn_x = serverPlayerEntity != null ? serverPlayerEntity.getX() : source.getPosition().x;
        double spawn_y = serverPlayerEntity != null ? serverPlayerEntity.getY() : source.getPosition().y;
        double spawn_z = serverPlayerEntity != null ? serverPlayerEntity.getZ() : source.getPosition().z;
        double spawn_xrot = serverPlayerEntity != null ? serverPlayerEntity.getXRot() : source.getRotation().x;
        double spawn_yrot = serverPlayerEntity != null ? serverPlayerEntity.getYRot() : source.getRotation().y;
        String worldKey = serverPlayerEntity != null ? serverPlayerEntity.level().dimension().toString() : source.getLevel().dimension().toString();

        DataManager.MapData mapData = new DataManager.MapData(name, spawn_x, spawn_y, spawn_z, spawn_xrot, spawn_yrot, worldKey);
        mapData.copyMovementFrom(ConfigWrapper.config, false);
        Minehop.mapList.add(mapData);
        DataManager.saveData(context.getSource().getLevel(), DataManager.mapListLocation, Minehop.mapList);

        Logger.logSuccess(serverPlayerEntity, "Created map \\/\n" + StringFormatting.limitDecimals(gson.toJson(mapData)));
        consoleReply(context, serverPlayerEntity, "Created map " + name + " at " + StringFormatting.limitDecimals(spawn_x + " " + spawn_y + " " + spawn_z) + " in " + worldKey, true);
        if (serverPlayerEntity == null) {
            syncMapsForAll(source.getServer());
        }


    }

    private static String canonicalMapName(String requested) {
        DataManager.MapData mapData = DataManager.getMap(requested);
        return mapData != null ? mapData.name : requested;
    }

    // All invalidation goes through LeaderboardIntegrity (UUID-safe identity, WR promotion, replays,
    // record_holder rank, WR replay entity, client sync, audit log).
    private static void handleInvalidatePlayer(CommandContext<CommandSourceStack> context) {
        LeaderboardCommands.invalidatePlayer(context, StringArgumentType.getString(context, "player_name"),
                canonicalMapName(StringArgumentType.getString(context, "map_name")), true, true);
    }

    private static void handleInvalidate(CommandContext<CommandSourceStack> context) {
        LeaderboardCommands.invalidateMap(context, canonicalMapName(StringArgumentType.getString(context, "map_name")), true, true);
    }

    private static void handleInvalidateAllTimesForPlayer(CommandContext<CommandSourceStack> context) {
        LeaderboardCommands.invalidatePlayer(context, StringArgumentType.getString(context, "player_name"), null, true, true);
    }

    private static void handleInvalidateTimesForMap(CommandContext<CommandSourceStack> context) {
        LeaderboardCommands.invalidateMap(context, canonicalMapName(StringArgumentType.getString(context, "map_name")), true, true);
    }

    private static void handleInvalidateReplaysForPlayer(CommandContext<CommandSourceStack> context) {
        LeaderboardCommands.invalidatePlayer(context, StringArgumentType.getString(context, "player_name"), null, false, true);
    }

    private static void handleInvalidateReplaysForMap(CommandContext<CommandSourceStack> context) {
        LeaderboardCommands.invalidateMap(context, canonicalMapName(StringArgumentType.getString(context, "map_name")), false, true);
    }

    private static void handleRemove(CommandContext<CommandSourceStack> context) {
        ServerPlayer serverPlayerEntity = context.getSource().getPlayer();

        String name = StringArgumentType.getString(context, "remove_name");

        DataManager.MapData removedData = null;

        for (Object object : Minehop.mapList) {
            if (object instanceof DataManager.MapData mapData) {
                if (mapData.name.equals(name)) {
                    removedData = mapData;
                    Minehop.mapList.remove(mapData);
                    DataManager.removeRatingsForMap(name);
                    DataManager.saveData(context.getSource().getLevel(), DataManager.mapListLocation, Minehop.mapList);
                    DataManager.saveData(context.getSource().getLevel(), DataManager.mapRatingsLocation, Minehop.mapRatingList);
                    break;
                }
            }
        }

        if (removedData != null) {
            // Cascade: a removed map keeps no times, WR, replays or WR replay entity (and its holder
            // loses record_holder unless they hold another WR). Re-adding the name starts clean.
            net.nerdorg.minehop.data.LeaderboardIntegrity.Report cascade = net.nerdorg.minehop.data.LeaderboardIntegrity.purgeMap(
                    context.getSource().getServer(), name, true, true, context.getSource().getTextName(), "map removed");
            Logger.logSuccess(serverPlayerEntity, "Removed map (and " + cascade.summary() + ") \\/\n" + StringFormatting.limitDecimals(gson.toJson(removedData)));
            consoleReply(context, serverPlayerEntity, "Removed map " + name + " (and " + cascade.summary() + ")", true);
            if (serverPlayerEntity == null) {
                syncMapsForAll(context.getSource().getServer());
            }
        }
        else {
            Logger.logFailure(serverPlayerEntity, "The map " + name + " does not exist.");
            consoleReply(context, serverPlayerEntity, "The map " + name + " does not exist.", false);
        }
    }

    private static void handleSetDifficulty(CommandContext<CommandSourceStack> context) {
        ServerPlayer serverPlayerEntity = context.getSource().getPlayer();

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
            DataManager.saveData(context.getSource().getLevel(), DataManager.mapListLocation, Minehop.mapList);

            Logger.logSuccess(serverPlayerEntity, "Set map difficulty \\/\n" + StringFormatting.limitDecimals(gson.toJson(difficultyData)));
        }
        else {
            Logger.logSuccess(serverPlayerEntity, "There is no map called " + name + ".");
        }
    }

    private static void handleSetMapSpawn(CommandContext<CommandSourceStack> context) {
        ServerPlayer serverPlayerEntity = context.getSource().getPlayer();

        String name = StringArgumentType.getString(context, "map_name");

        CommandSourceStack source = context.getSource();
        double spawn_x = serverPlayerEntity != null ? serverPlayerEntity.getX() : source.getPosition().x;
        double spawn_y = serverPlayerEntity != null ? serverPlayerEntity.getY() : source.getPosition().y;
        double spawn_z = serverPlayerEntity != null ? serverPlayerEntity.getZ() : source.getPosition().z;
        double spawn_xrot = serverPlayerEntity != null ? serverPlayerEntity.getXRot() : source.getRotation().x;
        double spawn_yrot = serverPlayerEntity != null ? serverPlayerEntity.getYRot() : source.getRotation().y;

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
            DataManager.saveData(context.getSource().getLevel(), DataManager.mapListLocation, Minehop.mapList);

            Logger.logSuccess(serverPlayerEntity, "Set map spawn \\/\n" + StringFormatting.limitDecimals(gson.toJson(spawnData)));
            consoleReply(context, serverPlayerEntity, "Set spawn of " + name, true);
        }
        else {
            Logger.logSuccess(serverPlayerEntity, "There is no map called " + name + ".");
            consoleReply(context, serverPlayerEntity, "There is no map called " + name + ".", false);
        }
    }

    // Fields `map manage set` may change. Identity (name/worldKey/spawn), ownership, plot bounds and
    // rating/play counters are deliberately excluded: they have their own commands or are maintained by the mod.
    private static final List<String> SETTABLE_MAP_FIELDS = List.of(
            "surf", "kz", "hns", "arena", "preserve_speed", "difficulty", "description",
            "movement_override", "movement_sv_friction", "movement_sv_accelerate", "movement_sv_airaccelerate",
            "movement_sv_maxairspeed", "movement_sv_jump_impulse", "movement_speed_mul", "movement_sv_gravity",
            "movement_sv_stopspeed", "movement_speed_coefficient", "movement_speed_cap", "movement_auto_step_up",
            "movement_css_crouch_jump", "movement_disable_sprint", "movement_fall_damage"
    );

    // `map manage set <map> <field> <value>`: sets one map setting without the GUI, so maps can be configured
    // from the server console (scripted map builds). Movement values go through applyMovementSettings, so they
    // get exactly the clamping the map creator GUI applies.
    private static int handleSetField(CommandContext<CommandSourceStack> context) {
        ServerPlayer serverPlayerEntity = context.getSource().getPlayer();
        String name = StringArgumentType.getString(context, "map_name");
        String field = StringArgumentType.getString(context, "field");
        String rawValue = StringArgumentType.getString(context, "value").trim();

        DataManager.MapData mapData = DataManager.getMap(name);
        if (mapData == null) {
            Logger.logFailure(serverPlayerEntity, "There is no map called " + name + ".");
            consoleReply(context, serverPlayerEntity, "There is no map called " + name + ".", false);
            return 0;
        }
        if (!SETTABLE_MAP_FIELDS.contains(field)) {
            Logger.logFailure(serverPlayerEntity, "Unknown map field " + field + ". Settable: " + String.join(", ", SETTABLE_MAP_FIELDS));
            consoleReply(context, serverPlayerEntity, "Unknown map field " + field + ". Settable: " + String.join(", ", SETTABLE_MAP_FIELDS), false);
            return 0;
        }

        try {
            java.lang.reflect.Field target = DataManager.MapData.class.getField(field);
            Class<?> type = target.getType();
            if (type == boolean.class) {
                if (!rawValue.equalsIgnoreCase("true") && !rawValue.equalsIgnoreCase("false")) {
                    throw new IllegalArgumentException("expected true or false");
                }
                target.setBoolean(mapData, Boolean.parseBoolean(rawValue));
            } else if (type == int.class) {
                int value = Integer.parseInt(rawValue);
                if (field.equals("difficulty")) {
                    value = Math.max(0, Math.min(5, value));
                }
                target.setInt(mapData, value);
            } else if (type == double.class) {
                double value = Double.parseDouble(rawValue);
                if (!Double.isFinite(value)) {
                    throw new IllegalArgumentException("expected a finite number");
                }
                target.setDouble(mapData, value);
            } else if (type == String.class) {
                String value = rawValue;
                if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
                    value = value.substring(1, value.length() - 1);
                }
                target.set(mapData, DescriptionCensor.sanitize(value));
            } else {
                throw new IllegalArgumentException("unsupported field type");
            }
        } catch (NoSuchFieldException | IllegalAccessException | IllegalArgumentException exception) {
            String message = "Could not set " + field + " to '" + rawValue + "': " + exception.getMessage();
            Logger.logFailure(serverPlayerEntity, message);
            consoleReply(context, serverPlayerEntity, message, false);
            return 0;
        }

        if (field.startsWith("movement_")) {
            mapData.applyMovementSettings(
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
                    mapData.movement_speed_cap,
                    mapData.movement_auto_step_up,
                    mapData.movement_css_crouch_jump,
                    mapData.movement_disable_sprint,
                    mapData.movement_fall_damage
            );
        }
        DataManager.saveData(context.getSource().getLevel(), DataManager.mapListLocation, Minehop.mapList);
        syncMapsForAll(context.getSource().getServer());

        String result;
        try {
            result = String.valueOf(DataManager.MapData.class.getField(field).get(mapData));
        } catch (NoSuchFieldException | IllegalAccessException exception) {
            result = rawValue;
        }
        Logger.logSuccess(serverPlayerEntity, "Set " + field + " of " + name + " to " + result + ".");
        consoleReply(context, serverPlayerEntity, "Set " + field + " of " + name + " to " + result + ".", true);
        return Command.SINGLE_SUCCESS;
    }

    // Feedback for commands run without a player (server console / RCON); players keep the existing chat messages.
    private static void consoleReply(CommandContext<CommandSourceStack> context, ServerPlayer player, String message, boolean success) {
        if (player != null) {
            return;
        }
        if (success) {
            context.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal(message), false);
        } else {
            context.getSource().sendFailure(net.minecraft.network.chat.Component.literal(message));
        }
    }

    private static void handleToggleArena(CommandContext<CommandSourceStack> context) {
        ServerPlayer serverPlayerEntity = context.getSource().getPlayer();

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
            DataManager.saveData(context.getSource().getLevel(), DataManager.mapListLocation, Minehop.mapList);

            Logger.logSuccess(serverPlayerEntity, "Toggled arena mode to " + toggleData.arena);
        }
        else {
            Logger.logSuccess(serverPlayerEntity, "There is no map called " + name + ".");
        }
    }

    private static void handleTogglePreserveSpeed(CommandContext<CommandSourceStack> context) {
        ServerPlayer serverPlayerEntity = context.getSource().getPlayer();

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
            DataManager.saveData(context.getSource().getLevel(), DataManager.mapListLocation, Minehop.mapList);

            Logger.logSuccess(serverPlayerEntity, "Toggled preserve speed on reset to " + toggleData.preserve_speed + " for " + name + ".");
        }
        else {
            Logger.logSuccess(serverPlayerEntity, "There is no map called " + name + ".");
        }
    }

    private static void handleToggleHNS(CommandContext<CommandSourceStack> context) {
        ServerPlayer serverPlayerEntity = context.getSource().getPlayer();

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
            DataManager.saveData(context.getSource().getLevel(), DataManager.mapListLocation, Minehop.mapList);

            Logger.logSuccess(serverPlayerEntity, "Toggled hns mode to " + toggleData.hns);
        }
        else {
            Logger.logSuccess(serverPlayerEntity, "There is no map called " + name + ".");
        }
    }

    private static void handleToggleKZ(CommandContext<CommandSourceStack> context) {
        ServerPlayer serverPlayerEntity = context.getSource().getPlayer();

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
            DataManager.saveData(context.getSource().getLevel(), DataManager.mapListLocation, Minehop.mapList);

            Logger.logSuccess(serverPlayerEntity, "Toggled kz mode to " + toggleData.kz);
        }
        else {
            Logger.logSuccess(serverPlayerEntity, "There is no map called " + name + ".");
        }
    }

    private static void handleListTop(CommandContext<CommandSourceStack> context) {
        ServerPlayer serverPlayerEntity = context.getSource().getPlayer();
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

    private static void handleList(CommandContext<CommandSourceStack> context) {
        ServerPlayer serverPlayerEntity = context.getSource().getPlayer();

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

    private static void handleInfo(CommandContext<CommandSourceStack> context) {
        ServerPlayer serverPlayerEntity = context.getSource().getPlayer();

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

    private static void suggestKnownPlayerNames(CommandSourceStack source, SuggestionsBuilder builder) {
        for (String playerName : collectKnownPlayerNames(source)) {
            builder.suggest(playerName, new LiteralMessage(playerName));
        }
    }

    private static Set<String> collectKnownPlayerNames(CommandSourceStack source) {
        Set<String> playerNames = new LinkedHashSet<>();
        if (source != null && source.getServer() != null) {
            for (ServerPlayer player : source.getServer().getPlayerList().getPlayers()) {
                if (player != null && player.getScoreboardName() != null && !player.getScoreboardName().isBlank()) {
                    playerNames.add(player.getScoreboardName());
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
        for (ServerPlayer worldPlayer : server.getPlayerList().getPlayers()) {
            PacketHandler.sendMaps(worldPlayer);
        }
    }

}
