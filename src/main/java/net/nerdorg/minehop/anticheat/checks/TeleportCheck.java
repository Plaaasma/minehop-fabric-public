package net.nerdorg.minehop.anticheat.checks;

import net.nerdorg.minehop.anticheat.AntiCheatCheck;
import net.nerdorg.minehop.anticheat.AntiCheatContext;

import java.util.Locale;

public final class TeleportCheck extends AntiCheatCheck {
    private static final double TELEPORT_DELTA_THRESHOLD = 12.0D;

    public TeleportCheck() {
        super("Teleport", 3.0D, 6.0D, 0.04D);
    }

    @Override
    public CheckResult run(AntiCheatContext context) {
        if (context == null) {
            return CheckResult.OK;
        }
        if (context.justTeleported || context.creativeOrSpectator || context.usingPlotCreative) {
            return CheckResult.OK;
        }
        double deltaSq = context.movedDelta.lengthSquared();
        double threshold = TELEPORT_DELTA_THRESHOLD * TELEPORT_DELTA_THRESHOLD;
        if (deltaSq <= threshold) {
            return CheckResult.OK;
        }
        double dist = Math.sqrt(deltaSq);
        String details = String.format(
                Locale.ROOT,
                "delta=%.2f from=(%.1f,%.1f,%.1f) to=(%.1f,%.1f,%.1f)",
                dist,
                context.preMovePosition.x, context.preMovePosition.y, context.preMovePosition.z,
                context.postMovePosition.x, context.postMovePosition.y, context.postMovePosition.z
        );
        return CheckResult.flagAndLagback(4.0D, details);
    }
}
