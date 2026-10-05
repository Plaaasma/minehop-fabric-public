package net.nerdorg.minehop.replays;

import net.nerdorg.minehop.platform.Services;
import net.minecraft.server.level.ServerPlayer;
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
        Services.EVENTS.onServerTickEnd(((server) -> {
            for (ServerPlayer playerEntity : server.getPlayerList().getPlayers()) {
                if (Minehop.timerManager.containsKey(playerEntity.getScoreboardName())) {
                    if (replayEntryMap.containsKey(playerEntity.getScoreboardName())) {
                        List<ReplayManager.ReplayEntry> replayEntries = replayEntryMap.get(playerEntity.getScoreboardName());
                        double jump_count = 0;
                        double last_jump_speed = 0;
                        double efficiency = 0;

                        if (Minehop.lastEfficiencyMap.containsKey(playerEntity.getScoreboardName())) {
                            ReplayManager.SSJEntry ssjEntry = Minehop.lastEfficiencyMap.get(playerEntity.getScoreboardName());
                            jump_count = ssjEntry.jump_count;
                            last_jump_speed = ssjEntry.last_jump_speed;
                            efficiency = ssjEntry.efficiency;
                        }
                        replayEntries.add(new ReplayManager.ReplayEntry(
                                playerEntity.getX(),
                                playerEntity.getY(),
                                playerEntity.getZ(),
                                (double) playerEntity.getXRot(),
                                (double) playerEntity.getYHeadRot(),
                                jump_count,
                                last_jump_speed,
                                efficiency
                        ));

                        replayEntryMap.put(playerEntity.getScoreboardName(), replayEntries);
                    }
                    else {
                        List<ReplayManager.ReplayEntry> replayEntries = new ArrayList<>();
                        double jump_count = 0;
                        double last_jump_speed = 0;
                        double efficiency = 0;

                        if (Minehop.lastEfficiencyMap.containsKey(playerEntity.getScoreboardName())) {
                            ReplayManager.SSJEntry ssjEntry = Minehop.lastEfficiencyMap.get(playerEntity.getScoreboardName());
                            jump_count = ssjEntry.jump_count;
                            last_jump_speed = ssjEntry.last_jump_speed;
                            efficiency = ssjEntry.efficiency;
                        }
                        replayEntries.add(new ReplayManager.ReplayEntry(
                                playerEntity.getX(),
                                playerEntity.getY(),
                                playerEntity.getZ(),
                                (double) playerEntity.getXRot(),
                                (double) playerEntity.getYHeadRot(),
                                jump_count,
                                last_jump_speed,
                                efficiency
                        ));

                        replayEntryMap.put(playerEntity.getScoreboardName(), replayEntries);
                    }
                }
            }
        }));
    }
}
