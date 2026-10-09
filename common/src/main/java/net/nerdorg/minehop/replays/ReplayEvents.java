package net.nerdorg.minehop.replays;

import net.minecraft.server.level.ServerPlayer;
import net.nerdorg.minehop.anticheat.stream.ClientTick;
import net.nerdorg.minehop.entity.custom.RunZones;
import net.nerdorg.minehop.platform.Services;

/**
 * Where a player's movement reaches the run timer, the run stats and the replay recorder. A player with a packet stream
 * (every real client) is followed one client tick at a time ({@link #onClientTick}); one without (fake players, test
 * bots) once per server tick.
 */
public class ReplayEvents {
    /**
     * Longest recording kept for one run: 60 minutes at 20 ticks per second (the longest real personal best is
     * about 19 minutes). Recording stops at the cap and a run that finishes past it saves no replay, so a player
     * idling in a run can't grow the store without bound (one idle run was 100 minutes, 120k frames). Pre- and
     * post-run frames come on top.
     */
    public static final int MAX_RECORDED_FRAMES = 20 * 60 * 60;

    /**
     * Server thread: one client tick of a player with a packet stream (see {@link ClientTick}), in packet order. The
     * single entry point from the anticheat's stream to everything that follows a player tick by tick (on versions
     * without ClientTickEnd packets, MovementValidator makes every move packet a tick and calls this the same way).
     */
    public static void onClientTick(ServerPlayer player, ClientTick tick) {
        RunStats.onClientTick(player, tick);
        RunClock.Event event = RunClock.onClientTick(player, tick);
        RunRecorder.onClientTick(player, tick, event, (float) RunStats.of(player).efficiency());
    }

    public static void register() {
        Services.EVENTS.onServerTickEnd(server -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (!RunClock.usesStream(player)) {
                    RunRecorder.recordServerTick(player);
                }
            }
        });
        // Finished runs still taking post-run frames are completed before the players are disconnected and the replay
        // store is closed.
        Services.EVENTS.onServerStopping(server -> RunRecorder.completeAll());
        Services.EVENTS.onServerStopped(server -> {
            RunRecorder.clear();
            RunClock.clear();
            RunZones.clear();
        });
    }
}
