package net.nerdorg.minehop.anticheat.stream;

import net.minecraft.world.phys.Vec3;

/**
 * One client tick of a player, as the server received it: what {@link MovementValidator} hands to the rest of the mod
 * (replay recording, run timing, run stats) for every tick in the client's packet stream, in packet order, on the
 * server thread. Every client tick produces exactly one, also while a teleport is unconfirmed and when the client
 * stood still (a 1.21.2+ client ends every tick with a ClientTickEnd packet; on older versions every move packet is a
 * tick).
 *
 * <p>Ticks keep their spacing and order when the server thread stalls: they queue up and are delivered in a burst
 * afterwards, each with the time its packet arrived on the network thread ({@link #arrivalNanos}), which a stall of
 * the server thread doesn't delay.
 *
 * <p><b>1.21.1 port (pre-1.21.2 protocol).</b> A client sends a move packet only for a tick in which it moved (more
 * than 2e-4 blocks since the last position it sent), turned or changed its on-ground flag, plus a position reminder
 * every 20 ticks; a tick in which it stood still sends nothing. Each such quiet tick is handed on as an
 * {@link #inferred} copy of the tick before it, right before the next packet's tick, when the gap between the two
 * packets' arrivals shows that ticks were skipped and the player was at rest across it (see
 * MovementValidator#quietTicksBefore). So the stream still has one tick per client tick for recording and run timing:
 * a launch from standing still starts the run one tick before the packet that left the ground, as the client's own
 * timer does. Inferred ticks don't count in {@link #index} (the anticheat's tick count stays the packets received).
 *
 * @param index           the client tick's number since the player's stream began (the count MovementValidator uses
 *                        for run timing, see {@link MovementValidator#clientTicks}); an inferred tick repeats the
 *                        index of the tick before it
 * @param arrivalNanos    {@code System.nanoTime()} on the network thread when the packet that ended this tick arrived
 *                        (its move packet; for an inferred tick, the next packet's arrival minus the ticks between);
 *                        the server-thread time it was processed if that isn't known ({@code networkTimed} false)
 * @param networkTimed    whether {@code arrivalNanos} is the network arrival time
 * @param position        where the player was at the end of the tick: the position the server accepted from the client,
 *                        or the teleport target while a teleport is unconfirmed (the client's moves are ignored then)
 * @param yaw             the client's yaw at the end of the tick, unwrapped, as the client used it
 * @param pitch           the client's pitch at the end of the tick
 * @param input           the client's movement keys for the tick (1.21.1: only sneak and sprint are known)
 * @param onGround        the client's own on-ground flag at the end of the tick
 * @param discontinuity   the player didn't get here by moving: a teleport, reset, lagback, respawn or dimension change
 *                        happened since the previous tick
 * @param awaitingTeleport the client hasn't confirmed a teleport yet
 * @param jumpCount       jumps in the current bhop chain (server-derived, see MovementValidator#jumpCount)
 * @param lastJumpSpeed   horizontal speed (blocks/tick) at the chain's last take-off
 * @param jumped          the client took off in a jump during this tick
 * @param inferred        1.21.1 port: a quiet tick the client sent no packet for (inferred, a copy of the tick before)
 */
public record ClientTick(long index, long arrivalNanos, boolean networkTimed, Vec3 position, float yaw, float pitch,
                         Input input, boolean onGround, boolean discontinuity, boolean awaitingTeleport, int jumpCount,
                         double lastJumpSpeed, boolean jumped, boolean inferred) {
}
