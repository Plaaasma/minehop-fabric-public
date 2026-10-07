package net.nerdorg.minehop.replays;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.anticheat.stream.MovementValidator;
import net.nerdorg.minehop.networking.payloads.SendEfficiencyPayload;
import net.nerdorg.minehop.platform.Services;
import net.nerdorg.minehop.spectate.SpectateSessions;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * The jump stats recorded into replay frames and shown on spectators' HUDs: jump count, speed at the last take-off
 * and strafe efficiency.
 *
 * <p>Jump count and take-off speed are derived on the server from the movement it accepted
 * ({@link MovementValidator#jumpCount}), so a client can't put made-up values into its replay or its spectators'
 * HUD. (Before, clients were supposed to send these unprompted - nothing ever asked them to, so every frame held 0,
 * and any client could inject whatever it liked.)
 *
 * <p>Efficiency needs the view-angle sweep only the client's movement code does, so it is asked for: while a player
 * is being recorded or spectated the server sends a request ({@link SendEfficiencyPayload}) at most every
 * {@link #REQUEST_INTERVAL_TICKS} ticks and accepts exactly one reply to it, clamped to 0..100. Unsolicited replies
 * are dropped. The request carries a negative nonce: clients up to 1.1.6 copy the request value into the reply, so
 * a reply that echoes the nonce identifies such a client, which has no efficiency to give (it reports 0 or stale
 * values); it isn't asked again and its efficiency stays 0. A newer client answers with its real efficiency and
 * never echoes. Efficiency is display data only; nothing competitive depends on it.
 */
public final class RunStats {
    public static final int REQUEST_INTERVAL_TICKS = 5;
    /** A request older than this without a reply is given up (lost or ignored), so the next one can be sent. */
    private static final int REQUEST_TIMEOUT_TICKS = 40;

    public record Snapshot(int jumpCount, double lastJumpSpeed, double efficiency) {
        public static final Snapshot NONE = new Snapshot(0, 0.0D, 0.0D);
    }

    private static final class EfficiencyState {
        double efficiency;
        double pendingNonce = Double.NaN;
        long requestTick;
        boolean legacyClient;
    }

    private static final Map<UUID, EfficiencyState> EFFICIENCY = new HashMap<>();
    private static final Random NONCES = new Random();

    private RunStats() {
    }

    public static void register() {
        Services.EVENTS.onServerTickEnd(RunStats::tick);
        Services.EVENTS.onServerStopped(server -> EFFICIENCY.clear());
    }

    /** The player's current stats (efficiency 0 until their client answered a request). */
    public static Snapshot of(ServerPlayer player) {
        if (player == null) {
            return Snapshot.NONE;
        }
        EfficiencyState state = EFFICIENCY.get(player.getUUID());
        return new Snapshot(MovementValidator.jumpCount(player), MovementValidator.lastJumpSpeed(player),
                state == null ? 0.0D : state.efficiency);
    }

    public static void forget(ServerPlayer player) {
        if (player != null) {
            EFFICIENCY.remove(player.getUUID());
        }
    }

    /** A client's stats reply (server thread). Only the efficiency is used, and only as the answer to a request. */
    public static void onReply(ServerPlayer player, double efficiency) {
        EfficiencyState state = player == null ? null : EFFICIENCY.get(player.getUUID());
        if (state == null || Double.isNaN(state.pendingNonce)) {
            return; // not asked for
        }
        double nonce = state.pendingNonce;
        state.pendingNonce = Double.NaN;
        if (efficiency == nonce) {
            state.legacyClient = true; // echoed the request: a client without real efficiency to report
            state.efficiency = 0.0D;
            return;
        }
        if (Double.isFinite(efficiency)) {
            state.efficiency = Math.max(0.0D, Math.min(100.0D, efficiency));
        }
    }

    private static void tick(MinecraftServer server) {
        long now = server.getTickCount();
        if (now % REQUEST_INTERVAL_TICKS != 0) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            boolean wanted = Minehop.timerManager.containsKey(player.getScoreboardName())
                    || !SpectateSessions.spectatorsOf(player).isEmpty();
            if (!wanted || player.isSpectator()) {
                continue;
            }
            EfficiencyState state = EFFICIENCY.computeIfAbsent(player.getUUID(), uuid -> new EfficiencyState());
            if (state.legacyClient) {
                continue;
            }
            if (!Double.isNaN(state.pendingNonce) && now - state.requestTick < REQUEST_TIMEOUT_TICKS) {
                continue;
            }
            state.pendingNonce = -1.0D - NONCES.nextInt(1_000_000);
            state.requestTick = now;
            Services.NETWORK.sendToPlayer(player, new SendEfficiencyPayload(state.pendingNonce));
        }
    }
}
