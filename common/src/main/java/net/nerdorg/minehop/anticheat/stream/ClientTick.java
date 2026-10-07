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
 * <p><b>1.20.1 (pre-1.21.2 protocol).</b> Every move packet is a client tick, but a client only sends one in a tick
 * in which it moved, turned or changed its ground flag (and a position "reminder" every 20th tick), so standing still
 * leaves gaps. MovementValidator hands on the ticks of such an idle gap too ({@link #inferred}, see
 * MovementValidator#idleTicksBefore): at the previous tick's position, view and ground flag, timed one tick apart
 * back from the packet that ends the gap. They keep replays and run timing on the client's tick clock; they are not
 * validated and not counted in {@link #index} (the anticheat's tick count). The client reports no movement or jump
 * keys before 1.21.2: {@link #input} only carries sneak and sprint.
 *
 * @param index           the client tick's number since the player's stream began (the count MovementValidator uses
 *                        for run timing, see {@link MovementValidator#clientTicks}); 1.20.1: move packets only, an
 *                        inferred idle tick has the number of the packet before it
 * @param arrivalNanos    {@code System.nanoTime()} on the network thread when the packet that ended this tick arrived
 *                        (its ClientTickEnd, or its move packet for a tick without one); the server-thread time it was
 *                        processed if that isn't known ({@code networkTimed} false)
 * @param networkTimed    whether {@code arrivalNanos} is the network arrival time
 * @param position        where the player was at the end of the tick: the position the server accepted from the client,
 *                        or the teleport target while a teleport is unconfirmed (the client's moves are ignored then)
 * @param yaw             the client's yaw at the end of the tick, unwrapped, as the client used it
 * @param pitch           the client's pitch at the end of the tick
 * @param input           the client's movement keys for the tick (1.20.1: sneak and sprint only)
 * @param onGround        the client's own on-ground flag at the end of the tick
 * @param discontinuity   the player didn't get here by moving: a teleport, reset, lagback, respawn or dimension change
 *                        happened since the previous tick
 * @param awaitingTeleport the client hasn't confirmed a teleport yet
 * @param jumpCount       jumps in the current bhop chain (server-derived, see MovementValidator#jumpCount)
 * @param lastJumpSpeed   horizontal speed (blocks/tick) at the chain's last take-off
 * @param jumped          the client took off in a jump during this tick
 * @param streamIndex     1.20.1: the tick's number in the stream including inferred idle ticks (the client's tick
 *                        clock), for comparing a run's ticks with its time
 * @param inferred        1.20.1: an idle tick the client sent no packet for (see above)
 */
public record ClientTick(long index, long arrivalNanos, boolean networkTimed, Vec3 position, float yaw, float pitch,
                         Input input, boolean onGround, boolean discontinuity, boolean awaitingTeleport, int jumpCount,
                         double lastJumpSpeed, boolean jumped, long streamIndex, boolean inferred) {
    /**
     * 1.20.1 stand-in for the 1.21.2+ {@code net.minecraft.world.entity.player.Input} (same accessors). A 1.20.1 client
     * reports only sneak and sprint (ServerboundPlayerCommandPacket); the movement and jump keys are unknown and false.
     */
    public record Input(boolean forward, boolean backward, boolean left, boolean right, boolean jump, boolean shift,
                        boolean sprint) {
        public static final Input EMPTY = new Input(false, false, false, false, false, false, false);
    }
}
