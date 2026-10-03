package net.nerdorg.minehop.anticheat.stream;

import net.minecraft.util.math.Vec3d;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player movement stream, rebuilt from the client's own packets (one entry per client tick).
 * Everything except {@link #timer} and {@link #pendingPings} is touched only on the server thread.
 */
final class StreamState {
    /**
     * 1.20.1 backport: the key states the server can know. Before 1.21.2 the client reports no
     * movement keys outside vehicles; only sneak and sprint arrive (as {@code ClientCommandC2SPacket}
     * press/release commands). Movement keys are unknown, and jump is unknown so it is always treated
     * as possible ({@link #jump()} = true): unknown input must never produce a flag.
     */
    record StreamInput(boolean sneak, boolean sprint) {
        static final StreamInput DEFAULT = new StreamInput(false, false);

        /** Unknown on 1.20.1: a jump is always considered possible. */
        boolean jump() {
            return true;
        }

        StreamInput withSneak(boolean value) {
            return new StreamInput(value, this.sprint);
        }

        StreamInput withSprint(boolean value) {
            return new StreamInput(this.sneak, value);
        }
    }

    /** A velocity transaction in flight. {@code speed} is the magnitude the server sent. */
    record PendingPing(long sentNanos, int carryTicks, double speed) {
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
    StreamInput input = StreamInput.DEFAULT;
    StreamInput lastTickInput = StreamInput.DEFAULT;
    int ticksSinceJumpInput = 1000;
    int ticksSinceSneakChange = 1000;
    /**
     * The client may still apply its css crouch lift: sneak was pressed (the lift waits for
     * headroom), or the client may have dropped the offset while sneak stayed held.
     */
    boolean crouchLiftOwed;
    double lastCrouchOffset = Double.NaN;
    /** Crouch offset the client may still drop after releasing sneak (see MovementValidator.uncrouchDrop). */
    double pendingCrouchDrop;

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
