package net.nerdorg.minehop.util;

import net.minecraft.server.network.ServerPlayerEntity;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player, per-channel minimum-interval throttle for custom C2S payloads.
 *
 * <p>Checked on the netty receive thread BEFORE the handler schedules work onto the server tick
 * thread, so a flooding client is dropped before it can stall the main loop. Keyed by (uuid,
 * channel); wall-clock based, which is fine for throttling. There was previously no rate-limiting of
 * any kind on custom payloads.
 */
public final class PacketRateLimiter {
    // (uuid|channel) -> last accepted epoch millis. Touched from the netty thread(s); concurrent.
    private static final ConcurrentHashMap<String, Long> LAST_ACCEPTED = new ConcurrentHashMap<>();

    private PacketRateLimiter() {
    }

    /**
     * Returns true if this player may be serviced for {@code channel} now, recording the time.
     * Returns false (drop the packet) if the last accepted packet on this channel was within
     * {@code minIntervalMs}. A null player is always dropped.
     */
    public static boolean allow(ServerPlayerEntity player, String channel, int minIntervalMs) {
        if (player == null) {
            return false;
        }
        if (minIntervalMs <= 0) {
            return true;
        }
        String key = player.getUuid() + "|" + channel;
        long now = System.currentTimeMillis();
        Long previous = LAST_ACCEPTED.get(key);
        if (previous != null && now - previous < minIntervalMs) {
            return false;
        }
        LAST_ACCEPTED.put(key, now);
        return true;
    }

    /** Drop all throttle state for a player (call on disconnect to avoid unbounded growth). */
    public static void clear(UUID uuid) {
        if (uuid == null) {
            return;
        }
        String prefix = uuid + "|";
        LAST_ACCEPTED.keySet().removeIf(key -> key.startsWith(prefix));
    }
}
