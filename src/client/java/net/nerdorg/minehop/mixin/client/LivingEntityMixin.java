// MADE BY hatninja ON GITHUB

package net.nerdorg.minehop.mixin.client;

import net.minecraft.entity.*;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.MinehopClient;
import net.nerdorg.minehop.block.ModBlocks;
import net.nerdorg.minehop.config.ConfigWrapper;
import net.nerdorg.minehop.config.MinehopConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin extends Entity {
    @Shadow protected abstract float getJumpVelocity();
    @Shadow public abstract boolean hasStatusEffect(StatusEffect effect);
    @Shadow public abstract StatusEffectInstance getStatusEffect(StatusEffect effect);

    @Shadow public abstract float getYaw(float tickDelta);

    @Shadow public abstract float getHeadYaw();

    private boolean wasOnGround;

    public LivingEntityMixin(EntityType<?> type, World world) {
        super(type, world);
    }

    @Inject(method = "isPushable", at = @At("HEAD"), cancellable = true)
    public void isPushable(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(false);
    }

    @Inject(method = "travel", at = @At("HEAD"), cancellable = true)
    public void travel(Vec3d movementInput, CallbackInfo ci) {
        boolean fullGrounded = this.wasOnGround && this.isOnGround(); //Allows for no friction 1-frame upon landing.
        if (fullGrounded) {
            if (!MinehopClient.groundedList.contains(this.getNameForScoreboard())) {
                MinehopClient.groundedList.add(this.getNameForScoreboard());
            }
        }
        else {
            if (MinehopClient.groundedList.contains(this.getNameForScoreboard())) {
                MinehopClient.groundedList.remove(this.getNameForScoreboard());
            }
        }
        this.wasOnGround = this.isOnGround();
    }

    @Inject(method = "jump", at = @At("HEAD"), cancellable = true)
    void jump(CallbackInfo ci) {
        boolean standingOnBooster = this.getWorld().getBlockState(this.getBlockPos()).isOf(ModBlocks.BOOSTER_BLOCK);

        if (!standingOnBooster) {
            Vec3d vecFin = this.getVelocity();
            double yVel = this.getJumpVelocity();
            if (this.hasStatusEffect(StatusEffects.JUMP_BOOST)) {
                yVel += 0.1F * (this.getStatusEffect(StatusEffects.JUMP_BOOST).getAmplifier() + 1);
            }

            this.setVelocity(vecFin.x, yVel, vecFin.z);
            this.velocityDirty = true;
        }
        this.setOnGround(false);

        ci.cancel();
    }

    private static Vec3d movementInputToVelocity(Vec3d movementInput, float speed, float yaw) {
        double d = movementInput.lengthSquared();
        Vec3d vec3d = (d > 1.0D ? movementInput.normalize() : movementInput).multiply(speed);
        float f = MathHelper.sin(yaw * 0.017453292F);
        float g = MathHelper.cos(yaw * 0.017453292F);
        return new Vec3d(vec3d.x * (double)g - vec3d.z * (double)f, vec3d.y, vec3d.z * (double)g + vec3d.x * (double)f);
    }

    private static boolean isFlying(PlayerEntity player) {
        return player != null && player.getAbilities().flying;
    }
}
