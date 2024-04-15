package net.nerdorg.minehop.config;

import me.shedaniel.autoconfig.AutoConfig;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.commands.SpectateCommands;
import net.nerdorg.minehop.data.DataManager;
import net.nerdorg.minehop.item.ModItems;
import net.nerdorg.minehop.networking.PacketHandler;
import net.nerdorg.minehop.util.ZoneUtil;

import java.util.*;

public class ConfigWrapper {
    public static MinehopConfig config;

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register((server) -> {
            HashMap<String, List<String>> newSpectatorList = new HashMap<>();
            for (ServerPlayerEntity playerEntity : server.getPlayerManager().getPlayerList()) {
                DataManager.MapData currentMap = ZoneUtil.getCurrentMap(playerEntity);

                if (playerEntity.isSpectator() || playerEntity.isCreative()) {
                    if (Minehop.timerManager.containsKey(playerEntity.getNameForScoreboard())) {
                        Minehop.timerManager.remove(playerEntity.getNameForScoreboard());
                    }
                }

                if (newSpectatorList.containsKey(playerEntity.getCameraEntity().getNameForScoreboard())) {
                    List<String> newList = newSpectatorList.get(playerEntity.getCameraEntity().getNameForScoreboard());
                    newList.add(playerEntity.getNameForScoreboard());
                    newSpectatorList.put(playerEntity.getCameraEntity().getNameForScoreboard(), newList);
                }
                else {
                    newSpectatorList.put(playerEntity.getCameraEntity().getNameForScoreboard(), new ArrayList<>(Arrays.asList(playerEntity.getNameForScoreboard())));
                }

                if (currentMap != null) {
                    if (currentMap.hns) {
                        Minehop.speedCapMap.put(playerEntity.getNameForScoreboard(), 0.6);
                    } else {
                        Minehop.speedCapMap.remove(playerEntity.getNameForScoreboard());
                    }
                }
                PacketHandler.sendConfigToClient(playerEntity, ConfigWrapper.config);
                if (playerEntity.isOnGround()) {
                    if (Minehop.efficiencyUpdateMap.containsKey(playerEntity.getNameForScoreboard())) {
                        PacketHandler.sendEfficiency(playerEntity, Minehop.efficiencyUpdateMap.get(playerEntity.getNameForScoreboard()));
                    } else {
                        PacketHandler.sendEfficiency(playerEntity, 0);
                    }
                }
            }
            SpectateCommands.spectatorList = newSpectatorList;
            if (server.getTicks() % 100 == 0) {
                for (ServerPlayerEntity playerEntity : server.getPlayerManager().getPlayerList()) {
                    if (!playerEntity.isCreative()) {
                        DataManager.MapData mapData = ZoneUtil.getCurrentMap(playerEntity);
                        if (mapData != null) {
                            if (mapData.arena) {
                                for (int slotNum = 1; slotNum < playerEntity.getInventory().size(); slotNum++) {
                                    playerEntity.getInventory().setStack(slotNum, new ItemStack(Items.AIR));
                                }
                                playerEntity.getInventory().setStack(0, new ItemStack(ModItems.INSTAGIB_GUN));
                            }
                        }
                    }
                    PacketHandler.sendSpectators(playerEntity);
                    PacketHandler.sendRecords(playerEntity);
                    PacketHandler.sendPersonalRecords(playerEntity);
                }
            }
        });
    }

    public static void loadConfig() {
        config = AutoConfig.getConfigHolder(MinehopConfig.class).getConfig();
    }

    public static void saveConfig(MinehopConfig minehopConfig) {
        AutoConfig.getConfigHolder(MinehopConfig.class).setConfig(minehopConfig);
        AutoConfig.getConfigHolder(MinehopConfig.class).save();
    }
}
