package net.nerdorg.minehop.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.VineBlock;
import net.minecraft.entity.*;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.anticheat.AntiCheatManager;
import net.nerdorg.minehop.block.ModBlocks;
import net.nerdorg.minehop.block.entity.BoostBlockEntity;
import net.nerdorg.minehop.config.MinehopConfig;
import net.nerdorg.minehop.config.ConfigWrapper;
import net.nerdorg.minehop.data.DataManager;
import net.nerdorg.minehop.entity.custom.ResetEntity;
import net.nerdorg.minehop.entity.custom.StartEntity;
import net.nerdorg.minehop.entity.custom.SurfRampEntity;
import net.nerdorg.minehop.hns.HNSManager;
import net.nerdorg.minehop.util.Logger;
import net.nerdorg.minehop.util.MovementUtil;
import net.nerdorg.minehop.util.SurfContact;
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
import java.util.HashMap;
import java.util.List;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin extends Entity {
    @Shadow private float movementSpeed;
    @Shadow public float sidewaysSpeed;
    @Shadow public float forwardSpeed;
    @Shadow private int jumpingCooldown;
    @Shadow protected boolean jumping;

    @Shadow protected abstract float getJumpVelocity();
    @Shadow public abstract boolean hasStatusEffect(RegistryEntry<StatusEffect> effect);
    @Shadow public abstract StatusEffectInstance getStatusEffect(RegistryEntry<StatusEffect> effect);
    @Shadow public abstract boolean isClimbing();

    @Shadow public abstract float getYaw(float tickDelta);

    @Shadow public abstract void updateLimbs(boolean flutter);

    @Shadow public float prevHeadYaw;

    @Shadow public abstract float getHeadYaw();

    @Shadow public abstract boolean isGliding();

    private boolean wasOnGround;
    @Unique private boolean cssCrouchOffsetApplied;
    @Unique private boolean cssWasSneaking;
    @Unique private boolean cssWasSprinting;
    @Unique private boolean cssAirCrouchSprintLock;
    private long boostTime = 0;
    private long ladderReleaseTime = 0;
    @Unique private static final double SOURCE_FRAME_TIME = 1.0D / 20.0D;
    @Unique private static final double SOURCE_UNIT_TO_BLOCKS_PER_TICK = 1.0D / 800.0D;
    @Unique private static final double SOURCE_SIM_TICKRATE = 128.0D;
    @Unique private static final double SOURCE_MAX_AIR_YAW_DELTA = 90.0D;
    @Unique private static final double CSS_CROUCH_DELTA_EPSILON = 1.0E-5D;
    @Unique private static final double SURF_CONTACT_BELOW_TOLERANCE = 0.20D;
    @Unique private static final double SURF_CONTACT_ABOVE_TOLERANCE = 0.20D;
    @Unique private static final double SURF_GROUNDED_NEAR_RAMP_MIN_BELOW_TOLERANCE = 0.88D;
    @Unique private static final double SURF_GROUNDED_NEAR_RAMP_MAX_ATTACH_GAP = 0.88D;
    @Unique private static final double SURF_SURFING_STICKY_ATTACH_GAP = 0.90D;
    @Unique private static final double SURF_TRACE_STEP = 0.08D;
    @Unique private static final int SURF_INCOMING_TRACE_MAX_STEPS = 10;
    @Unique private static final int SURF_INCOMING_TRACE_MAX_STEPS_FAST = 64;
    @Unique private static final double SURF_INCOMING_TRACE_FAST_MIN_DESCENT = -0.70D;
    @Unique private static final double SURF_INCOMING_TRACE_FAST_MIN_HORIZONTAL = 0.65D;
    @Unique private static final double SURF_INCOMING_TRACE_FAST_MIN_TOTAL_SPEED = 0.95D;
    @Unique private static final double SURF_INCOMING_TRACE_FAST_SHALLOW_MIN_DESCENT = -0.10D;
    @Unique private static final double SURF_INCOMING_TRACE_FAST_SHALLOW_MIN_HORIZONTAL = 0.80D;
    @Unique private static final double SURF_INCOMING_TRACE_FAST_SHALLOW_MIN_TOTAL_SPEED = 1.20D;
    @Unique private static final double SURF_INCOMING_TRACE_EARLY_EXIT_DISTANCE = 0.035D;
    @Unique private static final double SURF_INCOMING_TRACE_CULL_BOUNDS_EXTRA = 0.55D;
    @Unique private static final double SURF_COLLISION_QUERY_EXPAND = 0.65D;
    @Unique private static final double SURF_ENDPOINT_PROXIMITY_RADIUS = 1.20D;
    @Unique private static final int SURF_CONTACT_GRACE_TICKS = 5;
    @Unique private static final int SURF_COLLISION_BYPASS_GRACE_TICKS = 6;
    @Unique private static final double SURF_COLLISION_BYPASS_GRACE_MIN_HORIZONTAL = 0.22D;
    @Unique private static final double SURF_MAX_SNAP_UP = 0.35D;
    @Unique private static final double SURF_MAX_SNAP_DOWN = 0.65D;
    @Unique private static final double SURF_SNAP_DOWN_MAX_UPWARD = 0.10D;
    @Unique private static final double SURF_SPEED_BYPASS_MIN_HORIZONTAL = 0.18D;
    @Unique private static final double SURF_PRECONTACT_DESCENT_BYPASS_MIN_FALL = -0.18D;
    @Unique private static final double SURF_NEAR_RAMP_GROUND_FRICTION_SPEED_THRESHOLD = 0.45D;
    @Unique private static final double SURF_REUSE_MAX_ABOVE_FEET = 0.24D;
    @Unique private static final double SURF_REUSE_MIN_NORMAL_Y = 0.02D;
    @Unique private static final double SURF_NEARBY_RAMP_SEARCH_EXPAND = 6.0D;
    @Unique private static final double SURF_ENDPOINT_BYPASS_DISABLE_T_THRESHOLD = 0.12D;
    @Unique private static final double SURF_ENDPOINT_BYPASS_DISABLE_LATERAL_EXTRA = 1.35D;
    @Unique private static final double SURF_ENDPOINT_APPROX_MAX_ALONG_ALLOWANCE = 1.10D;
    @Unique private static final double SURF_HARD_ENDPOINT_DISABLE_MAX_HORIZONTAL = 0.65D;
    @Unique private static final double SURF_IMPACT_VERTICAL_RESTORE_FACTOR = 0.91D;
    @Unique private static final double SURF_IMPACT_HORIZONTAL_RESTORE_FACTOR = 0.96D;
    @Unique private static final double SURF_LANDING_KEEP_HORIZONTAL_MIN_SPEED = 0.18D;
    @Unique private static final double SURF_LANDING_KEEP_HORIZONTAL_RATIO = 0.99D;
    @Unique private static final double SURF_LANDING_BOUNCE_CLAMP_Y = -0.12D;
    @Unique private static final int SURF_LANDING_STABILIZE_TICKS = 3;
    @Unique private static final double SURF_LANDING_STABILIZE_MAX_Y = -0.10D;
    @Unique private static final double SURF_LANDING_VERTICAL_TO_HORIZONTAL_MIN_FALL = -0.32D;
    @Unique private static final double SURF_LANDING_VERTICAL_TO_HORIZONTAL_FACTOR = 0.78D;
    @Unique private static final double SURF_LANDING_VERTICAL_TO_HORIZONTAL_MIN_SPEED = 0.30D;
    @Unique private static final double SURF_LANDING_VERTICAL_TO_HORIZONTAL_MAX_INCOMING = 0.38D;
    @Unique private static final double SURF_LANDING_POST_GRAVITY_RESTORE_MIN_HORIZONTAL = 0.26D;
    @Unique private static final double SURF_LANDING_POST_GRAVITY_RESTORE_TRIGGER_RATIO = 0.84D;
    @Unique private static final double SURF_LANDING_POST_GRAVITY_RESTORE_KEEP_RATIO = 0.965D;
    @Unique private static final double SURF_LANDING_POST_GRAVITY_RESTORE_MAX_INTO_COMPONENT = 0.42D;
    @Unique private static final double SURF_LANDING_STALL_RESTORE_MIN_FALL = -0.08D;
    @Unique private static final double SURF_LANDING_STALL_RESTORE_MIN_INCOMING_HORIZONTAL = 0.26D;
    @Unique private static final double SURF_LANDING_STALL_RESTORE_MIN_PROJECTED_HORIZONTAL = 0.16D;
    @Unique private static final double SURF_LANDING_STALL_RESTORE_TRIGGER_RATIO = 0.90D;
    @Unique private static final double SURF_LANDING_STALL_RESTORE_KEEP_RATIO = 0.992D;
    @Unique private static final double SURF_LANDING_STALL_FORCE_RESTORE_TRIGGER_RATIO = 0.86D;
    @Unique private static final double SURF_LANDING_STALL_FORCE_RESTORE_KEEP_RATIO = 0.985D;
    @Unique private static final double SURF_LANDING_STALL_RESTORE_POST_GRAVITY_TRIGGER_RATIO = 0.88D;
    @Unique private static final double SURF_CONTACT_SPEED_RESTORE_MIN_HORIZONTAL = 0.20D;
    @Unique private static final double SURF_CONTACT_SPEED_RESTORE_TRIGGER_RATIO = 0.94D;
    @Unique private static final double SURF_CONTACT_SPEED_RESTORE_KEEP_RATIO = 0.992D;
    @Unique private static final double SURF_CONTACT_SPEED_RESTORE_POST_GRAVITY_TRIGGER_RATIO = 0.92D;
    @Unique private static final double SURF_CONTACT_SPEED_RESTORE_POST_GRAVITY_KEEP_RATIO = 0.99D;
    @Unique private static final double SURF_LANDING_CONTINUE_DESCENT_TRIGGER_FALL = -0.18D;
    @Unique private static final double SURF_LANDING_CONTINUE_DESCENT_MIN_Y = -0.10D;
    @Unique private static final double SURF_MISSED_CONTACT_LANDING_KEEP_RATIO = 0.97D;
    @Unique private static final double SURF_GROUNDED_FALLBACK_MAX_VERTICAL_DISTANCE = 1.10D;
    @Unique private static final double SURF_GROUNDED_FALLBACK_LATERAL_EXTRA = 0.95D;
    @Unique private static final double SURF_AIRBORNE_FALLBACK_MIN_DESCENT = -0.22D;
    @Unique private static final double SURF_AIRBORNE_FALLBACK_MAX_ABOVE_FEET = 0.34D;
    @Unique private static final double SURF_INCOMING_FALLBACK_MAX_VERTICAL_DISTANCE = 0.64D;
    @Unique private static final double SURF_INCOMING_FALLBACK_LATERAL_EXTRA = 0.55D;
    @Unique private static final double SURF_SEAM_REACQUIRE_MIN_HORIZONTAL = 0.18D;
    @Unique private static final double SURF_SEAM_REACQUIRE_MAX_VERTICAL_DISTANCE = 1.08D;
    @Unique private static final double SURF_SEAM_REACQUIRE_LATERAL_EXTRA = 1.45D;
    @Unique private static final double SURF_SEAM_CONTINUITY_MIN_HORIZONTAL = 0.36D;
    @Unique private static final double SURF_SEAM_CONTINUITY_MAX_ASCENT_HIGH_SPEED = 0.90D;
    @Unique private static final double SURF_SEAM_CONTINUITY_ASCENT_MIN_HORIZONTAL = 0.95D;
    @Unique private static final double SURF_POST_MOVE_CONTACT_CARRY_MAX_DESCENT = 0.22D;
    @Unique private static final double SURF_POST_MOVE_CONTACT_CARRY_MAX_ASCENT_HIGH_SPEED = 0.90D;
    @Unique private static final double SURF_POST_MOVE_CONTACT_CARRY_ASCENT_MIN_HORIZONTAL = 0.95D;
    @Unique private static final double SURF_SEAM_REACQUIRE_MAX_ASCENT_HIGH_SPEED = 0.90D;
    @Unique private static final double SURF_SEAM_REACQUIRE_ASCENT_MIN_HORIZONTAL = 0.95D;
    @Unique private static final double SURF_BYPASS_BLIND_APPROACH_MIN_DESCENT = -1.10D;
    @Unique private static final double SURF_BYPASS_BLIND_APPROACH_MIN_HORIZONTAL = 0.95D;
    @Unique private static final double SURF_BYPASS_BLIND_APPROACH_MIN_TOTAL = 1.80D;
    @Unique private static final double SURF_BYPASS_GRACE_DESCENT_CUTOFF = -0.28D;
    @Unique private static final int SURF_GROUND_SUPPRESS_CONTACT_TICKS = 3;
    @Unique private static final int SURF_GROUND_SUPPRESS_GRACE_TICKS = 2;
    @Unique private static final double SURF_GROUND_SUPPRESS_MIN_HORIZONTAL = 0.10D;
    @Unique private static final int SURF_GROUND_SUPPRESS_ON_GROUND_TICKS = 2;
    @Unique private static final double SURF_GROUND_SUPPRESS_ON_GROUND_MIN_HORIZONTAL = 0.12D;
    @Unique private static final double SURF_GROUND_SUPPRESS_ON_GROUND_MAX_VERTICAL = 0.12D;
    @Unique private static final int SURF_JUMP_SUPPRESS_TICKS = 4;
    @Unique private static final double SURF_JUMP_SUPPRESS_MIN_HORIZONTAL = 0.08D;
    @Unique private static final double SURF_GROUNDED_LANDING_CAPTURE_MIN_HORIZONTAL = 0.14D;
    @Unique private static final double SURF_GROUNDED_LANDING_CAPTURE_MAX_VERTICAL_DISTANCE = 0.70D;
    @Unique private static final double SURF_GROUNDED_LANDING_CAPTURE_LATERAL_EXTRA = 0.70D;
    @Unique private static final double SURF_GROUNDED_LANDING_CAPTURE_MAX_ABOVE_FEET = 0.18D;
    @Unique private static final double SURF_GROUNDED_LANDING_CAPTURE_MAX_BELOW_FEET = 0.36D;
    @Unique private static final double SURF_GROUNDED_LANDING_CAPTURE_MAX_INTO_COMPONENT = 0.02D;
    @Unique private static final double SURF_GROUNDED_LANDING_CAPTURE_LOOSE_MAX_VERTICAL_DISTANCE = 1.05D;
    @Unique private static final double SURF_GROUNDED_LANDING_CAPTURE_LOOSE_LATERAL_EXTRA = 1.20D;
    @Unique private static final double SURF_GROUNDED_LANDING_CAPTURE_LOOSE_MAX_ABOVE_FEET = 0.45D;
    @Unique private static final double SURF_GROUNDED_LANDING_CAPTURE_LOOSE_MAX_BELOW_FEET = 0.55D;
    @Unique private static final double SURF_GROUNDED_LANDING_CAPTURE_LOOSE_MAX_INTO_COMPONENT = 0.08D;
    @Unique private static final double SURF_LAST_CONTACT_CONTINUITY_MAX_VERTICAL_DISTANCE = 0.52D;
    @Unique private static final double SURF_LAST_CONTACT_CONTINUITY_LATERAL_EXTRA = 0.30D;
    @Unique private static final double SURF_CLIP_MIN_INTO_COMPONENT = -0.008D;
    @Unique private static final double SURF_CLIP_SPEED_PRESERVE_MIN_UPWARD_VELOCITY = -0.06D;
    @Unique private static final double SURF_CLIP_SPEED_PRESERVE_MIN_HORIZONTAL_SPEED = 0.24D;
    @Unique private static final double SURF_CLIP_SPEED_PRESERVE_MIN_CLIPPED_HORIZONTAL = 0.06D;
    @Unique private static final double SURF_CLIP_SPEED_PRESERVE_MAX_INTO_RATIO = 0.34D;
    @Unique private static final double SURF_CLIP_SPEED_PRESERVE_TRIGGER_RATIO = 0.97D;
    @Unique private static final double SURF_CLIP_SPEED_PRESERVE_KEEP_RATIO = 0.997D;
    @Unique private static final double SURF_CLIP_SPEED_PRESERVE_MAX_INTO_COMPONENT = 0.18D;
    @Unique private static final double SURF_CLIP_PLANAR_PRESERVE_MIN_SPEED = 0.20D;
    @Unique private static final double SURF_CLIP_PLANAR_PRESERVE_TRIGGER_RATIO = 0.88D;
    @Unique private static final double SURF_CLIP_PLANAR_PRESERVE_KEEP_RATIO = 0.985D;
    @Unique private static final double SURF_CLIP_PLANAR_PRESERVE_MAX_INTO_RATIO = 1.30D;
    @Unique private static final double SURF_CLIP_PLANAR_PRESERVE_MAX_INTO_RATIO_HIGH_SPEED = 2.10D;
    @Unique private static final double SURF_CLIP_PLANAR_PRESERVE_MIN_VERTICAL = -1.55D;
    @Unique private static final double SURF_CLIP_PLANAR_PRESERVE_MAX_VERTICAL = 0.24D;
    @Unique private static final double SURF_CLIP_PLANAR_PRESERVE_MAX_VERTICAL_HIGH_SPEED = 0.90D;
    @Unique private static final double SURF_CLIP_PLANAR_PRESERVE_HIGH_VERTICAL_MIN_HORIZONTAL = 0.95D;
    @Unique private static final double SURF_PREMOVE_CLIP_RESTORE_TRIGGER_RATIO = 0.90D;
    @Unique private static final double SURF_PREMOVE_CLIP_RESTORE_KEEP_RATIO = 0.995D;
    @Unique private static final double SURF_LANDING_POST_GRAVITY_MAX_Y = -0.07D;
    @Unique private static final double SURF_CLIP_OVERBOUNCE = 1.0D;
    @Unique private static final double SURF_CLIP_BEND_PRESERVE_KEEP_RATIO = 0.90D;
    @Unique private static final double SURF_CLIP_MAX_REDIRECT_COS = 0.788D;
    @Unique private static final double SURF_SWEEP_MAX_STEP = 0.20D;
    @Unique private static final double SURF_SURFACE_SKIN = 0.001D;
    // Surface-march attach band: while the binding foot is within this distance of its surface the
    // rider is treated as ATTACHED (gravity clipped + foot glued every tick) even when not strictly
    // penetrating. Stops the float->snap vy sawtooth (periodic vertical bump) on a sliding ride.
    @Unique private static final double SURF_HULL_ATTACH_GAP = 0.12D;
    // Coyote window (ticks) for the auto-bhop jump buffer: how long after losing ground contact a
    // held jump still fires, so landing on / sliding off a block edge still bhops instead of dropping.
    @Unique private static final int SURF_JUMP_COYOTE_TICKS = 5;
    // Min ticks between jumps — shared by the vanilla jump() path and the coyote buffer so they can't
    // both fire on close ticks (double jump). Well under the bhop air-time cycle (~28 ticks), so it
    // never blocks a legit consecutive bhop.
    @Unique private static final int SURF_JUMP_BUFFER_COOLDOWN = 6;
    // Smooth on-ramping: ease the feet toward the surface each tick instead of snapping, so getting
    // onto a ramp (and crossing seams between close ramps) glides instead of popping. LIFT_EASE =
    // fraction of the gap closed per tick; MAX_EMBED guarantees you never stay embedded more than
    // this (deep penetration / hard landings still catch quickly, so you never sink through);
    // EASE_DOWN caps how fast a floating foot is pulled back down per tick.
    @Unique private static final double SURF_HULL_LIFT_EASE = 0.30D;
    @Unique private static final double SURF_HULL_MAX_EMBED = 0.05D;
    @Unique private static final double SURF_HULL_EASE_DOWN = 0.08D;
    @Unique private static final double SURF_HULL_SAMPLE_BAND = 0.60D;
    @Unique private static final double SURF_CLIP_EPSILON = 1.0E-6D;
    @Unique private static final double SURF_SAMPLE_EDGE_INSET = 0.02D;
    @Unique private static final double SURF_FAST_SAMPLE_MIN_HORIZONTAL = 0.24D;
    @Unique private static final double SURF_FAST_SAMPLE_MAX_VERTICAL = 0.14D;
    @Unique private static final double[] AUTO_STEP_HEIGHT_CANDIDATES = new double[]{1.20D, 1.12D, 1.0D, 0.88D, 0.75D, 0.50D};
    @Unique private static final double AUTO_STEP_SURFACE_EPSILON = 1.0E-4D;
    @Unique private static final double[][] SURF_SAMPLE_PATTERN_FAST = new double[][]{
            {0.0D, 0.0D},
            {1.0D, 0.0D},
            {-1.0D, 0.0D},
            {0.0D, 1.0D},
            {0.0D, -1.0D},
            {1.0D, 1.0D},
            {1.0D, -1.0D},
            {-1.0D, 1.0D},
            {-1.0D, -1.0D}
    };
    @Unique private static final double[][] SURF_SAMPLE_PATTERN_FULL = new double[][]{
            {0.0D, 0.0D},
            {1.0D, 0.0D},
            {-1.0D, 0.0D},
            {0.0D, 1.0D},
            {0.0D, -1.0D},
            {1.0D, 1.0D},
            {1.0D, -1.0D},
            {-1.0D, 1.0D},
            {-1.0D, -1.0D},
            {0.55D, 0.55D},
            {0.55D, -0.55D},
            {-0.55D, 0.55D},
            {-0.55D, -0.55D}
    };
    @Unique private static final int SURF_RAMP_QUERY_CACHE_MAX_ENTRIES = 24;
    @Unique private SurfContact lastSurfContact;
    @Unique private float minehop$lastTravelYaw;
    @Unique private boolean minehop$hasLastTravelYaw;
    @Unique private double minehop$substepRemainder;
    @Unique private int minehop$lastAirSteps = 6;
    @Unique private int minehop$groundedCoyote = 999;
    @Unique private int minehop$jumpCooldownTicks = 0;
    // True once the player has genuinely descended (vy<-0.05) or is grounded since the last jump
    // impulse. Gates the coyote buffer so it can't re-fire near apex while still RISING from a jump
    // (on slopes/stairs hasRealGroundBelow stays true the whole rise, pinning groundedCoyote at 0).
    @Unique private boolean minehop$descendedSinceJump = true;
    // True only if jump has been held CONTINUOUSLY since the last ground contact. Gates the coyote
    // buffer so walking off a ledge (not holding jump) then tapping jump in the air can't trigger an
    // air jump, while an edge skim during a bhop (jump held the whole time) still fires.
    @Unique private boolean minehop$jumpHeldFromGround = false;
    @Unique private int minehop$nearRampAcGraceTicks;
    @Unique private boolean minehop$sweepHit;
    @Unique private double minehop$sweepT;
    @Unique private Vec3d minehop$sweepNormal;
    @Unique private double minehop$sweepSurfaceY;
    @Unique private boolean minehop$hullOnRamp;
    @Unique private Vec3d minehop$hullNormal;
    @Unique private double minehop$hullSurfaceY;
    @Unique private double minehop$bindLift;
    @Unique private Vec3d minehop$bindNormal;
    @Unique private double minehop$bindSurfaceY;
    @Unique private int surfContactGraceTicks;
    @Unique private int surfCollisionBypassGraceTicks;
    @Unique private int surfLandingStabilizeTicks;
    @Unique private int surfEntryNoFrictionTicks;
    @Unique private int surfGroundSuppressTicks;
    @Unique private int surfJumpSuppressTicks;
    @Unique private long surfStopLogTick = Long.MIN_VALUE;
    @Unique private long surfRampQueryCacheTick = Long.MIN_VALUE;
    @Unique private final HashMap<Long, Object> surfRampQueryCache = new HashMap<>();

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
        this.minehop$hasLastTravelYaw = false;
        this.minehop$descendedSinceJump = true; // don't carry a stale rising-state across a teleport
        this.minehop$jumpHeldFromGround = false;
        this.minehop$jumpCooldownTicks = 0;
    }

    @Inject(method = "damage", at = @At("HEAD"), cancellable = true)
    public void onDamage(ServerWorld world, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        MinehopConfig config = this.getType() == EntityType.PLAYER
                ? ConfigWrapper.getEffectiveConfig(this)
                : ConfigWrapper.config;
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
            if (!config.fall_damage)
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

    @Inject(method = "travel", at = @At("HEAD"), cancellable = true)
    public void travel(Vec3d movementInput, CallbackInfo ci) {
        MinehopConfig config;
        double speedCap = 1000000;
        if (Minehop.override_config && Minehop.receivedConfig) {
            config = new MinehopConfig();
            config.movement.sv_friction = Minehop.o_sv_friction;
            config.movement.sv_accelerate = Minehop.o_sv_accelerate;
            config.movement.sv_airaccelerate = Minehop.o_sv_airaccelerate;
            config.movement.sv_maxairspeed = Minehop.o_sv_maxairspeed;
            config.movement.sv_jump_impulse = Minehop.o_sv_jump_impulse;
            config.movement.speed_mul = Minehop.o_speed_mul;
            config.movement.sv_gravity = Minehop.o_sv_gravity;
            config.movement.sv_stopspeed = Minehop.o_sv_stopspeed;
            config.movement.speed_coefficient = Minehop.o_speed_coefficient;
            config.movement.auto_step_up = Minehop.o_auto_step_up;
            config.movement.css_crouch_jump = Minehop.o_css_crouch_jump;
            config.movement.disable_sprint = Minehop.o_disable_sprint;
            config.enabled = Minehop.o_enabled;
            config.fall_damage = Minehop.o_fall_damage;
            speedCap = Minehop.o_speed_cap;
        }
        else {
            config = ConfigWrapper.getEffectiveConfig(this);
        }

        if (this.getType() != EntityType.PLAYER) { return; }

        // Fully disable sprinting when configured (global or per-map). Runs on both client and
        // server every tick so the sprint state can never stick.
        if (config.movement.disable_sprint && this.isSprinting()) {
            this.setSprinting(false);
        }

        this.minehop$updateCssCrouchOffset(config);

        if (!config.enabled) { return; }

        if (!this.canMoveVoluntarily() && !this.isLogicalSideForUpdatingMovement()) { return; }

        if (this.isTouchingWater() || this.isInLava() || this.isGliding()) { return; }

        LivingEntity self = (LivingEntity) this.getWorld().getEntityById(this.getId());
        PlayerEntity debugPlayer = self instanceof PlayerEntity playerEntity ? playerEntity : null;
        ServerPlayerEntity startZonePlayer = !this.getWorld().isClient && self instanceof ServerPlayerEntity serverPlayer ? serverPlayer : null;
        StartEntity activeStartZone = startZonePlayer == null ? null : StartEntity.getStartZoneForPlayer(startZonePlayer);
        boolean inStartZone = activeStartZone != null;
        if (inStartZone) {
            Minehop.playerMapLocation.put(startZonePlayer.getUuidAsString(), activeStartZone);
            Vec3d clampedStartVelocity = StartEntity.clampVelocityToStartZoneSpeed(startZonePlayer, this.getVelocity());
            if (clampedStartVelocity != this.getVelocity() && !clampedStartVelocity.equals(this.getVelocity())) {
                this.setVelocity(clampedStartVelocity);
                this.velocityDirty = true;
            }
        }
        boolean surfDebug = debugPlayer != null
                && Minehop.surfDebugPlayers.contains(debugPlayer.getUuid())
                && !this.getWorld().isClient;
        boolean broadIncomingFallbackUsed = false;
        boolean groundedReacquireApplied = false;
        boolean endpointDisableTriggered = false;
        boolean catastrophicRestoreApplied = false;
        boolean forceRestorePreApplied = false;
        boolean forceRestorePostApplied = false;
        boolean resetVelocityCarryActive = false;
        if (!this.getWorld().isClient && self instanceof ServerPlayerEntity serverPlayerEntity) {
            Vec3d resetCarryVelocity = ResetEntity.applyScheduledVelocityCarry(serverPlayerEntity);
            if (resetCarryVelocity != null && resetCarryVelocity.lengthSquared() > 1.0E-8D) {
                Vec3d currentVelocity = this.getVelocity();
                this.setVelocity(resetCarryVelocity.x, currentVelocity.y, resetCarryVelocity.z);
                this.velocityDirty = true;
                resetVelocityCarryActive = true;
            }
        }

        if (this.getType() == EntityType.PLAYER && isFlying((PlayerEntity) self)) { return; }

        this.sidewaysSpeed /= 0.98F;
        this.forwardSpeed /= 0.98F;
        double sI = movementInput.x / 0.98F;
        double fI = movementInput.z / 0.98F;

        float airStrafeStartYaw = this.minehop$hasLastTravelYaw ? this.minehop$lastTravelYaw : this.getYaw();
        this.minehop$lastTravelYaw = this.getYaw();
        this.minehop$hasLastTravelYaw = true;

        this.jumpingCooldown = 0;

        BlockPos blockPos = this.getVelocityAffectingPos();

        SurfContact preMoveSurfContact = this.findSurfContact();
        if (preMoveSurfContact == null && !this.isOnGround()) {
            preMoveSurfContact = this.findIncomingSurfContact(this.getVelocity());
        }
        if (preMoveSurfContact == null && !this.isClimbing()) {
            Vec3d preVelocity = this.getVelocity();
            double preHorizontal = this.getHorizontalSpeed(preVelocity);
            if (preHorizontal >= SURF_COLLISION_BYPASS_GRACE_MIN_HORIZONTAL
                    && preVelocity.y <= this.getClipRestoreMaxVertical(preHorizontal)) {
                Box preBox = this.getBoundingBox();
                SurfContact broadIncomingFallback = this.findNearestSurfContactFallback(
                        this.getX() + preVelocity.x,
                        this.getZ() + preVelocity.z,
                        preBox.minY + preVelocity.y,
                        preBox.maxY + preVelocity.y,
                        SURF_SEAM_REACQUIRE_MAX_VERTICAL_DISTANCE + 0.35D,
                        SURF_SEAM_REACQUIRE_LATERAL_EXTRA + 0.55D
                );
                if (this.isReusableSurfContact(broadIncomingFallback)) {
                    preMoveSurfContact = broadIncomingFallback;
                    broadIncomingFallbackUsed = true;
                }
            }
        }
        boolean nearSurfRampPre = !this.isClimbing() && this.isNearSurfRamp();

        // Jump buffer for the edge bug: an edge (esp. thin blocks like carpet) can fail to register
        // onGround on the landing tick, so vanilla never calls jump() and you slide off ("edge bug").
        // The buffer fires a jump when you're holding jump and there is ACTUALLY ground within 0.20
        // below right now (groundedishForCoyote), aren't rising (vy<=0.10), have descended/landed
        // since the last jump, and aren't on/near a surf ramp. There is deliberately NO air coyote
        // window — requiring ground-below-NOW is what stops "walk off a platform then jump in the
        // air". Once consumed it won't re-fire until grounded again. (groundedCoyote field is kept
        // only for telemetry; it no longer gates the buffer.)
        if (this.minehop$jumpCooldownTicks > 0) {
            this.minehop$jumpCooldownTicks--;
        }
        boolean groundedishForCoyote = this.isOnGround() || this.minehop$hasRealGroundBelow();
        if (groundedishForCoyote) {
            this.minehop$groundedCoyote = 0;
            // Seed the continuous-hold flag from whether jump is held at this ground contact.
            this.minehop$jumpHeldFromGround = this.jumping;
        } else {
            if (this.minehop$groundedCoyote < 999) {
                this.minehop$groundedCoyote++;
            }
            // Released in the air -> the hold is no longer continuous from the ground, so a later
            // re-press can't fire the buffer (kills the walk-off-then-jump-in-air exploit).
            if (!this.jumping) {
                this.minehop$jumpHeldFromGround = false;
            }
        }
        // We've genuinely descended/landed (not rising from a jump) once we're truly on the ground
        // or actually falling. Until then the buffer must not fire — otherwise on a slope/stairs,
        // where hasRealGroundBelow keeps groundedCoyote pinned at 0 during the whole ascent, the
        // buffer fires a SECOND impulse at apex (cooldown=6 expires right as vy drops <=0.10).
        if (this.isOnGround() || this.getVelocity().y < -0.05D) {
            this.minehop$descendedSinceJump = true;
        }
        if (this.jumping
                && ((Object) this) instanceof PlayerEntity
                && this.minehop$jumpCooldownTicks <= 0
                && this.minehop$descendedSinceJump
                && this.minehop$jumpHeldFromGround
                && !this.isClimbing()
                && !this.isTouchingWater() && !this.isInLava() && !this.isGliding()
                && this.getVelocity().y <= 0.10D
                && groundedishForCoyote
                && preMoveSurfContact == null
                && !nearSurfRampPre) {
            double bufferYVel = config.movement.sv_jump_impulse * SOURCE_UNIT_TO_BLOCKS_PER_TICK;
            if (this.hasStatusEffect(StatusEffects.JUMP_BOOST)) {
                bufferYVel += 0.1F * (this.getStatusEffect(StatusEffects.JUMP_BOOST).getAmplifier() + 1);
            }
            Vec3d bufferVel = this.getVelocity();
            this.setVelocity(bufferVel.x, bufferYVel, bufferVel.z);
            this.velocityDirty = true;
            this.minehop$groundedCoyote = 999; // consume — one buffered jump per ground contact
            this.minehop$jumpCooldownTicks = SURF_JUMP_BUFFER_COOLDOWN;
            this.minehop$descendedSinceJump = false; // must descend/land again before next buffer fire
        }

        SurfContact previousSurfContact = this.lastSurfContact;
        double seamContinuityHorizontalPre = this.getHorizontalSpeed(this.getVelocity());
        double seamContinuityMaxAscentPre = this.getSeamContinuityMaxAscent(seamContinuityHorizontalPre);
        boolean nearSurfSupportPre = nearSurfRampPre || this.isReusableSurfContact(previousSurfContact);
        if (!nearSurfSupportPre
                && !this.isClimbing()
                && this.getHorizontalSpeed(this.getVelocity()) >= SURF_GROUND_SUPPRESS_MIN_HORIZONTAL
                && (this.isOnGround()
                || this.isReusableSurfContact(previousSurfContact)
                || this.surfGroundSuppressTicks > 0
                || this.surfContactGraceTicks > 0
                || this.surfCollisionBypassGraceTicks > 0)) {
            SurfContact preSupportContact = this.findJumpRampSupportContact(this.getBoundingBox(), this.getVelocity());
            nearSurfSupportPre = this.isReusableSurfContact(preSupportContact);
        }
        boolean preSurfContactFromGrace = false;
        if (preMoveSurfContact != null && !this.isClimbing()) {
            preMoveSurfContact = this.resolveSurfPenetration(preMoveSurfContact);
            preMoveSurfContact = this.snapToSurfSurface(preMoveSurfContact);
            if (preMoveSurfContact == null && this.isReusableSurfContact(previousSurfContact)) {
                preMoveSurfContact = previousSurfContact;
            }
            if (this.isReusableSurfContact(preMoveSurfContact)) {
                this.lastSurfContact = preMoveSurfContact;
                this.surfContactGraceTicks = SURF_CONTACT_GRACE_TICKS;
                this.surfCollisionBypassGraceTicks = SURF_COLLISION_BYPASS_GRACE_TICKS;
            } else if (this.surfContactGraceTicks > 0 && this.isReusableSurfContact(previousSurfContact) && nearSurfSupportPre) {
                preMoveSurfContact = previousSurfContact;
                preSurfContactFromGrace = true;
                this.surfContactGraceTicks--;
            } else {
                this.lastSurfContact = null;
                this.surfContactGraceTicks = 0;
            }
        } else if (!this.isClimbing() && this.surfContactGraceTicks > 0 && this.isReusableSurfContact(this.lastSurfContact) && nearSurfSupportPre) {
            preMoveSurfContact = this.lastSurfContact;
            preSurfContactFromGrace = true;
            this.surfContactGraceTicks--;
        } else if (!this.isClimbing()
                && nearSurfSupportPre
                && !this.isOnGround()
                && this.isReusableSurfContact(previousSurfContact)
                && (!previousSurfContact.hardEndpoint()
                    || (seamContinuityHorizontalPre > SURF_HARD_ENDPOINT_DISABLE_MAX_HORIZONTAL
                        && this.getVelocity().y <= SURF_POST_MOVE_CONTACT_CARRY_MAX_DESCENT))
                && seamContinuityHorizontalPre >= SURF_SEAM_CONTINUITY_MIN_HORIZONTAL
                && this.getVelocity().y <= seamContinuityMaxAscentPre) {

            preMoveSurfContact = previousSurfContact;
            preSurfContactFromGrace = true;
            this.lastSurfContact = previousSurfContact;
            this.surfContactGraceTicks = Math.max(this.surfContactGraceTicks, 1);
            this.surfCollisionBypassGraceTicks = Math.max(this.surfCollisionBypassGraceTicks, 2);
        } else {
            this.lastSurfContact = null;
            this.surfContactGraceTicks = 0;
        }
        nearSurfRampPre = !this.isClimbing() && this.isNearSurfRamp();
        nearSurfSupportPre = nearSurfRampPre || this.isReusableSurfContact(this.lastSurfContact);
        if (!nearSurfRampPre || this.isClimbing()) {
            this.surfEntryNoFrictionTicks = 0;
        } else if (this.surfEntryNoFrictionTicks > 0) {
            this.surfEntryNoFrictionTicks--;
        }
        double preHorizontalSpeed = this.getHorizontalSpeed(this.getVelocity());
        boolean transitionRampZonePre = !this.isClimbing() && this.isInRampTransitionZone();
        boolean nearRampEndpointPre = !this.isClimbing()
                && !transitionRampZonePre
                && this.isNearSurfRampEndpoint();
        double preTotalSpeed = this.getVelocity().length();
        boolean hardEndpointPre = preMoveSurfContact != null && preMoveSurfContact.hardEndpoint();
        if (nearRampEndpointPre && preSurfContactFromGrace && preHorizontalSpeed <= SURF_HARD_ENDPOINT_DISABLE_MAX_HORIZONTAL) {
            preMoveSurfContact = null;
            preSurfContactFromGrace = false;
            this.surfContactGraceTicks = 0;
            this.surfCollisionBypassGraceTicks = 0;
            hardEndpointPre = false;
        }
        if (hardEndpointPre && preSurfContactFromGrace && preHorizontalSpeed <= SURF_HARD_ENDPOINT_DISABLE_MAX_HORIZONTAL) {
            preMoveSurfContact = null;
            preSurfContactFromGrace = false;
            this.surfContactGraceTicks = 0;
            this.surfCollisionBypassGraceTicks = 0;
            hardEndpointPre = false;
        }
        boolean hasSurfSupportPre = preMoveSurfContact != null
                || (this.surfContactGraceTicks > 0 && this.lastSurfContact != null);
        boolean recentSurfStatePre = hasSurfSupportPre || this.surfCollisionBypassGraceTicks > 0;
        if (!this.isClimbing()) {
            boolean suppressionContextPre = nearSurfSupportPre
                    || recentSurfStatePre
                    || this.surfGroundSuppressTicks > 0;
            if (nearSurfSupportPre && preMoveSurfContact != null) {
                this.surfGroundSuppressTicks = SURF_GROUND_SUPPRESS_CONTACT_TICKS;
            } else if (recentSurfStatePre
                    && this.isOnGround()
                    && preHorizontalSpeed >= SURF_GROUND_SUPPRESS_ON_GROUND_MIN_HORIZONTAL
                    && this.getVelocity().y <= SURF_GROUND_SUPPRESS_ON_GROUND_MAX_VERTICAL) {
                this.surfGroundSuppressTicks = Math.max(this.surfGroundSuppressTicks, SURF_GROUND_SUPPRESS_ON_GROUND_TICKS);
            } else if (suppressionContextPre
                    && (preSurfContactFromGrace || this.isReusableSurfContact(this.lastSurfContact) || recentSurfStatePre)
                    && preHorizontalSpeed >= SURF_GROUND_SUPPRESS_MIN_HORIZONTAL
                    && this.getVelocity().y <= SURF_POST_MOVE_CONTACT_CARRY_MAX_DESCENT) {
                this.surfGroundSuppressTicks = Math.max(this.surfGroundSuppressTicks, SURF_GROUND_SUPPRESS_GRACE_TICKS);
            } else if (this.surfGroundSuppressTicks > 0) {
                this.surfGroundSuppressTicks--;
            }
        } else {
            this.surfGroundSuppressTicks = 0;
        }
        boolean speedBypassPre = nearSurfSupportPre
                && preHorizontalSpeed >= SURF_SPEED_BYPASS_MIN_HORIZONTAL
                && preHorizontalSpeed >= SURF_COLLISION_BYPASS_GRACE_MIN_HORIZONTAL
                && this.getVelocity().y <= 0.12D
                && hasSurfSupportPre;
        boolean descentBypassPre = nearSurfSupportPre
                && hasSurfSupportPre
                && preHorizontalSpeed >= SURF_COLLISION_BYPASS_GRACE_MIN_HORIZONTAL
                && this.getVelocity().y <= SURF_PRECONTACT_DESCENT_BYPASS_MIN_FALL;
        boolean blindVerticalDropPre = nearSurfRampPre
                && !recentSurfStatePre
                && preHorizontalSpeed < 0.10D
                && this.getVelocity().y < -0.10D;
        boolean bypassGraceEligiblePre = this.surfCollisionBypassGraceTicks > 0
                && nearSurfSupportPre
                && preHorizontalSpeed >= SURF_COLLISION_BYPASS_GRACE_MIN_HORIZONTAL
                && (this.surfContactGraceTicks > 0 || this.isReusableSurfContact(this.lastSurfContact));
        boolean highSpeedBlindApproachPre = nearSurfRampPre
                && preMoveSurfContact == null
                && !this.isOnGround()
                && this.surfGroundSuppressTicks == 0
                && this.getVelocity().y <= SURF_BYPASS_BLIND_APPROACH_MIN_DESCENT
                && preHorizontalSpeed >= SURF_BYPASS_BLIND_APPROACH_MIN_HORIZONTAL
                && preTotalSpeed >= SURF_BYPASS_BLIND_APPROACH_MIN_TOTAL;
        boolean riskyGraceDescentPre = preSurfContactFromGrace
                && this.getVelocity().y <= SURF_BYPASS_GRACE_DESCENT_CUTOFF;
        boolean groundSuppressBypassPre = this.surfGroundSuppressTicks > 0
                && preHorizontalSpeed >= SURF_GROUND_SUPPRESS_MIN_HORIZONTAL
                && this.getVelocity().y >= -0.06D
                && this.getVelocity().y <= SURF_POST_MOVE_CONTACT_CARRY_MAX_DESCENT;

        boolean realGroundBelow = !this.isClimbing() && this.minehop$hasRealGroundBelow();
        boolean bypassRampCollision = !this.isClimbing()
                && !realGroundBelow
                && !(hardEndpointPre && preSurfContactFromGrace)
                && !blindVerticalDropPre
                && !highSpeedBlindApproachPre
                && !riskyGraceDescentPre && (
                preMoveSurfContact != null
                        || (this.surfContactGraceTicks > 0 && this.lastSurfContact != null && nearSurfSupportPre)
                        || bypassGraceEligiblePre
                        || speedBypassPre
                        || descentBypassPre
                        || groundSuppressBypassPre
        );
        if (riskyGraceDescentPre) {
            this.surfCollisionBypassGraceTicks = 0;
        }
        if (bypassRampCollision) {
            Minehop.surfCollisionBypassEntities.add(this.getId());
        } else {
            Minehop.surfCollisionBypassEntities.remove(this.getId());
        }
        boolean fullGrounded = this.wasOnGround && this.isOnGround();
        boolean surfing = preMoveSurfContact != null && !this.isClimbing() && !realGroundBelow;
        boolean surfGroundSuppressed = this.surfGroundSuppressTicks > 0
                && !this.isClimbing()
                && !realGroundBelow
                && nearSurfSupportPre;
        boolean pseudoSurfing = !surfing
                && !this.isClimbing()
                && !realGroundBelow
                && nearSurfRampPre
                && recentSurfStatePre
                && preHorizontalSpeed >= SURF_COLLISION_BYPASS_GRACE_MIN_HORIZONTAL;
        if (surfing || pseudoSurfing || surfGroundSuppressed) {
            this.setOnGround(false);
        }
        if (realGroundBelow) {
            this.surfContactGraceTicks = 0;
            this.surfGroundSuppressTicks = 0;
        }
        boolean suppressGroundFrictionNearRamp = nearSurfRampPre
                && !realGroundBelow
                && (preMoveSurfContact != null || this.isReusableSurfContact(this.lastSurfContact))
                && (preHorizontalSpeed >= SURF_NEAR_RAMP_GROUND_FRICTION_SPEED_THRESHOLD || this.surfEntryNoFrictionTicks > 0)
                && !this.isClimbing();

        boolean useGroundMovement = fullGrounded
                && !surfing
                && !pseudoSurfing
                && !surfGroundSuppressed
                && !bypassRampCollision
                && !suppressGroundFrictionNearRamp
                && !resetVelocityCarryActive;
        if (useGroundMovement) {
            if (!Minehop.groundedList.contains(this.getNameForScoreboard())) {
                Minehop.groundedList.add(this.getNameForScoreboard());
            }
        }
        else {
            Minehop.groundedList.remove(this.getNameForScoreboard());
        }
        if (useGroundMovement) {
            this.setVelocity(applySourceFriction(this.getVelocity(), config.movement.sv_friction, config.movement.sv_stopspeed, 1.0D));
            Vec3d groundVelocity = this.getVelocity();
            double groundHorizontalSpeed = this.getHorizontalSpeed(groundVelocity);
            if (groundHorizontalSpeed > speedCap) {
                double groundScale = speedCap / groundHorizontalSpeed;
                this.setVelocity(groundVelocity.x * groundScale, groundVelocity.y, groundVelocity.z * groundScale);
                this.velocityDirty = true;
            }
        }
        this.wasOnGround = (surfing || pseudoSurfing || surfGroundSuppressed) ? false : this.isOnGround();

        if (this.isOnGround() && !surfing && this.getWorld().isClient && ((Object) this) instanceof PlayerEntity) {
            // Jump arc ended: latch the per-jump strafe summary (sync/efficiency/strafes) and reset
            // accumulators for the next jump. Client-only so it isn't double-driven by the server tick
            // (shared static map keyed by name) which would reset mid-jump and flash a bogus 100%.
            net.nerdorg.minehop.util.StrafeStats landedStats = Minehop.strafeStatsMap.get(this.getNameForScoreboard());
            if (landedStats != null && landedStats.measuredTicks > 0) {
                landedStats.latchAndReset();
            }
        }

        if (sI != 0.0F || fI != 0.0F) {
            Vec3d wishVector = MovementUtil.movementInputToVelocity(new Vec3d(sI, 0.0F, fI), 1.0F, this.getYaw());
            double wishVectorLength = wishVector.horizontalLength();
            if (wishVectorLength > 0.0D) {
                Vec3d wishDir = new Vec3d(wishVector.x / wishVectorLength, 0.0D, wishVector.z / wishVectorLength);
                double wishSpeed = getBaseWishSpeed(config) * wishVectorLength;
                Vec3d accelVec = new Vec3d(this.getVelocity().x, 0.0D, this.getVelocity().z);
                Vec3d acceleratedVelocity;
                double airWishSpeedCap = getAirWishSpeedCap(config);

                if (useGroundMovement) {
                    acceleratedVelocity = accelerateSource(accelVec, wishDir, wishSpeed, wishSpeed, config.movement.sv_accelerate, 1.0D, false, 1.0D);
                } else {
                    acceleratedVelocity = accelerateAirSubstepped(accelVec, sI, fI, config, airWishSpeedCap, airStrafeStartYaw, this.getYaw());
                }

                Vec3d newVelocity = new Vec3d(acceleratedVelocity.x, this.getVelocity().y, acceleratedVelocity.z);
                Vec3d newHorizontalVelocity = new Vec3d(newVelocity.x, 0.0D, newVelocity.z);
                if (inStartZone && startZonePlayer != null) {
                    Vec3d clampedStartZoneVelocity = StartEntity.clampVelocityToStartZoneSpeed(startZonePlayer, newVelocity);
                    newVelocity = clampedStartZoneVelocity;
                    newHorizontalVelocity = new Vec3d(clampedStartZoneVelocity.x, 0.0D, clampedStartZoneVelocity.z);
                }

                if (!useGroundMovement && this.getWorld().isClient && ((Object) this) instanceof PlayerEntity) {
                    // Faithful Source/bhop strafe stats, measured from the ACTUAL before/after
                    // (CLIENT-ONLY: the side that renders the HUD; identical predicted physics, and
                    // avoids the server tick double-recording the same shared StrafeStats / racing).
                    // horizontal velocity this tick (robust to the 128Hz substepped air accel).
                    double vBefore = Math.sqrt((accelVec.x * accelVec.x) + (accelVec.z * accelVec.z));
                    double vAfter = Math.sqrt((newVelocity.x * newVelocity.x) + (newVelocity.z * newVelocity.z));

                    // View turn applied this tick (degrees). airStrafeStartYaw is the yaw at tick start.
                    double yawDelta = Math.abs(normalizeAngle(this.getYaw() - airStrafeStartYaw));
                    boolean turning = yawDelta > 0.05D;
                    boolean moving = vBefore > 1.0E-4D;
                    boolean measured = turning && moving; // already airborne in this branch

                    double gain = vAfter - vBefore;
                    boolean good = gain > 1.0E-5D;

                    // The BEST speed + the turn (deg) that achieves it this tick, under the REAL
                    // substepped physics (swept). Both efficiency and gauge normalize against this so
                    // they agree: at the optimum both read 100; over/under-turning drops both.
                    double[] opt = this.minehop$optimalAirStrafe(accelVec, sI, fI, config,
                            airWishSpeedCap, airStrafeStartYaw, this.minehop$lastAirSteps);
                    double optimalGain = opt[0] - vBefore;
                    double optimalTurnDeg = opt[1];

                    // Efficiency = realized gain / best achievable gain (rare to hit 100%).
                    double effTick = optimalGain > 1.0E-6D ? MathHelper.clamp(gain / optimalGain, 0.0D, 1.0D) : 0.0D;

                    // Gauge = your view turn vs the optimal turn, 0..200, 100 = perfect (center).
                    double gaugeRatio = optimalTurnDeg > 1.0E-3D
                            ? MathHelper.clamp((yawDelta / optimalTurnDeg) * 100.0D, 0.0D, 200.0D)
                            : (yawDelta < 0.5D ? 100.0D : 200.0D);

                    int strafeSign = sI > 0.0F ? 1 : (sI < 0.0F ? -1 : 0);

                    net.nerdorg.minehop.util.StrafeStats stats = Minehop.strafeStatsMap
                            .computeIfAbsent(this.getNameForScoreboard(), k -> new net.nerdorg.minehop.util.StrafeStats());
                    stats.recordTick(measured, good, effTick, gaugeRatio, strafeSign);

                    // Legacy/compat live maps (read by the HUD via shared statics in SP).
                    Minehop.efficiencyMap.put(this.getNameForScoreboard(), stats.liveEfficiency);
                }

                this.setVelocity(new Vec3d(newHorizontalVelocity.getX(), newVelocity.getY(), newHorizontalVelocity.getZ()));
                if (surfDebug) {
                    double beforeAccelSpeed = this.getHorizontalSpeed(accelVec) * 800.0D;
                    double afterAccelSpeed = this.getHorizontalSpeed(newHorizontalVelocity) * 800.0D;
                    Minehop.LOGGER.info(String.format(java.util.Locale.ROOT,
                            "[ACDBG] branch=%s sI=%.2f fI=%.2f yaw=%.1f prevYaw=%.1f onGround=%b useGround=%b surfing=%b preContact=%b nearRamp=%b cap=%.1f wishSpeed=%.1f beforeAccel=%.1f afterAccel=%.1f u/s",
                            useGroundMovement ? "GROUND" : "AIR",
                            sI, fI, this.getYaw(), this.prevYaw,
                            this.isOnGround(), useGroundMovement, surfing,
                            preMoveSurfContact != null, nearSurfRampPre,
                            airWishSpeedCap * 800.0D, wishSpeed * 800.0D,
                            beforeAccelSpeed, afterAccelSpeed));
                }
            }
        }

        if (Minehop.surfHullSolverEnabled && !this.isClimbing()) {
            Vec3d ladderNormalH = this.getLadderNormal();
            this.setVelocity(applySourceLadderMove(this.getVelocity(), fI, sI, ladderNormalH));
            Vec3d velBeforeMoveH = this.getVelocity();
            Vec3d posBeforeMoveH = this.getPos();
            boolean handledH = this.minehop$surfMoveAndCollide(velBeforeMoveH);
            if (!handledH) {
                this.move(MovementType.SELF, velBeforeMoveH);
            }
            if (config.movement.auto_step_up) {
                this.minehop$tryForcedAutoStepUp(velBeforeMoveH, posBeforeMoveH, null);
            }
            if (!this.getWorld().isClient && self instanceof ServerPlayerEntity speH) {
                ResetEntity.recordObservedVelocity(speH, this.getPos().subtract(posBeforeMoveH));
            }
            boolean onRampH = handledH && this.minehop$hullOnRamp;
            if (onRampH && !this.minehop$hasRealGroundBelow()) {
                this.setOnGround(false);
            }
            Vec3d preVelH = this.getVelocity();
            double yVelH = preVelH.y;
            double gravityH = getGravityPerTick(config);
            if (preVelH.y <= 0.0D && this.hasStatusEffect(StatusEffects.SLOW_FALLING)) {
                gravityH = 0.01D;
                this.fallDistance = 0.0F;
            }
            if (this.hasStatusEffect(StatusEffects.LEVITATION)) {
                yVelH += (0.05D * (this.getStatusEffect(StatusEffects.LEVITATION).getAmplifier() + 1) - preVelH.y) * 0.2D;
                this.fallDistance = 0.0F;
            } else if (this.getWorld().isClient && !this.getWorld().isChunkLoaded(blockPos)) {
                yVelH = 0.0D;
            } else if (!this.hasNoGravity() && !(this.isClimbing() && ladderNormalH != null)) {
                yVelH -= gravityH;
            }
            Vec3d afterGravH = new Vec3d(preVelH.x, yVelH, preVelH.z);
            if (inStartZone && startZonePlayer != null) {
                afterGravH = StartEntity.clampVelocityToStartZoneSpeed(startZonePlayer, afterGravH);
            }
            if (onRampH && this.minehop$hullNormal != null) {
                afterGravH = this.clipVelocityCore(afterGravH, this.minehop$hullNormal);
                if (inStartZone && startZonePlayer != null) {
                    afterGravH = StartEntity.clampVelocityToStartZoneSpeed(startZonePlayer, afterGravH);
                }
            }
            BlockState belowStateH = this.getWorld().getBlockState(this.getBlockPos());
            if (belowStateH.isOf(ModBlocks.BOOSTER_BLOCK) && (this.getWorld().getTime() > this.boostTime + 5 || this.getWorld().getTime() < this.boostTime)) {
                this.boostTime = this.getWorld().getTime();
                BoostBlockEntity boostBlockEntityH = (BoostBlockEntity) this.getWorld().getBlockEntity(this.getBlockPos());
                if (boostBlockEntityH != null) {
                    afterGravH = afterGravH.add(boostBlockEntityH.getXPower(), boostBlockEntityH.getYPower(), boostBlockEntityH.getZPower());
                }
            }
            if (inStartZone && startZonePlayer != null) {
                afterGravH = StartEntity.clampVelocityToStartZoneSpeed(startZonePlayer, afterGravH);
            }
            this.setVelocity(afterGravH);

            if (nearSurfRampPre || onRampH) {
                this.minehop$nearRampAcGraceTicks = 8;
            } else if (this.minehop$nearRampAcGraceTicks > 0) {
                this.minehop$nearRampAcGraceTicks--;
            }
            if (!this.getWorld().isClient && self instanceof ServerPlayerEntity acPlayerH) {
                boolean surfingForAcH = onRampH || nearSurfRampPre || this.minehop$nearRampAcGraceTicks > 0;
                AntiCheatManager.onMovementTick(
                        acPlayerH,
                        posBeforeMoveH,
                        this.getPos(),
                        velBeforeMoveH,
                        afterGravH,
                        this.isOnGround(),
                        this.wasOnGround,
                        this.isClimbing(),
                        this.isTouchingWater() || this.isInLava(),
                        surfingForAcH,
                        this.jumping && (this.isOnGround() || this.wasOnGround),
                        speedCap
                );
            }
            if (surfDebug) {
                Minehop.LOGGER.info(String.format(java.util.Locale.ROOT,
                        "[ACDBG] HULL onRamp=%b ny=%.3f surfY=%.3f pos=(%.2f,%.3f,%.2f) vel=(%.3f,%.3f,%.3f)",
                        onRampH, onRampH && this.minehop$hullNormal != null ? this.minehop$hullNormal.y : 0.0D,
                        this.minehop$hullSurfaceY, this.getX(), this.getBoundingBox().minY, this.getZ(),
                        afterGravH.x, afterGravH.y, afterGravH.z));
            }
            this.updateLimbs(self instanceof Flutterer);
            ci.cancel();
            return;
        }

        if (preMoveSurfContact != null && !this.isClimbing()) {
            Vec3d incomingPreClipVelocity = this.getVelocity();
            Vec3d clippedPreClipVelocity = this.clipVelocityAgainstSurfRamp(incomingPreClipVelocity, preMoveSurfContact.normal());
            double incomingPreClipHorizontal = this.getHorizontalSpeed(incomingPreClipVelocity);
            if (incomingPreClipVelocity.y <= this.getClipRestoreMaxVertical(incomingPreClipHorizontal)) {
                clippedPreClipVelocity = this.restoreSurfClipHorizontal(
                        clippedPreClipVelocity,
                        incomingPreClipVelocity,
                        preMoveSurfContact.normal(),
                        SURF_PREMOVE_CLIP_RESTORE_TRIGGER_RATIO,
                        SURF_PREMOVE_CLIP_RESTORE_KEEP_RATIO
                );
            }
            this.setVelocity(clippedPreClipVelocity);
        }

        Vec3d ladderNormal = this.getLadderNormal();
        boolean sourceLadderActive = this.isClimbing() && ladderNormal != null;
        this.setVelocity(applySourceLadderMove(this.getVelocity(), fI, sI, ladderNormal));
        Vec3d velocityBeforeMove = this.getVelocity();
        if (preMoveSurfContact == null && !this.isClimbing()) {
            SurfContact incomingPreMoveContact = this.findIncomingSurfContact(velocityBeforeMove);
            if (this.isReusableSurfContact(incomingPreMoveContact)) {
                preMoveSurfContact = incomingPreMoveContact;
                this.lastSurfContact = incomingPreMoveContact;
                this.surfContactGraceTicks = SURF_CONTACT_GRACE_TICKS;
                this.surfCollisionBypassGraceTicks = SURF_COLLISION_BYPASS_GRACE_TICKS;
                Minehop.surfCollisionBypassEntities.add(this.getId());
                Vec3d incomingVelocityBeforeClip = velocityBeforeMove;
                velocityBeforeMove = this.clipVelocityAgainstSurfRamp(incomingVelocityBeforeClip, incomingPreMoveContact.normal());
                double incomingBeforeClipHorizontal = this.getHorizontalSpeed(incomingVelocityBeforeClip);
                if (incomingVelocityBeforeClip.y <= this.getClipRestoreMaxVertical(incomingBeforeClipHorizontal)) {
                    velocityBeforeMove = this.restoreSurfClipHorizontal(
                            velocityBeforeMove,
                            incomingVelocityBeforeClip,
                            incomingPreMoveContact.normal(),
                            SURF_PREMOVE_CLIP_RESTORE_TRIGGER_RATIO,
                            SURF_PREMOVE_CLIP_RESTORE_KEEP_RATIO
                    );
                }
                this.setVelocity(velocityBeforeMove);
            }
        }
        Vec3d posBeforeMove = this.getPos();
        this.move(MovementType.SELF, velocityBeforeMove);
        StartEntity postMoveStartZone = startZonePlayer == null ? null : StartEntity.getStartZoneForPlayer(startZonePlayer);
        boolean inStartZoneAfterMove = postMoveStartZone != null;
        if (inStartZoneAfterMove) {
            Minehop.playerMapLocation.put(startZonePlayer.getUuidAsString(), postMoveStartZone);
        }
        if (config.movement.auto_step_up) {
            this.minehop$tryForcedAutoStepUp(velocityBeforeMove, posBeforeMove, preMoveSurfContact);
        }
        if (!this.getWorld().isClient && self instanceof ServerPlayerEntity serverPlayerEntityForSample) {
            Vec3d movedThisTick = this.getPos().subtract(posBeforeMove);
            ResetEntity.recordObservedVelocity(serverPlayerEntityForSample, movedThisTick);
        }
        SurfContact postMoveSurfContact = this.findSurfContact();
        if (postMoveSurfContact == null && this.shouldCarryPreMoveContactAfterMove(preMoveSurfContact)) {
            postMoveSurfContact = preMoveSurfContact;
        }
        SurfContact recoveredImpactContact = null;
        if (postMoveSurfContact == null && !this.isClimbing()) {
            boolean nearRampAfterMoveForRecovery = this.isNearSurfRamp();

            boolean likelyRampImpact = nearRampAfterMoveForRecovery
                    && velocityBeforeMove.y < -0.35D
                    && this.getVelocity().y > velocityBeforeMove.y + 0.20D;
            if (likelyRampImpact) {

                recoveredImpactContact = this.isReusableSurfContact(preMoveSurfContact)
                        ? preMoveSurfContact
                        : this.findIncomingSurfContact(velocityBeforeMove);
                if (!this.isReusableSurfContact(recoveredImpactContact)) {
                    recoveredImpactContact = null;
                } else {
                    postMoveSurfContact = recoveredImpactContact;
                }
            }
        }
        boolean nearSurfRampPost = !this.isClimbing() && this.isNearSurfRamp();
        SurfContact previousPostSurfContact = this.lastSurfContact;
        boolean postSurfContactFromGrace = false;
        if (postMoveSurfContact != null && !this.isClimbing()) {
            SurfContact fallbackPostContact = postMoveSurfContact;
            postMoveSurfContact = this.resolveSurfPenetration(postMoveSurfContact);
            postMoveSurfContact = this.snapToSurfSurface(postMoveSurfContact);
            if (postMoveSurfContact == null && this.isReusableSurfContact(fallbackPostContact)) {
                postMoveSurfContact = fallbackPostContact != null ? fallbackPostContact : previousPostSurfContact;
            }
            if (this.isReusableSurfContact(postMoveSurfContact)) {
                this.lastSurfContact = postMoveSurfContact;
                this.surfContactGraceTicks = SURF_CONTACT_GRACE_TICKS;
                this.surfCollisionBypassGraceTicks = SURF_COLLISION_BYPASS_GRACE_TICKS;
            } else if (this.surfContactGraceTicks > 0 && this.isReusableSurfContact(previousPostSurfContact) && nearSurfRampPost) {
                postMoveSurfContact = previousPostSurfContact;
                postSurfContactFromGrace = true;
                this.surfContactGraceTicks--;
            }
        } else if (!this.isClimbing() && this.surfContactGraceTicks > 0 && this.isReusableSurfContact(this.lastSurfContact) && nearSurfRampPost) {
            postMoveSurfContact = this.lastSurfContact;
            postSurfContactFromGrace = true;
            this.surfContactGraceTicks--;
        }
        double seamContinuityHorizontalPost = this.getHorizontalSpeed(this.getVelocity());
        double seamContinuityMaxAscentPost = this.getSeamContinuityMaxAscent(seamContinuityHorizontalPost);
        if (postMoveSurfContact == null
                && !this.isClimbing()
                && this.surfContactGraceTicks == 0
                && nearSurfRampPost
                && !this.isOnGround()
                && this.isReusableSurfContact(previousPostSurfContact)
                && (!previousPostSurfContact.hardEndpoint()
                    || (seamContinuityHorizontalPost > SURF_HARD_ENDPOINT_DISABLE_MAX_HORIZONTAL
                        && this.getVelocity().y <= SURF_POST_MOVE_CONTACT_CARRY_MAX_DESCENT))
                && seamContinuityHorizontalPost >= SURF_SEAM_CONTINUITY_MIN_HORIZONTAL
                && this.getVelocity().y <= seamContinuityMaxAscentPost) {

            postMoveSurfContact = previousPostSurfContact;
            postSurfContactFromGrace = true;
            this.lastSurfContact = previousPostSurfContact;
            this.surfContactGraceTicks = 1;
            this.surfCollisionBypassGraceTicks = Math.max(this.surfCollisionBypassGraceTicks, 2);
        }
        if (postMoveSurfContact == null && this.surfCollisionBypassGraceTicks > 0) {
            if (this.surfContactGraceTicks == 0) {
                this.surfCollisionBypassGraceTicks = Math.max(this.surfCollisionBypassGraceTicks - 2, 0);
            } else {
                this.surfCollisionBypassGraceTicks--;
            }
        }
        if (postMoveSurfContact == null
                && this.surfContactGraceTicks == 0
                && !this.isReusableSurfContact(this.lastSurfContact)) {
            this.surfCollisionBypassGraceTicks = 0;
        }
        if (postMoveSurfContact == null
                && this.surfContactGraceTicks == 0
                && !this.isOnGround()
                && this.getVelocity().y < -0.28D) {

            this.surfCollisionBypassGraceTicks = 0;
        }
        nearSurfRampPost = !this.isClimbing() && this.isNearSurfRamp();
        double postHorizontalSpeed = this.getHorizontalSpeed(this.getVelocity());
        Vec3d groundedLandingRestoreDir = null;
        double groundedLandingRestoreHorizontal = 0.0D;
        boolean groundedLandingReacquiredThisTick = false;
        boolean nearSurfSupportPost = nearSurfRampPost || this.isReusableSurfContact(this.lastSurfContact);
        if (!nearSurfSupportPost
                && !this.isClimbing()
                && postHorizontalSpeed >= SURF_GROUND_SUPPRESS_MIN_HORIZONTAL
                && (this.isOnGround()
                || this.isReusableSurfContact(this.lastSurfContact)
                || this.surfGroundSuppressTicks > 0
                || this.surfContactGraceTicks > 0
                || this.surfCollisionBypassGraceTicks > 0)) {
            SurfContact postSupportContact = this.findJumpRampSupportContact(this.getBoundingBox(), this.getVelocity());
            nearSurfSupportPost = this.isReusableSurfContact(postSupportContact);
        }
        double incomingHorizontalForReacquire = this.getHorizontalSpeed(velocityBeforeMove);
        boolean groundedLandingStallForReacquire = this.isOnGround()
                && incomingHorizontalForReacquire >= SURF_LANDING_STALL_RESTORE_MIN_INCOMING_HORIZONTAL
                && postHorizontalSpeed + 1.0E-6D < incomingHorizontalForReacquire * 0.85D;
        double seamReacquireMaxVertical = incomingHorizontalForReacquire >= SURF_SEAM_REACQUIRE_ASCENT_MIN_HORIZONTAL
                ? SURF_SEAM_REACQUIRE_MAX_ASCENT_HIGH_SPEED
                : 0.24D;
        if (postMoveSurfContact == null
                && !this.isClimbing()
                && nearSurfRampPost
                && (!this.isOnGround() || groundedLandingStallForReacquire)
                && (postHorizontalSpeed >= SURF_SEAM_REACQUIRE_MIN_HORIZONTAL || groundedLandingStallForReacquire)
                && this.getVelocity().y <= seamReacquireMaxVertical) {
            Box postBox = this.getBoundingBox();
            SurfContact seamRecoveryContact = this.findNearestSurfContactFallback(
                    this.getX(),
                    this.getZ(),
                    postBox.minY,
                    postBox.maxY,
                    SURF_SEAM_REACQUIRE_MAX_VERTICAL_DISTANCE,
                    SURF_SEAM_REACQUIRE_LATERAL_EXTRA
            );
            if (this.isReusableSurfContact(seamRecoveryContact)) {
                postMoveSurfContact = seamRecoveryContact;
                this.lastSurfContact = seamRecoveryContact;
                this.surfContactGraceTicks = SURF_CONTACT_GRACE_TICKS;
                this.surfCollisionBypassGraceTicks = SURF_COLLISION_BYPASS_GRACE_TICKS;
                if (groundedLandingStallForReacquire) {
                    Vec3d clippedIncoming = this.clipVelocityAgainstSurfRamp(velocityBeforeMove, seamRecoveryContact.normal());
                    Vec3d restoreDir = this.resolveLandingRestoreDirection(clippedIncoming, velocityBeforeMove, seamRecoveryContact.normal());
                    if (restoreDir != null) {
                        groundedLandingRestoreHorizontal = Math.max(
                                postHorizontalSpeed,
                                incomingHorizontalForReacquire * SURF_LANDING_STALL_RESTORE_KEEP_RATIO
                        );
                        groundedLandingRestoreDir = restoreDir;
                        groundedLandingReacquiredThisTick = true;
                        groundedReacquireApplied = true;
                        this.setOnGround(false);
                        this.surfEntryNoFrictionTicks = Math.max(this.surfEntryNoFrictionTicks, 3);
                        this.surfGroundSuppressTicks = Math.max(this.surfGroundSuppressTicks, SURF_GROUND_SUPPRESS_ON_GROUND_TICKS);
                        this.surfJumpSuppressTicks = Math.max(this.surfJumpSuppressTicks, SURF_JUMP_SUPPRESS_TICKS);
                        postHorizontalSpeed = Math.max(postHorizontalSpeed, groundedLandingRestoreHorizontal);
                    }
                }
            }
        }
        boolean bypassGraceEligiblePost = this.surfCollisionBypassGraceTicks > 0
                && nearSurfSupportPost
                && postHorizontalSpeed >= SURF_COLLISION_BYPASS_GRACE_MIN_HORIZONTAL
                && (this.surfContactGraceTicks > 0 || this.isReusableSurfContact(this.lastSurfContact));
        boolean transitionRampZonePost = !this.isClimbing() && this.isInRampTransitionZone();
        boolean nearRampEndpointPost = !this.isClimbing()
                && !transitionRampZonePost
                && this.isNearSurfRampEndpoint();
        boolean hardEndpointPost = postMoveSurfContact != null && postMoveSurfContact.hardEndpoint();
        if (nearRampEndpointPost
                && postSurfContactFromGrace
                && preMoveSurfContact == null
                && postHorizontalSpeed <= SURF_HARD_ENDPOINT_DISABLE_MAX_HORIZONTAL) {
            postMoveSurfContact = null;
            postSurfContactFromGrace = false;
            this.surfContactGraceTicks = 0;
            this.surfCollisionBypassGraceTicks = 0;
            hardEndpointPost = false;
        }
        boolean hardEndpointGracePost = postMoveSurfContact == null
                && this.lastSurfContact != null
                && this.lastSurfContact.hardEndpoint();
        double endpointCarryHorizontal = Math.max(preHorizontalSpeed, postHorizontalSpeed);
        boolean highSpeedEndpointCarry = endpointCarryHorizontal > SURF_HARD_ENDPOINT_DISABLE_MAX_HORIZONTAL
                && this.getVelocity().y <= SURF_POST_MOVE_CONTACT_CARRY_MAX_DESCENT
                && (nearSurfRampPost
                    || nearSurfSupportPost
                    || this.isReusableSurfContact(previousPostSurfContact)
                    || this.isReusableSurfContact(preMoveSurfContact));
        boolean disableBypassAtHardEndpoint = !this.isClimbing()
                && postMoveSurfContact == null
                && !nearSurfSupportPost
                && (hardEndpointPost || hardEndpointGracePost || (hardEndpointPre && preMoveSurfContact != null))
                && !highSpeedEndpointCarry;
        if (disableBypassAtHardEndpoint) {
            endpointDisableTriggered = true;
            this.surfContactGraceTicks = 0;
            this.surfCollisionBypassGraceTicks = 0;
        }
        boolean hasSurfSupportPost = postMoveSurfContact != null
                || (this.surfContactGraceTicks > 0 && this.lastSurfContact != null);
        boolean recentSurfStatePost = hasSurfSupportPost || this.surfCollisionBypassGraceTicks > 0;
        boolean speedBypassPost = nearSurfSupportPost
                && hasSurfSupportPost
                && postHorizontalSpeed >= SURF_COLLISION_BYPASS_GRACE_MIN_HORIZONTAL
                && postHorizontalSpeed >= SURF_SPEED_BYPASS_MIN_HORIZONTAL;
        if (!this.isClimbing() && nearSurfSupportPost) {
            if (postMoveSurfContact != null) {
                this.surfGroundSuppressTicks = Math.max(this.surfGroundSuppressTicks, SURF_GROUND_SUPPRESS_CONTACT_TICKS);
            } else if ((postSurfContactFromGrace || recentSurfStatePost)
                    && postHorizontalSpeed >= SURF_GROUND_SUPPRESS_MIN_HORIZONTAL
                    && this.getVelocity().y <= SURF_POST_MOVE_CONTACT_CARRY_MAX_DESCENT) {
                this.surfGroundSuppressTicks = Math.max(this.surfGroundSuppressTicks, SURF_GROUND_SUPPRESS_GRACE_TICKS);
            }
        } else if (!nearSurfSupportPost) {
            this.surfGroundSuppressTicks = 0;
        }
        if (!this.isClimbing()
                && this.isOnGround()
                && nearSurfRampPost
                && postMoveSurfContact != null
                && postHorizontalSpeed >= SURF_JUMP_SUPPRESS_MIN_HORIZONTAL
                && this.getVelocity().y <= SURF_GROUND_SUPPRESS_ON_GROUND_MAX_VERTICAL + 0.10D) {
            this.surfJumpSuppressTicks = Math.max(this.surfJumpSuppressTicks, SURF_JUMP_SUPPRESS_TICKS);
        }
        if (!this.isClimbing()) {
            if (this.surfJumpSuppressTicks > 0) {
                this.surfJumpSuppressTicks--;
            }
        } else {
            this.surfJumpSuppressTicks = 0;
        }
        boolean realGroundBelowPost = !this.isClimbing() && this.minehop$hasRealGroundBelow();
        if (realGroundBelowPost) {
            this.surfContactGraceTicks = 0;
            this.surfCollisionBypassGraceTicks = 0;
            this.surfGroundSuppressTicks = 0;
            this.lastSurfContact = null;
            postMoveSurfContact = null;
        }
        boolean keepBypassAfterMove = !disableBypassAtHardEndpoint
                && !this.isClimbing()
                && !realGroundBelowPost
                && (
                postMoveSurfContact != null
                        || (this.surfContactGraceTicks > 0 && this.lastSurfContact != null && nearSurfSupportPost)
                        || bypassGraceEligiblePost
                        || speedBypassPost
        );
        if (postMoveSurfContact == null && !bypassGraceEligiblePost && !speedBypassPost) {
            this.surfCollisionBypassGraceTicks = 0;
        }
        if (keepBypassAfterMove) {
            Minehop.surfCollisionBypassEntities.add(this.getId());
        } else {
            Minehop.surfCollisionBypassEntities.remove(this.getId());
        }
        if (postMoveSurfContact != null && !this.isClimbing() && !realGroundBelowPost) {
            this.setOnGround(false);
        }

        Vec3d preVel = this.getVelocity();
        if (groundedLandingReacquiredThisTick && groundedLandingRestoreDir != null && groundedLandingRestoreHorizontal > 1.0E-6D) {
            double resolvedHorizontal = this.getHorizontalSpeed(preVel);
            if (resolvedHorizontal + 1.0E-6D < groundedLandingRestoreHorizontal * 0.92D) {
                preVel = new Vec3d(
                        groundedLandingRestoreDir.x * groundedLandingRestoreHorizontal,
                        Math.min(preVel.y, -0.08D),
                        groundedLandingRestoreDir.z * groundedLandingRestoreHorizontal
                );
            }
            Vec3d movedAfterCollision = this.getPos().subtract(posBeforeMove);
            double movedHorizontal = Math.hypot(movedAfterCollision.x, movedAfterCollision.z);
            if (movedHorizontal + 1.0E-6D < groundedLandingRestoreHorizontal * 0.35D) {
                double nudgeDistance = MathHelper.clamp(groundedLandingRestoreHorizontal * 0.20D, 0.04D, 0.16D);
                Vec3d nudge = groundedLandingRestoreDir.multiply(nudgeDistance);
                Box nudgedBox = this.getBoundingBox().offset(nudge.x, 0.0D, nudge.z);
                if (this.getWorld().isSpaceEmpty(this, nudgedBox)) {
                    this.setPosition(this.getX() + nudge.x, this.getY(), this.getZ() + nudge.z);
                }
            }
            this.setOnGround(false);
        }
        SurfContact catastrophicSlowdownContact = postMoveSurfContact;
        if (!this.isUsableSurfContactForCarryOrRestore(catastrophicSlowdownContact)
                && this.isUsableSurfContactForCarryOrRestore(preMoveSurfContact)) {
            catastrophicSlowdownContact = preMoveSurfContact;
        }
        if (!this.isUsableSurfContactForCarryOrRestore(catastrophicSlowdownContact)
                && this.isUsableSurfContactForCarryOrRestore(this.lastSurfContact)) {
            catastrophicSlowdownContact = this.lastSurfContact;
        }
        double incomingHorizontalCatastrophic = this.getHorizontalSpeed(velocityBeforeMove);
        double resolvedHorizontalCatastrophic = this.getHorizontalSpeed(preVel);
        boolean catastrophicRampSlowdown = !this.isClimbing()
                && this.isUsableSurfContactForCarryOrRestore(catastrophicSlowdownContact)
                && (nearSurfRampPre || nearSurfRampPost || nearSurfSupportPost)
                && incomingHorizontalCatastrophic >= SURF_CONTACT_SPEED_RESTORE_MIN_HORIZONTAL
                && resolvedHorizontalCatastrophic + 1.0E-6D
                < incomingHorizontalCatastrophic * SURF_CONTACT_SPEED_RESTORE_POST_GRAVITY_TRIGGER_RATIO;
        if (catastrophicRampSlowdown) {
            Vec3d restoreDir = this.resolveLandingRestoreDirection(
                    preVel,
                    velocityBeforeMove,
                    catastrophicSlowdownContact.normal()
            );
            if (restoreDir != null) {
                catastrophicRestoreApplied = true;
                double targetHorizontal = Math.max(
                        resolvedHorizontalCatastrophic,
                        incomingHorizontalCatastrophic * SURF_CONTACT_SPEED_RESTORE_POST_GRAVITY_KEEP_RATIO
                );
                preVel = new Vec3d(
                        restoreDir.x * targetHorizontal,
                        Math.min(preVel.y, -0.06D),
                        restoreDir.z * targetHorizontal
                );
                this.surfEntryNoFrictionTicks = Math.max(this.surfEntryNoFrictionTicks, 3);
                this.surfCollisionBypassGraceTicks = Math.max(this.surfCollisionBypassGraceTicks, 3);
                this.surfGroundSuppressTicks = Math.max(this.surfGroundSuppressTicks, SURF_GROUND_SUPPRESS_ON_GROUND_TICKS);
                this.surfJumpSuppressTicks = Math.max(this.surfJumpSuppressTicks, SURF_JUMP_SUPPRESS_TICKS);
                this.setOnGround(false);
            }
        }
        if (postMoveSurfContact == null
                && !this.isClimbing()
                && nearSurfSupportPost
                && preHorizontalSpeed >= SURF_COLLISION_BYPASS_GRACE_MIN_HORIZONTAL) {
            Vec3d incomingHorizontalVec = new Vec3d(velocityBeforeMove.x, 0.0D, velocityBeforeMove.z);
            double incomingHorizontal = incomingHorizontalVec.horizontalLength();
            double resolvedHorizontal = this.getHorizontalSpeed(preVel);
            if (incomingHorizontal > 1.0E-6D
                    && resolvedHorizontal + 1.0E-6D < incomingHorizontal * 0.97D
                    && (this.isOnGround() || velocityBeforeMove.y <= 0.08D)) {
                Vec3d incomingHorizontalDir = incomingHorizontalVec.multiply(1.0D / incomingHorizontal);
                double targetHorizontal = incomingHorizontal * 0.985D;
                preVel = new Vec3d(
                        incomingHorizontalDir.x * targetHorizontal,
                        Math.min(preVel.y, -0.08D),
                        incomingHorizontalDir.z * targetHorizontal
                );
                this.setOnGround(false);
                this.surfGroundSuppressTicks = Math.max(
                        this.surfGroundSuppressTicks,
                        SURF_GROUND_SUPPRESS_ON_GROUND_TICKS
                );
                this.surfCollisionBypassGraceTicks = Math.max(this.surfCollisionBypassGraceTicks, 2);
            }
        }
        if (recoveredImpactContact != null && preVel.y > velocityBeforeMove.y) {
            double restoredImpactY = velocityBeforeMove.y * SURF_IMPACT_VERTICAL_RESTORE_FACTOR;
            preVel = new Vec3d(preVel.x, Math.min(preVel.y, restoredImpactY), preVel.z);
            double incomingHorizontal = this.getHorizontalSpeed(velocityBeforeMove);
            double resolvedHorizontal = this.getHorizontalSpeed(preVel);
            if (incomingHorizontal > 0.08D && resolvedHorizontal + 1.0E-6D < incomingHorizontal * 0.92D) {
                double targetHorizontal = incomingHorizontal * SURF_IMPACT_HORIZONTAL_RESTORE_FACTOR;
                if (resolvedHorizontal > 1.0E-6D) {
                    double scale = targetHorizontal / resolvedHorizontal;
                    preVel = new Vec3d(preVel.x * scale, preVel.y, preVel.z * scale);
                } else {
                    preVel = new Vec3d(
                            velocityBeforeMove.x * SURF_IMPACT_HORIZONTAL_RESTORE_FACTOR,
                            preVel.y,
                            velocityBeforeMove.z * SURF_IMPACT_HORIZONTAL_RESTORE_FACTOR
                    );
                }
            }
        }
        if (postMoveSurfContact == null && !this.isClimbing() && nearSurfRampPost) {

            double incomingHorizontal = this.getHorizontalSpeed(velocityBeforeMove);
            double resolvedHorizontal = this.getHorizontalSpeed(preVel);
            boolean likelyRampImpactNoContact = velocityBeforeMove.y < -0.22D
                    && preVel.y > velocityBeforeMove.y + 0.14D
                    && incomingHorizontal >= 0.22D
                    && resolvedHorizontal + 1.0E-6D < incomingHorizontal * 0.95D;
            if (likelyRampImpactNoContact) {
                Vec3d incomingHorizontalVec = new Vec3d(velocityBeforeMove.x, 0.0D, velocityBeforeMove.z);
                double incomingHorizontalVecLen = incomingHorizontalVec.horizontalLength();
                if (incomingHorizontalVecLen > 1.0E-6D) {
                    Vec3d incomingHorizontalDir = incomingHorizontalVec.multiply(1.0D / incomingHorizontalVecLen);
                    double targetHorizontal = Math.max(
                            resolvedHorizontal,
                            incomingHorizontal * SURF_MISSED_CONTACT_LANDING_KEEP_RATIO
                    );
                    preVel = new Vec3d(
                            incomingHorizontalDir.x * targetHorizontal,
                            preVel.y,
                            incomingHorizontalDir.z * targetHorizontal
                    );
                }

                if (preVel.y > -0.12D) {
                    preVel = new Vec3d(preVel.x, -0.14D, preVel.z);
                }
                if (this.surfCollisionBypassGraceTicks < 2) {
                    this.surfCollisionBypassGraceTicks = 2;
                }
                if (incomingHorizontal >= SURF_JUMP_SUPPRESS_MIN_HORIZONTAL) {
                    this.surfJumpSuppressTicks = Math.max(this.surfJumpSuppressTicks, SURF_JUMP_SUPPRESS_TICKS);
                }
            }
        }
        if (postMoveSurfContact != null && !this.isClimbing()) {
            double incomingHorizontal = this.getHorizontalSpeed(velocityBeforeMove);
            double resolvedHorizontal = this.getHorizontalSpeed(preVel);
            if (velocityBeforeMove.y < -0.18D
                    && incomingHorizontal >= SURF_LANDING_KEEP_HORIZONTAL_MIN_SPEED
                    && resolvedHorizontal + 1.0E-6D < incomingHorizontal * 0.94D) {
                Vec3d projectedIncoming = this.clipVelocityAgainstSurfRamp(velocityBeforeMove, postMoveSurfContact.normal());
                Vec3d incomingHorizontalVec = new Vec3d(projectedIncoming.x, 0.0D, projectedIncoming.z);
                double incomingHorizontalVecLen = incomingHorizontalVec.horizontalLength();
                if (incomingHorizontalVecLen < 1.0E-6D) {
                    incomingHorizontalVec = new Vec3d(velocityBeforeMove.x, 0.0D, velocityBeforeMove.z);
                    incomingHorizontalVecLen = incomingHorizontalVec.horizontalLength();
                }
                if (incomingHorizontalVecLen > 1.0E-6D) {
                    Vec3d incomingHorizontalDir = incomingHorizontalVec.multiply(1.0D / incomingHorizontalVecLen);
                    double targetHorizontal = Math.max(
                            resolvedHorizontal,
                            incomingHorizontal * SURF_LANDING_KEEP_HORIZONTAL_RATIO
                    );
                    preVel = new Vec3d(
                            incomingHorizontalDir.x * targetHorizontal,
                            preVel.y,
                            incomingHorizontalDir.z * targetHorizontal
                    );
                }
            }

            Vec3d projectedIncomingForStall = this.clipVelocityAgainstSurfRamp(velocityBeforeMove, postMoveSurfContact.normal());
            double projectedIncomingHorizontal = this.getHorizontalSpeed(projectedIncomingForStall);
            double clipRestoreMaxVertical = this.getClipRestoreMaxVertical(incomingHorizontal);
            if (velocityBeforeMove.y <= clipRestoreMaxVertical
                    && incomingHorizontal >= SURF_LANDING_STALL_RESTORE_MIN_INCOMING_HORIZONTAL
                    && projectedIncomingHorizontal >= SURF_LANDING_STALL_RESTORE_MIN_PROJECTED_HORIZONTAL
                    && resolvedHorizontal + 1.0E-6D < projectedIncomingHorizontal * SURF_LANDING_STALL_RESTORE_TRIGGER_RATIO) {
                Vec3d projectedHorizontalVec = new Vec3d(projectedIncomingForStall.x, 0.0D, projectedIncomingForStall.z);
                double projectedHorizontalLen = projectedHorizontalVec.horizontalLength();
                if (projectedHorizontalLen > 1.0E-6D) {
                    Vec3d projectedHorizontalDir = projectedHorizontalVec.multiply(1.0D / projectedHorizontalLen);
                    double targetHorizontal = Math.max(
                            resolvedHorizontal,
                            projectedIncomingHorizontal * SURF_LANDING_STALL_RESTORE_KEEP_RATIO
                    );
                    preVel = new Vec3d(
                            projectedHorizontalDir.x * targetHorizontal,
                            Math.min(preVel.y, -0.08D),
                            projectedHorizontalDir.z * targetHorizontal
                    );
                    this.surfEntryNoFrictionTicks = Math.max(this.surfEntryNoFrictionTicks, 3);
                    this.surfCollisionBypassGraceTicks = Math.max(this.surfCollisionBypassGraceTicks, 2);
                    this.surfJumpSuppressTicks = Math.max(this.surfJumpSuppressTicks, SURF_JUMP_SUPPRESS_TICKS);
                }
            }

            resolvedHorizontal = this.getHorizontalSpeed(preVel);
            if (velocityBeforeMove.y <= clipRestoreMaxVertical
                    && incomingHorizontal >= SURF_LANDING_STALL_RESTORE_MIN_INCOMING_HORIZONTAL
                    && resolvedHorizontal + 1.0E-6D < incomingHorizontal * SURF_LANDING_STALL_FORCE_RESTORE_TRIGGER_RATIO) {
                Vec3d restoreDir = this.resolveLandingRestoreDirection(
                        this.clipVelocityAgainstSurfRamp(velocityBeforeMove, postMoveSurfContact.normal()),
                        velocityBeforeMove,
                        postMoveSurfContact.normal()
                );
                if (restoreDir != null) {
                    forceRestorePreApplied = true;
                    double targetHorizontal = Math.max(
                            resolvedHorizontal,
                            incomingHorizontal * SURF_LANDING_STALL_FORCE_RESTORE_KEEP_RATIO
                    );
                    preVel = new Vec3d(
                            restoreDir.x * targetHorizontal,
                            Math.min(preVel.y, -0.08D),
                            restoreDir.z * targetHorizontal
                    );
                    this.surfEntryNoFrictionTicks = Math.max(this.surfEntryNoFrictionTicks, 3);
                    this.surfCollisionBypassGraceTicks = Math.max(this.surfCollisionBypassGraceTicks, 2);
                    this.surfJumpSuppressTicks = Math.max(this.surfJumpSuppressTicks, SURF_JUMP_SUPPRESS_TICKS);
                    this.setOnGround(false);
                }
            }

            resolvedHorizontal = this.getHorizontalSpeed(preVel);
            if (incomingHorizontal >= SURF_CONTACT_SPEED_RESTORE_MIN_HORIZONTAL
                    && resolvedHorizontal + 1.0E-6D < incomingHorizontal * SURF_CONTACT_SPEED_RESTORE_TRIGGER_RATIO) {
                Vec3d restoreDir = this.resolveLandingRestoreDirection(
                        preVel,
                        velocityBeforeMove,
                        postMoveSurfContact.normal()
                );
                if (restoreDir != null) {
                    forceRestorePreApplied = true;
                    double targetHorizontal = Math.max(
                            resolvedHorizontal,
                            incomingHorizontal * SURF_CONTACT_SPEED_RESTORE_KEEP_RATIO
                    );
                    preVel = new Vec3d(
                            restoreDir.x * targetHorizontal,
                            Math.min(preVel.y, -0.06D),
                            restoreDir.z * targetHorizontal
                    );
                    this.surfEntryNoFrictionTicks = Math.max(this.surfEntryNoFrictionTicks, 3);
                    this.surfCollisionBypassGraceTicks = Math.max(this.surfCollisionBypassGraceTicks, 2);
                    this.surfJumpSuppressTicks = Math.max(this.surfJumpSuppressTicks, SURF_JUMP_SUPPRESS_TICKS);
                    this.setOnGround(false);
                }
            }
        }
        boolean reacquiredSurfContact = postMoveSurfContact != null
                && preMoveSurfContact == null
                && !this.isClimbing();
        if (reacquiredSurfContact) {
            double incomingHorizontal = this.getHorizontalSpeed(velocityBeforeMove);
            double resolvedHorizontal = this.getHorizontalSpeed(preVel);
            if (incomingHorizontal >= SURF_LANDING_KEEP_HORIZONTAL_MIN_SPEED
                    && velocityBeforeMove.y <= 0.08D
                    && resolvedHorizontal + 1.0E-6D < incomingHorizontal * 0.985D) {
                Vec3d projectedIncoming = this.clipVelocityAgainstSurfRamp(velocityBeforeMove, postMoveSurfContact.normal());
                Vec3d incomingHorizontalVec = new Vec3d(projectedIncoming.x, 0.0D, projectedIncoming.z);
                double incomingHorizontalVecLen = incomingHorizontalVec.horizontalLength();
                if (incomingHorizontalVecLen < 1.0E-6D) {
                    incomingHorizontalVec = new Vec3d(velocityBeforeMove.x, 0.0D, velocityBeforeMove.z);
                    incomingHorizontalVecLen = incomingHorizontalVec.horizontalLength();
                }
                if (incomingHorizontalVecLen > 1.0E-6D) {
                    Vec3d incomingHorizontalDir = incomingHorizontalVec.multiply(1.0D / incomingHorizontalVecLen);
                    double targetHorizontal = Math.max(
                            resolvedHorizontal,
                            incomingHorizontal * SURF_LANDING_KEEP_HORIZONTAL_RATIO
                    );
                    preVel = new Vec3d(
                            incomingHorizontalDir.x * targetHorizontal,
                            preVel.y,
                            incomingHorizontalDir.z * targetHorizontal
                    );
                }
            }
        }
        boolean justAcquiredSurfContact = postMoveSurfContact != null
                && preMoveSurfContact == null
                && previousPostSurfContact == null;
        if ((justAcquiredSurfContact || reacquiredSurfContact) && !this.isClimbing()) {

            this.surfEntryNoFrictionTicks = Math.max(this.surfEntryNoFrictionTicks, 3);
            if (postHorizontalSpeed >= SURF_JUMP_SUPPRESS_MIN_HORIZONTAL
                    && velocityBeforeMove.y <= SURF_GROUND_SUPPRESS_ON_GROUND_MAX_VERTICAL + 0.10D) {
                this.surfJumpSuppressTicks = Math.max(this.surfJumpSuppressTicks, SURF_JUMP_SUPPRESS_TICKS);
            }
        } else if (postMoveSurfContact == null
                && this.surfContactGraceTicks == 0
                && this.surfCollisionBypassGraceTicks == 0) {
            this.surfEntryNoFrictionTicks = 0;
        }
        if (justAcquiredSurfContact && !this.isClimbing()) {
            double incomingHorizontal = this.getHorizontalSpeed(velocityBeforeMove);
            double resolvedHorizontal = this.getHorizontalSpeed(preVel);
            if (incomingHorizontal >= SURF_LANDING_KEEP_HORIZONTAL_MIN_SPEED
                    && resolvedHorizontal + 1.0E-6D < incomingHorizontal * SURF_LANDING_KEEP_HORIZONTAL_RATIO) {
                Vec3d projectedIncoming = this.clipVelocityAgainstSurfRamp(velocityBeforeMove, postMoveSurfContact.normal());
                Vec3d incomingHorizontalVec = new Vec3d(projectedIncoming.x, 0.0D, projectedIncoming.z);
                double incomingHorizontalVecLen = incomingHorizontalVec.horizontalLength();
                if (incomingHorizontalVecLen < 1.0E-6D) {
                    incomingHorizontalVec = new Vec3d(velocityBeforeMove.x, 0.0D, velocityBeforeMove.z);
                    incomingHorizontalVecLen = incomingHorizontalVec.horizontalLength();
                }
                if (incomingHorizontalVecLen > 1.0E-6D) {
                    Vec3d incomingHorizontalDir = incomingHorizontalVec.multiply(1.0D / incomingHorizontalVecLen);
                    double targetHorizontal = Math.max(
                            resolvedHorizontal,
                            incomingHorizontal * SURF_LANDING_KEEP_HORIZONTAL_RATIO
                    );
                    preVel = new Vec3d(
                            incomingHorizontalDir.x * targetHorizontal,
                            preVel.y,
                            incomingHorizontalDir.z * targetHorizontal
                    );
                }
            }

            if (velocityBeforeMove.y <= SURF_LANDING_VERTICAL_TO_HORIZONTAL_MIN_FALL
                    && incomingHorizontal < SURF_LANDING_VERTICAL_TO_HORIZONTAL_MAX_INCOMING) {
                Vec3d normal = postMoveSurfContact.normal();
                Vec3d projectedIncoming = this.clipVelocityAgainstSurfRamp(velocityBeforeMove, normal);
                Vec3d projectedHorizontal = new Vec3d(projectedIncoming.x, 0.0D, projectedIncoming.z);
                Vec3d gravity = new Vec3d(0.0D, -1.0D, 0.0D);
                Vec3d downhillOnPlane = gravity.subtract(normal.multiply(gravity.dotProduct(normal)));
                Vec3d downhillHorizontal = new Vec3d(downhillOnPlane.x, 0.0D, downhillOnPlane.z);

                Vec3d launchDirection = null;
                if (projectedHorizontal.lengthSquared() > 1.0E-6D) {
                    launchDirection = projectedHorizontal.normalize();
                } else if (downhillHorizontal.lengthSquared() > 1.0E-6D) {
                    launchDirection = downhillHorizontal.normalize();
                } else {
                    Vec3d horizontalFromNormal = new Vec3d(normal.x, 0.0D, normal.z);
                    if (horizontalFromNormal.lengthSquared() > 1.0E-6D) {
                        launchDirection = horizontalFromNormal.normalize();
                    }
                }
                if (launchDirection != null) {
                    double slopeFactor = MathHelper.clamp(1.0D - normal.y, 0.12D, 1.0D);
                    double targetHorizontal = Math.max(
                            resolvedHorizontal,
                            Math.max(
                                    SURF_LANDING_VERTICAL_TO_HORIZONTAL_MIN_SPEED,
                                    -velocityBeforeMove.y * SURF_LANDING_VERTICAL_TO_HORIZONTAL_FACTOR * slopeFactor
                            )
                    );
                    if (resolvedHorizontal + 1.0E-6D < targetHorizontal) {
                        preVel = new Vec3d(
                                launchDirection.x * targetHorizontal,
                                preVel.y,
                                launchDirection.z * targetHorizontal
                        );
                    }
                }
            }
        }
        if (justAcquiredSurfContact && velocityBeforeMove.y < -0.30D && preVel.y > SURF_LANDING_BOUNCE_CLAMP_Y) {
            preVel = new Vec3d(preVel.x, SURF_LANDING_BOUNCE_CLAMP_Y, preVel.z);
            this.surfLandingStabilizeTicks = SURF_LANDING_STABILIZE_TICKS;
        }
        if (postMoveSurfContact != null
                && velocityBeforeMove.y < SURF_LANDING_CONTINUE_DESCENT_TRIGGER_FALL
                && preVel.y > SURF_LANDING_CONTINUE_DESCENT_MIN_Y) {
            preVel = new Vec3d(preVel.x, SURF_LANDING_CONTINUE_DESCENT_MIN_Y, preVel.z);
        }
        if (!this.isClimbing() && postMoveSurfContact != null && this.surfLandingStabilizeTicks > 0) {
            if (preVel.y > SURF_LANDING_STABILIZE_MAX_Y) {
                preVel = new Vec3d(preVel.x, SURF_LANDING_STABILIZE_MAX_Y, preVel.z);
            }
            this.surfLandingStabilizeTicks--;
        } else if (postMoveSurfContact == null) {
            this.surfLandingStabilizeTicks = 0;
        }

        double yVel = preVel.y;
        double gravity = getGravityPerTick(config);
        if (preVel.y <= 0.0D && this.hasStatusEffect(StatusEffects.SLOW_FALLING)) {
            gravity = 0.01D;
            this.fallDistance = 0.0F;
        }
        if (this.hasStatusEffect(StatusEffects.LEVITATION)) {
            yVel += (0.05D * (this.getStatusEffect(StatusEffects.LEVITATION).getAmplifier() + 1) - preVel.y) * 0.2D;
            this.fallDistance = 0.0F;
        } else if (this.getWorld().isClient && !this.getWorld().isChunkLoaded(blockPos)) {
            yVel = 0.0D;
        } else if (!this.hasNoGravity() && !sourceLadderActive) {
            yVel -= gravity;
        }

        Vec3d velocityAfterGravity = new Vec3d(preVel.x, yVel, preVel.z);
        if (postMoveSurfContact != null && !this.isClimbing()) {
            velocityAfterGravity = this.clipVelocityAgainstSurfRamp(velocityAfterGravity, postMoveSurfContact.normal());

            if ((justAcquiredSurfContact || reacquiredSurfContact)) {
                Vec3d normal = postMoveSurfContact.normal();
                if (normal != null && normal.lengthSquared() > 1.0E-8D) {
                    double incomingInto = new Vec3d(preVel.x, yVel, preVel.z).dotProduct(normal.normalize());
                    double preGravityHorizontal = this.getHorizontalSpeed(preVel);
                    double postGravityHorizontal = this.getHorizontalSpeed(velocityAfterGravity);
                    if (incomingInto >= -SURF_LANDING_POST_GRAVITY_RESTORE_MAX_INTO_COMPONENT
                            && preGravityHorizontal >= SURF_LANDING_POST_GRAVITY_RESTORE_MIN_HORIZONTAL
                            && postGravityHorizontal + 1.0E-6D < preGravityHorizontal * SURF_LANDING_POST_GRAVITY_RESTORE_TRIGGER_RATIO) {
                        Vec3d restoreDir = this.resolveLandingRestoreDirection(velocityAfterGravity, velocityBeforeMove, normal);
                        if (restoreDir != null) {
                            double targetHorizontal = Math.max(
                                    postGravityHorizontal,
                                    preGravityHorizontal * SURF_LANDING_POST_GRAVITY_RESTORE_KEEP_RATIO
                            );
                            velocityAfterGravity = new Vec3d(
                                    restoreDir.x * targetHorizontal,
                                    velocityAfterGravity.y,
                                    restoreDir.z * targetHorizontal
                            );
                            double adjust = velocityAfterGravity.dotProduct(normal);
                            if (adjust < 0.0D) {
                                velocityAfterGravity = velocityAfterGravity.subtract(normal.multiply(adjust));
                            }
                        }
                    }
                }
            }
            Vec3d projectedAfterGravity = this.clipVelocityAgainstSurfRamp(
                    new Vec3d(velocityBeforeMove.x, Math.min(velocityBeforeMove.y, 0.0D), velocityBeforeMove.z),
                    postMoveSurfContact.normal()
            );
            double projectedAfterGravityHorizontal = this.getHorizontalSpeed(projectedAfterGravity);
            double currentAfterGravityHorizontal = this.getHorizontalSpeed(velocityAfterGravity);
            double incomingHorizontalAfterGravity = this.getHorizontalSpeed(velocityBeforeMove);
            double clipRestoreMaxVerticalAfterGravity = this.getClipRestoreMaxVertical(incomingHorizontalAfterGravity);
            if (velocityBeforeMove.y <= clipRestoreMaxVerticalAfterGravity
                    && projectedAfterGravityHorizontal >= SURF_LANDING_STALL_RESTORE_MIN_PROJECTED_HORIZONTAL
                    && currentAfterGravityHorizontal + 1.0E-6D
                    < projectedAfterGravityHorizontal * SURF_LANDING_STALL_RESTORE_POST_GRAVITY_TRIGGER_RATIO) {
                Vec3d projectedHorizontalVec = new Vec3d(projectedAfterGravity.x, 0.0D, projectedAfterGravity.z);
                double projectedHorizontalLen = projectedHorizontalVec.horizontalLength();
                if (projectedHorizontalLen > 1.0E-6D) {
                    Vec3d projectedHorizontalDir = projectedHorizontalVec.multiply(1.0D / projectedHorizontalLen);
                    double targetHorizontal = Math.max(
                            currentAfterGravityHorizontal,
                            projectedAfterGravityHorizontal * SURF_LANDING_STALL_RESTORE_KEEP_RATIO
                    );
                    velocityAfterGravity = new Vec3d(
                            projectedHorizontalDir.x * targetHorizontal,
                            Math.min(velocityAfterGravity.y, -0.08D),
                            projectedHorizontalDir.z * targetHorizontal
                    );
                    this.surfEntryNoFrictionTicks = Math.max(this.surfEntryNoFrictionTicks, 3);
                    this.surfCollisionBypassGraceTicks = Math.max(this.surfCollisionBypassGraceTicks, 2);
                    this.surfJumpSuppressTicks = Math.max(this.surfJumpSuppressTicks, SURF_JUMP_SUPPRESS_TICKS);
                }
            }
            currentAfterGravityHorizontal = this.getHorizontalSpeed(velocityAfterGravity);
            if (velocityBeforeMove.y <= clipRestoreMaxVerticalAfterGravity
                    && incomingHorizontalAfterGravity >= SURF_LANDING_STALL_RESTORE_MIN_INCOMING_HORIZONTAL
                    && currentAfterGravityHorizontal + 1.0E-6D
                    < incomingHorizontalAfterGravity * SURF_LANDING_STALL_FORCE_RESTORE_TRIGGER_RATIO) {
                Vec3d restoreDir = this.resolveLandingRestoreDirection(
                        velocityAfterGravity,
                        velocityBeforeMove,
                        postMoveSurfContact.normal()
                );
                if (restoreDir != null) {
                    forceRestorePostApplied = true;
                    double targetHorizontal = Math.max(
                            currentAfterGravityHorizontal,
                            incomingHorizontalAfterGravity * SURF_LANDING_STALL_FORCE_RESTORE_KEEP_RATIO
                    );
                    velocityAfterGravity = new Vec3d(
                            restoreDir.x * targetHorizontal,
                            Math.min(velocityAfterGravity.y, -0.08D),
                            restoreDir.z * targetHorizontal
                    );
                    this.surfEntryNoFrictionTicks = Math.max(this.surfEntryNoFrictionTicks, 3);
                    this.surfCollisionBypassGraceTicks = Math.max(this.surfCollisionBypassGraceTicks, 2);
                    this.surfJumpSuppressTicks = Math.max(this.surfJumpSuppressTicks, SURF_JUMP_SUPPRESS_TICKS);
                    this.setOnGround(false);
                }
            }
            currentAfterGravityHorizontal = this.getHorizontalSpeed(velocityAfterGravity);
            if (incomingHorizontalAfterGravity >= SURF_CONTACT_SPEED_RESTORE_MIN_HORIZONTAL
                    && currentAfterGravityHorizontal + 1.0E-6D
                    < incomingHorizontalAfterGravity * SURF_CONTACT_SPEED_RESTORE_POST_GRAVITY_TRIGGER_RATIO) {
                Vec3d restoreDir = this.resolveLandingRestoreDirection(
                        velocityAfterGravity,
                        velocityBeforeMove,
                        postMoveSurfContact.normal()
                );
                if (restoreDir != null) {
                    forceRestorePostApplied = true;
                    double targetHorizontal = Math.max(
                            currentAfterGravityHorizontal,
                            incomingHorizontalAfterGravity * SURF_CONTACT_SPEED_RESTORE_POST_GRAVITY_KEEP_RATIO
                    );
                    velocityAfterGravity = new Vec3d(
                            restoreDir.x * targetHorizontal,
                            Math.min(velocityAfterGravity.y, -0.06D),
                            restoreDir.z * targetHorizontal
                    );
                    this.surfEntryNoFrictionTicks = Math.max(this.surfEntryNoFrictionTicks, 3);
                    this.surfCollisionBypassGraceTicks = Math.max(this.surfCollisionBypassGraceTicks, 2);
                    this.surfJumpSuppressTicks = Math.max(this.surfJumpSuppressTicks, SURF_JUMP_SUPPRESS_TICKS);
                    this.setOnGround(false);
                }
            }
            if (velocityBeforeMove.y < -0.24D && velocityAfterGravity.y > SURF_LANDING_POST_GRAVITY_MAX_Y) {
                velocityAfterGravity = new Vec3d(
                        velocityAfterGravity.x,
                        SURF_LANDING_POST_GRAVITY_MAX_Y,
                        velocityAfterGravity.z
                );
            }
        }

        BlockState belowState = this.getWorld().getBlockState(this.getBlockPos());
        if (belowState.isOf(ModBlocks.BOOSTER_BLOCK) && (this.getWorld().getTime() > this.boostTime + 5 || this.getWorld().getTime() < this.boostTime)) {
            this.boostTime = this.getWorld().getTime();
            BoostBlockEntity boostBlockEntity = (BoostBlockEntity) this.getWorld().getBlockEntity(this.getBlockPos());
            if (boostBlockEntity != null) {
                velocityAfterGravity = velocityAfterGravity.add(boostBlockEntity.getXPower(), boostBlockEntity.getYPower(), boostBlockEntity.getZPower());
            }
        }
        if ((inStartZone || inStartZoneAfterMove) && startZonePlayer != null) {
            velocityAfterGravity = StartEntity.clampVelocityToStartZoneSpeed(startZonePlayer, velocityAfterGravity);
        }
        double incomingHorizontalFinal = this.getHorizontalSpeed(velocityBeforeMove);
        double preGravityHorizontalFinal = this.getHorizontalSpeed(preVel);
        double postGravityHorizontalFinal = this.getHorizontalSpeed(velocityAfterGravity);
        Vec3d movedSincePreMove = this.getPos().subtract(posBeforeMove);
        double movedHorizontalFinal = Math.hypot(movedSincePreMove.x, movedSincePreMove.z);
        boolean speedCollapseEvent = incomingHorizontalFinal >= SURF_CONTACT_SPEED_RESTORE_MIN_HORIZONTAL
                && postGravityHorizontalFinal + 1.0E-6D < incomingHorizontalFinal * 0.74D;
        boolean movementStallEvent = incomingHorizontalFinal >= SURF_CONTACT_SPEED_RESTORE_MIN_HORIZONTAL
                && movedHorizontalFinal + 1.0E-6D < incomingHorizontalFinal * 0.22D;
        boolean significantDropEvent = incomingHorizontalFinal >= 0.35D
                && postGravityHorizontalFinal + 1.0E-6D < incomingHorizontalFinal * 0.90D;
        boolean significantStallEvent = incomingHorizontalFinal >= 0.35D
                && movedHorizontalFinal + 1.0E-6D < incomingHorizontalFinal * 0.40D
                && (postGravityHorizontalFinal + 1.0E-6D < incomingHorizontalFinal * 0.98D
                    || preGravityHorizontalFinal + 1.0E-6D < incomingHorizontalFinal * 0.98D);
        boolean contactLossEvent = incomingHorizontalFinal >= 0.30D
                && nearSurfRampPost
                && preMoveSurfContact != null
                && postMoveSurfContact == null;
        boolean endpointRiskEvent = (nearRampEndpointPre || nearRampEndpointPost)
                && incomingHorizontalFinal >= 0.35D
                && postGravityHorizontalFinal + 1.0E-6D < incomingHorizontalFinal * 0.96D;
        boolean contactLossWithImpactEvent = contactLossEvent
                && (significantDropEvent || significantStallEvent || speedCollapseEvent || movementStallEvent || endpointRiskEvent);
        if (speedCollapseEvent
                || movementStallEvent
                || significantDropEvent
                || significantStallEvent
                || contactLossWithImpactEvent
                || endpointRiskEvent
                || endpointDisableTriggered) {
            this.logSurfStopEvent(
                    debugPlayer,
                    speedCollapseEvent,
                    movementStallEvent,
                    significantDropEvent,
                    significantStallEvent,
                    contactLossEvent,
                    endpointRiskEvent,
                    incomingHorizontalFinal,
                    preGravityHorizontalFinal,
                    postGravityHorizontalFinal,
                    movedHorizontalFinal,
                    nearSurfRampPre,
                    nearSurfRampPost,
                    nearSurfSupportPost,
                    preMoveSurfContact != null,
                    postMoveSurfContact != null,
                    hardEndpointPre,
                    hardEndpointPost,
                    disableBypassAtHardEndpoint,
                    highSpeedEndpointCarry,
                    bypassRampCollision,
                    keepBypassAfterMove,
                    broadIncomingFallbackUsed,
                    groundedReacquireApplied,
                    catastrophicRestoreApplied,
                    forceRestorePreApplied,
                    forceRestorePostApplied
            );
        }
        this.setVelocity(velocityAfterGravity);

        if (surfDebug && this.shouldLogSurfDebugTick()) {
            this.logSurfDebugState(
                    debugPlayer,
                    preMoveSurfContact,
                    postMoveSurfContact,
                    hardEndpointPre,
                    hardEndpointPost,
                    nearSurfRampPre,
                    nearSurfRampPost,
                    nearRampEndpointPre,
                    nearRampEndpointPost,
                    bypassRampCollision,
                    keepBypassAfterMove,
                    preHorizontalSpeed,
                    postHorizontalSpeed
            );
        }

        if (nearSurfRampPre || nearSurfRampPost || preMoveSurfContact != null || postMoveSurfContact != null
                || this.surfContactGraceTicks > 0 || this.surfCollisionBypassGraceTicks > 0
                || this.surfGroundSuppressTicks > 0) {
            this.minehop$nearRampAcGraceTicks = 8;
        } else if (this.minehop$nearRampAcGraceTicks > 0) {
            this.minehop$nearRampAcGraceTicks--;
        }

        if (!this.getWorld().isClient && self instanceof ServerPlayerEntity acPlayer) {
            boolean surfingForAc = preMoveSurfContact != null || postMoveSurfContact != null
                    || this.surfContactGraceTicks > 0 || this.surfCollisionBypassGraceTicks > 0
                    || nearSurfRampPre || nearSurfRampPost
                    || this.surfGroundSuppressTicks > 0 || this.surfJumpSuppressTicks > 0
                    || this.minehop$nearRampAcGraceTicks > 0;
            AntiCheatManager.onMovementTick(
                    acPlayer,
                    posBeforeMove,
                    this.getPos(),
                    velocityBeforeMove,
                    velocityAfterGravity,
                    this.isOnGround(),
                    this.wasOnGround,
                    this.isClimbing(),
                    this.isTouchingWater() || this.isInLava(),
                    surfingForAc,
                    this.jumping && (this.isOnGround() || this.wasOnGround),
                    speedCap
            );
        }

        this.updateLimbs(self instanceof Flutterer);

        ci.cancel();
    }

    @Unique
    private double getSeamContinuityMaxAscent(double horizontalSpeed) {
        return horizontalSpeed >= SURF_SEAM_CONTINUITY_ASCENT_MIN_HORIZONTAL
                ? SURF_SEAM_CONTINUITY_MAX_ASCENT_HIGH_SPEED
                : SURF_POST_MOVE_CONTACT_CARRY_MAX_DESCENT;
    }

    @Unique
    private double getClipRestoreMaxVertical(double horizontalSpeed) {
        return horizontalSpeed >= SURF_CLIP_PLANAR_PRESERVE_HIGH_VERTICAL_MIN_HORIZONTAL
                ? SURF_CLIP_PLANAR_PRESERVE_MAX_VERTICAL_HIGH_SPEED
                : SURF_CLIP_PLANAR_PRESERVE_MAX_VERTICAL;
    }

    @Unique
    private double getClipPlanarPreserveMaxIntoRatio(double horizontalSpeed) {
        return horizontalSpeed >= SURF_CLIP_PLANAR_PRESERVE_HIGH_VERTICAL_MIN_HORIZONTAL
                ? SURF_CLIP_PLANAR_PRESERVE_MAX_INTO_RATIO_HIGH_SPEED
                : SURF_CLIP_PLANAR_PRESERVE_MAX_INTO_RATIO;
    }

    @Unique
    private boolean shouldCarryPreMoveContactAfterMove(SurfContact preMoveSurfContact) {
        if (this.isClimbing() || preMoveSurfContact == null || !this.isUsableSurfContactForCarryOrRestore(preMoveSurfContact)) {
            return false;
        }
        if (!this.isNearSurfRamp()) {
            return false;
        }
        double horizontalSpeed = this.getHorizontalSpeed(this.getVelocity());
        if (horizontalSpeed < SURF_SEAM_CONTINUITY_MIN_HORIZONTAL) {
            return false;
        }
        double carryMaxVertical = horizontalSpeed >= SURF_POST_MOVE_CONTACT_CARRY_ASCENT_MIN_HORIZONTAL
                ? SURF_POST_MOVE_CONTACT_CARRY_MAX_ASCENT_HIGH_SPEED
                : SURF_POST_MOVE_CONTACT_CARRY_MAX_DESCENT;
        if (this.getVelocity().y > carryMaxVertical) {
            return false;
        }

        boolean transitionZone = this.isInRampTransitionZone();
        boolean nearHardEndpoint = !transitionZone && this.isNearSurfRampEndpoint();
        if (preMoveSurfContact.hardEndpoint()
                && horizontalSpeed <= SURF_HARD_ENDPOINT_DISABLE_MAX_HORIZONTAL) {
            return false;
        }
        if (nearHardEndpoint && horizontalSpeed <= SURF_HARD_ENDPOINT_DISABLE_MAX_HORIZONTAL) {
            return false;
        }
        return true;
    }

    @Unique
    private boolean isUsableSurfContactForCarryOrRestore(SurfContact contact) {
        if (contact == null) {
            return false;
        }
        Vec3d normal = contact.normal();
        return normal != null && normal.y >= SURF_REUSE_MIN_NORMAL_Y && normal.y <= 0.999D;
    }

    public double findOptimalStrafeAngle(double sI, double fI, MinehopConfig config, boolean fullGrounded) {
        if (sI == 0.0D && fI == 0.0D) {
            return this.getYaw();
        }

        double highestVelocity = -Double.MAX_VALUE;
        double optimalAngle = 0;

        Vec3d horizontalVelocity = new Vec3d(this.getVelocity().x, 0.0D, this.getVelocity().z);
        double baseWishSpeed = getBaseWishSpeed(config);
        double airWishSpeedCap = getAirWishSpeedCap(config);

        for (double angle = this.prevYaw - 45; angle < this.prevYaw + 45; angle += 1) {
            Vec3d wishVector = MovementUtil.movementInputToVelocity(new Vec3d(sI, 0.0F, fI), 1.0F, (float) angle);
            double wishVectorLength = wishVector.horizontalLength();
            if (wishVectorLength <= 0.0D) {
                continue;
            }

            Vec3d wishDir = new Vec3d(wishVector.x / wishVectorLength, 0.0D, wishVector.z / wishVectorLength);
            double wishSpeed = baseWishSpeed * wishVectorLength;
            Vec3d candidateVelocity;

            if (fullGrounded) {
                candidateVelocity = accelerateSource(horizontalVelocity, wishDir, wishSpeed, wishSpeed, config.movement.sv_accelerate, 1.0D, false, 1.0D);
            } else {
                candidateVelocity = accelerateSource(horizontalVelocity, wishDir, wishSpeed, airWishSpeedCap, config.movement.sv_airaccelerate, 1.0D, true, 1.0D);
            }

            if (candidateVelocity.horizontalLength() > highestVelocity) {
                highestVelocity = candidateVelocity.horizontalLength();
                optimalAngle = angle;
            }
        }
        return optimalAngle;
    }

    @Unique
    private SurfContact findSurfContact() {
        if (this.isClimbing()) {
            return null;
        }

        Box boundingBox = this.getBoundingBox();
        double feetY = boundingBox.minY;
        double headY = boundingBox.maxY;
        double minContactY = feetY - this.getDynamicSurfBelowTolerance(this.getVelocity().y);
        double maxContactY = headY + SURF_CONTACT_ABOVE_TOLERANCE;

        SurfContact contact = this.findSurfContactAt(
                this.getX(),
                this.getZ(),
                minContactY,
                maxContactY,
                feetY,
                headY
        );
        if (this.isReusableSurfContact(contact)) {
            return contact;
        }

        if (this.isOnGround() && this.hasRecentSurfActivity()) {
            SurfContact groundedFallback = this.findGroundedSurfContactFallback(
                    this.getX(),
                    this.getZ(),
                    feetY,
                    headY
            );
            if (this.isReusableSurfContact(groundedFallback)) {
                return groundedFallback;
            }
        } else if (!this.isOnGround()
                && (this.getVelocity().y <= SURF_AIRBORNE_FALLBACK_MIN_DESCENT
                    || this.getHorizontalSpeed(this.getVelocity()) >= SURF_SEAM_REACQUIRE_MIN_HORIZONTAL)) {
            SurfContact incomingFallback = this.findIncomingSurfContactFallback(
                    this.getX(),
                    this.getZ(),
                    feetY,
                    headY
            );
            if (this.isReusableSurfContact(incomingFallback)) {
                return incomingFallback;
            }
        }

        if (this.isReusableSurfContact(this.lastSurfContact)
                && this.surfContactGraceTicks > 0
                && this.getHorizontalSpeed(this.getVelocity()) >= SURF_SEAM_REACQUIRE_MIN_HORIZONTAL
                && this.getVelocity().y <= SURF_POST_MOVE_CONTACT_CARRY_MAX_DESCENT
                && this.isNearSurfRamp()) {
            SurfContact continuityFallback = this.findNearestSurfContactFallback(
                    this.getX(),
                    this.getZ(),
                    feetY,
                    headY,
                    SURF_LAST_CONTACT_CONTINUITY_MAX_VERTICAL_DISTANCE,
                    SURF_LAST_CONTACT_CONTINUITY_LATERAL_EXTRA
            );
            if (this.isReusableSurfContact(continuityFallback)) {
                return continuityFallback;
            }
        }

        return null;
    }

    @Unique
    private SurfContact findIncomingSurfContact(Vec3d currentVelocity) {
        if (this.isClimbing()) {
            return null;
        }
        if (currentVelocity == null) {
            currentVelocity = this.getVelocity();
        }

        Box boundingBox = this.getBoundingBox();
        double baseX = this.getX();
        double baseZ = this.getZ();
        double feetY = boundingBox.minY;
        double headY = boundingBox.maxY;
        double velocityLength = currentVelocity.length();
        double horizontalSpeed = currentVelocity.horizontalLength();
        boolean fastSteepIncoming = currentVelocity.y <= SURF_INCOMING_TRACE_FAST_MIN_DESCENT
                && horizontalSpeed >= SURF_INCOMING_TRACE_FAST_MIN_HORIZONTAL
                && velocityLength >= SURF_INCOMING_TRACE_FAST_MIN_TOTAL_SPEED;
        boolean fastShallowIncoming = currentVelocity.y <= SURF_INCOMING_TRACE_FAST_SHALLOW_MIN_DESCENT
                && horizontalSpeed >= SURF_INCOMING_TRACE_FAST_SHALLOW_MIN_HORIZONTAL
                && velocityLength >= SURF_INCOMING_TRACE_FAST_SHALLOW_MIN_TOTAL_SPEED;
        boolean highSpeedIncoming = fastSteepIncoming || fastShallowIncoming;
        int maxTraceSteps = Math.min(320, Math.max(SURF_INCOMING_TRACE_MAX_STEPS,
                (int) Math.ceil(velocityLength / SURF_TRACE_STEP) + 8));
        int traceSteps = Math.max(1, (int) Math.ceil(velocityLength / SURF_TRACE_STEP));
        traceSteps = Math.min(traceSteps, maxTraceSteps);
        double dynamicBelowTolerance = this.getDynamicSurfBelowTolerance(currentVelocity.y);
        double[][] samplePattern = this.getSurfSamplePattern();
        double halfX = (boundingBox.maxX - boundingBox.minX) * 0.5D;
        double halfZ = (boundingBox.maxZ - boundingBox.minZ) * 0.5D;
        double edgeX = Math.max(halfX - SURF_SAMPLE_EDGE_INSET, 0.0D);
        double edgeZ = Math.max(halfZ - SURF_SAMPLE_EDGE_INSET, 0.0D);

        double predictedX = baseX + currentVelocity.x;
        double predictedZ = baseZ + currentVelocity.z;
        double predictedFeetY = feetY + currentVelocity.y;
        double predictedHeadY = headY + currentVelocity.y;

        Box incomingSearchBox = new Box(
                Math.min(baseX, predictedX) - 2.5D,
                Math.min(feetY, predictedFeetY) - dynamicBelowTolerance - 1.0D,
                Math.min(baseZ, predictedZ) - 2.5D,
                Math.max(baseX, predictedX) + 2.5D,
                Math.max(headY, predictedHeadY) + SURF_CONTACT_ABOVE_TOLERANCE + 1.0D,
                Math.max(baseZ, predictedZ) + 2.5D
        );
        List<SurfRampEntity> incomingRamps = this.collectNearbySurfRampsCached(incomingSearchBox, SURF_COLLISION_QUERY_EXPAND);
        SurfContact bestContact = null;
        double bestDistance = Double.MAX_VALUE;
        if (!incomingRamps.isEmpty()) {
            for (int i = 0; i <= traceSteps; i++) {
                double t = (double) i / (double) traceSteps;
                double sampleX = baseX + currentVelocity.x * t;
                double sampleZ = baseZ + currentVelocity.z * t;
                double sampleFeetY = feetY + currentVelocity.y * t;
                double sampleHeadY = headY + currentVelocity.y * t;
                double minContactY = sampleFeetY - dynamicBelowTolerance;
                double maxContactY = sampleHeadY + SURF_CONTACT_ABOVE_TOLERANCE;

                SurfContact contact = this.findSurfContactAtFromRamps(
                        sampleX,
                        sampleZ,
                        minContactY,
                        maxContactY,
                        sampleFeetY,
                        sampleHeadY,
                        incomingRamps,
                        samplePattern,
                        edgeX,
                        edgeZ,
                        true
                );
                if (!this.isReusableSurfContact(contact)) {
                    continue;
                }

                double surfaceDistance = Math.abs(sampleFeetY - contact.surfaceY()) + (t * 0.05D);
                if (surfaceDistance <= SURF_INCOMING_TRACE_EARLY_EXIT_DISTANCE) {
                    return contact;
                }
                if (surfaceDistance < bestDistance) {
                    bestDistance = surfaceDistance;
                    bestContact = contact;
                }
            }
        }

        if (this.isReusableSurfContact(bestContact)) {
            return bestContact;
        }

        SurfContact fallback = this.findIncomingSurfContactFallback(
                predictedX,
                predictedZ,
                predictedFeetY,
                predictedHeadY
        );
        if (this.isReusableSurfContact(fallback)) {
            return fallback;
        }

        return null;
    }

    @Unique
    private SurfContact findSurfContactAt(
            double baseX,
            double baseZ,
            double minContactY,
            double maxContactY,
            double targetFeetY,
            double targetHeadY
    ) {
        Box searchBox = new Box(
                baseX - 2.5D,
                minContactY - 1.0D,
                baseZ - 2.5D,
                baseX + 2.5D,
                maxContactY + 1.0D,
                baseZ + 2.5D
        );
        List<SurfRampEntity> ramps = this.collectNearbySurfRampsCached(searchBox, SURF_COLLISION_QUERY_EXPAND);
        if (ramps.isEmpty()) {
            return null;
        }

        Box sampleBox = this.getBoundingBox();
        double halfX = (sampleBox.maxX - sampleBox.minX) * 0.5D;
        double halfZ = (sampleBox.maxZ - sampleBox.minZ) * 0.5D;
        double edgeX = Math.max(halfX - SURF_SAMPLE_EDGE_INSET, 0.0D);
        double edgeZ = Math.max(halfZ - SURF_SAMPLE_EDGE_INSET, 0.0D);
        double[][] samplePattern = this.getSurfSamplePattern();
        return this.findSurfContactAtFromRamps(
                baseX,
                baseZ,
                minContactY,
                maxContactY,
                targetFeetY,
                targetHeadY,
                ramps,
                samplePattern,
                edgeX,
                edgeZ,
                false
        );
    }

    @Unique
    private SurfContact findSurfContactAtFromRamps(
            double baseX,
            double baseZ,
            double minContactY,
            double maxContactY,
            double targetFeetY,
            double targetHeadY,
            List<SurfRampEntity> ramps,
            double[][] samplePattern,
            double edgeX,
            double edgeZ,
            boolean coarseBoundsCull
    ) {
        SurfContact bestContact = null;
        double closestSurfaceDistance = Double.MAX_VALUE;
        for (double[] offsetMultiplier : samplePattern) {
            double sampleX = baseX + edgeX * offsetMultiplier[0];
            double sampleZ = baseZ + edgeZ * offsetMultiplier[1];

            for (SurfRampEntity ramp : ramps) {
                Box rampBounds = ramp.getBoundingBox();
                if (rampBounds.maxY < minContactY - 0.40D || rampBounds.minY > maxContactY + 0.40D) {
                    continue;
                }
                if (!this.isNearRampBoundsXZ(ramp, sampleX, sampleZ, 0.35D)) {
                    continue;
                }
                if (coarseBoundsCull) {
                    if (rampBounds.maxY < minContactY - SURF_INCOMING_TRACE_CULL_BOUNDS_EXTRA
                            || rampBounds.minY > maxContactY + SURF_INCOMING_TRACE_CULL_BOUNDS_EXTRA) {
                        continue;
                    }
                    if (!this.isNearRampBoundsXZ(ramp, sampleX, sampleZ, SURF_INCOMING_TRACE_CULL_BOUNDS_EXTRA)) {
                        continue;
                    }
                }
                SurfContact contact = ramp.sampleContact(sampleX, sampleZ, minContactY, maxContactY);
                if (contact == null) {
                    continue;
                }

                double surfaceDistance = Math.abs(targetFeetY - contact.surfaceY());
                double aboveFeet = contact.surfaceY() - targetFeetY;
                if (contact.surfaceY() > targetHeadY + 0.35D) {
                    continue;
                }

                if (aboveFeet > SURF_REUSE_MAX_ABOVE_FEET) {
                    continue;
                }
                if (surfaceDistance < closestSurfaceDistance) {
                    closestSurfaceDistance = surfaceDistance;
                    bestContact = contact;
                }
            }
        }

        if (bestContact == null) {
            return null;
        }

        if (!this.isSurfAttachDistanceValid(targetFeetY, bestContact.surfaceY())) {
            return null;
        }

        return bestContact;
    }

    @Unique
    private SurfContact findGroundedSurfContactFallback(double baseX, double baseZ, double targetFeetY, double targetHeadY) {
        return this.findNearestSurfContactFallback(
                baseX,
                baseZ,
                targetFeetY,
                targetHeadY,
                SURF_GROUNDED_FALLBACK_MAX_VERTICAL_DISTANCE,
                SURF_GROUNDED_FALLBACK_LATERAL_EXTRA
        );
    }

    @Unique
    private SurfContact findIncomingSurfContactFallback(double baseX, double baseZ, double targetFeetY, double targetHeadY) {
        return this.findNearestSurfContactFallback(
                baseX,
                baseZ,
                targetFeetY,
                targetHeadY,
                SURF_INCOMING_FALLBACK_MAX_VERTICAL_DISTANCE,
                SURF_INCOMING_FALLBACK_LATERAL_EXTRA
        );
    }

    @Unique
    private SurfContact findJumpRampSupportContact(Box referenceBox, Vec3d velocity) {
        if (referenceBox == null || velocity == null || this.isClimbing()) {
            return null;
        }
        double feetY = referenceBox.minY;
        double headY = referenceBox.maxY;
        SurfContact contact = this.findNearestSurfContactFallback(
                this.getX(),
                this.getZ(),
                feetY,
                headY,
                SURF_GROUNDED_LANDING_CAPTURE_MAX_VERTICAL_DISTANCE,
                SURF_GROUNDED_LANDING_CAPTURE_LATERAL_EXTRA
        );
        SurfContact strictValidated = this.validateJumpSupportContact(
                contact,
                feetY,
                velocity,
                SURF_GROUNDED_LANDING_CAPTURE_MAX_ABOVE_FEET,
                SURF_GROUNDED_LANDING_CAPTURE_MAX_BELOW_FEET,
                SURF_GROUNDED_LANDING_CAPTURE_MAX_INTO_COMPONENT
        );
        if (strictValidated != null) {
            return strictValidated;
        }

        if (!this.isOnGround()) {
            return null;
        }
        SurfContact looseContact = this.findNearestSurfContactFallback(
                this.getX(),
                this.getZ(),
                feetY,
                headY,
                SURF_GROUNDED_LANDING_CAPTURE_LOOSE_MAX_VERTICAL_DISTANCE,
                SURF_GROUNDED_LANDING_CAPTURE_LOOSE_LATERAL_EXTRA
        );
        return this.validateJumpSupportContact(
                looseContact,
                feetY,
                velocity,
                SURF_GROUNDED_LANDING_CAPTURE_LOOSE_MAX_ABOVE_FEET,
                SURF_GROUNDED_LANDING_CAPTURE_LOOSE_MAX_BELOW_FEET,
                SURF_GROUNDED_LANDING_CAPTURE_LOOSE_MAX_INTO_COMPONENT
        );
    }

    @Unique
    private SurfContact validateJumpSupportContact(
            SurfContact contact,
            double feetY,
            Vec3d velocity,
            double maxAboveFeet,
            double maxBelowFeet,
            double maxIntoComponent
    ) {
        if (!this.isReusableSurfContact(contact) || velocity == null) {
            return null;
        }
        double aboveFeet = contact.surfaceY() - feetY;
        double belowFeet = feetY - contact.surfaceY();
        if (aboveFeet > maxAboveFeet || belowFeet > maxBelowFeet) {
            return null;
        }
        Vec3d normal = contact.normal();
        if (normal == null) {
            return null;
        }
        double horizontal = this.getHorizontalSpeed(velocity);
        if (horizontal < SURF_GROUNDED_LANDING_CAPTURE_MIN_HORIZONTAL && !this.isOnGround()) {
            return null;
        }
        double into = velocity.dotProduct(normal);
        if (into > maxIntoComponent && velocity.y > 0.0D) {
            return null;
        }
        return contact;
    }

    @Unique
    private SurfContact findNearestSurfContactFallback(
            double baseX,
            double baseZ,
            double targetFeetY,
            double targetHeadY,
            double maxVerticalDistance,
            double lateralToleranceExtra
    ) {
        Box searchBox = new Box(
                baseX - 2.2D,
                targetFeetY - 1.0D,
                baseZ - 2.2D,
                baseX + 2.2D,
                targetHeadY + 0.8D,
                baseZ + 2.2D
        );
        List<SurfRampEntity> ramps = this.collectNearbySurfRampsCached(searchBox, SURF_COLLISION_QUERY_EXPAND);
        if (ramps.isEmpty()) {
            return null;
        }

        SurfContact best = null;
        double bestDistance = Double.MAX_VALUE;
        Box sampleBox = this.getBoundingBox();
        double halfX = (sampleBox.maxX - sampleBox.minX) * 0.5D;
        double halfZ = (sampleBox.maxZ - sampleBox.minZ) * 0.5D;
        double edgeX = Math.max(halfX - SURF_SAMPLE_EDGE_INSET, 0.0D);
        double edgeZ = Math.max(halfZ - SURF_SAMPLE_EDGE_INSET, 0.0D);
        double[][] samplePattern = this.getSurfSamplePattern();
        for (double[] offsetMultiplier : samplePattern) {
            double sampleX = baseX + edgeX * offsetMultiplier[0];
            double sampleZ = baseZ + edgeZ * offsetMultiplier[1];

            for (SurfRampEntity ramp : ramps) {
                if (!this.isNearRampBoundsXZ(ramp, sampleX, sampleZ, lateralToleranceExtra)) {
                    continue;
                }
                SurfContact contact = ramp.sampleNearestContact(
                        sampleX,
                        sampleZ,
                        targetFeetY,
                        maxVerticalDistance
                );
                if (contact == null) {
                    continue;
                }
                Vec3d normal = contact.normal();
                if (normal == null || normal.y < SURF_REUSE_MIN_NORMAL_Y || normal.y > 0.999D) {
                    continue;
                }

                double aboveFeet = contact.surfaceY() - targetFeetY;
                if (aboveFeet > SURF_AIRBORNE_FALLBACK_MAX_ABOVE_FEET) {
                    continue;
                }
                if (!this.isSurfAttachDistanceValid(targetFeetY, contact.surfaceY())) {
                    continue;
                }
                if (contact.surfaceY() > targetHeadY + 0.30D) {
                    continue;
                }

                double surfaceDistance = Math.abs(targetFeetY - contact.surfaceY());
                if (surfaceDistance < bestDistance) {
                    bestDistance = surfaceDistance;
                    best = contact;
                }
            }
        }

        return best;
    }

    @Unique
    private SurfContact resolveSurfPenetration(SurfContact contact) {
        if (contact == null) {
            return null;
        }
        if (!this.isReusableSurfContact(contact)) {
            return null;
        }

        Box boundingBox = this.getBoundingBox();
        double feetY = boundingBox.minY;
        double penetration = contact.surfaceY() - feetY;

        if (penetration > 0.0D
                && penetration <= SURF_MAX_SNAP_UP
                && !this.isOnGround()) {
            this.setPosition(this.getX(), this.getY() + penetration + 1.0E-3D, this.getZ());
            SurfContact refreshed = this.findSurfContact();
            if (this.isReusableSurfContact(refreshed)) {
                return refreshed;
            }
            return this.isReusableSurfContact(contact) ? contact : null;
        }

        return this.isReusableSurfContact(contact) ? contact : null;
    }

    @Unique
    private SurfContact snapToSurfSurface(SurfContact contact) {
        if (contact == null) {
            return null;
        }

        if (this.isOnGround()) {
            return contact;
        }

        Box boundingBox = this.getBoundingBox();
        double feetY = boundingBox.minY;
        double deltaY = contact.surfaceY() - feetY;

        if (deltaY < 0.0D) {
            if (-deltaY <= SURF_MAX_SNAP_DOWN && this.getVelocity().y <= SURF_SNAP_DOWN_MAX_UPWARD) {
                if (Math.abs(deltaY) > 1.0E-4D) {
                    this.setPosition(this.getX(), this.getY() + deltaY, this.getZ());
                }
                SurfContact refreshed = this.findSurfContact();
                if (this.isReusableSurfContact(refreshed)) {
                    return refreshed;
                }
                return this.isReusableSurfContact(contact) ? contact : null;
            }
            return contact;
        }

        if (deltaY > 0.0D && deltaY <= SURF_MAX_SNAP_UP) {
            if (Math.abs(deltaY) > 1.0E-4D) {
                this.setPosition(this.getX(), this.getY() + deltaY + 1.0E-3D, this.getZ());
            }
            SurfContact refreshed = this.findSurfContact();
            if (this.isReusableSurfContact(refreshed)) {
                return refreshed;
            }
            return this.isReusableSurfContact(contact) ? contact : null;
        }

        return contact;
    }

    @Unique
    private boolean isReusableSurfContactState() {
        return Minehop.surfCollisionBypassEntities.contains(this.getId())
                || this.surfContactGraceTicks > 0
                || this.surfCollisionBypassGraceTicks > 0;
    }

    @Unique
    private boolean isReusableSurfContact(SurfContact contact) {
        if (contact == null) {
            return false;
        }
        Vec3d normal = contact.normal();
        if (normal == null || normal.y < SURF_REUSE_MIN_NORMAL_Y || normal.y > 0.999D) {
            return false;
        }

        double feetY = this.getBoundingBox().minY;
        double aboveFeet = contact.surfaceY() - feetY;
        if (aboveFeet > SURF_REUSE_MAX_ABOVE_FEET) {
            return false;
        }
        return this.isSurfAttachDistanceValid(feetY, contact.surfaceY());
    }

    @Unique
    private boolean shouldLogSurfDebugTick() {
        return this.getWorld().getTime() % 4L == 0L;
    }

    @Unique
    private void logSurfDebugState(
            PlayerEntity player,
            SurfContact preMoveSurfContact,
            SurfContact postMoveSurfContact,
            boolean hardEndpointPre,
            boolean hardEndpointPost,
            boolean nearSurfRampPre,
            boolean nearSurfRampPost,
            boolean nearRampEndpointPre,
            boolean nearRampEndpointPost,
            boolean bypassPre,
            boolean bypassPost,
            double preHorizontalSpeed,
            double postHorizontalSpeed
    ) {
        if (player == null || this.getWorld().isClient) {
            return;
        }
        Minehop.LOGGER.info(
                "SurfDebug player=" + player.getNameForScoreboard()
                        + " tick=" + this.getWorld().getTime()
                        + " preContact=" + (preMoveSurfContact != null)
                        + " postContact=" + (postMoveSurfContact != null)
                        + " hardEndPre=" + hardEndpointPre
                        + " hardEndPost=" + hardEndpointPost
                        + " nearPre=" + nearSurfRampPre
                        + " nearPost=" + nearSurfRampPost
                        + " nearEndPre=" + nearRampEndpointPre
                        + " nearEndPost=" + nearRampEndpointPost
                        + " bypassPre=" + bypassPre
                        + " bypassPost=" + bypassPost
                        + " onGround=" + this.isOnGround()
                        + " vy=" + String.format("%.4f", this.getVelocity().y)
                        + " hPre=" + String.format("%.3f", preHorizontalSpeed)
                        + " hPost=" + String.format("%.3f", postHorizontalSpeed)
                        + " grace=" + this.surfContactGraceTicks
                        + " bypassGrace=" + this.surfCollisionBypassGraceTicks
        );
    }

    @Unique
    private void logSurfStopEvent(
            PlayerEntity player,
            boolean speedCollapseEvent,
            boolean movementStallEvent,
            boolean significantDropEvent,
            boolean significantStallEvent,
            boolean contactLossEvent,
            boolean endpointRiskEvent,
            double incomingHorizontal,
            double preGravityHorizontal,
            double postGravityHorizontal,
            double movedHorizontal,
            boolean nearSurfRampPre,
            boolean nearSurfRampPost,
            boolean nearSurfSupportPost,
            boolean preContact,
            boolean postContact,
            boolean hardEndpointPre,
            boolean hardEndpointPost,
            boolean endpointDisableTriggered,
            boolean highSpeedEndpointCarry,
            boolean bypassPre,
            boolean bypassPost,
            boolean broadIncomingFallbackUsed,
            boolean groundedReacquireApplied,
            boolean catastrophicRestoreApplied,
            boolean forceRestorePreApplied,
            boolean forceRestorePostApplied
    ) {
        World world = this.getWorld();
        if (world == null) {
            return;
        }
        long tick = world.getTime();
        if (this.surfStopLogTick == tick) {
            return;
        }
        this.surfStopLogTick = tick;
        String side = world.isClient ? "client" : "server";
        String playerName = player != null ? player.getNameForScoreboard() : ("entity#" + this.getId());
        Minehop.LOGGER.warn(
                "SurfStop side={} player={} tick={} pos=({},{},{}) collapse={} stall={} drop={} softStall={} contactLoss={} edgeRisk={} hIn={} hPreG={} hOut={} moved={} "
                        + "nearPre={} nearPost={} nearSupportPost={} preC={} postC={} hardPre={} hardPost={} "
                        + "endpointDisable={} endpointCarry={} bypassPre={} bypassPost={} broadFallback={} reacquire={} catRestore={} preRestore={} postRestore={} "
                        + "grace={} bypassGrace={} groundSup={} jumpSup={} onGround={} vy={}",
                side,
                playerName,
                tick,
                String.format("%.2f", this.getX()),
                String.format("%.2f", this.getY()),
                String.format("%.2f", this.getZ()),
                speedCollapseEvent,
                movementStallEvent,
                significantDropEvent,
                significantStallEvent,
                contactLossEvent,
                endpointRiskEvent,
                String.format("%.3f", incomingHorizontal),
                String.format("%.3f", preGravityHorizontal),
                String.format("%.3f", postGravityHorizontal),
                String.format("%.3f", movedHorizontal),
                nearSurfRampPre,
                nearSurfRampPost,
                nearSurfSupportPost,
                preContact,
                postContact,
                hardEndpointPre,
                hardEndpointPost,
                endpointDisableTriggered,
                highSpeedEndpointCarry,
                bypassPre,
                bypassPost,
                broadIncomingFallbackUsed,
                groundedReacquireApplied,
                catastrophicRestoreApplied,
                forceRestorePreApplied,
                forceRestorePostApplied,
                this.surfContactGraceTicks,
                this.surfCollisionBypassGraceTicks,
                this.surfGroundSuppressTicks,
                this.surfJumpSuppressTicks,
                this.isOnGround(),
                String.format("%.4f", this.getVelocity().y)
        );
    }

    @Unique
    private void resetSurfRampQueryCacheIfNeeded() {
        World world = this.getWorld();
        long tick = world != null ? world.getTime() : Long.MIN_VALUE;
        if (this.surfRampQueryCacheTick != tick) {
            this.surfRampQueryCacheTick = tick;
            this.surfRampQueryCache.clear();
        }
    }

    @Unique
    private long quantizeSurfQueryValue(double value) {
        return Math.round(value * 256.0D);
    }

    @Unique
    private long computeSurfRampQueryKey(Box queryBox, double expand, int queryType) {
        long hash = 1469598103934665603L;
        hash = (hash ^ queryType) * 1099511628211L;
        hash = (hash ^ this.quantizeSurfQueryValue(expand)) * 1099511628211L;
        hash = (hash ^ this.quantizeSurfQueryValue(queryBox.minX)) * 1099511628211L;
        hash = (hash ^ this.quantizeSurfQueryValue(queryBox.minY)) * 1099511628211L;
        hash = (hash ^ this.quantizeSurfQueryValue(queryBox.minZ)) * 1099511628211L;
        hash = (hash ^ this.quantizeSurfQueryValue(queryBox.maxX)) * 1099511628211L;
        hash = (hash ^ this.quantizeSurfQueryValue(queryBox.maxY)) * 1099511628211L;
        hash = (hash ^ this.quantizeSurfQueryValue(queryBox.maxZ)) * 1099511628211L;
        return hash;
    }

    @Unique
    private boolean hasNearbySurfRampCached(Box queryBox, double expand) {
        World world = this.getWorld();
        if (world == null || queryBox == null) {
            return false;
        }
        this.resetSurfRampQueryCacheIfNeeded();
        long key = this.computeSurfRampQueryKey(queryBox, expand, 1);
        Object cached = this.surfRampQueryCache.get(key);
        if (cached instanceof Boolean value) {
            return value;
        }
        boolean nearby = SurfRampEntity.hasNearbyRamp(world, queryBox, expand);
        if (this.surfRampQueryCache.size() >= SURF_RAMP_QUERY_CACHE_MAX_ENTRIES) {
            this.surfRampQueryCache.clear();
        }
        this.surfRampQueryCache.put(key, nearby);
        return nearby;
    }

    @SuppressWarnings("unchecked")
    @Unique
    private List<SurfRampEntity> collectNearbySurfRampsCached(Box queryBox, double expand) {
        World world = this.getWorld();
        if (world == null || queryBox == null) {
            return List.of();
        }
        this.resetSurfRampQueryCacheIfNeeded();
        long key = this.computeSurfRampQueryKey(queryBox, expand, 2);
        Object cached = this.surfRampQueryCache.get(key);
        if (cached instanceof List<?>) {
            return (List<SurfRampEntity>) cached;
        }
        List<SurfRampEntity> ramps = SurfRampEntity.collectNearbyRamps(world, queryBox, expand);
        List<SurfRampEntity> immutable = ramps.isEmpty() ? List.of() : List.copyOf(ramps);
        if (this.surfRampQueryCache.size() >= SURF_RAMP_QUERY_CACHE_MAX_ENTRIES) {
            this.surfRampQueryCache.clear();
        }
        this.surfRampQueryCache.put(key, immutable);
        return immutable;
    }

    @Unique
    private boolean isNearRampBoundsXZ(SurfRampEntity ramp, double x, double z, double lateralExtra) {
        if (ramp == null) {
            return false;
        }
        Box bounds = ramp.getBoundingBox();
        double minX = bounds.minX - lateralExtra;
        double maxX = bounds.maxX + lateralExtra;
        double minZ = bounds.minZ - lateralExtra;
        double maxZ = bounds.maxZ + lateralExtra;
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }

    @Unique
    private boolean isNearRampEndpointApprox(
            SurfRampEntity ramp,
            double x,
            double z,
            double endpointTThreshold,
            double lateralToleranceExtra
    ) {
        if (ramp == null) {
            return false;
        }
        Vec3d start = ramp.getStart();
        Vec3d end = ramp.getEnd();
        double horizontalLength = Math.hypot(end.x - start.x, end.z - start.z);
        if (horizontalLength < 1.0E-6D) {
            return false;
        }
        double clampedThreshold = MathHelper.clamp(endpointTThreshold, 0.01D, 0.45D);
        double maxThresholdFromAlong = MathHelper.clamp(
                SURF_ENDPOINT_APPROX_MAX_ALONG_ALLOWANCE / horizontalLength,
                0.01D,
                0.45D
        );
        double effectiveThreshold = Math.min(clampedThreshold, maxThresholdFromAlong);
        return ramp.isNearEndpointXZ(x, z, effectiveThreshold, lateralToleranceExtra);
    }

    @Unique
    private boolean isNearSurfRamp() {
        if (this.isClimbing()) {
            return false;
        }
        return this.hasNearbySurfRampCached(this.getBoundingBox(), SURF_NEARBY_RAMP_SEARCH_EXPAND);
    }

    @Unique
    private boolean isInRampTransitionZone() {
        if (this.isClimbing()) {
            return false;
        }

        Box boundingBox = this.getBoundingBox();
        List<SurfRampEntity> ramps = this.collectNearbySurfRampsCached(boundingBox, 2.8D);
        if (ramps.size() < 2) {
            return false;
        }

        double x = this.getX();
        double z = this.getZ();
        double feetY = boundingBox.minY;
        double endpointRadiusSq = SURF_ENDPOINT_PROXIMITY_RADIUS * SURF_ENDPOINT_PROXIMITY_RADIUS;

        for (SurfRampEntity ramp : ramps) {
            if (ramp == null || !ramp.isAlive() || ramp.isRemoved()) {
                continue;
            }
            if (!this.isNearRampEndpointApprox(
                    ramp,
                    x,
                    z,
                    SURF_ENDPOINT_BYPASS_DISABLE_T_THRESHOLD,
                    SURF_ENDPOINT_BYPASS_DISABLE_LATERAL_EXTRA
            )) {
                continue;
            }
            SurfContact contact = ramp.sampleNearestContact(x, z, feetY, 1.35D);
            if (contact == null) {
                continue;
            }

            Vec3d start = ramp.getStart();
            Vec3d end = ramp.getEnd();
            Vec3d endpoint = this.horizontalDistanceSquared(x, z, start.x, start.z)
                    <= this.horizontalDistanceSquared(x, z, end.x, end.z) ? start : end;
            if (this.horizontalDistanceSquared(x, z, endpoint.x, endpoint.z) > endpointRadiusSq) {
                continue;
            }
            if (this.hasConnectedRampAtEndpoint(ramp, endpoint, ramps)) {
                return true;
            }
        }

        return false;
    }

    @Unique
    private boolean isNearSurfRampEndpoint() {
        if (this.isClimbing()) {
            return false;
        }

        Box boundingBox = this.getBoundingBox();
        List<SurfRampEntity> ramps = this.collectNearbySurfRampsCached(boundingBox, 2.8D);
        if (ramps.isEmpty()) {
            return false;
        }

        double x = this.getX();
        double z = this.getZ();
        for (SurfRampEntity ramp : ramps) {
            if (ramp == null || !ramp.isAlive() || ramp.isRemoved()) {
                continue;
            }
            if (!this.isNearRampEndpointApprox(
                    ramp,
                    x,
                    z,
                    SURF_ENDPOINT_BYPASS_DISABLE_T_THRESHOLD,
                    SURF_ENDPOINT_BYPASS_DISABLE_LATERAL_EXTRA
            )) {
                continue;
            }
            if (this.hasAlternateRampSupportAtPosition(ramps, ramp, x, z)) {
                continue;
            }

            Vec3d start = ramp.getStart();
            Vec3d end = ramp.getEnd();
            Vec3d endpoint = this.horizontalDistanceSquared(x, z, start.x, start.z)
                    <= this.horizontalDistanceSquared(x, z, end.x, end.z) ? start : end;
            if (this.hasConnectedRampAtEndpoint(ramp, endpoint, ramps)) {
                continue;
            }
            return true;
        }

        return false;
    }

    @Unique
    private boolean hasAlternateRampSupportAtPosition(List<SurfRampEntity> ramps, SurfRampEntity sourceRamp, double x, double z) {
        if (ramps == null || ramps.isEmpty()) {
            return false;
        }
        Box bb = this.getBoundingBox();
        double minY = bb.minY - 1.25D;
        double maxY = bb.maxY + 1.25D;
        for (SurfRampEntity other : ramps) {
            if (other == null || other == sourceRamp || !other.isAlive() || other.isRemoved()) {
                continue;
            }

            if (this.isNearRampBoundsXZ(other, x, z, 0.30D)) {
                return true;
            }
            SurfContact contact = other.sampleContact(x, z, minY, maxY);
            if (contact != null) {
                return true;
            }
        }
        return false;
    }

    @Unique
    private boolean hasConnectedRampAtEndpoint(SurfRampEntity sourceRamp, Vec3d endpoint, List<SurfRampEntity> candidates) {
        if (sourceRamp == null || endpoint == null) {
            return false;
        }
        List<SurfRampEntity> nearby;
        if (candidates != null && !candidates.isEmpty()) {
            nearby = candidates;
        } else {
            Box endpointBox = new Box(
                    endpoint.x - 1.0D,
                    endpoint.y - 1.5D,
                    endpoint.z - 1.0D,
                    endpoint.x + 1.0D,
                    endpoint.y + 1.5D,
                    endpoint.z + 1.0D
            );
            nearby = this.collectNearbySurfRampsCached(endpointBox, 1.2D);
        }
        if (nearby.isEmpty()) {
            return false;
        }

        double endpointAttachRadius = 0.55D;
        double endpointAttachRadiusSq = endpointAttachRadius * endpointAttachRadius;

        for (SurfRampEntity other : nearby) {
            if (other == sourceRamp || !other.isAlive() || other.isRemoved()) {
                continue;
            }
            if (!this.isNearRampBoundsXZ(other, endpoint.x, endpoint.z, 1.10D)) {
                continue;
            }

            SurfContact contact = other.sampleContact(
                    endpoint.x,
                    endpoint.z,
                    endpoint.y - 4.0D,
                    endpoint.y + 4.0D
            );
            if (contact != null) {
                return true;
            }

            Vec3d otherStart = other.getStart();
            Vec3d otherEnd = other.getEnd();
            double dStartSq = endpoint.squaredDistanceTo(otherStart);
            double dEndSq = endpoint.squaredDistanceTo(otherEnd);
            if (Math.min(dStartSq, dEndSq) <= endpointAttachRadiusSq) {
                return true;
            }
        }
        return false;
    }

    @Unique
    private double horizontalDistanceSquared(double ax, double az, double bx, double bz) {
        double dx = ax - bx;
        double dz = az - bz;
        return dx * dx + dz * dz;
    }

    @Unique
    private boolean isSurfAttachDistanceValid(double feetY, double surfaceY) {
        double belowDistance = feetY - surfaceY;
        if (belowDistance <= 0.0D) {
            return true;
        }

        double vy = this.getVelocity().y;
        double maxAttachGap;
        if (vy < -1.0D) {
            maxAttachGap = 0.48D;
        } else if (vy < -0.55D) {
            maxAttachGap = 0.34D;
        } else if (vy < -0.20D) {
            maxAttachGap = 0.24D;
        } else {
            maxAttachGap = 0.12D;
        }

        // While actively surfing, keep the contact "sticky" as a curved/sloped surface drops away
        // beneath the feet faster than the feet descend (prevents curve contact flicker).
        double horizontalSpeed = this.getHorizontalSpeed(this.getVelocity());
        if (!this.isOnGround() && this.isReusableSurfContactState() && horizontalSpeed >= SURF_COLLISION_BYPASS_GRACE_MIN_HORIZONTAL) {
            maxAttachGap = Math.max(maxAttachGap, SURF_SURFING_STICKY_ATTACH_GAP);
        }

        if (this.isOnGround() && this.isNearSurfRamp()) {
            maxAttachGap = Math.max(maxAttachGap, SURF_GROUNDED_NEAR_RAMP_MAX_ATTACH_GAP);
        }
        return belowDistance <= maxAttachGap;
    }

    @Unique
    private boolean hasRecentSurfActivity() {
        if (this.minehop$hasRecentSurfActivityNoContact()) {
            return true;
        }
        return this.isReusableSurfContact(this.lastSurfContact);
    }

    @Unique
    private boolean minehop$hasRecentSurfActivityNoContact() {
        if (this.surfContactGraceTicks > 0 || this.surfCollisionBypassGraceTicks > 0) {
            return true;
        }
        if (this.surfGroundSuppressTicks > 0 || this.surfJumpSuppressTicks > 0) {
            return true;
        }
        return this.getHorizontalSpeed(this.getVelocity()) >= SURF_GROUNDED_LANDING_CAPTURE_MIN_HORIZONTAL;
    }

    @Unique
    private double getDynamicSurfBelowTolerance(double verticalVelocity) {
        double fallComponent = Math.max(-verticalVelocity, 0.0D);
        double belowTolerance = SURF_CONTACT_BELOW_TOLERANCE + Math.min(fallComponent * 0.75D, 0.60D);
        if (this.isOnGround() && this.isNearSurfRamp()) {
            belowTolerance = Math.max(belowTolerance, SURF_GROUNDED_NEAR_RAMP_MIN_BELOW_TOLERANCE);
        }
        return belowTolerance;
    }

    @Unique
    private double[][] getSurfSamplePattern() {
        if (!this.isOnGround()
                && this.getVelocity().y <= SURF_FAST_SAMPLE_MAX_VERTICAL
                && this.getHorizontalSpeed(this.getVelocity()) >= SURF_FAST_SAMPLE_MIN_HORIZONTAL) {
            return SURF_SAMPLE_PATTERN_FAST;
        }
        return SURF_SAMPLE_PATTERN_FULL;
    }

    @Unique
    private Vec3d resolveLandingRestoreDirection(Vec3d clippedVelocity, Vec3d incomingVelocity, Vec3d surfaceNormal) {
        Vec3d clippedHorizontal = new Vec3d(clippedVelocity.x, 0.0D, clippedVelocity.z);
        if (clippedHorizontal.lengthSquared() > 1.0E-6D) {
            return clippedHorizontal.normalize();
        }

        Vec3d gravity = new Vec3d(0.0D, -1.0D, 0.0D);
        Vec3d downhillOnPlane = gravity.subtract(surfaceNormal.multiply(gravity.dotProduct(surfaceNormal)));
        Vec3d downhillHorizontal = new Vec3d(downhillOnPlane.x, 0.0D, downhillOnPlane.z);
        if (downhillHorizontal.lengthSquared() > 1.0E-6D) {
            return downhillHorizontal.normalize();
        }

        Vec3d incomingHorizontal = new Vec3d(incomingVelocity.x, 0.0D, incomingVelocity.z);
        if (incomingHorizontal.lengthSquared() > 1.0E-6D) {
            return incomingHorizontal.normalize();
        }
        return null;
    }

    @Unique
    private Vec3d restoreSurfClipHorizontal(
            Vec3d clippedVelocity,
            Vec3d incomingVelocity,
            Vec3d surfaceNormal,
            double triggerRatio,
            double keepRatio
    ) {
        if (clippedVelocity == null || incomingVelocity == null || surfaceNormal == null) {
            return clippedVelocity;
        }
        if (surfaceNormal.lengthSquared() < 1.0E-8D) {
            return clippedVelocity;
        }

        double incomingHorizontal = this.getHorizontalSpeed(incomingVelocity);
        if (incomingHorizontal < SURF_LANDING_STALL_RESTORE_MIN_INCOMING_HORIZONTAL) {
            return clippedVelocity;
        }
        double clippedHorizontal = this.getHorizontalSpeed(clippedVelocity);
        if (clippedHorizontal + 1.0E-6D >= incomingHorizontal * triggerRatio) {
            return clippedVelocity;
        }

        Vec3d restoreDir = this.resolveLandingRestoreDirection(clippedVelocity, incomingVelocity, surfaceNormal);
        if (restoreDir == null) {
            return clippedVelocity;
        }

        double targetHorizontal = Math.max(clippedHorizontal, incomingHorizontal * keepRatio);
        Vec3d restoredVelocity = new Vec3d(
                restoreDir.x * targetHorizontal,
                clippedVelocity.y,
                restoreDir.z * targetHorizontal
        );
        Vec3d normal = surfaceNormal.normalize();
        double into = restoredVelocity.dotProduct(normal);
        if (into < 0.0D) {
            restoredVelocity = restoredVelocity.subtract(normal.multiply(into));
        }
        return restoredVelocity;
    }

    @Unique
    private Vec3d clipVelocityCore(Vec3d velocity, Vec3d normal) {
        double backoff = velocity.dotProduct(normal) * SURF_CLIP_OVERBOUNCE;
        if (backoff >= 0.0D) {
            return velocity;
        }
        return velocity.subtract(normal.multiply(backoff));
    }

    @Unique
    private void sweepFeetVsSurface(Vec3d feetStart, Vec3d delta, java.util.List<SurfRampEntity> ramps) {
        this.minehop$sweepHit = false;
        double len = delta.length();
        int steps = Math.max(1, (int) Math.ceil(len / SURF_SWEEP_MAX_STEP));
        double[][] pattern = this.getSurfSamplePattern();
        Box bb = this.getBoundingBox();
        double edgeX = Math.max((bb.maxX - bb.minX) * 0.5D - SURF_SAMPLE_EDGE_INSET, 0.0D);
        double edgeZ = Math.max((bb.maxZ - bb.minZ) * 0.5D - SURF_SAMPLE_EDGE_INSET, 0.0D);
        double bestT = Double.MAX_VALUE;
        for (int s = 0; s < steps; s++) {
            double f0 = (double) s / (double) steps;
            double f1 = (double) (s + 1) / (double) steps;
            for (double[] off : pattern) {
                double ox = off[0] * edgeX;
                double oz = off[1] * edgeZ;
                double x0 = feetStart.x + ox + delta.x * f0;
                double z0 = feetStart.z + oz + delta.z * f0;
                double y0 = feetStart.y + delta.y * f0;
                double x1 = feetStart.x + ox + delta.x * f1;
                double z1 = feetStart.z + oz + delta.z * f1;
                double y1 = feetStart.y + delta.y * f1;
                for (SurfRampEntity ramp : ramps) {
                    SurfContact c0 = ramp.sampleSurface(x0, z0, y0, SURF_HULL_SAMPLE_BAND, SURF_HULL_SAMPLE_BAND);
                    SurfContact c1 = ramp.sampleSurface(x1, z1, y1, SURF_HULL_SAMPLE_BAND, SURF_HULL_SAMPLE_BAND);
                    if (c0 == null && c1 == null) {
                        continue;
                    }
                    double surfY0 = c0 != null ? c0.surfaceY() : c1.surfaceY();
                    double surfY1 = c1 != null ? c1.surfaceY() : c0.surfaceY();
                    SurfContact endContact = c1 != null ? c1 : c0;
                    if (y0 < surfY0 - SURF_SURFACE_SKIN) {
                        if (f0 < bestT) {
                            bestT = f0;
                            this.minehop$sweepHit = true;
                            this.minehop$sweepT = f0;
                            this.minehop$sweepNormal = (c0 != null ? c0 : endContact).normal();
                            this.minehop$sweepSurfaceY = surfY0;
                        }
                        continue;
                    }
                    boolean above0 = y0 >= surfY0 - SURF_SURFACE_SKIN;
                    boolean below1 = y1 < surfY1 - SURF_SURFACE_SKIN;
                    if (above0 && below1) {
                        double denom = (y1 - y0) - (surfY1 - surfY0);
                        double localFrac = Math.abs(denom) < 1.0E-9D
                                ? 0.0D
                                : MathHelper.clamp((surfY0 - y0) / denom, 0.0D, 1.0D);
                        double tHit = f0 + localFrac * (f1 - f0);
                        if (tHit < bestT) {
                            bestT = tHit;
                            this.minehop$sweepHit = true;
                            this.minehop$sweepT = tHit;
                            SurfContact nc = localFrac < 0.5D ? (c0 != null ? c0 : endContact) : endContact;
                            this.minehop$sweepNormal = nc.normal();
                            this.minehop$sweepSurfaceY = surfY0 + (surfY1 - surfY0) * localFrac;
                        }
                    }
                }
            }
            if (this.minehop$sweepHit) {
                break;
            }
        }
    }

    @Unique
    private boolean minehop$surfMoveAndCollide(Vec3d velocity) {
        if (this.isClimbing() || this.minehop$hasRealGroundBelow()) {
            return false;
        }
        Box queryBox = this.getBoundingBox().stretch(velocity).expand(SURF_HULL_SAMPLE_BAND);
        java.util.List<SurfRampEntity> ramps = this.collectNearbySurfRampsCached(queryBox, SURF_COLLISION_QUERY_EXPAND);
        if (ramps.isEmpty()) {
            return false;
        }

        double[][] pattern = this.getSurfSamplePattern();
        Box bb = this.getBoundingBox();
        double edgeX = Math.max((bb.maxX - bb.minX) * 0.5D - SURF_SAMPLE_EDGE_INSET, 0.0D);
        double edgeZ = Math.max((bb.maxZ - bb.minZ) * 0.5D - SURF_SAMPLE_EDGE_INSET, 0.0D);

        Vec3d curFeet = this.getPos();
        Vec3d disp = Vec3d.ZERO;
        Vec3d vel = velocity;
        this.minehop$hullOnRamp = false;
        this.minehop$hullNormal = null;

        // Surface-following march: advance the FULL horizontal motion in fixed substeps and, at
        // each substep, rest the binding foot on its local surface + clip velocity along that
        // normal. Unlike a truncating swept-collision bump loop, horizontal advance is never
        // shortened by curvature, so a curved/concave surface can never stall the player for a
        // tick (the prior bump loop burned its 4 bumps re-clipping a tangent step that kept
        // dipping into the rising surface, leaving the tick's displacement near zero).
        int steps = Math.max(1, (int) Math.ceil(velocity.length() / SURF_SWEEP_MAX_STEP));
        double dt = 1.0D / (double) steps;
        boolean onRampNow = false;

        // Pre-clip: convert any into-surface velocity accumulated last tick (gravity) into
        // tangential motion BEFORE moving. Otherwise the first substep over-descends into the
        // surface and the corrective snap-up shows up as a positional bob (~g, 2-tick limit cycle)
        // even though the stored velocity reads smooth.
        this.minehop$computeBind(curFeet, pattern, edgeX, edgeZ, ramps);
        if (this.minehop$bindNormal != null
                && this.minehop$bindLift > -SURF_HULL_ATTACH_GAP
                && vel.dotProduct(this.minehop$bindNormal) < 0.0D) {
            vel = this.clipVelocityCore(vel, this.minehop$bindNormal);
        }

        for (int s = 0; s < steps; s++) {
            Vec3d newFeet = curFeet.add(vel.multiply(dt));

            this.minehop$computeBind(newFeet, pattern, edgeX, edgeZ, ramps);
            double maxLift = this.minehop$bindLift;
            Vec3d bindNormal = this.minehop$bindNormal;
            double bindSurfaceY = this.minehop$bindSurfaceY;

            if (bindNormal != null && maxLift > -SURF_HULL_ATTACH_GAP) {
                // ATTACHED: clip EVERY substep (removes gravity's into-surface component so the
                // velocity rides along the surface). Small gaps are left for the eased post-loop
                // reconciliation (smooth on-ramp); but if the foot has penetrated DEEPER than the
                // allowed embed, lift out the excess within the substep so a fast fall can't tunnel
                // through the zero-thickness ramp surface.
                vel = this.clipVelocityCore(vel, bindNormal);
                if (maxLift > SURF_HULL_MAX_EMBED) {
                    double antiTunnel = Math.min(maxLift - SURF_HULL_MAX_EMBED, SURF_MAX_SNAP_UP);
                    newFeet = newFeet.add(0.0D, antiTunnel, 0.0D);
                }
                onRampNow = true;
                this.minehop$hullNormal = bindNormal;
                this.minehop$hullSurfaceY = bindSurfaceY;
            } else {
                // Well above the surface (sharp convex roll) or off the ramp entirely: free substep.
                onRampNow = false;
            }

            disp = disp.add(newFeet.subtract(curFeet));
            curFeet = newFeet;
        }

        // Eased vertical reconciliation toward the surface: smoothly settle onto the ramp instead of
        // snapping. A real/deep penetration still catches fast (gap - MAX_EMBED) so you never sink
        // through; small approach/seam gaps ease over a few ticks; a floating foot is pulled down
        // slowly. Only when actually moving into/along the surface (not jumping/flicking away).
        if (onRampNow) {
            this.minehop$computeBind(curFeet, pattern, edgeX, edgeZ, ramps);
            if (this.minehop$bindNormal != null && this.minehop$bindLift > -SURF_HULL_ATTACH_GAP) {
                double gap = this.minehop$bindLift; // >0 = penetrating (lift up), <=0 = above surface
                double correction;
                if (gap > 0.0D) {
                    correction = Math.min(SURF_MAX_SNAP_UP, Math.max(gap * SURF_HULL_LIFT_EASE, gap - SURF_HULL_MAX_EMBED));
                } else if (vel.dotProduct(this.minehop$bindNormal) <= SURF_SURFACE_SKIN) {
                    correction = Math.max(gap * SURF_HULL_LIFT_EASE, -SURF_HULL_EASE_DOWN);
                } else {
                    correction = 0.0D;
                }
                if (correction != 0.0D) {
                    curFeet = curFeet.add(0.0D, correction, 0.0D);
                    disp = disp.add(0.0D, correction, 0.0D);
                }
                this.minehop$hullNormal = this.minehop$bindNormal;
                this.minehop$hullSurfaceY = this.minehop$bindSurfaceY;
            }
        }

        // End-of-move contact state drives the outer gravity clip: only treat as on-ramp if the
        // FINAL substep was actually resting, so flick-offs and convex rolls fly free.
        this.minehop$hullOnRamp = onRampNow;

        this.setVelocity(vel);
        this.velocityDirty = true;
        this.move(MovementType.SELF, disp);
        return true;
    }

    // Binding foot at the given feet position = the foot needing the most lift to rest on its own
    // local surface (the up-slope edge on a tilt). Sets minehop$bindLift / minehop$bindNormal /
    // minehop$bindSurfaceY; bindNormal stays null when no foot is over any ramp.
    @Unique
    private void minehop$computeBind(Vec3d feet, double[][] pattern, double edgeX, double edgeZ,
                                     java.util.List<SurfRampEntity> ramps) {
        double maxLift = -Double.MAX_VALUE;
        Vec3d bindNormal = null;
        double bindSurfaceY = 0.0D;
        for (double[] off : pattern) {
            double fx = feet.x + off[0] * edgeX;
            double fz = feet.z + off[1] * edgeZ;
            for (SurfRampEntity ramp : ramps) {
                SurfContact c = ramp.sampleSurface(fx, fz, feet.y, SURF_HULL_SAMPLE_BAND, SURF_HULL_SAMPLE_BAND);
                if (c == null) {
                    continue;
                }
                double lift = c.surfaceY() + SURF_SURFACE_SKIN - feet.y;
                if (lift > maxLift) {
                    maxLift = lift;
                    bindNormal = c.normal();
                    bindSurfaceY = c.surfaceY();
                }
            }
        }
        this.minehop$bindLift = maxLift;
        this.minehop$bindNormal = bindNormal;
        this.minehop$bindSurfaceY = bindSurfaceY;
    }

    @Unique
    private Vec3d clipVelocityAgainstSurfRamp(Vec3d velocity, Vec3d surfNormal) {
        if (surfNormal == null || surfNormal.lengthSquared() < SURF_CLIP_EPSILON) {
            return velocity;
        }

        Vec3d normal = surfNormal.normalize();
        double intoRampComponent = velocity.dotProduct(normal);
        double horizontalSpeed = this.getHorizontalSpeed(velocity);
        if (intoRampComponent >= SURF_CLIP_MIN_INTO_COMPONENT) {
            return velocity;
        }

        double backoff = intoRampComponent * SURF_CLIP_OVERBOUNCE;
        Vec3d clipped = velocity.subtract(normal.multiply(backoff));
        double adjust = clipped.dotProduct(normal);
        if (adjust < 0.0D) {
            clipped = clipped.subtract(normal.multiply(adjust));
        }

        double intoRatio = Math.abs(intoRampComponent) / Math.max(horizontalSpeed, 1.0E-6D);
        if (velocity.y >= SURF_CLIP_SPEED_PRESERVE_MIN_UPWARD_VELOCITY
                && horizontalSpeed >= SURF_CLIP_SPEED_PRESERVE_MIN_HORIZONTAL_SPEED
                && intoRampComponent >= -SURF_CLIP_SPEED_PRESERVE_MAX_INTO_COMPONENT
                && intoRatio <= SURF_CLIP_SPEED_PRESERVE_MAX_INTO_RATIO) {
            double clippedHorizontal = this.getHorizontalSpeed(clipped);
            if (clippedHorizontal >= SURF_CLIP_SPEED_PRESERVE_MIN_CLIPPED_HORIZONTAL
                    && clippedHorizontal + 1.0E-6D < horizontalSpeed * SURF_CLIP_SPEED_PRESERVE_TRIGGER_RATIO) {
                double targetHorizontal = horizontalSpeed * SURF_CLIP_SPEED_PRESERVE_KEEP_RATIO;
                double scale = targetHorizontal / clippedHorizontal;
                clipped = new Vec3d(clipped.x * scale, clipped.y, clipped.z * scale);
                double scaledAdjust = clipped.dotProduct(normal);
                if (scaledAdjust < 0.0D) {
                    clipped = clipped.subtract(normal.multiply(scaledAdjust));
                }
            }
        }

        Vec3d projectedIncoming = velocity.subtract(normal.multiply(intoRampComponent));
        double projectedIncomingSpeed = projectedIncoming.length();
        double clippedPlanarSpeed = clipped.length();
        double planarIntoRatio = Math.abs(intoRampComponent) / Math.max(projectedIncomingSpeed, 1.0E-6D);
        double clipPlanarMaxVertical = this.getClipRestoreMaxVertical(horizontalSpeed);
        double clipPlanarMaxIntoRatio = this.getClipPlanarPreserveMaxIntoRatio(horizontalSpeed);
        double clipPlanarMinVertical = horizontalSpeed >= 1.0D
                ? Math.min(SURF_CLIP_PLANAR_PRESERVE_MIN_VERTICAL, -horizontalSpeed * 0.7D - 0.4D)
                : SURF_CLIP_PLANAR_PRESERVE_MIN_VERTICAL;
        if (velocity.y >= clipPlanarMinVertical
                && velocity.y <= clipPlanarMaxVertical
                && projectedIncomingSpeed >= SURF_CLIP_PLANAR_PRESERVE_MIN_SPEED
                && planarIntoRatio <= clipPlanarMaxIntoRatio
                && clippedPlanarSpeed + 1.0E-6D < projectedIncomingSpeed * SURF_CLIP_PLANAR_PRESERVE_TRIGGER_RATIO) {
            double targetPlanarSpeed = projectedIncomingSpeed * SURF_CLIP_PLANAR_PRESERVE_KEEP_RATIO;
            if (clippedPlanarSpeed > 1.0E-6D) {
                clipped = clipped.multiply(targetPlanarSpeed / clippedPlanarSpeed);
            } else if (projectedIncomingSpeed > 1.0E-6D) {
                clipped = projectedIncoming.normalize().multiply(targetPlanarSpeed);
            }
            double planarAdjust = clipped.dotProduct(normal);
            if (planarAdjust < 0.0D) {
                clipped = clipped.subtract(normal.multiply(planarAdjust));
            }
        }

        // Cap per-tick horizontal redirection. On a sharp / AABB-discretized curve the contact normal
        // snaps between ticks, so projecting velocity onto the turned plane removes a large horizontal
        // component in ONE tick (perceived as a velocity reversal / instant stop, then sliding off the
        // ramp). Limit how far the horizontal direction may turn per clip so curve-following is gradual,
        // and preserve speed. Flat / gently-curved surf barely redirects, so this never fires there.
        double inH = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
        double clH = Math.sqrt(clipped.x * clipped.x + clipped.z * clipped.z);
        if (inH >= SURF_CLIP_SPEED_PRESERVE_MIN_HORIZONTAL_SPEED && clH > 1.0E-4D) {
            double idx = velocity.x / inH;
            double idz = velocity.z / inH;
            double cdx = clipped.x / clH;
            double cdz = clipped.z / clH;
            double cosA = MathHelper.clamp(idx * cdx + idz * cdz, -1.0D, 1.0D);
            if (cosA < SURF_CLIP_MAX_REDIRECT_COS) {
                double rot = Math.acos(cosA) - Math.acos(SURF_CLIP_MAX_REDIRECT_COS);
                double cross = idx * cdz - idz * cdx;
                double ang = (cross >= 0.0D ? -1.0D : 1.0D) * rot;
                double ca = Math.cos(ang);
                double sa = Math.sin(ang);
                double ndx = cdx * ca - cdz * sa;
                double ndz = cdx * sa + cdz * ca;
                double mag = Math.max(clH, inH * SURF_CLIP_BEND_PRESERVE_KEEP_RATIO);
                clipped = new Vec3d(ndx * mag, clipped.y, ndz * mag);
                double adj = clipped.dotProduct(normal);
                if (adj < 0.0D) {
                    clipped = clipped.subtract(normal.multiply(adj));
                }
            }
        }
        return clipped;
    }

    @Unique
    private double getHorizontalSpeed(Vec3d velocity) {
        return Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
    }

    @Unique
    private boolean minehop$hasRealGroundBelow() {
        Box box = this.getBoundingBox();
        if (box.maxX - box.minX < 1.0E-4D || box.maxZ - box.minZ < 1.0E-4D) {
            return false;
        }
        Box below = new Box(
                box.minX + 0.001D,
                box.minY - 0.20D,
                box.minZ + 0.001D,
                box.maxX - 0.001D,
                box.minY - 0.001D,
                box.maxZ - 0.001D
        );
        return !this.getWorld().isSpaceEmpty(this, below);
    }

    @Unique
    private double getBaseWishSpeed(MinehopConfig config) {
        return this.movementSpeed * config.movement.speed_mul;
    }

    @Unique
    private double getAirWishSpeedCap(MinehopConfig config) {
        double capInBlocksPerTick = config.movement.sv_maxairspeed * SOURCE_UNIT_TO_BLOCKS_PER_TICK;
        return Math.max(capInBlocksPerTick * Math.max(config.movement.speed_coefficient, 0.0D), 0.0D);
    }

    @Unique
    private double getGravityPerTick(MinehopConfig config) {
        return config.movement.sv_gravity * SOURCE_FRAME_TIME * SOURCE_UNIT_TO_BLOCKS_PER_TICK;
    }

    @Unique
    private Vec3d applySourceFriction(Vec3d velocity, double friction, double stopSpeed, double surfaceFriction) {
        Vec3d horizontalVelocity = new Vec3d(velocity.x, 0.0D, velocity.z);
        double speed = horizontalVelocity.horizontalLength();
        if (speed < 1.0E-8D) {
            return velocity;
        }

        double stopSpeedBlocksPerTick = Math.max(stopSpeed, 0.0D) * SOURCE_UNIT_TO_BLOCKS_PER_TICK;
        double control = Math.max(speed, stopSpeedBlocksPerTick);
        double drop = control * friction * SOURCE_FRAME_TIME * surfaceFriction;
        double newSpeed = Math.max(speed - drop, 0.0D);
        double speedScale = newSpeed / speed;

        return new Vec3d(horizontalVelocity.x * speedScale, velocity.y, horizontalVelocity.z * speedScale);
    }

    @Unique
    private Vec3d accelerateSource(Vec3d horizontalVelocity, Vec3d wishDir, double wishSpeed, double wishSpeedCap, double accelerate, double surfaceFriction, boolean useUncappedWishSpeedForAccel, double frameFraction) {
        double cappedWishSpeed = Math.min(wishSpeed, wishSpeedCap);
        double currentSpeed = horizontalVelocity.dotProduct(wishDir);
        double addSpeed = cappedWishSpeed - currentSpeed;
        if (addSpeed <= 0.0D) {
            return horizontalVelocity;
        }

        double accelerationWishSpeed = useUncappedWishSpeedForAccel ? wishSpeed : cappedWishSpeed;
        double accelSpeed = accelerate * SOURCE_FRAME_TIME * frameFraction * accelerationWishSpeed * surfaceFriction;
        if (accelSpeed > addSpeed) {
            accelSpeed = addSpeed;
        }

        return horizontalVelocity.add(wishDir.multiply(accelSpeed));
    }

    @Unique
    private Vec3d accelerateAirSubstepped(Vec3d horizontalVelocity, double sI, double fI, MinehopConfig config, double airWishSpeedCap, float startYaw, float endYaw) {
        double simRate = SOURCE_SIM_TICKRATE;
        if (simRate < 20.0D) {
            simRate = 20.0D;
        }
        double subFrameFraction = 20.0D / simRate;
        double budget = simRate * SOURCE_FRAME_TIME + this.minehop$substepRemainder;
        int steps = (int) Math.floor(budget + 1.0E-9D);
        if (steps < 1) {
            steps = 1;
        }
        this.minehop$substepRemainder = Math.max(0.0D, budget - steps);
        this.minehop$lastAirSteps = steps;

        float yawDelta = MathHelper.wrapDegrees(endYaw - startYaw);
        if (yawDelta > SOURCE_MAX_AIR_YAW_DELTA) {
            yawDelta = (float) SOURCE_MAX_AIR_YAW_DELTA;
        } else if (yawDelta < -SOURCE_MAX_AIR_YAW_DELTA) {
            yawDelta = (float) -SOURCE_MAX_AIR_YAW_DELTA;
        }
        float effectiveStartYaw = endYaw - yawDelta;

        double accel = config.movement.sv_airaccelerate;
        double baseWishSpeed = getBaseWishSpeed(config);

        Vec3d hv = horizontalVelocity;
        for (int i = 0; i < steps && i < 4096; i++) {
            float frac = (float) ((i + 1.0D) / steps);
            float yawI = MathHelper.lerpAngleDegrees(frac, effectiveStartYaw, endYaw);
            Vec3d wishVector = MovementUtil.movementInputToVelocity(new Vec3d(sI, 0.0D, fI), 1.0F, yawI);
            double wvl = wishVector.horizontalLength();
            if (wvl <= 0.0D) {
                continue;
            }
            Vec3d wishDir = new Vec3d(wishVector.x / wvl, 0.0D, wishVector.z / wvl);
            double wishSpeed = baseWishSpeed * wvl;
            hv = accelerateSource(hv, wishDir, wishSpeed, airWishSpeedCap, accel, 1.0D, true, subFrameFraction);
        }
        return hv;
    }

    // Pure (no instance-state) replay of the substepped air accel for a GIVEN turn (startYaw->endYaw)
    // and step count, returning the resulting horizontal speed. Used to find this tick's optimal
    // strafe gain so efficiency is normalized against the real physics (not a wrong closed-form cap).
    @Unique
    private double minehop$simAirStrafeSpeed(Vec3d horizontalVelocity, double sI, double fI, MinehopConfig config,
                                             double airWishSpeedCap, float startYaw, float endYaw, int steps) {
        double subFrameFraction = 20.0D / SOURCE_SIM_TICKRATE;
        float yawDelta = MathHelper.wrapDegrees(endYaw - startYaw);
        if (yawDelta > SOURCE_MAX_AIR_YAW_DELTA) {
            yawDelta = (float) SOURCE_MAX_AIR_YAW_DELTA;
        } else if (yawDelta < -SOURCE_MAX_AIR_YAW_DELTA) {
            yawDelta = (float) -SOURCE_MAX_AIR_YAW_DELTA;
        }
        float effectiveStartYaw = endYaw - yawDelta;
        double accel = config.movement.sv_airaccelerate;
        double baseWishSpeed = getBaseWishSpeed(config);
        Vec3d hv = horizontalVelocity;
        for (int i = 0; i < steps && i < 4096; i++) {
            float frac = (float) ((i + 1.0D) / steps);
            float yawI = MathHelper.lerpAngleDegrees(frac, effectiveStartYaw, endYaw);
            Vec3d wishVector = MovementUtil.movementInputToVelocity(new Vec3d(sI, 0.0D, fI), 1.0F, yawI);
            double wvl = wishVector.horizontalLength();
            if (wvl <= 0.0D) {
                continue;
            }
            Vec3d wishDir = new Vec3d(wishVector.x / wvl, 0.0D, wishVector.z / wvl);
            double wishSpeed = baseWishSpeed * wvl;
            hv = accelerateSource(hv, wishDir, wishSpeed, airWishSpeedCap, accel, 1.0D, true, subFrameFraction);
        }
        return Math.sqrt(hv.x * hv.x + hv.z * hv.z);
    }

    // Best horizontal speed achievable this tick over all legal turns (sweep), plus the |turn| (deg)
    // that achieves it. Returns {bestSpeed, optimalTurnAbsDeg}. Both the efficiency (gain ratio) and
    // the gauge (turn ratio) normalize against this real-physics optimum so they stay consistent.
    @Unique
    private double[] minehop$optimalAirStrafe(Vec3d horizontalVelocity, double sI, double fI, MinehopConfig config,
                                              double airWishSpeedCap, float startYaw, int steps) {
        double best = horizontalVelocity.horizontalLength();
        double bestTurn = 0.0D;
        for (double d = -SOURCE_MAX_AIR_YAW_DELTA; d <= SOURCE_MAX_AIR_YAW_DELTA + 1.0E-6D; d += 1.0D) {
            double s = this.minehop$simAirStrafeSpeed(horizontalVelocity, sI, fI, config, airWishSpeedCap,
                    startYaw, (float) (startYaw + d), steps);
            if (s > best) {
                best = s;
                bestTurn = Math.abs(d);
            }
        }
        return new double[]{best, bestTurn};
    }

    private static double normalizeAngle(double angle) {
        angle = angle % 360;
        if (angle > 180) angle -= 360;
        else if (angle < -180) angle += 360;
        return angle;
    }

    @Unique
    private Vec3d getLadderNormal() {
        if (!this.isClimbing()) {
            return null;
        }

        BlockPos climbPos = this.getBlockPos();
        BlockState blockState = this.getWorld().getBlockState(climbPos);
        if (!blockState.isIn(BlockTags.CLIMBABLE)) {
            return null;
        }

        if (blockState.isOf(Blocks.LADDER) && blockState.contains(HorizontalFacingBlock.FACING)) {
            Direction facing = blockState.get(HorizontalFacingBlock.FACING);
            Vec3d normal = new Vec3d(facing.getOffsetX(), 0.0D, facing.getOffsetZ());
            return normal.lengthSquared() > 1.0E-8D ? normal.normalize() : null;
        }

        List<Direction> normalCandidates = new ArrayList<>(4);
        boolean[] usedCandidates = new boolean[4];
        for (Direction wallDir : Direction.Type.HORIZONTAL) {
            BlockPos supportPos = climbPos.offset(wallDir);
            BlockState supportState = this.getWorld().getBlockState(supportPos);
            if (supportState.isSideSolidFullSquare(this.getWorld(), supportPos, wallDir.getOpposite())) {
                this.minehop$addLadderNormalCandidate(normalCandidates, usedCandidates, wallDir.getOpposite());
            }
        }

        if (normalCandidates.isEmpty() && blockState.isOf(Blocks.VINE)) {
            if (blockState.contains(VineBlock.NORTH) && blockState.get(VineBlock.NORTH)) {
                this.minehop$addLadderNormalCandidate(normalCandidates, usedCandidates, Direction.SOUTH);
            }
            if (blockState.contains(VineBlock.SOUTH) && blockState.get(VineBlock.SOUTH)) {
                this.minehop$addLadderNormalCandidate(normalCandidates, usedCandidates, Direction.NORTH);
            }
            if (blockState.contains(VineBlock.WEST) && blockState.get(VineBlock.WEST)) {
                this.minehop$addLadderNormalCandidate(normalCandidates, usedCandidates, Direction.EAST);
            }
            if (blockState.contains(VineBlock.EAST) && blockState.get(VineBlock.EAST)) {
                this.minehop$addLadderNormalCandidate(normalCandidates, usedCandidates, Direction.WEST);
            }
        }

        if (normalCandidates.isEmpty()) {
            return null;
        }

        Vec3d approach = new Vec3d(this.getVelocity().x, 0.0D, this.getVelocity().z);
        if (approach.lengthSquared() < 1.0E-8D) {
            Vec3d look = this.getRotationVector();
            approach = new Vec3d(look.x, 0.0D, look.z);
        }
        Vec3d approachDir = approach.lengthSquared() > 1.0E-8D ? approach.normalize() : Vec3d.ZERO;

        Vec3d fromCenter = new Vec3d(
                this.getX() - (climbPos.getX() + 0.5D),
                0.0D,
                this.getZ() - (climbPos.getZ() + 0.5D)
        );
        Vec3d fromCenterDir = fromCenter.lengthSquared() > 1.0E-8D ? fromCenter.normalize() : Vec3d.ZERO;

        Vec3d best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (Direction candidate : normalCandidates) {
            Vec3d normal = new Vec3d(candidate.getOffsetX(), 0.0D, candidate.getOffsetZ());
            if (normal.lengthSquared() < 1.0E-8D) {
                continue;
            }

            double score = 0.0D;
            if (approachDir.lengthSquared() > 1.0E-8D) {

                score += -normal.dotProduct(approachDir) * 2.0D;
            }
            if (fromCenterDir.lengthSquared() > 1.0E-8D) {

                score += normal.dotProduct(fromCenterDir);
            }

            if (score > bestScore) {
                bestScore = score;
                best = normal;
            }
        }

        return best == null ? null : best.normalize();
    }

    @Unique
    private void minehop$addLadderNormalCandidate(List<Direction> candidates, boolean[] used, Direction direction) {
        if (direction == null || direction.getAxis().isVertical()) {
            return;
        }

        int index = switch (direction) {
            case NORTH -> 0;
            case SOUTH -> 1;
            case WEST -> 2;
            case EAST -> 3;
            default -> -1;
        };

        if (index < 0 || used[index]) {
            return;
        }
        used[index] = true;
        candidates.add(direction);
    }

    @Unique
    private Vec3d applySourceLadderMove(Vec3d motion, double fI, double sI, Vec3d ladderNormal) {
        if (!this.isClimbing() || ladderNormal == null) {
            return motion;
        }

        final double climbSpeed = 200.0D * SOURCE_UNIT_TO_BLOCKS_PER_TICK;
        final double ladderJumpDetachSpeed = 270.0D * SOURCE_UNIT_TO_BLOCKS_PER_TICK;
        final double ladderAngleThreshold = -0.707D;
        final double ladderPerpDampen = 0.2D;

        this.onLanding();

        if (this.jumping) {
            long now = this.getWorld().getTime();
            if (now < this.ladderReleaseTime || now > this.ladderReleaseTime + 4L) {
                this.ladderReleaseTime = now;
                return ladderNormal.multiply(ladderJumpDetachSpeed);
            }
            return motion;
        }

        double forwardSpeed = fI * climbSpeed;
        double rightSpeed = sI * climbSpeed;
        if (Math.abs(forwardSpeed) < 1.0E-8D && Math.abs(rightSpeed) < 1.0E-8D) {
            return Vec3d.ZERO;
        }

        Vec3d forward = this.getRotationVector().normalize();
        Vec3d up = new Vec3d(0.0D, 1.0D, 0.0D);
        Vec3d right = up.crossProduct(forward);
        if (right.lengthSquared() < 1.0E-8D) {

            double yawRad = Math.toRadians(this.getYaw());
            right = new Vec3d(Math.cos(yawRad), 0.0D, Math.sin(yawRad));
        } else {
            right = right.normalize();
        }

        Vec3d desiredVelocity = forward.multiply(forwardSpeed).add(right.multiply(rightSpeed));
        Vec3d perp = up.crossProduct(ladderNormal);
        if (perp.lengthSquared() < 1.0E-8D) {
            return Vec3d.ZERO;
        }
        perp = perp.normalize();

        double normal = desiredVelocity.dotProduct(ladderNormal);
        Vec3d intoFace = ladderNormal.multiply(normal);
        Vec3d lateral = desiredVelocity.subtract(intoFace);
        Vec3d tmp = ladderNormal.crossProduct(perp);
        if (tmp.lengthSquared() < 1.0E-8D) {
            return Vec3d.ZERO;
        }
        tmp = tmp.normalize();

        double tmpDist = tmp.dotProduct(lateral);
        double perpDist = perp.dotProduct(lateral);
        Vec3d angleVec = perp.multiply(perpDist).add(intoFace);
        if (angleVec.lengthSquared() > 1.0E-8D) {
            angleVec = angleVec.normalize();
            double angleDot = angleVec.dotProduct(ladderNormal);
            if (angleDot < ladderAngleThreshold) {
                lateral = tmp.multiply(tmpDist).add(perp.multiply(ladderPerpDampen * perpDist));
            }
        }

        Vec3d ladderVelocity = lateral.add(tmp.multiply(-normal));
        if (this.isOnGround() && normal > 0.0D) {
            ladderVelocity = ladderVelocity.add(ladderNormal.multiply(climbSpeed));
        }

        return ladderVelocity;
    }

    @Unique
    private void minehop$updateCssCrouchOffset(MinehopConfig config) {
        boolean sneakingNow = this.isSneaking();
        boolean sprintingNow = this.isSprinting();
        if (config == null) {
            this.cssWasSneaking = sneakingNow;
            this.cssWasSprinting = sprintingNow;
            this.cssAirCrouchSprintLock = false;
            return;
        }

        boolean cssCrouchEnabled = config.enabled
                && config.movement.css_crouch_jump
                && this.getType() == EntityType.PLAYER;
        boolean airborneForSprintCarry = !this.isOnGround()
                && !this.isClimbing()
                && !this.isTouchingWater()
                && !this.isInLava()
                && !this.isGliding();

        if (cssCrouchEnabled
                && airborneForSprintCarry
                && sneakingNow
                && !this.cssWasSneaking
                && this.cssWasSprinting) {
            this.cssAirCrouchSprintLock = true;
        }

        if (this.cssAirCrouchSprintLock) {
            if (cssCrouchEnabled && airborneForSprintCarry && sneakingNow) {
                if (!this.isSprinting() && !config.movement.disable_sprint) {
                    this.setSprinting(true);
                }
            } else {
                this.cssAirCrouchSprintLock = false;
            }
        }

        double crouchDelta = this.minehop$getCssCrouchDelta();
        if (crouchDelta <= CSS_CROUCH_DELTA_EPSILON) {
            this.cssCrouchOffsetApplied = false;
            this.cssWasSneaking = sneakingNow;
            this.cssWasSprinting = this.isSprinting();
            return;
        }

        boolean canApply = cssCrouchEnabled
                && this.isSneaking()
                && !this.isClimbing()
                && !this.isTouchingWater()
                && !this.isInLava()
                && !this.isGliding();

        if (canApply && !this.cssCrouchOffsetApplied) {
            Box upBox = this.getBoundingBox().offset(0.0D, crouchDelta, 0.0D);
            if (this.getWorld().isSpaceEmpty(this, upBox)) {
                this.setPosition(this.getX(), this.getY() + crouchDelta, this.getZ());
                this.velocityDirty = true;
                this.cssCrouchOffsetApplied = true;
            }
        }

        if (!canApply && this.cssCrouchOffsetApplied) {
            Box downBox = this.getBoundingBox().offset(0.0D, -crouchDelta, 0.0D);
            if (this.getWorld().isSpaceEmpty(this, downBox)) {
                this.setPosition(this.getX(), this.getY() - crouchDelta, this.getZ());
                this.velocityDirty = true;
                this.cssCrouchOffsetApplied = false;
            }
        }

        this.cssWasSneaking = sneakingNow;
        this.cssWasSprinting = this.isSprinting();
    }

    @Unique
    private double minehop$getCssCrouchDelta() {
        double standingHeight = this.getDimensions(EntityPose.STANDING).height();
        double crouchingHeight = this.getDimensions(EntityPose.CROUCHING).height();
        if (!Double.isFinite(standingHeight) || !Double.isFinite(crouchingHeight)) {
            return 0.0D;
        }
        return Math.max(standingHeight - crouchingHeight, 0.0D);
    }

    @Inject(method = "jump", at = @At("HEAD"), cancellable = true)
    void jump(CallbackInfo ci) {
        MinehopConfig config;

        if (Minehop.override_config && Minehop.receivedConfig) {
            config = new MinehopConfig();
            config.movement.sv_friction = Minehop.o_sv_friction;
            config.movement.sv_accelerate = Minehop.o_sv_accelerate;
            config.movement.sv_airaccelerate = Minehop.o_sv_airaccelerate;
            config.movement.sv_maxairspeed = Minehop.o_sv_maxairspeed;
            config.movement.sv_jump_impulse = Minehop.o_sv_jump_impulse;
            config.movement.speed_mul = Minehop.o_speed_mul;
            config.movement.sv_gravity = Minehop.o_sv_gravity;
            config.movement.sv_stopspeed = Minehop.o_sv_stopspeed;
            config.movement.speed_coefficient = Minehop.o_speed_coefficient;
            config.movement.auto_step_up = Minehop.o_auto_step_up;
            config.movement.css_crouch_jump = Minehop.o_css_crouch_jump;
            config.movement.disable_sprint = Minehop.o_disable_sprint;
            config.enabled = Minehop.o_enabled;
            config.fall_damage = Minehop.o_fall_damage;
        }
        else {
            config = ConfigWrapper.getEffectiveConfig(this);
        }

        if (!config.enabled) { return; }

        boolean jumpRealGroundBelow = this.minehop$hasRealGroundBelow();

        if (!this.isClimbing() && !jumpRealGroundBelow) {
            Vec3d jumpVelocity = this.getVelocity();
            double jumpHorizontal = this.getHorizontalSpeed(jumpVelocity);
            SurfContact groundedLandingSupport = this.findJumpRampSupportContact(this.getBoundingBox(), jumpVelocity);
            boolean jumpNearSurfSupport = this.isNearSurfRamp()
                    || this.isReusableSurfContact(this.lastSurfContact)
                    || this.isReusableSurfContact(groundedLandingSupport);
            boolean jumpSurfLatched = this.surfGroundSuppressTicks > 0
                    || this.surfContactGraceTicks > 0
                    || this.surfCollisionBypassGraceTicks > 0
                    || this.surfJumpSuppressTicks > 0
                    || this.isReusableSurfContact(this.lastSurfContact);
            boolean jumpRecentlySurfSuppressed = this.surfJumpSuppressTicks > 0
                    && jumpHorizontal >= SURF_JUMP_SUPPRESS_MIN_HORIZONTAL
                    && (this.isOnGround() || jumpVelocity.y <= SURF_GROUND_SUPPRESS_ON_GROUND_MAX_VERTICAL + 0.06D);
            if (jumpRecentlySurfSuppressed) {
                this.velocityDirty = true;
                ci.cancel();
                return;
            }
            boolean groundedSurfSupportNow = this.isReusableSurfContact(groundedLandingSupport);
            if (!groundedSurfSupportNow
                    && this.isOnGround()
                    && this.isNearSurfRamp()
                    && jumpHorizontal >= SURF_JUMP_SUPPRESS_MIN_HORIZONTAL) {
                Box jumpBox = this.getBoundingBox();
                SurfContact jumpWideSupport = this.findNearestSurfContactFallback(
                        this.getX(),
                        this.getZ(),
                        jumpBox.minY,
                        jumpBox.maxY,
                        SURF_GROUNDED_LANDING_CAPTURE_LOOSE_MAX_VERTICAL_DISTANCE,
                        SURF_SEAM_REACQUIRE_LATERAL_EXTRA
                );
                groundedSurfSupportNow = this.isReusableSurfContact(jumpWideSupport);
            }
            if (this.isOnGround()
                    && groundedSurfSupportNow
                    && jumpHorizontal >= SURF_JUMP_SUPPRESS_MIN_HORIZONTAL
                    && jumpVelocity.y <= SURF_GROUND_SUPPRESS_ON_GROUND_MAX_VERTICAL + 0.10D) {
                this.surfJumpSuppressTicks = Math.max(this.surfJumpSuppressTicks, SURF_JUMP_SUPPRESS_TICKS);
                this.velocityDirty = true;
                ci.cancel();
                return;
            }
            boolean groundedRampLandingFrame = this.isOnGround()
                    && jumpVelocity.y <= SURF_GROUND_SUPPRESS_ON_GROUND_MAX_VERTICAL
                    && jumpHorizontal >= SURF_GROUND_SUPPRESS_ON_GROUND_MIN_HORIZONTAL
                    && this.isReusableSurfContact(groundedLandingSupport);
            if (groundedRampLandingFrame) {
                this.surfGroundSuppressTicks = Math.max(
                        this.surfGroundSuppressTicks,
                        SURF_GROUND_SUPPRESS_ON_GROUND_TICKS
                );
                this.velocityDirty = true;
                ci.cancel();
                return;
            }
            if (jumpSurfLatched
                    && jumpHorizontal >= SURF_GROUND_SUPPRESS_MIN_HORIZONTAL
                    && (this.isOnGround() || jumpVelocity.y <= SURF_GROUND_SUPPRESS_ON_GROUND_MAX_VERTICAL)
                    && (jumpNearSurfSupport
                    || this.surfGroundSuppressTicks > 0
                    || this.surfContactGraceTicks > 0
                    || this.surfCollisionBypassGraceTicks > 0)) {
                this.velocityDirty = true;
                ci.cancel();
                return;
            }
        }

        SurfContact jumpSurfContact = jumpRealGroundBelow ? null : this.findSurfContact();
        if (jumpSurfContact == null && !this.isClimbing() && !jumpRealGroundBelow) {
            SurfContact jumpSupportContact = this.findJumpRampSupportContact(this.getBoundingBox(), this.getVelocity());
            if (this.isReusableSurfContact(jumpSupportContact)) {
                jumpSurfContact = jumpSupportContact;
            }
        }
        boolean jumpSurfGraceContact = !this.isClimbing()
                && !jumpRealGroundBelow
                && (this.isNearSurfRamp() || this.isReusableSurfContact(this.lastSurfContact))
                && this.isReusableSurfContact(this.lastSurfContact)
                && (this.surfContactGraceTicks > 0 || this.surfCollisionBypassGraceTicks > 0);
        if ((jumpSurfContact != null || jumpSurfGraceContact) && !this.isClimbing()) {
            this.velocityDirty = true;
            ci.cancel();
            return;
        }

        // Shared cooldown with the coyote buffer in travel(): if we already jumped within the last
        // few ticks, don't fire again (prevents the buffer + vanilla path double-jumping). Far below
        // the bhop air cycle, so legit consecutive bhops are unaffected.
        if (((Object) this) instanceof PlayerEntity && this.minehop$jumpCooldownTicks > 0) {
            this.velocityDirty = true;
            ci.cancel();
            return;
        }

        // Hard guard: by here all surf-ramp jumps are already cancelled, so the only legitimate
        // remaining jump is off real ground. Never let jump() apply an impulse in mid-air (e.g. if
        // vanilla calls jump() during a client/server onGround desync) — that would be the "walk off
        // a platform then jump in the air" bug. The coyote buffer in travel() is the only sanctioned
        // non-onGround jump and it requires ground within 0.20 below at the firing tick.
        if (((Object) this) instanceof PlayerEntity && !this.isOnGround() && !jumpRealGroundBelow) {
            this.velocityDirty = true;
            ci.cancel();
            return;
        }

        Vec3d vecFin = this.getVelocity();
        double yVel = config.movement.sv_jump_impulse * SOURCE_UNIT_TO_BLOCKS_PER_TICK;
        if (this.hasStatusEffect(StatusEffects.JUMP_BOOST)) {
            yVel += 0.1F * (this.getStatusEffect(StatusEffects.JUMP_BOOST).getAmplifier() + 1);
        }

        this.setVelocity(vecFin.x, yVel, vecFin.z);
        this.velocityDirty = true;
        if (((Object) this) instanceof PlayerEntity) {
            this.minehop$jumpCooldownTicks = SURF_JUMP_BUFFER_COOLDOWN;
            this.minehop$descendedSinceJump = false; // must descend/land again before the coyote buffer can fire
        }

        ci.cancel();
    }

    @Inject(method = "getStepHeight", at = @At("HEAD"), cancellable = true)
    private void minehop$applyAutoStepUp(CallbackInfoReturnable<Float> cir) {
        if (this.getType() != EntityType.PLAYER) {
            return;
        }

        MinehopConfig effectiveConfig = Minehop.override_config && Minehop.receivedConfig
                ? null
                : ConfigWrapper.getEffectiveConfig(this);
        boolean movementEnabled = Minehop.override_config && Minehop.receivedConfig
                ? Minehop.o_enabled
                : effectiveConfig != null && effectiveConfig.enabled;
        if (!movementEnabled) {
            return;
        }

        boolean autoStepUp = Minehop.override_config && Minehop.receivedConfig
                ? Minehop.o_auto_step_up
                : effectiveConfig != null && effectiveConfig.movement.auto_step_up;
        if (!autoStepUp) {
            return;
        }

        if (this.isClimbing() || this.isTouchingWater() || this.isInLava() || this.isGliding()) {
            return;
        }

        if (this.isNearSurfRamp()) {
            return;
        }

        if (this.surfContactGraceTicks > 0 || this.isReusableSurfContact(this.lastSurfContact)) {
            return;
        }

        cir.setReturnValue(1.12F);
    }

    @Unique
    private void minehop$tryForcedAutoStepUp(Vec3d attemptedMove, Vec3d posBeforeMove, SurfContact preMoveSurfContact) {
        if (!this.horizontalCollision) {
            return;
        }

        if (this.isClimbing() || this.isTouchingWater() || this.isInLava() || this.isGliding()) {
            return;
        }

        if (this.isNearSurfRamp()) {
            return;
        }

        if (this.surfContactGraceTicks > 0 || this.isReusableSurfContact(this.lastSurfContact) || this.isReusableSurfContact(preMoveSurfContact)) {
            return;
        }

        double attemptedHorizontalSq = attemptedMove.x * attemptedMove.x + attemptedMove.z * attemptedMove.z;
        if (attemptedHorizontalSq < 1.0E-6D) {
            return;
        }

        Vec3d moved = this.getPos().subtract(posBeforeMove);
        Vec3d remainder = new Vec3d(attemptedMove.x - moved.x, 0.0D, attemptedMove.z - moved.z);
        double remainderSq = remainder.x * remainder.x + remainder.z * remainder.z;
        if (remainderSq < 1.0E-8D) {
            double attemptedHorizontal = Math.sqrt(attemptedHorizontalSq);
            if (attemptedHorizontal < 1.0E-6D) {
                return;
            }

            double nudge = Math.min(0.35D, attemptedHorizontal);
            remainder = new Vec3d((attemptedMove.x / attemptedHorizontal) * nudge, 0.0D, (attemptedMove.z / attemptedHorizontal) * nudge);
            remainderSq = remainder.x * remainder.x + remainder.z * remainder.z;
            if (remainderSq < 1.0E-8D) {
                return;
            }
        }

        Vec3d attemptedHorizontal = new Vec3d(attemptedMove.x, 0.0D, attemptedMove.z);
        Vec3d halfRemainder = remainder.multiply(0.5D);
        Vec3d[] cornerCandidates = new Vec3d[]{
                remainder,
                new Vec3d(remainder.x, 0.0D, 0.0D),
                new Vec3d(0.0D, 0.0D, remainder.z),
                halfRemainder,
                attemptedHorizontal,
                new Vec3d(attemptedMove.x, 0.0D, 0.0D),
                new Vec3d(0.0D, 0.0D, attemptedMove.z)
        };

        Box currentBox = this.getBoundingBox();
        for (double stepHeight : AUTO_STEP_HEIGHT_CANDIDATES) {
            for (Vec3d horizontalOffset : cornerCandidates) {
                if (this.minehop$tryForcedStepCandidate(currentBox, horizontalOffset, stepHeight, attemptedMove)) {
                    return;
                }
            }
        }
    }

    @Unique
    private boolean minehop$tryForcedStepCandidate(Box currentBox, Vec3d horizontalOffset, double stepHeight, Vec3d attemptedMove) {
        double horizontalSq = horizontalOffset.x * horizontalOffset.x + horizontalOffset.z * horizontalOffset.z;
        if (horizontalSq < 1.0E-8D) {
            return false;
        }

        Box raisedBox = currentBox.offset(0.0D, stepHeight, 0.0D);
        if (!this.getWorld().isSpaceEmpty(this, raisedBox)) {
            return false;
        }

        Box finalBox = raisedBox.offset(horizontalOffset.x, 0.0D, horizontalOffset.z);
        if (!this.getWorld().isSpaceEmpty(this, finalBox)) {
            return false;
        }

        double supportY = this.minehop$findBestAutoStepSupportY(currentBox, finalBox);
        if (Double.isNaN(supportY)) {
            return false;
        }

        double exactStep = supportY - currentBox.minY;
        if (exactStep <= AUTO_STEP_SURFACE_EPSILON || exactStep > stepHeight + AUTO_STEP_SURFACE_EPSILON) {
            return false;
        }

        Box snappedFinalBox = currentBox.offset(horizontalOffset.x, exactStep, horizontalOffset.z);
        if (!this.getWorld().isSpaceEmpty(this, snappedFinalBox)) {
            return false;
        }

        this.setPosition(this.getX() + horizontalOffset.x, supportY, this.getZ() + horizontalOffset.z);

        Vec3d currentVelocity = this.getVelocity();
        this.setVelocity(attemptedMove.x, Math.max(currentVelocity.y, 0.0D), attemptedMove.z);
        this.horizontalCollision = false;
        this.velocityDirty = true;
        return true;
    }

    @Unique
    private double minehop$findBestAutoStepSupportY(Box currentBox, Box targetBox) {
        double minX = targetBox.minX + 1.0E-3D;
        double maxX = targetBox.maxX - 1.0E-3D;
        double minZ = targetBox.minZ + 1.0E-3D;
        double maxZ = targetBox.maxZ - 1.0E-3D;
        if (minX >= maxX || minZ >= maxZ) {
            return Double.NaN;
        }

        double minAllowedTop = currentBox.minY + AUTO_STEP_SURFACE_EPSILON;
        double maxAllowedTop = targetBox.minY + AUTO_STEP_SURFACE_EPSILON;
        int minY = MathHelper.floor(currentBox.minY - 1.0D);
        int maxY = MathHelper.floor(targetBox.minY + AUTO_STEP_SURFACE_EPSILON);

        double bestTop = Double.NEGATIVE_INFINITY;
        ShapeContext shapeContext = ShapeContext.of(this);
        for (int y = minY; y <= maxY; y++) {
            for (int x = MathHelper.floor(minX); x <= MathHelper.floor(maxX); x++) {
                for (int z = MathHelper.floor(minZ); z <= MathHelper.floor(maxZ); z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = this.getWorld().getBlockState(pos);
                    if (state.isAir()) {
                        continue;
                    }

                    VoxelShape shape = state.getCollisionShape(this.getWorld(), pos, shapeContext);
                    if (shape.isEmpty()) {
                        continue;
                    }

                    for (Box shapePart : shape.getBoundingBoxes()) {
                        double partMinX = pos.getX() + shapePart.minX;
                        double partMaxX = pos.getX() + shapePart.maxX;
                        double partMinZ = pos.getZ() + shapePart.minZ;
                        double partMaxZ = pos.getZ() + shapePart.maxZ;
                        if (partMaxX <= minX || partMinX >= maxX || partMaxZ <= minZ || partMinZ >= maxZ) {
                            continue;
                        }

                        double topY = pos.getY() + shapePart.maxY;
                        if (topY < minAllowedTop || topY > maxAllowedTop) {
                            continue;
                        }

                        if (topY > bestTop) {
                            bestTop = topY;
                        }
                    }
                }
            }
        }

        return bestTop == Double.NEGATIVE_INFINITY ? Double.NaN : bestTop;
    }

    private static boolean isFlying(PlayerEntity player) {
        return player != null && player.getAbilities().flying;
    }
}
