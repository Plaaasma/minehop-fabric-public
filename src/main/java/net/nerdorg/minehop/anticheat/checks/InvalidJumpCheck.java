package net.nerdorg.minehop.anticheat.checks;

import net.minecraft.entity.effect.StatusEffects;
import net.nerdorg.minehop.anticheat.AntiCheatCheck;
import net.nerdorg.minehop.anticheat.AntiCheatContext;

import java.util.Locale;

public final class InvalidJumpCheck extends AntiCheatCheck {
    private static final int MIN_AIRBORNE_BEFORE_INVALID = 6;

    public InvalidJumpCheck() {
        super("InvalidJump", 4.0D, 8.0D, 0.04D);
    }

    @Override
    public CheckResult run(AntiCheatContext context) {
        if (context == null) {
            return CheckResult.OK;
        }
        if (!context.jumpThisTick) {
            return CheckResult.OK;
        }
        if (context.justTeleported || context.creativeOrSpectator || context.usingPlotCreative) {
            return CheckResult.OK;
        }
        if (context.climbing || context.inFluid || context.surfing) {
            return CheckResult.OK;
        }
        if (context.player.hasStatusEffect(StatusEffects.LEVITATION)) {
            return CheckResult.OK;
        }
        if (context.player.isGliding() || context.player.hasVehicle()) {
            return CheckResult.OK;
        }
        if (context.onGround || context.wasOnGround) {
            return CheckResult.OK;
        }
        if (context.state.airborneTicks() < MIN_AIRBORNE_BEFORE_INVALID) {
            return CheckResult.OK;
        }
        String details = String.format(
                Locale.ROOT,
                "midairJump airTicks=%d",
                context.state.airborneTicks()
        );
        return CheckResult.flagAndLagback(2.0D, details);
    }
}
