// MADE BY hatninja ON GITHUB

package net.nerdorg.minehop.mixin;

import net.minecraft.server.network.ServerPlayNetworkHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(ServerPlayNetworkHandler.class)
public abstract class ServerPlayNetworkHandlerMixin {
    // Keep movement thresholds high enough for Minehop movement, but finite so hacked
    // clients cannot use effectively-unbounded move packets.
    private static final float SAFE_PLAYER_MOVE_THRESHOLD = 4096.0F;
    private static final float SAFE_ELYTRA_MOVE_THRESHOLD = 8192.0F;
    private static final double SAFE_VEHICLE_MOVE_THRESHOLD = 4096.0D;
    private static final double DISABLED_MOVED_WRONGLY_THRESHOLD = Double.MAX_VALUE;

    @ModifyConstant(method = "onPlayerMove", constant = @Constant(floatValue = 100.0F))
    private float toofast_PlayerMaxSpeed(float speed) {
        return SAFE_PLAYER_MOVE_THRESHOLD;
    }

    @ModifyConstant(method = "onPlayerMove", constant = @Constant(floatValue = 300.0F))
    private float toofast_ElytraMaxSpeed(float speed) {
        return SAFE_ELYTRA_MOVE_THRESHOLD;
    }

    @ModifyConstant(method = "onVehicleMove", constant = @Constant(doubleValue = 100.0))
    private double toofast_VehicleMaxSpeed(double speed) {
        return SAFE_VEHICLE_MOVE_THRESHOLD;
    }

    @ModifyConstant(method = "onPlayerMove", constant = @Constant(doubleValue = 0.0625D))
    private double minehop$disableMovedWrongly(double threshold) {
        return DISABLED_MOVED_WRONGLY_THRESHOLD;
    }
}
