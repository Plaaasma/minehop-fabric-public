package net.nerdorg.minehop.anticheat.stream;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec3;

/**
 * Per-player movement stream, rebuilt from the client's own packets (one entry per client tick).
 * Everything except {@link #timer} and {@link #pendingPings} is touched only on the server thread.
 */
final class StreamState {
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
    boolean everSentTickEnd;
    int ticksSinceTeleport;
    /** Finalized client ticks (used to check a run's tick count against real time). */
    long clientTicks;
    /** Largest recently measured velocity-transaction round trip (decays). */
    long transactionRttNanos;

    // --- the client tick currently being received ---
    Vec3 tickPos;
    boolean tickHasYaw;
    float tickYaw;
    boolean tickHorizontalCollision;
    /** The client's own on-ground flag (last move packet received; persists across quiet ticks). */
    boolean clientOnGround;

    // --- history: state at the end of the last finalized client tick ---
    Vec3 lastPos;
    Vec3 lastStep;
    boolean lastSupported;
    boolean lastHorizontalCollision;
    /** Consecutive ticks the player ended with ground under the client's friction test. */
    int groundTicks;
    /** Consecutive ticks the player ended 0.20-0.26 above ground (supported, but frictionless). */
    int bandHoverTicks;
    /** Previous tick was a fully checked airborne tick (its step is the exact air velocity). */
    boolean lastWasCheckedAir;
    /** Upper bound on horizontal velocity² entering the next tick. */
    double horizontalVelocityBound2;
    /** Consecutive ticks that bound was carried past the realized movement by a sneak clip. */
    int sneakCarryTicks;
    float lastYaw;
    boolean hasLastYaw;
    Input input = Input.EMPTY;
    Input lastTickInput = Input.EMPTY;
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

    // --- jump stats shown to spectators / stored in replays (MovementValidator.trackJumps) ---
    int jumpCount;
    double lastJumpSpeed;

    /** Position at the end of the last tick that wasn't lagged back (lagback anchor). */
    Vec3 lastGoodPos;

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
    void resetBaseline(Vec3 position) {
        this.lastPos = position;
        this.lastStep = null;
        this.lastWasCheckedAir = false;
        this.horizontalVelocityBound2 = 0.0D;
        this.sneakCarryTicks = 0;
        this.groundTicks = 0;
        this.bandHoverTicks = 0;
        this.inAir = false;
        this.airVy = 0.0D;
        this.airExcess = 0.0D;
        this.lastGoodPos = position;
    }
}
