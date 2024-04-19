// ORIGINAL BY hatninja ON GITHUB

package net.nerdorg.minehop.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.entity.*;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.block.ModBlocks;
import net.nerdorg.minehop.block.entity.BoostBlockEntity;
import net.nerdorg.minehop.config.MinehopConfig;
import net.nerdorg.minehop.config.ConfigWrapper;
import net.nerdorg.minehop.data.DataManager;
import net.nerdorg.minehop.hns.HNSManager;
import net.nerdorg.minehop.util.Logger;
import net.nerdorg.minehop.util.MovementUtil;
import net.nerdorg.minehop.util.ZoneUtil;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
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

    @Shadow protected abstract float getJumpVelocity();
    @Shadow public abstract boolean hasStatusEffect(StatusEffect effect);
    @Shadow public abstract StatusEffectInstance getStatusEffect(StatusEffect effect);
    @Shadow public abstract boolean isFallFlying();
    @Shadow public abstract boolean isClimbing();

    @Shadow public abstract float getYaw(float tickDelta);

    @Shadow public abstract void updateLimbs(boolean flutter);

    @Shadow public float prevHeadYaw;

    @Shadow public abstract float getHeadYaw();

    @Shadow public int stuckArrowTimer;

    @Shadow public abstract boolean isHoldingOntoLadder();

    @Shadow protected float field_6215;

    @Shadow protected abstract boolean shouldSwimInFluids();

    @Shadow @Final public static int field_30063;
    private boolean wasOnGround;
    private long boostTime = 0;
    private long ladderReleaseTime = 0;

    public LivingEntityMixin(EntityType<?> type, World world) {
        super(type, world);
    }

    @Inject(method = "isPushable", at = @At("HEAD"), cancellable = true)
    public void isPushable(CallbackInfoReturnable<Boolean> cir) {
        if (!Minehop.o_hns) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "teleport", at = @At("HEAD"))
    public void onTeleport(double x, double y, double z, boolean particleEffects, CallbackInfoReturnable<Boolean> cir) {
        HNSManager.taggedMap.remove(this.getNameForScoreboard());
    }

    @Inject(method = "damage", at = @At("HEAD"), cancellable = true)
    public void onDamage(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (source.isOf(DamageTypes.FALL)) {
            if (this.getWorld().getEntityById(this.getId()) instanceof PlayerEntity player) {
                DataManager.MapData mapData = ZoneUtil.getCurrentMap(player);
                if (mapData != null && mapData.hns) {
                    BlockState belowState = this.getWorld().getBlockState(this.getBlockPos().offset(Direction.DOWN, 1));
                    if (amount >= 20 && !(belowState.getBlock() instanceof StairsBlock)) {
                        HNSManager.taggedMap.put(player.getNameForScoreboard(), true);
                        Logger.logFailure(player, "You were tagged because you fell too far. You can break your fall by landing on stairs.");
                    }
                }
            }
            cir.cancel();
        }
        else {
            Entity sourceEntity = source.getSource();
            if (sourceEntity != null) {
                DataManager.MapData mapData = ZoneUtil.getCurrentMap(sourceEntity);

                if (mapData != null && mapData.hns) {
                    if (sourceEntity instanceof PlayerEntity player) {
                        if (player.getEyePos().distanceTo(this.getEyePos()) > 3.5 && player.getEyePos().distanceTo(this.getPos()) > 3.5) {
                            cir.cancel();
                        }
                        if (player.getEyePos().getY() <= this.getPos().getY() - 1) {
                            cir.cancel();
                        }
                    }
                }
            }
        }
    }

    /**
     * @Author lolrow and Plaaasma
     * @Reason Improved quick turning and added gauge/efficiency calculation. I don't think any of lolrows code is here anymore but he's cool so he can stay.
     */

    @Inject(method = "travel", at = @At("HEAD"), cancellable = true)
    public void travel(Vec3d movementInput, CallbackInfo ci) {
        MinehopConfig config;
        double speedCap = 1000000;
        if (Minehop.override_config) {
            config = new MinehopConfig();
            config.sv_friction = Minehop.o_sv_friction;
            config.sv_accelerate = Minehop.o_sv_accelerate;
            config.sv_airaccelerate = Minehop.o_sv_airaccelerate;
            config.sv_maxairspeed = Minehop.o_sv_maxairspeed;
            config.speed_mul = Minehop.o_speed_mul;
            config.sv_gravity = Minehop.o_sv_gravity;
            speedCap = Minehop.o_speed_cap;
        }
        else {
            config = ConfigWrapper.config;
        }

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
        double fI = movementInput.z / 0.98F;

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
            Minehop.groundedList.remove(this.getNameForScoreboard());
        }
        if (fullGrounded) {
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

        if (!fullGrounded && !this.isClimbing()) {
            sI = sI * yawDifference;
            fI = fI * yawDifference;
        }

        double perfectAngle = findOptimalStrafeAngle(sI, fI, config, fullGrounded);

        // AUTOSTRAFER FOR TESTING PURPOSES, PROBABLY GOING TO ADD IT AS A GAMEMODE CAUSE IT'S REALLY FUN
//        this.setYaw((float) perfectAngle);

        if (this.isOnGround()) {
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
            double accelVel = (this.isOnGround() ? config.sv_accelerate : (config.sv_airaccelerate));

            float maxVel;
            double angleBetween = 0;
            if (fullGrounded) {
                maxVel = (float) (this.movementSpeed * config.speed_mul);
            } else {
                maxVel = (float) (config.sv_maxairspeed);

                angleBetween = Math.acos(accelVec.normalize().dotProduct(moveDir.normalize()));

                maxVel *= (float) (angleBetween * angleBetween * angleBetween);
            }

            if (projVel + accelVel > maxVel) {
                accelVel = maxVel - projVel;
            }
            Vec3d accelDir = moveDir.multiply(Math.max(accelVel, 0.0F));

            Vec3d newVelocity = accelVec.add(accelDir);
            Vec3d newHorizontalVelocity = newVelocity;

            double currentHorizontalSpeed = newHorizontalVelocity.horizontalLength();

            if (currentHorizontalSpeed > speedCap && !fullGrounded) {
                // Scale down the horizontal velocity to the speedCap
                newHorizontalVelocity = newHorizontalVelocity.multiply(speedCap / currentHorizontalSpeed);
            }

            if (!fullGrounded) {
                double v = Math.sqrt((newVelocity.x * newVelocity.x) + (newVelocity.z * newVelocity.z));
                double nogainv2 = (accelVec.x * accelVec.x) + (accelVec.z * accelVec.z);
                double nogainv = Math.sqrt(nogainv2);
                double maxgainv = Math.sqrt(nogainv2 + (maxVel * maxVel));

                double normalYaw = this.getYaw();

                double gaugeValue = sI < 0 || fI < 0 ? (normalYaw - perfectAngle) : (perfectAngle - normalYaw);
                gaugeValue = normalizeAngle(gaugeValue) * 2;

                List<Double> gaugeList = Minehop.gaugeListMap.containsKey(this.getNameForScoreboard()) ? Minehop.gaugeListMap.get(this.getNameForScoreboard()) : new ArrayList<>();
                gaugeList.add(gaugeValue);
                Minehop.gaugeListMap.put(this.getNameForScoreboard(), gaugeList);

                double strafeEfficiency = MathHelper.clamp((((v - nogainv) / (maxgainv - nogainv)) * 100), 0D, 100D);
                Minehop.efficiencyMap.put(this.getNameForScoreboard(), strafeEfficiency);
                List<Double> efficiencyList = Minehop.efficiencyListMap.containsKey(this.getNameForScoreboard()) ? Minehop.efficiencyListMap.get(this.getNameForScoreboard()) : new ArrayList<>();
                efficiencyList.add(strafeEfficiency);
                Minehop.efficiencyListMap.put(this.getNameForScoreboard(), efficiencyList);
            }

            this.setVelocity(new Vec3d(newHorizontalVelocity.getX(), newVelocity.getY(), newHorizontalVelocity.getZ()));
        }

        double ladderYaw = 0;
        BlockState blockState = this.getBlockStateAtPos();
        if (blockState.isIn(BlockTags.CLIMBABLE)) {
            if (blockState.isOf(Blocks.LADDER)) {
                ladderYaw = normalizeAngle(blockState.get(HorizontalFacingBlock.FACING).asRotation() + 180);
            }
        }

        this.setVelocity(applyClimbingSpeed(this.getVelocity(), fI, sI, ladderYaw));
        this.move(MovementType.SELF, this.getVelocity());

        //u8
        //Ladder Logic
        //
        Vec3d preVel = this.getVelocity();
        if (this.isClimbing()) {
            if (jumping) {
                if (this.getWorld().getTime() < ladderReleaseTime || this.getWorld().getTime() > ladderReleaseTime + 4) {
                    Vec3d jumpDir = MovementUtil.movementInputToVelocity(new Vec3d(0.0F, 0.0F, -1.0F), 1.0F, (float) ladderYaw);

                    Vec3d accelDir = jumpDir.multiply(0.25);

                    preVel = preVel.add(accelDir);

                    ladderReleaseTime = this.getWorld().getTime();
                }
            }
            else {
                double ladderfI;
                double entityYaw = normalizeAngle(this.getYaw());
                double yawDif = normalizeAngle(entityYaw - ladderYaw);
                if (yawDif < -45 && yawDif > -135) {
                    ladderfI = -sI;
                }
                else if (yawDif < -135 || yawDif > 135) {
                    ladderfI = -fI;
                }
                else if (yawDif < 135 && yawDif > 45) {
                    ladderfI = sI;
                }
                else {
                    ladderfI = fI;
                }

                if (ladderfI > 0.4) {
                    ladderfI = 0.4;
                }
                else if (ladderfI < -0.35) {
                    ladderfI = -0.35;
                }

                preVel = new Vec3d(preVel.x * 0.7F, ladderfI, preVel.z * 0.7F);
            }
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

        BlockState belowState = this.getWorld().getBlockState(this.getBlockPos());
        if (belowState.isOf(ModBlocks.BOOSTER_BLOCK) && (this.getWorld().getTime() > this.boostTime + 5 || this.getWorld().getTime() < this.boostTime)) {
            this.boostTime = this.getWorld().getTime();
            BoostBlockEntity boostBlockEntity = (BoostBlockEntity) this.getWorld().getBlockEntity(this.getBlockPos());
            if (boostBlockEntity != null) {
                preVel = preVel.add(boostBlockEntity.getXPower(), 0, boostBlockEntity.getZPower());
                yVel += boostBlockEntity.getYPower();
            }
        }
        this.setVelocity(preVel.x,yVel,preVel.z);

        //
        //Update limbs.
        //
        this.updateLimbs(self instanceof Flutterer);

        //Override original method.
        ci.cancel();
    }

    public double findOptimalStrafeAngle(double sI, double fI, MinehopConfig config, boolean fullGrounded) {
        double highestVelocity = -Double.MAX_VALUE;
        double optimalAngle = 0;
        for (double angle = this.prevYaw - 45; angle < this.prevYaw + 45; angle += 1) {  // Test angles 0 to 355 degrees, in 5 degree increments
            Vec3d moveDir = MovementUtil.movementInputToVelocity(new Vec3d(sI, 0.0F, fI), 1.0F, (float) angle);
            Vec3d accelVec = this.getVelocity();

            double projVel = new Vec3d(accelVec.x, 0.0F, accelVec.z).dotProduct(moveDir);
            double accelVel = (this.isOnGround() ? config.sv_accelerate : (config.sv_airaccelerate));

            float maxVel;
            if (fullGrounded) {
                maxVel = (float) (this.movementSpeed * config.speed_mul);
            } else {
                maxVel = (float) (config.sv_maxairspeed);

                double angleBetween = Math.acos(accelVec.normalize().dotProduct(moveDir.normalize()));

                maxVel *= (float) (angleBetween * angleBetween * angleBetween);
            }

            if (projVel + accelVel > maxVel) {
                accelVel = maxVel - projVel;
            }
            Vec3d accelDir = moveDir.multiply(Math.max(accelVel, 0.0F));

            Vec3d newVelocity = accelVec.add(accelDir);

            if (newVelocity.horizontalLength() > highestVelocity) {
                highestVelocity = newVelocity.horizontalLength();
                optimalAngle = angle;
            }
        }
        return optimalAngle;
    }

    private static double normalizeAngle(double angle) {
        angle = angle % 360;
        if (angle > 180) angle -= 360;
        else if (angle < -180) angle += 360;
        return angle;
    }

    @Unique
    private Vec3d applyClimbingSpeed(Vec3d motion, double fI, double sI, double ladderYaw) {
        if (this.isClimbing() && !this.jumping) {
            this.onLanding();
            double d = 0;
            if (!(ladderYaw == 90 || ladderYaw == -90)) {
                 d = MathHelper.clamp(motion.x, -0.35000000596046448, 0.35000000596046448);
            }
            double e = 0;
            if (!(ladderYaw == 180 || ladderYaw == -180 || ladderYaw == 0)) {
                e = MathHelper.clamp(motion.z, -0.35000000596046448, 0.35000000596046448);
            }
            double g = motion.y;
            if (g < 0.0 && this.getWorld().getEntityById(this.getId()) instanceof PlayerEntity && fI == 0 && sI == 0) {
                g = 0.0;
            }

            motion = new Vec3d(d, g, e);
        }
        else if (this.isClimbing() && this.jumping) {
            this.onLanding();
            double d = motion.x;
            if (ladderYaw == 90) {
                d = MathHelper.clamp(motion.x, 0, 1000000);
            }
            else if (ladderYaw == -90) {
                d = MathHelper.clamp(motion.x, -1000000, 0);
            }
            double e = motion.z;
            if (ladderYaw == 180 || ladderYaw == -180) {
                e = MathHelper.clamp(motion.z, 0, 1000000);
            }
            else if (ladderYaw == 0) {
                e = MathHelper.clamp(motion.z, -1000000, 0);
            }
            double g = motion.y;

            motion = new Vec3d(d, g, e);
        }

        return motion;
    }

    @Inject(method = "jump", at = @At("HEAD"), cancellable = true)
    void jump(CallbackInfo ci) {
        Vec3d vecFin = this.getVelocity();
        double yVel = this.getJumpVelocity();
        if (this.hasStatusEffect(StatusEffects.JUMP_BOOST)) {
            yVel += 0.1F * (this.getStatusEffect(StatusEffects.JUMP_BOOST).getAmplifier() + 1);
        }

        this.setVelocity(vecFin.x, yVel, vecFin.z);
        this.velocityDirty = true;

        ci.cancel();
    }

    private static boolean isFlying(PlayerEntity player) {
        return player != null && player.getAbilities().flying;
    }
}
