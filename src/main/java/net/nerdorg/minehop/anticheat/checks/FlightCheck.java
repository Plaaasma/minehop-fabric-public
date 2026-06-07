package net.nerdorg.minehop.anticheat.checks;

import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.util.math.Vec3d;
import net.nerdorg.minehop.anticheat.AntiCheatCheck;
import net.nerdorg.minehop.anticheat.AntiCheatContext;

import java.util.Locale;

public final class FlightCheck extends AntiCheatCheck {
    private static final int MIN_AIRBORNE_TICKS_BEFORE_CHECK = 12;
    private static final int SUSTAINED_FLIGHT_TICKS = 24;
    private static final double MAX_SUSTAINED_UPWARD_VELOCITY = 0.01D;

    public FlightCheck() {
        super("Flight", 5.0D, 10.0D, 0.06D);
    }

    @Override
    public CheckResult run(AntiCheatContext context) {
        if (context == null || context.config == null) {
            return CheckResult.OK;
        }
        if (context.justTeleported || context.creativeOrSpectator || context.usingPlotCreative
                || context.climbing || context.inFluid || context.surfing) {
            return CheckResult.OK;
        }
        if (context.player.getAbilities().flying || context.player.getAbilities().allowFlying) {
            return CheckResult.OK;
        }
        if (context.player.isGliding() || context.player.hasVehicle()) {
            return CheckResult.OK;
        }
        if (context.player.hasStatusEffect(StatusEffects.LEVITATION)
                || context.player.hasStatusEffect(StatusEffects.SLOW_FALLING)) {
            return CheckResult.OK;
        }

        int airTicks = context.state.airborneTicks();
        if (airTicks < MIN_AIRBORNE_TICKS_BEFORE_CHECK) {
            return CheckResult.OK;
        }

        Vec3d velocity = context.postMoveVelocity;
        double verticalDelta = context.verticalDelta();

        if (airTicks >= SUSTAINED_FLIGHT_TICKS) {
            if (verticalDelta >= -0.005D && Math.abs(velocity.y) <= MAX_SUSTAINED_UPWARD_VELOCITY) {
                String details = String.format(
                        Locale.ROOT,
                        "airTicks=%d dy=%.4f vy=%.4f",
                        airTicks, verticalDelta, velocity.y
                );
                return CheckResult.flagAndLagback(2.0D, details);
            }
        }

        if (airTicks >= MIN_AIRBORNE_TICKS_BEFORE_CHECK && verticalDelta > 0.42D) {
            String details = String.format(
                    Locale.ROOT,
                    "midairAscent dy=%.4f airTicks=%d",
                    verticalDelta, airTicks
            );
            return CheckResult.flag(2.0D, details);
        }

        return CheckResult.OK;
    }
}
