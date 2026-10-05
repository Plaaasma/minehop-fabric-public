package net.nerdorg.minehop.util;

/**
 * Per-player, per-jump strafe statistics, computed the Source/Roblop-bhop faithful way from the
 * ACTUAL before/after horizontal velocity each air tick (robust to the 128Hz substepped air accel,
 * which a re-simulation approach can't measure correctly).
 *
 * Metrics (faithful to shavit bhoptimer / momentum-mod):
 *  - sync%       = goodTicks / measuredTicks * 100   (good = speed actually increased this tick)
 *  - efficiency% = effSum / measuredTicks * 100      (per-tick realized gain / max possible gain)
 *  - gauge       = |viewYawDelta| / optimalAngle * 100, 0..200, 100 = perfect, smoothed over 10 ticks
 *  - strafes     = number of A<->D strafe-key sign flips this jump
 * A tick is "measured" when airborne, moving, and actively turning.
 */
public final class StrafeStats {
    private static final int GAUGE_WINDOW = 10;

    // Per-jump accumulators.
    public int measuredTicks = 0;
    public int goodTicks = 0;
    public double effSum = 0.0D;
    public int strafes = 0;
    public int lastStrafeSign = 0;

    // Live (current) values read by the HUD.
    public double liveSync = 0.0D;
    public double liveEfficiency = 0.0D;
    public double liveGauge = 0.0D;

    // Latched at the previous landing (per-jump summary).
    public double lastJumpSync = 0.0D;
    public double lastJumpEfficiency = 0.0D;
    public int lastJumpStrafes = 0;

    private final double[] gaugeBuffer = new double[GAUGE_WINDOW];
    private int gaugeCount = 0;
    private int gaugeIndex = 0;

    /** Record one air tick. gaugeRatio is only meaningful (and pushed) when the tick is measured. */
    public void recordTick(boolean measured, boolean good, double effTick, double gaugeRatio, int strafeSign) {
        if (strafeSign != 0 && this.lastStrafeSign != 0 && strafeSign != this.lastStrafeSign) {
            this.strafes++;
        }
        if (strafeSign != 0) {
            this.lastStrafeSign = strafeSign;
        }
        if (measured) {
            this.measuredTicks++;
            if (good) {
                this.goodTicks++;
            }
            this.effSum += effTick;
            this.gaugeBuffer[this.gaugeIndex] = gaugeRatio;
            this.gaugeIndex = (this.gaugeIndex + 1) % GAUGE_WINDOW;
            if (this.gaugeCount < GAUGE_WINDOW) {
                this.gaugeCount++;
            }
        }
        this.liveSync = this.measuredTicks > 0 ? (double) this.goodTicks / this.measuredTicks * 100.0D : 0.0D;
        this.liveEfficiency = this.measuredTicks > 0 ? this.effSum / this.measuredTicks * 100.0D : 0.0D;
        double sum = 0.0D;
        for (int i = 0; i < this.gaugeCount; i++) {
            sum += this.gaugeBuffer[i];
        }
        this.liveGauge = this.gaugeCount > 0 ? sum / this.gaugeCount : 0.0D;
    }

    /** Latch the just-finished jump's summary and reset accumulators for the next jump arc. */
    public void latchAndReset() {
        if (this.measuredTicks > 0) {
            this.lastJumpSync = this.liveSync;
            this.lastJumpEfficiency = this.liveEfficiency;
            this.lastJumpStrafes = this.strafes;
        }
        this.measuredTicks = 0;
        this.goodTicks = 0;
        this.effSum = 0.0D;
        this.strafes = 0;
        this.lastStrafeSign = 0;
        this.liveSync = 0.0D;
        this.liveEfficiency = 0.0D;
        // Intentionally DO NOT reset liveGauge here: a bhop touches ground for a single tick between
        // jumps, and zeroing it would slam the gauge marker to the far left for that tick. Hold the
        // last airborne value; the buffer below is cleared so the next jump rebuilds it fresh.
        this.gaugeCount = 0;
        this.gaugeIndex = 0;
    }
}
