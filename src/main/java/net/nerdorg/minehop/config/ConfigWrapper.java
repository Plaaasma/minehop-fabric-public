package net.nerdorg.minehop.config;

import me.shedaniel.autoconfig.AutoConfig;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.commands.SpectateCommands;
import net.nerdorg.minehop.networking.PacketHandler;

import java.util.*;

public class ConfigWrapper {
    public static MinehopConfig config;

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register((server) -> {
            for (ServerPlayerEntity playerEntity : server.getPlayerManager().getPlayerList()) {
                PacketHandler.sendConfigToClient(playerEntity, ConfigWrapper.config);
                if (playerEntity.isOnGround()) {
                    if (Minehop.efficiencyUpdateMap.containsKey(playerEntity.getNameForScoreboard())) {
                        PacketHandler.sendEfficiency(playerEntity, Minehop.efficiencyUpdateMap.get(playerEntity.getNameForScoreboard()));
                    } else {
                        PacketHandler.sendEfficiency(playerEntity, 0);
                    }
                }
            }
            HashMap<String, List<String>> newSpectatorList = new HashMap<>();
            if (server.getTicks() % 100 == 0) {
                for (ServerPlayerEntity playerEntity : server.getPlayerManager().getPlayerList()) {
                    if (newSpectatorList.containsKey(playerEntity.getCameraEntity().getNameForScoreboard())) {
                        List<String> newList = newSpectatorList.get(playerEntity.getCameraEntity().getNameForScoreboard());
                        newList.add(playerEntity.getNameForScoreboard());
                        newSpectatorList.put(playerEntity.getCameraEntity().getNameForScoreboard(), newList);
                    }
                    else {
                        newSpectatorList.put(playerEntity.getCameraEntity().getNameForScoreboard(), new ArrayList<>(Arrays.asList(playerEntity.getNameForScoreboard())));
                    }

                    if (!(Minehop.adminList.contains(Objects.requireNonNull(playerEntity.getNameForScoreboard())))) {
                        PacketHandler.sendAntiCheatCheck(playerEntity);
                    }
                }
            }
            SpectateCommands.spectatorList = newSpectatorList;
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
