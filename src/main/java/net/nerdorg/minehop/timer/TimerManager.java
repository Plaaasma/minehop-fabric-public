package net.nerdorg.minehop.timer;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.GameRules;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.config.ConfigWrapper;
import net.nerdorg.minehop.data.DataManager;
import net.nerdorg.minehop.networking.PacketHandler;
import net.nerdorg.minehop.util.Logger;

import java.util.HashMap;
import java.util.List;

public class TimerManager {
    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register((server) -> {
            for (String playerName : Minehop.timerManager.keySet()) {
                ServerPlayerEntity serverPlayerEntity = server.getPlayerManager().getPlayer(playerName);
                if (serverPlayerEntity != null) {
                    HashMap<String, Long> timerMap = Minehop.timerManager.get(playerName);
                    List<String> keyList = timerMap.keySet().stream().toList();
                    String mapName = keyList.get(0);
                    DataManager.RecordData personalRecordData = DataManager.getPersonalRecord(playerName, mapName);
                    double personalRecord = 0;
                    if (personalRecordData != null) {
                        personalRecord = personalRecordData.time;
                    }
                    String formattedNumber = String.format("%.5f", (float) (System.nanoTime() - timerMap.get(mapName)) / 1000000000);
                    Logger.logActionBar(serverPlayerEntity, "Time: " + formattedNumber + " PB: " + (personalRecord != 0 ? String.format("%.5f", personalRecord) : "No PB"));
                }
            }
        });
    }
}
