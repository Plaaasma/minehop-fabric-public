package net.nerdorg.minehop.anticheat.stream;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Lag-compensated client tick clock (the "Timer" check). Fed on the network thread, so a stalled
 * server thread can never turn a burst of queued packets into a false positive.
 *
 * <p>A vanilla 1.21.2+ client sends exactly one {@code ClientTickEndC2SPacket} per client tick; a move
 * packet with no tick-end since the previous move is also counted, so withholding tick-ends doesn't
 * hide extra ticks. Each tick is worth one server tick of time and real time between arrivals is
 * subtracted, so the balance only goes positive when the client ticks faster than real time.
 *
 * <p>Time owed to the client (a negative balance) is only kept while something real explains it,
 * otherwise a cheat could bank seconds while idle and then run a timer unnoticed:
 * <ul>
 *   <li><b>Arrival gaps</b> (stalls, freezes): the delayed ticks arrive in a burst right after a
 *   network stall, so a gap's credit is kept for a few seconds. A gap is only credited if the client
 *   also went silent: heartbeat pongs come from the same client thread that ticks and travel the same
 *   connection, so a pong arriving in the middle of a tick gap means the client was alive and
 *   answering but not ticking ("withheld"). Credit left after the window is dropped once the
 *   connection is back to its normal round trip.</li>
 *   <li><b>Uplink queueing</b> (bufferbloat, a saturated upload): ticks then arrive late but steadily
 *   and the heartbeat round trip rises by the same queue delay, so credit up to that rise is kept
 *   until the queue drains. It doesn't decay meanwhile, so the drain can't look like a timer.</li>
 *   <li>A small jitter allowance otherwise.</li>
 * </ul>
 */
public final class TimerBalance {
    /** Absolute cap on time owed to the client. */
    static final long CREDIT_FLOOR_NANOS = -10_000_000_000L;
    /** Owed time always allowed (arrival jitter). */
    static final long JITTER_CREDIT_NANOS = 300_000_000L;
    /** Owed time allowed before any round trip has been measured (right after joining). */
    static final long UNMEASURED_CREDIT_NANOS = 1_000_000_000L;
    /** Upper bound on credit justified by a raised round trip. */
    static final long MAX_QUEUE_CREDIT_NANOS = 8_000_000_000L;
    /** How far ahead of real time the client may get before it is a violation. */
    static final long VIOLATION_NANOS = 300_000_000L;
    /** Arrival gaps at least this long mean the packets after them may be a catch-up burst. */
    static final long LAG_GAP_NANOS = 500_000_000L;
    /** A gap's credit may be paid back (and violations are evidence only) for this long after it. */
    static final long LAG_BURST_WINDOW_NANOS = 3_000_000_000L;
    /**
     * Pongs only prove the client was answering-but-not-ticking when they arrive this far inside a
     * tick gap from both ends: a pong sent in the frame right before a freeze, or followed by a
     * retransmitted tick, can otherwise land just inside it.
     */
    static final long PONG_IN_GAP_MARGIN_NANOS = 300_000_000L;
    /** ...and when at least two of them, this far apart, came from different client frames. */
    static final long PONG_IN_GAP_SPREAD_NANOS = 150_000_000L;
    /**
     * ...and only pongs answered promptly (round trip within this of the baseline): a burst of pings
     * that queued up on a stalled downlink gets answered in one frame and can trickle in over a slow
     * uplink, but those carry a long round trip.
     */
    static final long PROMPT_PONG_SLACK_NANOS = 250_000_000L;
    /** Vanilla catches up at most 10 ticks per frame: always owed after any gap, even a withheld one. */
    static final long CATCHUP_CREDIT_NANOS = 500_000_000L;
    static final long CATCHUP_WINDOW_NANOS = 1_000_000_000L;
    /** Round trip this close to the baseline means no uplink queue. */
    static final long QUEUE_NORMAL_NANOS = 250_000_000L;
    /** The round-trip baseline is the minimum over this long. */
    static final long RTT_BASELINE_WINDOW_NANOS = 60_000_000_000L;
    /** A violation within this long of the previous one means the client keeps running fast. */
    static final long SUSTAINED_WINDOW_NANOS = 10_000_000_000L;
    /** Heartbeats still unanswered after this long while ticks keep arriving are withheld. */
    static final long HEARTBEAT_WITHHELD_NANOS = 10_000_000_000L;
    /** Tiny decay of owed time (absorbs client clock drift over long sessions). */
    static final double DECAY_PER_TICK = 0.0001D;

    /**
     * A violation. {@code afterLagBurst}: it may be catch-up after a stall (flag only).
     * {@code sustained}: another (non-burst) violation happened shortly before, so this is a client
     * that keeps ticking fast rather than a one-off anomaly.
     */
    public record Violation(long aheadNanos, boolean afterLagBurst, boolean sustained) {
    }

    private record RttSample(long atNanos, long rttNanos) {
    }

    private long lastArrivalNanos = Long.MIN_VALUE;
    private long lastViolationNanos = Long.MIN_VALUE;
    // A little credit at join absorbs the ticks queued while the client was still loading.
    private long balanceNanos = -1_000_000_000L;
    /** The part of the owed time that came from arrival gaps (paid back first, dropped later). */
    private long gapCreditNanos;
    private long ticksSeen;
    private boolean tickEndSinceMove = true;
    private boolean expectTeleportEcho;

    private final LinkedHashMap<Integer, Long> outstandingHeartbeats = new LinkedHashMap<>();
    private final ArrayDeque<RttSample> rttSamples = new ArrayDeque<>();
    private long latestRttNanos = -1L;
    /** Heartbeat pongs that arrived since the last tick: {arrival nanos, round trip nanos}. */
    private final ArrayDeque<long[]> pongsSinceTick = new ArrayDeque<>();
    /** Real (silent) gaps make violations evidence-only for a while; this is when the last one ended. */
    private long lastRealGapNanos = Long.MIN_VALUE;
    /** Until when gap credit may still be paid back before it is dropped. */
    private long gapCreditUntilNanos = Long.MIN_VALUE;
    private int withheldGaps;
    private boolean withheldHeartbeats;

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

    /** Server thread: a heartbeat ping with this id was just sent. */
    public synchronized void onHeartbeatSent(int id, long nowNanos) {
        this.outstandingHeartbeats.put(id, nowNanos);
        // A client that never answers can't grow this without bound.
        while (this.outstandingHeartbeats.size() > 400) {
            Iterator<Long> it = this.outstandingHeartbeats.values().iterator();
            it.next();
            it.remove();
        }
    }

    /** Network thread, in packet order: the client answered heartbeat {@code id}. */
    public synchronized void onHeartbeatPong(int id, long nowNanos) {
        Long sent = this.outstandingHeartbeats.remove(id);
        if (sent == null) {
            return;
        }
        // Pongs are answered in order: anything sent before this one is not coming any more.
        Iterator<Map.Entry<Integer, Long>> it = this.outstandingHeartbeats.entrySet().iterator();
        while (it.hasNext() && it.next().getValue() <= sent) {
            it.remove();
        }
        long rtt = Math.max(0L, nowNanos - sent);
        this.latestRttNanos = rtt;
        this.rttSamples.addLast(new RttSample(nowNanos, rtt));
        while (!this.rttSamples.isEmpty() && nowNanos - this.rttSamples.peekFirst().atNanos() > RTT_BASELINE_WINDOW_NANOS) {
            this.rttSamples.removeFirst();
        }
        if (this.lastArrivalNanos != Long.MIN_VALUE && this.pongsSinceTick.size() < 64) {
            this.pongsSinceTick.addLast(new long[]{nowNanos, rtt});
        }
    }

    /** Main thread: tick gaps during which the client kept answering (since the last call). */
    public synchronized int takeWithheldGaps() {
        int n = this.withheldGaps;
        this.withheldGaps = 0;
        return n;
    }

    /** Main thread: heartbeats have gone unanswered for a long time while ticks kept arriving. */
    public synchronized boolean takeWithheldHeartbeats() {
        boolean w = this.withheldHeartbeats;
        this.withheldHeartbeats = false;
        return w;
    }

    private Violation tick(long nowNanos, long nanosPerTick) {
        this.ticksSeen++;
        if (this.lastArrivalNanos == Long.MIN_VALUE) {
            this.lastArrivalNanos = nowNanos;
            this.lastRealGapNanos = nowNanos;
            this.gapCreditUntilNanos = nowNanos + LAG_BURST_WINDOW_NANOS;
            // The join credit covers ticks the client queued while it was loading (it already answers
            // heartbeats on the loading screen, so a round trip exists before the first tick): it is
            // a backlog like a stall's, so it is paid back and dropped like gap credit.
            this.gapCreditNanos = Math.max(0L, -this.balanceNanos);
            this.pongsSinceTick.clear();
            return null;
        }
        long elapsed = Math.max(0L, nowNanos - this.lastArrivalNanos);
        if (elapsed >= LAG_GAP_NANOS) {
            if (answeredDuringGap(this.lastArrivalNanos, nowNanos)) {
                // Alive and answering but not ticking: those ticks were never sent. Only a normal
                // catch-up burst is owed.
                this.withheldGaps++;
                this.gapCreditNanos += Math.min(elapsed - nanosPerTick, CATCHUP_CREDIT_NANOS);
                this.gapCreditUntilNanos = Math.max(this.gapCreditUntilNanos, nowNanos + CATCHUP_WINDOW_NANOS);
            } else {
                this.lastRealGapNanos = nowNanos;
                this.gapCreditNanos += elapsed - nanosPerTick;
                this.gapCreditUntilNanos = Math.max(this.gapCreditUntilNanos, nowNanos + LAG_BURST_WINDOW_NANOS);
            }
        }
        this.lastArrivalNanos = nowNanos;
        this.pongsSinceTick.clear();
        boolean afterGap = nowNanos - this.lastRealGapNanos < LAG_BURST_WINDOW_NANOS;

        long delta = nanosPerTick - elapsed;
        this.balanceNanos += delta;
        if (delta > 0L && this.gapCreditNanos > 0L) {
            this.gapCreditNanos = Math.max(0L, this.gapCreditNanos - delta);
        }
        this.balanceNanos -= (long) (this.balanceNanos * DECAY_PER_TICK);

        long queue = queueDelay(nowNanos);
        if (nowNanos > this.gapCreditUntilNanos && this.gapCreditNanos > 0L && queue >= 0L && queue <= QUEUE_NORMAL_NANOS) {
            // The stall's backlog never came (a freeze, or ticks that were never sent): drop it.
            this.gapCreditNanos = 0L;
        }
        long allowed = (queue < 0L ? UNMEASURED_CREDIT_NANOS : JITTER_CREDIT_NANOS + Math.min(queue, MAX_QUEUE_CREDIT_NANOS))
                + this.gapCreditNanos;
        long floor = Math.max(CREDIT_FLOOR_NANOS, -allowed);
        if (this.balanceNanos < floor) {
            this.balanceNanos = floor;
        }
        this.gapCreditNanos = Math.min(this.gapCreditNanos, Math.max(0L, -this.balanceNanos));

        if (!this.outstandingHeartbeats.isEmpty()
                && nowNanos - this.outstandingHeartbeats.values().iterator().next() > HEARTBEAT_WITHHELD_NANOS) {
            this.withheldHeartbeats = true;
        }

        if (this.balanceNanos > VIOLATION_NANOS) {
            long ahead = this.balanceNanos;
            this.balanceNanos = 0L;
            this.gapCreditNanos = 0L;
            boolean sustained = false;
            if (!afterGap) {
                sustained = this.lastViolationNanos != Long.MIN_VALUE
                        && nowNanos - this.lastViolationNanos < SUSTAINED_WINDOW_NANOS;
                this.lastViolationNanos = nowNanos;
            }
            return new Violation(ahead, afterGap, sustained);
        }
        return null;
    }

    /** At least two heartbeat pongs from different frames arrived well inside the tick gap. */
    private boolean answeredDuringGap(long gapStartNanos, long gapEndNanos) {
        if (this.rttSamples.isEmpty()) {
            return false;
        }
        long baseline = Long.MAX_VALUE;
        for (RttSample sample : this.rttSamples) {
            baseline = Math.min(baseline, sample.rttNanos());
        }
        long first = Long.MAX_VALUE;
        long last = Long.MIN_VALUE;
        int inside = 0;
        for (long[] pong : this.pongsSinceTick) {
            long p = pong[0];
            if (pong[1] <= baseline + PROMPT_PONG_SLACK_NANOS
                    && p - gapStartNanos >= PONG_IN_GAP_MARGIN_NANOS && gapEndNanos - p >= PONG_IN_GAP_MARGIN_NANOS) {
                inside++;
                first = Math.min(first, p);
                last = Math.max(last, p);
            }
        }
        return inside >= 2 && last - first >= PONG_IN_GAP_SPREAD_NANOS;
    }

    /**
     * Current uplink queueing delay estimate: how far the round trip is above its recent minimum,
     * counting heartbeats still in flight (a growing queue shows up there before its pong does).
     * -1 if no round trip has been measured yet.
     */
    private long queueDelay(long nowNanos) {
        if (this.rttSamples.isEmpty()) {
            return -1L;
        }
        long baseline = Long.MAX_VALUE;
        for (RttSample s : this.rttSamples) {
            baseline = Math.min(baseline, s.rttNanos());
        }
        long current = this.latestRttNanos;
        if (!this.outstandingHeartbeats.isEmpty()) {
            current = Math.max(current, nowNanos - this.outstandingHeartbeats.values().iterator().next());
        }
        return Math.max(0L, current - baseline);
    }

    public synchronized long ticksSeen() {
        return this.ticksSeen;
    }

    public synchronized long balanceNanos() {
        return this.balanceNanos;
    }
}
