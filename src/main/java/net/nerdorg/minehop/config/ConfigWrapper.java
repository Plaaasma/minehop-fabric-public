package net.nerdorg.minehop.config;

import me.shedaniel.autoconfig.AutoConfig;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.nerdorg.minehop.networking.PacketHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ConfigWrapper {
    public static MinehopConfig config;

    public static List<String> adminList = List.of(
            "lolrow",
            "Plaaasma",
            "Moriz"
    );

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register((server) -> {
            for (ServerPlayerEntity playerEntity : server.getPlayerManager().getPlayerList()) {
                PacketHandler.sendConfigToClient(playerEntity, ConfigWrapper.config);
            }
            if (server.getTicks() % 100 == 0) {
                for (ServerPlayerEntity playerEntity : server.getPlayerManager().getPlayerList()) {
                    if (!(adminList.contains(Objects.requireNonNull(playerEntity.getDisplayName()).getString()))) {
                        System.out.println(playerEntity.getDisplayName().getString());
                        PacketHandler.sendAntiCheatCheck(playerEntity);
                    }
                }
            }
        });
    }

    public static void loadConfig() {
        config = AutoConfig.getConfigHolder(MinehopConfig.class).getConfig();
    }
}
