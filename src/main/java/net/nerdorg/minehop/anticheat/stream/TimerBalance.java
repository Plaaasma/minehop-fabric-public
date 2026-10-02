package net.nerdorg.minehop.anticheat.stream;

/**
 * Lag-compensated client tick clock (the "Timer" check). Fed on the network thread, so a stalled
 * server thread can never turn a burst of queued packets into a false positive.
 *
 * <p>A vanilla 1.21.2+ client sends exactly one {@code ClientTickEndC2SPacket} per client tick; a move
 * packet with no tick-end since the previous move is also counted, so withholding tick-ends doesn't
 * hide extra ticks. Each tick is worth one server tick of time and real time between arrivals is
 * subtracted. A network stall pushes the balance negative (that time is owed to the client) and the
 * burst afterwards pays it back, so only ticking faster than real time can push it positive. Owed
 * time slowly decays (~50 s time constant) so it can't be banked indefinitely; a tiny client clock
 * drift is absorbed by the same decay.
 *
 * <p>1.20.1 backport: there are no tick-end packets, so only {@link #onMovePacket} is fed and every
 * move packet after the first is one tick (the "no tick-end since the previous move" path). Idle
 * ticks send no packet, which only builds credit, bounded by {@link #CREDIT_FLOOR_NANOS} and the decay.
 */
public final class TimerBalance {
    /** Lag owed to the client is kept up to this much (covers multi-second stalls and server GC). */
    static final long CREDIT_FLOOR_NANOS = -5_000_000_000L;
    /** How far ahead of real time the client may get before it is a violation. */
    static final long VIOLATION_NANOS = 300_000_000L;
    /** Arrival gaps at least this long mean the packets after them may be a catch-up burst. */
    static final long LAG_GAP_NANOS = 500_000_000L;
    /** Violations within this long after such a gap are reported but never lagged back. */
    static final long LAG_BURST_WINDOW_NANOS = 3_000_000_000L;
    static final double DECAY_PER_TICK = 0.001D;

    /** A violation; {@code afterLagBurst} means it may be catch-up after a stall (flag only). */
    public record Violation(long aheadNanos, boolean afterLagBurst) {
    }

    private long lastArrivalNanos = Long.MIN_VALUE;
    private long lastGapNanos = Long.MIN_VALUE;
    // A little credit at join absorbs the ticks queued while the client was still loading.
    private long balanceNanos = -1_000_000_000L;
    private long ticksSeen;
    private boolean tickEndSinceMove = true;
    private boolean expectTeleportEcho;

    /** A tick-end packet arrived. */
    public synchronized Violation onTickEnd(long nowNanos, long nanosPerTick) {
        this.tickEndSinceMove = true;
        return tick(nowNanos, nanosPerTick);
    }

    /** A move packet arrived: if no tick-end came since the previous move, it is an extra tick. */
    public synchronized Violation onMovePacket(long nowNanos, long nanosPerTick) {
        if (this.expectTeleportEcho) {
            // The client echoes one move packet right after confirming a teleport (not a tick).
            this.expectTeleportEcho = false;
            return null;
        }
        if (!this.tickEndSinceMove) {
            return tick(nowNanos, nanosPerTick);
        }
        this.tickEndSinceMove = false;
        return null;
    }

    public synchronized void onTeleportConfirm() {
        this.expectTeleportEcho = true;
    }

    private Violation tick(long nowNanos, long nanosPerTick) {
        this.ticksSeen++;
        if (this.lastArrivalNanos == Long.MIN_VALUE) {
            this.lastArrivalNanos = nowNanos;
            this.lastGapNanos = nowNanos;
            return null;
        }
        long elapsed = Math.max(0L, nowNanos - this.lastArrivalNanos);
        this.lastArrivalNanos = nowNanos;
        if (elapsed >= LAG_GAP_NANOS) {
            this.lastGapNanos = nowNanos;
        }
        this.balanceNanos += nanosPerTick - elapsed;
        this.balanceNanos -= (long) (this.balanceNanos * DECAY_PER_TICK);
        if (this.balanceNanos < CREDIT_FLOOR_NANOS) {
            this.balanceNanos = CREDIT_FLOOR_NANOS;
        }
        if (this.balanceNanos > VIOLATION_NANOS) {
            long ahead = this.balanceNanos;
            this.balanceNanos = 0L;
            return new Violation(ahead, nowNanos - this.lastGapNanos < LAG_BURST_WINDOW_NANOS);
        }
        return null;
    }

    public synchronized long ticksSeen() {
        return this.ticksSeen;
    }

    public synchronized long balanceNanos() {
        return this.balanceNanos;
    }
}
