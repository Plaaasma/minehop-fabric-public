// MADE BY hatninja ON GITHUB

package net.nerdorg.minehop.mixin;

import com.mojang.authlib.minecraft.client.MinecraftClient;
import net.minecraft.entity.*;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.block.ModBlocks;
import net.nerdorg.minehop.config.MinehopConfig;
import net.nerdorg.minehop.config.ConfigWrapper;
import net.nerdorg.minehop.networking.PacketHandler;
import net.nerdorg.minehop.util.MovementUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin extends Entity {
    @Shadow private float movementSpeed;
    @Shadow public float sidewaysSpeed;
    @Shadow public float forwardSpeed;
    @Shadow private int jumpingCooldown;
    @Shadow protected boolean jumping;

    @Shadow protected abstract Vec3d applyClimbingSpeed(Vec3d velocity);
    @Shadow protected abstract float getJumpVelocity();
    @Shadow public abstract boolean hasStatusEffect(StatusEffect effect);
    @Shadow public abstract StatusEffectInstance getStatusEffect(StatusEffect effect);
    @Shadow public abstract boolean isFallFlying();
    @Shadow public abstract boolean isClimbing();

    @Shadow public abstract float getYaw(float tickDelta);

    @Shadow public abstract void updateLimbs(boolean flutter);

    @Shadow public float prevHeadYaw;

    @Shadow public abstract float getHeadYaw();

    @Shadow protected abstract boolean shouldSwimInFluids();

    @Shadow protected abstract void spawnItemParticles(ItemStack stack, int count);

    private boolean wasOnGround;
    private Vec3d lastSpeed = this.getVelocity();

    public LivingEntityMixin(EntityType<?> type, World world) {
        super(type, world);
    }

    @Inject(method = "isPushable", at = @At("HEAD"), cancellable = true)
    public void isPushable(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(false);
    }

    @Inject(method = "travel", at = @At("HEAD"), cancellable = true)
    public void travel(Vec3d movementInput, CallbackInfo ci) {
        MinehopConfig config;
        if (Minehop.override_config) {
            config = new MinehopConfig();
            config.sv_friction = Minehop.o_sv_friction;
            config.sv_accelerate = Minehop.o_sv_accelerate;
            config.sv_airaccelerate = Minehop.o_sv_airaccelerate;
            config.sv_maxairspeed = Minehop.o_sv_maxairspeed;
            config.speed_mul = Minehop.o_speed_mul;
            config.sv_gravity = Minehop.o_sv_gravity;
        }
        else {
            config = ConfigWrapper.config;
        }

        boolean standingOnBooster = this.getWorld().getBlockState(this.getBlockPos()).isOf(ModBlocks.BOOSTER_BLOCK);

        //Enable for Players only
        if (this.getType() != EntityType.PLAYER) { return; }

        if (!this.canMoveVoluntarily() && !this.isLogicalSideForUpdatingMovement()) { return; }

        //Cancel override if not in plain walking state.
        if (this.isTouchingWater() || this.isInLava() || this.isFallFlying()) { return; }

        //I don't have a better clue how to do this atm.
        LivingEntity self = (LivingEntity) this.getWorld().getEntityById(this.getId());

        //Disable on creative flying.
        if (this.getType() == EntityType.PLAYER && isFlying((PlayerEntity) self)) { return; }

        //Reverse multiplication done by the function that calls this one.
        this.sidewaysSpeed /= 0.98F;
        this.forwardSpeed /= 0.98F;
        double sI = movementInput.x / 0.98F;
        double actualsI = movementInput.x / 0.98F;
        double fI = movementInput.z / 0.98F;
        double uI = movementInput.y;

        //Have no jump cooldown, why not?
        this.jumpingCooldown = 0;

        //Get Slipperiness and Movement speed.
        BlockPos blockPos = this.getVelocityAffectingPos();
        float slipperiness = this.getWorld().getBlockState(blockPos).getBlock().getSlipperiness();
        float friction = 1-(slipperiness*slipperiness);

        //
        //Apply Friction
        //
        boolean fullGrounded = this.wasOnGround && this.isOnGround(); //Allows for no friction 1-frame upon landing.
        if (fullGrounded) {
            if (!Minehop.groundedList.contains(this.getNameForScoreboard())) {
                Minehop.groundedList.add(this.getNameForScoreboard());
            }
        }
        else {
            if (Minehop.groundedList.contains(this.getNameForScoreboard())) {
                Minehop.groundedList.remove(this.getNameForScoreboard());
            }
        }
        if (fullGrounded && !standingOnBooster) {
            Vec3d velFin = this.getVelocity();
            Vec3d horFin = new Vec3d(velFin.x,0.0F,velFin.z);
            float speed = (float) horFin.length();
            if (speed > 0.001F) {
                float drop = 0.0F;

                drop += (speed * config.sv_friction * friction);

                float newspeed = Math.max(speed - drop, 0.0F);
                newspeed /= speed;
                this.setVelocity(
                        horFin.x * newspeed,
                        velFin.y,
                        horFin.z * newspeed
                );
            }
        }
        this.wasOnGround = this.isOnGround();

        //
        // Accelerate
        //
        float yawDifference = MathHelper.wrapDegrees(this.getHeadYaw() - this.prevHeadYaw);
        if (yawDifference < 0) {
            yawDifference = yawDifference * -1;
        }
        if (!this.isOnGround()) {
            sI = sI * yawDifference;
        }
        else {
            if (Minehop.efficiencyListMap.containsKey(this.getNameForScoreboard())) {
                List<Double> efficiencyList = Minehop.efficiencyListMap.get(this.getNameForScoreboard());
                if (efficiencyList != null && efficiencyList.size() > 0) {
                    double averageEfficiency = efficiencyList.stream().mapToDouble(Double::doubleValue).average().orElse(Double.NaN);
                    Entity localEntity = this.getWorld().getEntityById(this.getId());
                    if (localEntity instanceof PlayerEntity playerEntity) {
                        Minehop.efficiencyUpdateMap.put(playerEntity.getNameForScoreboard(), averageEfficiency);
                    }
                    Minehop.efficiencyListMap.put(this.getNameForScoreboard(), new ArrayList<>());
                }
            }
        }

        if (sI != 0.0F || fI != 0.0F) {
            Vec3d moveDir = MovementUtil.movementInputToVelocity(new Vec3d(sI, 0.0F, fI), 1.0F, this.getYaw());
            Vec3d accelVec = this.getVelocity();

            double projVel = new Vec3d(accelVec.x, 0.0F, accelVec.z).dotProduct(moveDir);
            // double accelVel = (this.isOnGround() ? config.sv_accelerate : (config.sv_airaccelerate / (this.horizontalSpeed * 10000)));
            double accelVel = (this.isOnGround() ? config.sv_accelerate : ((config.sv_airaccelerate) / (this.horizontalSpeed * 100000))); // 100000
            //float maxVel = (float) (this.isOnGround() ? this.movementSpeed * config.speed_mul : config.sv_maxairspeed); //This is fucking dogshit

            // Attempt 1: Pretty good!

            /**
             * @Author lolrow
             * @Reason Fixed movement made it better and fucking awesome.
             */
            float maxVel;
            if (this.isOnGround() && !this.jumping) {
                maxVel = (float) (this.movementSpeed * config.speed_mul);
            } else {
                // Increase maximum air speed based on the yawDifference
                // maxVel = (float) (config.sv_maxairspeed * (1.0f + (yawDifference / 180.0f))); <- Alternative.
                maxVel = (float) (config.sv_maxairspeed * (1.0f + (yawDifference / 10.25f))); // 90.0f is the normal value, might revert back to it
                // yawDifference / 50.0f is good
                // yawDifference / 25.0f may be better, but it's hard to say
                // yawDifference / 10.0f is good
                // yawDifference / 7.8f is better
                // yawDifference / 11.5f

                maxVel = (float) Math.min(maxVel, config.sv_maxairspeed * 1000000000000000.0f); // Limit to prevent astronomical speed gain.
                // 2.0f <- decent maybe
            }

            if (projVel + accelVel > maxVel) {
                accelVel = maxVel - projVel;
            }
            Vec3d accelDir = moveDir.multiply(Math.max(accelVel, 0.0F));

            Vec3d newVelocity = accelVec.add(accelDir);

            if (!this.isOnGround()) {
                double trueMaxVel = maxVel - projVel;
                double v = Math.sqrt((newVelocity.x * newVelocity.x) + (newVelocity.z * newVelocity.z));
                double nogainv2 = (accelVec.x * accelVec.x) + (accelVec.z * accelVec.z);
                double nogainv = Math.sqrt(nogainv2);
                double maxgainv = Math.sqrt(nogainv2 + (trueMaxVel * trueMaxVel));
//                double qt = 0.785398f;
//                double gauge = MathHelper.clamp(1D + (MathHelper.abs((float) MathHelper.atan2(sI * lastSpeed.z - fI * lastSpeed.x, sI * lastSpeed.x + fI * lastSpeed.z)) - qt) / MathHelper.atan2(trueMaxVel, nogainv), 0D, 2D);
                double strafeEfficiency = MathHelper.clamp((((v - nogainv) / (maxgainv - nogainv)) * 33), 0D, 100D);
                Minehop.efficiencyMap.put(this.getNameForScoreboard(), strafeEfficiency);
                List<Double> efficiencyList = Minehop.efficiencyListMap.containsKey(this.getNameForScoreboard()) ? Minehop.efficiencyListMap.get(this.getNameForScoreboard()) : new ArrayList<>();
                efficiencyList.add(strafeEfficiency);
                Minehop.efficiencyListMap.put(this.getNameForScoreboard(), efficiencyList);
            }

            this.setVelocity(newVelocity);
        }

        this.setVelocity(this.applyClimbingSpeed(this.getVelocity()));
        this.move(MovementType.SELF, this.getVelocity());

        //u8
        //Ladder Logic
        //
        Vec3d preVel = this.getVelocity();
        if ((this.horizontalCollision || this.jumping) && this.isClimbing()) {
            preVel = new Vec3d(preVel.x * 0.7D, 0.2D, preVel.z * 0.7D);
        }

        //
        //Apply Gravity (If not in Water)
        //
        double yVel = preVel.y;
        double gravity = config.sv_gravity;
        if (preVel.y <= 0.0D && this.hasStatusEffect(StatusEffects.SLOW_FALLING)) {
            gravity = 0.01D;
            this.fallDistance = 0.0F;
        }
        if (this.hasStatusEffect(StatusEffects.LEVITATION)) {
            yVel += (0.05D * (this.getStatusEffect(StatusEffects.LEVITATION).getAmplifier() + 1) - preVel.y) * 0.2D;
            this.fallDistance = 0.0F;
        } else if (this.getWorld().isClient && !this.getWorld().isChunkLoaded(blockPos)) {
            yVel = 0.0D;
        } else if (!this.hasNoGravity()) {
            yVel -= gravity;
        }
        this.setVelocity(preVel.x,yVel,preVel.z);

        //
        //Update limbs.
        //
        this.updateLimbs(self instanceof Flutterer);

        //Override original method.
        ci.cancel();
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

        ci.cancel();
    }

    private static boolean isFlying(PlayerEntity player) {
        return player != null && player.getAbilities().flying;
    }
}
