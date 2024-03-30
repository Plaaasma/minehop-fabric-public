package net.nerdorg.minehop.config;

import me.shedaniel.autoconfig.AutoConfig;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.nerdorg.minehop.networking.PacketHandler;

public class ConfigWrapper {
    public static MinehopConfig config;

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register((server) -> {
            for (ServerPlayerEntity playerEntity : server.getPlayerManager().getPlayerList()) {
                PacketHandler.sendConfigToClient(playerEntity, ConfigWrapper.config);
            }
        });
    }

    public static void loadConfig() {
        config = AutoConfig.getConfigHolder(MinehopConfig.class).getConfig();
    }
}
