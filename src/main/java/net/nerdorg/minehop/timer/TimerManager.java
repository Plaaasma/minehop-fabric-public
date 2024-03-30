package net.nerdorg.minehop.timer;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.config.ConfigWrapper;
import net.nerdorg.minehop.networking.PacketHandler;
import net.nerdorg.minehop.util.Logger;

public class TimerManager {
    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register((server) -> {
            for (String playerName : Minehop.timerManager.keySet()) {
                ServerPlayerEntity serverPlayerEntity = server.getPlayerManager().getPlayer(playerName);
                String formattedNumber = String.format("%.2f", (double) Minehop.timerManager.get(playerName) / 20D);
                Logger.logActionBar(serverPlayerEntity, "Time: " + formattedNumber);
                Minehop.timerManager.put(playerName, Minehop.timerManager.get(playerName) + 1);
            }
        });
    }
}
