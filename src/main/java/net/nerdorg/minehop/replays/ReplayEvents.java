package net.nerdorg.minehop.replays;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.config.ConfigWrapper;
import net.nerdorg.minehop.data.DataManager;
import net.nerdorg.minehop.networking.PacketHandler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class ReplayEvents {
    public static HashMap<String, List<ReplayManager.ReplayEntry>> replayEntryMap = new HashMap<>();

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(((server) -> {
            for (ServerPlayerEntity playerEntity : server.getPlayerManager().getPlayerList()) {
                if (Minehop.timerManager.containsKey(playerEntity.getNameForScoreboard())) {
                    if (replayEntryMap.containsKey(playerEntity.getNameForScoreboard())) {
                        List<ReplayManager.ReplayEntry> replayEntries = replayEntryMap.get(playerEntity.getNameForScoreboard());
                        double jump_count = 0;
                        double last_jump_speed = 0;
                        double efficiency = 0;

                        if (Minehop.lastEfficiencyMap.containsKey(playerEntity.getNameForScoreboard())) {
                            ReplayManager.SSJEntry ssjEntry = Minehop.lastEfficiencyMap.get(playerEntity.getNameForScoreboard());
                            jump_count = ssjEntry.jump_count;
                            last_jump_speed = ssjEntry.last_jump_speed;
                            efficiency = ssjEntry.efficiency;
                        }
                        replayEntries.add(new ReplayManager.ReplayEntry(
                                playerEntity.getX(),
                                playerEntity.getY(),
                                playerEntity.getZ(),
                                (double) playerEntity.getPitch(),
                                (double) playerEntity.getHeadYaw(),
                                jump_count,
                                last_jump_speed,
                                efficiency
                        ));

                        replayEntryMap.put(playerEntity.getNameForScoreboard(), replayEntries);
                    }
                    else {
                        List<ReplayManager.ReplayEntry> replayEntries = new ArrayList<>();
                        double jump_count = 0;
                        double last_jump_speed = 0;
                        double efficiency = 0;

                        if (Minehop.lastEfficiencyMap.containsKey(playerEntity.getNameForScoreboard())) {
                            ReplayManager.SSJEntry ssjEntry = Minehop.lastEfficiencyMap.get(playerEntity.getNameForScoreboard());
                            jump_count = ssjEntry.jump_count;
                            last_jump_speed = ssjEntry.last_jump_speed;
                            efficiency = ssjEntry.efficiency;
                        }
                        replayEntries.add(new ReplayManager.ReplayEntry(
                                playerEntity.getX(),
                                playerEntity.getY(),
                                playerEntity.getZ(),
                                (double) playerEntity.getPitch(),
                                (double) playerEntity.getHeadYaw(),
                                jump_count,
                                last_jump_speed,
                                efficiency
                        ));

                        replayEntryMap.put(playerEntity.getNameForScoreboard(), replayEntries);
                    }
                }
            }
        }));
    }
}
