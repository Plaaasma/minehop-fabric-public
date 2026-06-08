// MADE BY hatninja ON GITHUB

package net.nerdorg.minehop.mixin;

import net.minecraft.entity.*;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.block.ModBlocks;
import net.nerdorg.minehop.config.ConfigWrapper;
import net.nerdorg.minehop.config.MinehopConfig;
import net.nerdorg.minehop.util.MovementUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(ServerPlayNetworkHandler.class)
public abstract class ServerPlayNetworkHandlerMixin {
    // Keep movement thresholds high enough for Minehop movement, but finite so hacked
    // clients cannot use effectively-unbounded move packets.
    private static final float SAFE_PLAYER_MOVE_THRESHOLD = 4096.0F;
    private static final float SAFE_ELYTRA_MOVE_THRESHOLD = 8192.0F;
    private static final double SAFE_VEHICLE_MOVE_THRESHOLD = 4096.0D;
    private static final double MINEHOP_STEP_MOVED_WRONGLY_THRESHOLD = 1.44D;

    @Shadow public ServerPlayerEntity player;

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
    private double minehop$relaxMovedWronglyForCustomStep(double threshold) {
        if (this.player == null || this.player.isCreative() || this.player.isSpectator()) {
            return threshold;
        }
        MinehopConfig config = ConfigWrapper.getEffectiveConfig(this.player);
        if (config == null || !config.enabled) {
            return threshold;
        }
        if (!config.movement.auto_step_up && !config.movement.css_crouch_jump) {
            return threshold;
        }
        if (!this.player.horizontalCollision) {
            return threshold;
        }
        return MINEHOP_STEP_MOVED_WRONGLY_THRESHOLD;
    }
}
