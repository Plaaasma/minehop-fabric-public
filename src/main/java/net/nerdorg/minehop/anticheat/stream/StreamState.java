package net.nerdorg.minehop.anticheat.stream;

import net.minecraft.util.math.Vec3d;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player movement stream, rebuilt from the client's own packets (one entry per client tick).
 * Everything except {@link #timer} and {@link #pendingPings} is touched only on the server thread.
 */
final class StreamState {
    /** A velocity transaction in flight. {@code speed} is the magnitude the server sent. */
    record PendingPing(long sentNanos, int carryTicks, double speed) {
    }

    /**
     * 1.21.1 port: stand-in for 1.21.2+'s {@code net.minecraft.util.PlayerInput} (same accessor names
     * so the checks read the same as on 1.21.4). Pre-1.21.2 clients never report their movement keys:
     * {@code PlayerInputC2SPacket} is vehicle-only (sent only while riding) and there is no per-tick
     * input packet. Only sneak and sprint are known, from {@code ClientCommandC2SPacket}
     * (PRESS/RELEASE_SHIFT_KEY, START/STOP_SPRINTING), which the client sends right before that tick's
     * move packet. W/A/S/D and jump stay {@code false} here and every consumer is gated on
     * {@link MovementValidator}'s *_KNOWN flags, so the unknown keys can never create a flag.
     */
    record Input(boolean forward, boolean backward, boolean left, boolean right, boolean jump, boolean sneak,
                 boolean sprint) {
        static final Input DEFAULT = new Input(false, false, false, false, false, false, false);

        Input withSneak(boolean sneak) {
            return new Input(this.forward, this.backward, this.left, this.right, this.jump, sneak, this.sprint);
        }

        Input withSprint(boolean sprint) {
            return new Input(this.forward, this.backward, this.left, this.right, this.jump, this.sneak, sprint);
        }
    }

    final TimerBalance timer = new TimerBalance();
    /** System.nanoTime() of the last teleport request/confirm (any thread reads it). */
    volatile long lastTeleportNanos;
    final Map<Integer, PendingPing> pendingPings = new ConcurrentHashMap<>();

    // --- packet bookkeeping ---
    /** Vanilla ignores move packets between requestTeleport and the client's confirm. */
    boolean awaitingTeleport;
    /** Speed carried by the teleport being awaited (preserve-speed resets). */
    double pendingTeleportSpeed;
    /** The client echoes one Full move packet at the teleport target right after confirming. */
    boolean teleportEchoPending;
    /** Ticks during which movement is only hard-capped, and the baseline is rebuilt from it. */
    int graceTicks;
    /** Largest speed the server itself gave the player during the current grace (teleport/velocity). */
    double graceSpeed;
    /** 1.21.1 port: always false — pre-1.21.2 clients have no tick-end packet (kept for parity). */
    boolean everSentTickEnd;
    int ticksSinceTeleport;
    /** Finalized client ticks (used to check a run's tick count against real time). */
    long clientTicks;
    /** Largest recently measured velocity-transaction round trip (decays). */
    long transactionRttNanos;

    // --- the client tick currently being received ---
    Vec3d tickPos;
    boolean tickHasYaw;
    float tickYaw;
    boolean tickHorizontalCollision;
    /** The client's own on-ground flag (last move packet received; persists across quiet ticks). */
    boolean clientOnGround;

    // --- history: state at the end of the last finalized client tick ---
    Vec3d lastPos;
    Vec3d lastStep;
    boolean lastSupported;
    boolean lastHorizontalCollision;
    /** Consecutive ticks the player ended supported (ground). */
    int groundTicks;
    /** Previous tick was a fully checked airborne tick (its step is the exact air velocity). */
    boolean lastWasCheckedAir;
    /** Upper bound on horizontal velocity² entering the next tick. */
    double horizontalVelocityBound2;
    float lastYaw;
    boolean hasLastYaw;
    Input input = Input.DEFAULT;
    Input lastTickInput = Input.DEFAULT;
    /** Crouch offset the client may still drop after releasing sneak (see MovementValidator.uncrouchDrop). */
    double pendingCrouchDrop;
    int ticksSinceJumpInput = 1000;
    int ticksSinceSneakChange = 1000;
    /**
     * The client may still apply its css crouch lift: sneak was pressed (the lift waits for
     * headroom), or the client may have dropped the offset while sneak stayed held.
     */
    boolean crouchLiftOwed;
    double lastCrouchOffset = Double.NaN;

    // --- vertical air phase ---
    boolean inAir;
    /** Upper bound on the true vertical velocity used for the next tick's move. */
    double airVy;
    /** Height gained above the ballistic arc this air phase; legit only via the crouch offset. */
    double airExcess;

    // --- movement settings; when they change the client applies them a round trip later ---
    double lastAirCap = Double.NaN;
    double lastGravity = Double.NaN;
    double lastJumpVy = Double.NaN;
    double heldAirCap;
    double heldGravity;
    double heldJumpVy;
    int holdTicks;

    /** Position at the end of the last tick that wasn't lagged back (lagback anchor). */
    Vec3d lastGoodPos;

    // --- violation accumulators (unexplained movement, decaying) ---
    double horizontalBuffer;
    double verticalBuffer;
    double strafeBuffer;

    void clearTickAccumulation() {
        this.tickPos = null;
        this.tickHasYaw = false;
        this.tickHorizontalCollision = false;
    }

    /** Forget velocity history: the next ticks rebuild it from realized movement. */
    void resetBaseline(Vec3d position) {
        this.lastPos = position;
        this.lastStep = null;
        this.lastWasCheckedAir = false;
        this.horizontalVelocityBound2 = 0.0D;
        this.groundTicks = 0;
        this.inAir = false;
        this.airVy = 0.0D;
        this.airExcess = 0.0D;
        this.lastGoodPos = position;
    }
}
