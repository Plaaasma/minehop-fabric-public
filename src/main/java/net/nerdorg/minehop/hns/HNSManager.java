package net.nerdorg.minehop.hns;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.commands.SpectateCommands;
import net.nerdorg.minehop.config.ConfigWrapper;
import net.nerdorg.minehop.data.DataManager;
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
        ServerTickEvents.END_SERVER_TICK.register((server) -> {
            handleMapTimers(server);
            resetMapHasTaggers();

            for (ServerPlayerEntity playerEntity : server.getPlayerManager().getPlayerList()) {
                if (!taggedMap.containsKey(playerEntity.getEntityName())) {
                    taggedMap.put(playerEntity.getEntityName(), false);
                    playerEntity.setGlowing(false);
                }
                else {
                    DataManager.MapData mapData = ZoneUtil.getCurrentMap(playerEntity);
                    if (taggedMap.get(playerEntity.getEntityName())) {
                        if (mapData != null) {
                            if (!mapData.hns) {
                                taggedMap.put(playerEntity.getEntityName(), false);
                            }
                        }
                    }

                    if (mapData != null) {
                        boolean isTagged = taggedMap.get(playerEntity.getEntityName());
                        if (isTagged) {
                            mapHasTaggers.put(mapData.name, true);

                            playerEntity.setGlowing(true);
                        } else {
                            playerEntity.setGlowing(false);
                        }
                    }
                    else {
                        playerEntity.setGlowing(false);
                    }
                }
            }

            addTaggerIfNonePresent(server);
        });

        ServerLivingEntityEvents.ALLOW_DAMAGE.register((livingEntity, damageSource, amount) -> {
            if (livingEntity instanceof PlayerEntity player) {
                DataManager.MapData mapData = ZoneUtil.getCurrentMap(player);
                if (mapData != null) {
                    if (mapData.hns) {
                        Entity sourceEntity = damageSource.getSource();
                        if (sourceEntity != null) {
                            if (sourceEntity instanceof PlayerEntity sourcePlayer) {
                                if (!sourcePlayer.isCreative() && !sourcePlayer.isSpectator() && taggedMap.containsKey(sourcePlayer.getEntityName())) {
                                    boolean sourceIsTagged = taggedMap.get(sourcePlayer.getEntityName());
                                    if (sourceIsTagged) {
                                        Logger.logFailure(player, "You were tagged by " + sourcePlayer.getEntityName());
                                        Logger.logSuccess(sourcePlayer, "You tagged " + player.getEntityName());
                                        taggedMap.put(player.getEntityName(), true);
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
                            mapTimers.put(mapData.name, server.getTicks());
                        }
                    }

                    if (mapTimers.containsKey(mapData.name)) {
                        int startTime = mapTimers.get(mapData.name);
                        if (server.getTicks() >= startTime + 3600) {
                            removeAllTaggersAndReset(mapData.name, server);
                            mapTimers.put(mapData.name, server.getTicks());
                        } else {
                            int timeDif = server.getTicks() - startTime;
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
        for (ServerPlayerEntity playerEntity : server.getPlayerManager().getPlayerList()) {
            DataManager.MapData mapData = ZoneUtil.getCurrentMap(playerEntity);
            if (mapData != null) {
                if (mapData.name.equals(mapName)) {
                    Logger.logSuccess(playerEntity, message);
                }
            }
        }
    }

    private static void assignRandomTagger(String mapName, MinecraftServer server) {
        List<ServerPlayerEntity> playersOnMap = new ArrayList<>();
        for (ServerPlayerEntity playerEntity : server.getPlayerManager().getPlayerList()) {
            DataManager.MapData mapData = ZoneUtil.getCurrentMap(playerEntity);
            if (mapData != null) {
                if (mapData.name.equals(mapName)) {
                    playersOnMap.add(playerEntity);
                }
            }
        }
        if (!playersOnMap.isEmpty()) {
            ServerPlayerEntity randomPlayer = playersOnMap.get(random.nextInt(playersOnMap.size()));
            Logger.logFailure(randomPlayer, "You were randomly seleceted to be tagged because nobody was tagged.");
            taggedMap.put(randomPlayer.getEntityName(), true);
        }
    }

    private static boolean resetIfAllTagged(String mapName, MinecraftServer server) {
        boolean allTagged = true;
        int players = 0;
        for (ServerPlayerEntity playerEntity : server.getPlayerManager().getPlayerList()) {
            DataManager.MapData mapData = ZoneUtil.getCurrentMap(playerEntity);
            if (mapData != null) {
                if (mapData.name.equals(mapName)) {
                    players += 1;
                    if (taggedMap.containsKey(playerEntity.getEntityName())) {
                        boolean tagged = taggedMap.get(playerEntity.getEntityName());
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
        for (ServerPlayerEntity playerEntity : server.getPlayerManager().getPlayerList()) {
            DataManager.MapData mapData = ZoneUtil.getCurrentMap(playerEntity);
            if (mapData != null) {
                if (mapData.name.equals(mapName)) {
                    taggedMap.remove(playerEntity.getEntityName());
                }
            }
        }
    }

    private static void removeAllTaggersAndReset(String mapName, MinecraftServer server) {
        for (ServerPlayerEntity playerEntity : server.getPlayerManager().getPlayerList()) {
            DataManager.MapData mapData = ZoneUtil.getCurrentMap(playerEntity);
            if (mapData != null) {
                if (mapData.name.equals(mapName)) {
                    taggedMap.remove(playerEntity.getEntityName());
                    ServerWorld foundWorld = null;
                    for (ServerWorld serverWorld : server.getWorlds()) {
                        if (serverWorld.getRegistryKey().toString().equals(mapData.worldKey)) {
                            foundWorld = serverWorld;
                            break;
                        }
                    }
                    if (foundWorld != null) {
                        List<Vec3d> spawnCheck = new ArrayList<>();
                        spawnCheck.add(new Vec3d(mapData.x, mapData.y, mapData.z));
                        spawnCheck.add(new Vec3d(mapData.xrot, mapData.yrot, 0));

                        List<List<Vec3d>> checkpointPositions = new ArrayList<>();
                        if (mapData.checkpointPositions != null) {
                            checkpointPositions.addAll(mapData.checkpointPositions);
                        }
                        checkpointPositions.add(spawnCheck);

                        List<Vec3d> randomCheckpoint = checkpointPositions.get(random.nextInt(0, checkpointPositions.size()));
                        Vec3d targetPos = randomCheckpoint.get(0);
                        Vec3d rotPos = randomCheckpoint.get(1);
                        boolean tagged = false;
                        if (taggedMap.containsKey(playerEntity.getEntityName())) {
                            tagged = taggedMap.get(playerEntity.getEntityName());
                        }
                        Logger.logSuccess(playerEntity, "HNS round over, " + (tagged ? "you got tagged :(" : "you survived as a hider!"));
                        playerEntity.teleport(foundWorld, targetPos.getX(), targetPos.getY(), targetPos.getZ(), PositionFlag.VALUES, (float) rotPos.getY(), (float) rotPos.getX());
                    }
                }
            }
        }
    }
}
