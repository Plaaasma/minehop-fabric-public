package net.nerdorg.minehop.replays;

import net.nerdorg.minehop.anticheat.stream.ClientTick;
import net.nerdorg.minehop.platform.Services;
import net.minecraft.server.level.ServerPlayer;
import net.nerdorg.minehop.Minehop;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class ReplayEvents {
    public static HashMap<String, List<ReplayManager.ReplayEntry>> replayEntryMap = new HashMap<>();

    /**
     * Longest recording kept for one run: 60 minutes at 20 ticks per second (the longest real personal best is
     * about 19 minutes). Recording stops at the cap and a run that finishes past it saves no replay, so a player
     * idling in a run can't grow the store without bound (one idle run was 100 minutes, 120k frames).
     */
    public static final int MAX_RECORDED_FRAMES = 20 * 60 * 60;

    /**
     * Server thread: one client tick of a player with a packet stream (see {@link ClientTick}), in packet order. The
     * single entry point from the anticheat's stream to everything that follows a player tick by tick.
     */
    public static void onClientTick(ServerPlayer player, ClientTick tick) {
    }

    public static void register() {
        Services.EVENTS.onServerTickEnd(((server) -> {
            for (ServerPlayer playerEntity : server.getPlayerList().getPlayers()) {
                String playerName = playerEntity.getScoreboardName();
                if (!Minehop.timerManager.containsKey(playerName)) {
                    continue;
                }
                List<ReplayManager.ReplayEntry> replayEntries = replayEntryMap.computeIfAbsent(playerName, name -> new ArrayList<>());
                if (replayEntries.size() >= MAX_RECORDED_FRAMES) {
                    continue;
                }
                // Jump count/speed derived by the server, efficiency as answered on request (see RunStats).
                RunStats.Snapshot stats = RunStats.of(playerEntity);
                replayEntries.add(new ReplayManager.ReplayEntry(
                        playerEntity.getX(),
                        playerEntity.getY(),
                        playerEntity.getZ(),
                        (double) playerEntity.getXRot(),
                        (double) playerEntity.getYHeadRot(),
                        stats.jumpCount(),
                        stats.lastJumpSpeed(),
                        stats.efficiency()
                ));
            }
        }));
    }
}
