package net.nerdorg.minehop.replays.storage;

/**
 * How a replay's frames map to time: shared by the server's ghost entities and the client's own playback, so both
 * show a run the same way. Pure Java.
 *
 * <p>A tick-stream recording has one frame per client tick, {@link #FRAME_NANOS} apart. Older recordings were sampled
 * once per server tick: one that lag left with clearly fewer frames than its time needs is played stretched to its
 * time ({@link #frameNanos}). Frame {@code i} is at run time {@code (i - runStart) * frame time}; the run's timer shows
 * 0 during the pre-run frames and the run's time from its end-zone entry on ({@link #runSeconds}).
 */
public final class ReplayTiming {
    /** Recorded frames are one client tick (at 20 TPS) apart. */
    public static final long FRAME_NANOS = 50_000_000L;
    /**
     * An older (server-tick) recording with fewer frames than this share of its time's ticks was recorded under lag
     * and is played stretched to its time.
     */
    public static final double LEGACY_STRETCH_BELOW = 0.9D;
    /**
     * In a recording without flags, a step longer than this between two frames is taken for a teleport (~160 blocks/s,
     * more than anyone moves): playback jumps there instead of sliding across. Same rule as the path tool.
     */
    public static final double TELEPORT_STEP = 8.0D;

    private ReplayTiming() {
    }

    /** Playback time per frame, in nanoseconds, for a run of {@code runTime} seconds. */
    public static long frameNanos(ReplayFrames frames, double runTime) {
        if (frames != null && !frames.isEmpty() && !frames.tickStream() && Double.isFinite(runTime) && runTime > 0.0D) {
            double expected = runTime * 1_000_000_000.0D / FRAME_NANOS;
            if (frames.size() < expected * LEGACY_STRETCH_BELOW) {
                return Math.round(runTime * 1_000_000_000.0D / frames.size());
            }
        }
        return FRAME_NANOS;
    }

    /**
     * Seconds the run's timer shows at (fractional) frame {@code position}: 0 before the run's first frame, the run's
     * time from its end-zone entry on (recordings with post-run frames), else the time since the run's first frame,
     * capped at the run's time.
     */
    public static double runSeconds(ReplayFrames frames, double position, long frameNanos, double runTime) {
        double frameSeconds = frameNanos / 1_000_000_000.0D;
        if (frames == null) {
            return position * frameSeconds;
        }
        if (position < frames.runStart()) {
            return 0.0D;
        }
        if (position >= frames.runEnd() - 1 && frames.postFrames() > 0) {
            return runTime;
        }
        double elapsed = (position - frames.runStart()) * frameSeconds;
        return runTime > 0.0D ? Math.min(elapsed, runTime) : elapsed;
    }

    /** The (fractional) frame shown {@code seconds} into the run (negative: before its start), not clamped. */
    public static double positionAt(ReplayFrames frames, double seconds, long frameNanos) {
        return frames.runStart() + seconds * 1_000_000_000.0D / frameNanos;
    }

    /**
     * True if playback must jump into frame {@code i} rather than move there from frame {@code i - 1}: a teleport of
     * the recording (flagged in tick-stream recordings, guessed from the step length in older ones).
     */
    public static boolean jumpsInto(ReplayFrames frames, int i) {
        if (frames == null || i <= 0 || i >= frames.size()) {
            return false;
        }
        if (frames.tickStream()) {
            return frames.isDiscontinuity(i);
        }
        double dx = frames.x(i) - frames.x(i - 1);
        double dy = frames.y(i) - frames.y(i - 1);
        double dz = frames.z(i) - frames.z(i - 1);
        double step2 = dx * dx + dy * dy + dz * dz;
        return !(step2 <= TELEPORT_STEP * TELEPORT_STEP);
    }
}
