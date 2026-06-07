package net.nerdorg.minehop.anticheat.checks;

import net.nerdorg.minehop.anticheat.AntiCheatCheck;
import net.nerdorg.minehop.anticheat.AntiCheatContext;

import java.util.Locale;

public final class SpeedCheck extends AntiCheatCheck {
    private static final double SOURCE_UNIT_TO_BLOCKS_PER_TICK = 1.0D / 800.0D;
    private static final double ABSOLUTE_HARD_FLOOR_PER_TICK = 1.4D;
    private static final double TOLERANCE_RATIO = 1.30D;
    private static final double SURFING_TOLERANCE_BONUS = 0.55D;

    public SpeedCheck() {
        super("Speed", 6.0D, 14.0D, 0.05D);
    }

    @Override
    public CheckResult run(AntiCheatContext context) {
        if (context == null || context.config == null) {
            return CheckResult.OK;
        }
        if (context.justTeleported || context.creativeOrSpectator || context.usingPlotCreative
                || context.inFluid || context.climbing) {
            return CheckResult.OK;
        }

        double speed = context.horizontalSpeed();
        if (speed <= 0.001D) {
            return CheckResult.OK;
        }

        double softLimit = computeMaxAllowedSpeedPerTick(context);
        if (speed <= softLimit) {
            return CheckResult.OK;
        }

        double overage = speed - softLimit;
        double increment = 1.0D + Math.min(overage / Math.max(softLimit * 0.5D, 0.1D), 8.0D);
        String details = String.format(
                Locale.ROOT,
                "speed=%.3f limit=%.3f overage=%.3f",
                speed, softLimit, overage
        );
        return CheckResult.flagAndLagback(increment, details);
    }

    private static double computeMaxAllowedSpeedPerTick(AntiCheatContext context) {
        double airCap = Math.max(context.config.movement.sv_maxairspeed * SOURCE_UNIT_TO_BLOCKS_PER_TICK
                * Math.max(context.config.movement.speed_coefficient, 1.0D), 0.0D);
        double previousHorizontal = context.preMoveVelocity == null ? 0.0D
                : Math.sqrt(context.preMoveVelocity.x * context.preMoveVelocity.x + context.preMoveVelocity.z * context.preMoveVelocity.z);
        double base = Math.max(previousHorizontal, context.state.topRecentHorizontalSpeed()) + airCap;
        double tolerance = base * TOLERANCE_RATIO + 0.25D;
        if (context.surfing) {
            tolerance += SURFING_TOLERANCE_BONUS;
        }
        if (context.speedCap > 0.0D) {
            double speedCapTolerance = context.speedCap * 1.15D + 0.35D;
            tolerance = Math.min(tolerance, speedCapTolerance);
        }
        return Math.max(tolerance, ABSOLUTE_HARD_FLOOR_PER_TICK);
    }
}
