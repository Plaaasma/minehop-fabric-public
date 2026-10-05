package net.nerdorg.minehop.hns;

import net.nerdorg.minehop.platform.Services;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.commands.SpectateCommands;
import net.nerdorg.minehop.config.ConfigWrapper;
import net.nerdorg.minehop.data.DataManager;
import net.nerdorg.minehop.entity.ModEntities;
import net.nerdorg.minehop.entity.custom.GamemodeEntity;
import net.nerdorg.minehop.entity.custom.ResetEntity;
import net.nerdorg.minehop.item.ModItems;
import net.nerdorg.minehop.networking.PacketHandler;
import net.nerdorg.minehop.util.Logger;
import net.nerdorg.minehop.util.ZoneUtil;

import java.util.*;

public class HNSManager {
    public static HashMap<String, Boolean> taggedMap = new HashMap<>();
    public static HashMap<String, Boolean> mapHasTaggers = new HashMap<>();
    public static HashMap<String, Integer> mapTimers = new HashMap<>();

    private static final Random random = new Random();

    public static void register() {
        Services.EVENTS.onServerTickEnd((server) -> {
            handleMapTimers(server);
            resetMapHasTaggers();

            for (ServerPlayer playerEntity : server.getPlayerList().getPlayers()) {
                if (!taggedMap.containsKey(playerEntity.getScoreboardName())) {
                    taggedMap.put(playerEntity.getScoreboardName(), false);
                    playerEntity.setGlowingTag(false);
                }
                else {
                    DataManager.MapData mapData = ZoneUtil.getCurrentMap(playerEntity);
                    if (taggedMap.get(playerEntity.getScoreboardName())) {
                        if (mapData != null) {
                            if (!mapData.hns) {
                                taggedMap.put(playerEntity.getScoreboardName(), false);
                            }
                        }
                    }

                    if (mapData != null) {
                        boolean isTagged = taggedMap.get(playerEntity.getScoreboardName());
                        if (isTagged) {
                            mapHasTaggers.put(mapData.name, true);

                            playerEntity.setGlowingTag(true);
                        } else {
                            playerEntity.setGlowingTag(false);
                        }
                    }
                    else {
                        playerEntity.setGlowingTag(false);
                    }
                }
            }

            addTaggerIfNonePresent(server);
        });

        Services.EVENTS.onAllowDamage((livingEntity, damageSource, amount) -> {
            if (livingEntity instanceof Player player) {
                DataManager.MapData mapData = ZoneUtil.getCurrentMap(player);
                if (mapData != null) {
                    if (mapData.hns) {
                        Entity sourceEntity = damageSource.getDirectEntity();
                        if (sourceEntity != null) {
                            if (sourceEntity instanceof Player sourcePlayer) {
                                if (!sourcePlayer.isCreative() && !sourcePlayer.isSpectator() && taggedMap.containsKey(sourcePlayer.getScoreboardName())) {
                                    boolean sourceIsTagged = taggedMap.get(sourcePlayer.getScoreboardName());
                                    if (sourceIsTagged) {
                                        Logger.logFailure(player, "You were tagged by " + sourcePlayer.getScoreboardName());
                                        Logger.logSuccess(sourcePlayer, "You tagged " + player.getScoreboardName());
                                        taggedMap.put(player.getScoreboardName(), true);
                                    }
                                }
                            }
                        }
                    }
                }
            }
            return true;
        });
    }

    private static void resetMapHasTaggers() {
        for (DataManager.MapData mapData : Minehop.mapList) {
            if (mapData.hns) {
                mapHasTaggers.put(mapData.name, false);
            }
        }
    }

    private static void handleMapTimers(MinecraftServer server) {
        for (DataManager.MapData mapData : Minehop.mapList) {
            if (mapData.hns) {
                if (!resetIfAllTagged(mapData.name, server)) {
                    if (mapHasTaggers.containsKey(mapData.name)) {
                        if (!mapHasTaggers.get(mapData.name)) {
                            mapTimers.put(mapData.name, server.getTickCount());
                        }
                    }

                    if (mapTimers.containsKey(mapData.name)) {
                        int startTime = mapTimers.get(mapData.name);
                        if (server.getTickCount() >= startTime + 3600) {
                            removeAllTaggersAndReset(mapData.name, server);
                            mapTimers.put(mapData.name, server.getTickCount());
                        } else {
                            int timeDif = server.getTickCount() - startTime;
                            if (timeDif % 1200 == 0) {
                                logToAllParticipants(mapData.name, server, (((3600 - timeDif) / 20) / 60) + " Minutes remaining in HNS round.");
                            }
                        }
                    }
                }
            }
        }
    }

    private static void addTaggerIfNonePresent(MinecraftServer server) {
        for (DataManager.MapData mapData : Minehop.mapList) {
            if (mapData.hns) {
                if (mapHasTaggers.containsKey(mapData.name)) {
                    boolean hasTaggers = mapHasTaggers.get(mapData.name);
                    if (!hasTaggers) {
                        assignRandomTagger(mapData.name, server);
                    }
                }
            }
        }
    }

    private static void logToAllParticipants(String mapName, MinecraftServer server, String message) {
        for (ServerPlayer playerEntity : server.getPlayerList().getPlayers()) {
            DataManager.MapData mapData = ZoneUtil.getCurrentMap(playerEntity);
            if (mapData != null) {
                if (mapData.name.equals(mapName)) {
                    Logger.logSuccess(playerEntity, message);
                }
            }
        }
    }

    private static void assignRandomTagger(String mapName, MinecraftServer server) {
        List<ServerPlayer> playersOnMap = new ArrayList<>();
        for (ServerPlayer playerEntity : server.getPlayerList().getPlayers()) {
            DataManager.MapData mapData = ZoneUtil.getCurrentMap(playerEntity);
            if (mapData != null) {
                if (mapData.name.equals(mapName)) {
                    playersOnMap.add(playerEntity);
                }
            }
        }
        if (!playersOnMap.isEmpty()) {
            ServerPlayer randomPlayer = playersOnMap.get(random.nextInt(playersOnMap.size()));
            Logger.logFailure(randomPlayer, "You were randomly selected to be tagged because nobody was tagged.");
            taggedMap.put(randomPlayer.getScoreboardName(), true);
        }
    }

    private static boolean resetIfAllTagged(String mapName, MinecraftServer server) {
        boolean allTagged = true;
        int players = 0;
        for (ServerPlayer playerEntity : server.getPlayerList().getPlayers()) {
            DataManager.MapData mapData = ZoneUtil.getCurrentMap(playerEntity);
            if (mapData != null) {
                if (mapData.name.equals(mapName)) {
                    players += 1;
                    if (taggedMap.containsKey(playerEntity.getScoreboardName())) {
                        boolean tagged = taggedMap.get(playerEntity.getScoreboardName());
                        if (!tagged) {
                            allTagged = false;
                            break;
                        }
                    }
                }
            }
        }

        if (allTagged && players > 1) {
            removeAllTaggersAndReset(mapName, server);
            return true;
        }

        return false;
    }

    private static void removeAllTaggers(String mapName, MinecraftServer server) {
        for (ServerPlayer playerEntity : server.getPlayerList().getPlayers()) {
            DataManager.MapData mapData = ZoneUtil.getCurrentMap(playerEntity);
            if (mapData != null) {
                if (mapData.name.equals(mapName)) {
                    taggedMap.remove(playerEntity.getScoreboardName());
                }
            }
        }
    }

    private static void removeAllTaggersAndReset(String mapName, MinecraftServer server) {
        for (ServerPlayer playerEntity : server.getPlayerList().getPlayers()) {
            DataManager.MapData mapData = ZoneUtil.getCurrentMap(playerEntity);
            if (mapData != null) {
                if (mapData.name.equals(mapName)) {
                    ServerLevel foundWorld = null;
                    for (ServerLevel serverWorld : server.getAllLevels()) {
                        if (serverWorld.dimension().toString().equals(mapData.worldKey)) {
                            foundWorld = serverWorld;
                            break;
                        }
                    }
                    if (foundWorld != null) {
                        List<Vec3> spawnCheck = new ArrayList<>();
                        spawnCheck.add(new Vec3(mapData.x, mapData.y, mapData.z));
                        spawnCheck.add(new Vec3(mapData.xrot, mapData.yrot, 0));

                        List<List<Vec3>> checkpointPositions = new ArrayList<>();
                        if (mapData.checkpointPositions != null) {
                            checkpointPositions.addAll(mapData.checkpointPositions);
                        }
                        checkpointPositions.add(spawnCheck);

                        List<Vec3> randomCheckpoint = checkpointPositions.get(random.nextInt(0, checkpointPositions.size()));
                        Vec3 targetPos = randomCheckpoint.get(0);
                        Vec3 rotPos = randomCheckpoint.get(1);
                        boolean tagged = false;
                        if (taggedMap.containsKey(playerEntity.getScoreboardName())) {
                            tagged = taggedMap.get(playerEntity.getScoreboardName());
                        }
                        Logger.logSuccess(playerEntity, "HNS round over, " + (tagged ? "you got tagged :(" : "you survived as a hider!"));
                        ZoneUtil.teleportTo(playerEntity, ZoneUtil.makeTeleportTarget(foundWorld, targetPos, (float) rotPos.y(), (float) rotPos.x()));
                    }
                    taggedMap.remove(playerEntity.getScoreboardName());
                }
            }
        }
    }
}
