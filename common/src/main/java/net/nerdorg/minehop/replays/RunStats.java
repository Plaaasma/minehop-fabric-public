package net.nerdorg.minehop.replays;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec3;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.anticheat.stream.ClientTick;
import net.nerdorg.minehop.anticheat.stream.MovementValidator;
import net.nerdorg.minehop.config.ConfigWrapper;
import net.nerdorg.minehop.config.MinehopConfig;
import net.nerdorg.minehop.platform.Services;
import net.nerdorg.minehop.spectate.SpectateSessions;
import net.nerdorg.minehop.util.MovementUtil;
import net.nerdorg.minehop.util.StrafeStats;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * The jump stats recorded into replay frames and shown on spectators' HUDs: jump count, speed at the last take-off
 * and strafe efficiency. All of them are derived on the server from the client tick stream (the movement the server
 * accepted, see {@link ClientTick}), so a client can't put made-up values into its replay or its spectators' HUD and
 * every client version gets them.
 *
 * <p>Jump count and take-off speed: {@link MovementValidator#jumpCount}, the same rule as the client's own SSJ counter.
 *
 * <p>Efficiency is the client HUD's own computation (LivingEntityMixin#travel and {@link StrafeStats}) replayed per
 * client tick: on every air-accelerate tick (movement keys held, not two ticks on friction ground) the tick's gain is
 * the substepped air accelerate of the entering velocity (the previous tick's displacement) with the tick's keys and
 * its turn from the previous yaw to this one, and the optimum is the best gain over every turn of -90..90 degrees
 * (1-degree sweep); a tick counts when the player turned more than 0.05 degrees and moved. Efficiency is the mean
 * realized/optimal gain over a jump, latched on landing, reported like the HUD (the running value from the fourth
 * counted tick, else the last jump's). The client knows the substep count of each tick (6 or 7, from a 128 Hz budget);
 * the server keeps the same budget and checks it against the realized displacement. In free air the replay is the
 * client's computation exactly; collisions, a map speed cap or server-sent velocity within a tick make it approximate.
 * The sweep costs ~180 substep simulations per counted tick, so it only runs for players who are in a run, are being
 * spectated, or are logged for comparison ({@code -Dminehop.efflog}).
 *
 * <p>Clients used to be asked for their efficiency (a nonce'd request); clients up to 1.1.6 could only echo it and
 * recorded 0. Nothing is requested any more and any reply is dropped.
 */
public final class RunStats {
    private static final boolean LOG = Boolean.getBoolean("minehop.efflog");
    private static final double SOURCE_UNIT_TO_BLOCKS_PER_TICK = 1.0D / 800.0D;
    private static final double SOURCE_FRAME_TIME = 1.0D / 20.0D;
    private static final double SOURCE_SIM_TICKRATE = 128.0D;
    private static final double SOURCE_MAX_AIR_YAW_DELTA = 90.0D;

    public record Snapshot(int jumpCount, double lastJumpSpeed, double efficiency) {
        public static final Snapshot NONE = new Snapshot(0, 0.0D, 0.0D);
    }

    /** One player's strafe stats, replayed from their client ticks. */
    private static final class Strafe {
        final StrafeStats stats = new StrafeStats();
        boolean hasPrevious;
        Vec3 previousPos;
        /** Horizontal velocity entering the next tick: the previous tick's displacement. */
        Vec3 previousStep;
        float previousYaw;
        /** The client's on-ground flag at the end of the previous tick (its onGround() when the next tick starts). */
        boolean previousOnGround;
        /** Friction ground at the end of the previous tick, and of the one before (the client's two-tick ground rule). */
        boolean frictionGround1;
        boolean frictionGround2;
        /** The client's air-substep budget carried between air-accelerate ticks (6.4 substeps per tick). */
        double substepRemainder;
        /** Sneak held in the previous tick: the client crouches (and slows its keys) by the previous tick's input. */
        boolean previousShift;
    }

    private static final Map<UUID, Strafe> STRAFE = new HashMap<>();

    private RunStats() {
    }

    public static void register() {
        Services.EVENTS.onServerStopped(server -> STRAFE.clear());
    }

    /** The player's current stats. */
    public static Snapshot of(ServerPlayer player) {
        if (player == null) {
            return Snapshot.NONE;
        }
        Strafe strafe = STRAFE.get(player.getUUID());
        return new Snapshot(MovementValidator.jumpCount(player), MovementValidator.lastJumpSpeed(player),
                strafe == null ? 0.0D : efficiency(strafe.stats));
    }

    /** What the HUD shows: the running value mid-jump (from the fourth counted tick), else the last jump's. 0..100. */
    private static double efficiency(StrafeStats stats) {
        double efficiency = stats.measuredTicks >= 4 ? stats.liveEfficiency : stats.lastJumpEfficiency;
        return Double.isFinite(efficiency) ? Math.max(0.0D, Math.min(100.0D, efficiency)) : 0.0D;
    }

    public static void forget(ServerPlayer player) {
        if (player != null) {
            STRAFE.remove(player.getUUID());
        }
    }

    /**
     * A client's stats reply. Nothing is requested any more (efficiency is computed here), so every reply is dropped;
     * the channel stays registered for older clients.
     */
    public static void onReply(ServerPlayer player, double efficiency) {
    }

    private static boolean wanted(ServerPlayer player) {
        return LOG || Minehop.timerManager.containsKey(player.getScoreboardName())
                || !SpectateSessions.spectatorsOf(player).isEmpty();
    }

    /** One client tick of the player (ReplayEvents#onClientTick). */
    static void onClientTick(ServerPlayer player, ClientTick tick) {
        if (!wanted(player) || player.isSpectator()) {
            Strafe idle = STRAFE.get(player.getUUID());
            if (idle != null) {
                idle.hasPrevious = false;
            }
            return;
        }
        Strafe strafe = STRAFE.computeIfAbsent(player.getUUID(), uuid -> new Strafe());
        Vec3 pos = tick.position();
        boolean frictionGround = MovementValidator.frictionGround(player, pos);
        if (!strafe.hasPrevious || tick.discontinuity() || tick.awaitingTeleport()) {
            remember(strafe, tick, pos, null, frictionGround);
            return;
        }
        Vec3 step = pos.subtract(strafe.previousPos);
        // The client latches the jump's stats when a tick starts on the ground.
        if (strafe.previousOnGround && strafe.stats.measuredTicks > 0) {
            strafe.stats.latchAndReset();
        }
        Input input = tick.input();
        double sI = (input.left() ? 1.0D : 0.0D) - (input.right() ? 1.0D : 0.0D);
        double fI = (input.forward() ? 1.0D : 0.0D) - (input.backward() ? 1.0D : 0.0D);
        // Two ticks starting on friction ground are ground movement (LivingEntityMixin: wasServerGrounded && realGroundBelow).
        boolean groundMovement = strafe.frictionGround1 && strafe.frictionGround2;
        if ((sI != 0.0D || fI != 0.0D) && !groundMovement && strafe.previousStep != null && !player.getAbilities().flying) {
            airTick(player, strafe, tick, step, sI, fI);
        }
        remember(strafe, tick, pos, step, frictionGround);
    }

    private static void remember(Strafe strafe, ClientTick tick, Vec3 pos, Vec3 step, boolean frictionGround) {
        strafe.hasPrevious = true;
        strafe.previousPos = pos;
        strafe.previousStep = step;
        strafe.previousYaw = tick.yaw();
        strafe.previousOnGround = tick.onGround();
        strafe.previousShift = tick.input().shift();
        strafe.frictionGround2 = step == null ? frictionGround : strafe.frictionGround1;
        strafe.frictionGround1 = frictionGround;
    }

    private static void airTick(ServerPlayer player, Strafe strafe, ClientTick tick, Vec3 step, double sI, double fI) {
        MinehopConfig config = ConfigWrapper.getEffectiveConfig(player);
        // The client's movement keys are scaled while using an item and while crouching (LocalPlayer#aiStep). It
        // decides crouching before it reads this tick's keys, i.e. by the previous tick's sneak key.
        double impulse = 1.0D;
        if (player.isUsingItem() && !player.isPassenger()) {
            impulse *= 0.2D;
        }
        if (strafe.previousShift) {
            impulse *= player.getAttributeValue(Attributes.SNEAKING_SPEED);
        }
        sI *= impulse;
        fI *= impulse;
        double accel = config.movement.sv_airaccelerate;
        double baseWishSpeed = player.getAttributeValue(Attributes.MOVEMENT_SPEED) * config.movement.speed_mul;
        double cap = Math.max(config.movement.sv_maxairspeed * SOURCE_UNIT_TO_BLOCKS_PER_TICK
                * Math.max(config.movement.speed_coefficient, 0.0D), 0.0D);
        // The entering velocity, after vanilla's per-axis cutoff (LivingEntity#aiStep zeroes components below 0.003).
        Vec3 velocity = new Vec3(Math.abs(strafe.previousStep.x) < 0.003D ? 0.0D : strafe.previousStep.x, 0.0D,
                Math.abs(strafe.previousStep.z) < 0.003D ? 0.0D : strafe.previousStep.z);
        float startYaw = strafe.previousYaw;
        float endYaw = tick.yaw();

        // The tick's substep count: the client's 128 Hz budget, checked against the realized displacement (in free air
        // exactly one of 6 and 7 substeps reproduces it).
        double budget = SOURCE_SIM_TICKRATE * SOURCE_FRAME_TIME + strafe.substepRemainder;
        int predicted = Math.max(1, (int) Math.floor(budget + 1.0E-9D));
        double realized = Math.sqrt(step.x * step.x + step.z * step.z);
        double six = simAirStrafeSpeed(velocity, sI, fI, accel, baseWishSpeed, cap, startYaw, endYaw, 6);
        double seven = simAirStrafeSpeed(velocity, sI, fI, accel, baseWishSpeed, cap, startYaw, endYaw, 7);
        double tolerance = 1.0E-7D + realized * 1.0E-6D;
        double errorSix = Math.abs(six - realized);
        double errorSeven = Math.abs(seven - realized);
        int steps = predicted;
        if (errorSix <= tolerance && errorSeven > tolerance) {
            steps = 6;
        } else if (errorSeven <= tolerance && errorSix > tolerance) {
            steps = 7;
        }
        strafe.substepRemainder = Math.max(0.0D, Math.min(0.999D, budget - steps));
        double after = steps == 6 ? six : steps == 7 ? seven
                : simAirStrafeSpeed(velocity, sI, fI, accel, baseWishSpeed, cap, startYaw, endYaw, steps);

        double before = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
        double yawDelta = Math.abs(normalizeAngle(endYaw - startYaw));
        boolean measured = yawDelta > 0.05D && before > 1.0E-4D;
        double gain = after - before;
        boolean good = gain > 1.0E-5D;
        double effTick = 0.0D;
        double gaugeRatio = 0.0D;
        if (measured) {
            double best = before;
            double bestTurn = 0.0D;
            for (double d = -SOURCE_MAX_AIR_YAW_DELTA; d <= SOURCE_MAX_AIR_YAW_DELTA + 1.0E-6D; d += 1.0D) {
                double speed = simAirStrafeSpeed(velocity, sI, fI, accel, baseWishSpeed, cap, startYaw, (float) (startYaw + d), steps);
                if (speed > best) {
                    best = speed;
                    bestTurn = Math.abs(d);
                }
            }
            double optimalGain = best - before;
            effTick = optimalGain > 1.0E-6D ? Mth.clamp(gain / optimalGain, 0.0D, 1.0D) : 0.0D;
            gaugeRatio = bestTurn > 1.0E-3D ? Mth.clamp((yawDelta / bestTurn) * 100.0D, 0.0D, 200.0D)
                    : (yawDelta < 0.5D ? 100.0D : 200.0D);
        }
        int strafeSign = sI > 0.0D ? 1 : (sI < 0.0D ? -1 : 0);
        strafe.stats.recordTick(measured, good, effTick, gaugeRatio, strafeSign);
        if (LOG) {
            Vec3 pos = tick.position();
            Minehop.LOGGER.info(String.format(Locale.ROOT,
                    "[EFF] %s tick=%d pos=(%.4f,%.4f,%.4f) steps=%d%s measured=%b eff=%.4f live=%.3f last=%.3f n=%d hud=%.3f",
                    player.getScoreboardName(), tick.index(), pos.x, pos.y, pos.z, steps,
                    errorSix <= tolerance || errorSeven <= tolerance ? "" : "?", measured, effTick,
                    strafe.stats.liveEfficiency, strafe.stats.lastJumpEfficiency, strafe.stats.measuredTicks,
                    efficiency(strafe.stats)));
        }
    }

    /**
     * Horizontal speed after one tick of the client's substepped air accelerate (LivingEntityMixin
     * #minehop$simAirStrafeSpeed / #accelerateSource): {@code steps} substeps, each a Source air accelerate towards the
     * keys' direction at the yaw lerped from {@code startYaw} (turn clamped to 90 degrees) to {@code endYaw}.
     */
    public static double simAirStrafeSpeed(Vec3 horizontalVelocity, double sI, double fI, double accel, double baseWishSpeed,
                                    double airWishSpeedCap, float startYaw, float endYaw, int steps) {
        double subFrameFraction = 20.0D / SOURCE_SIM_TICKRATE;
        float yawDelta = Mth.wrapDegrees(endYaw - startYaw);
        if (yawDelta > SOURCE_MAX_AIR_YAW_DELTA) {
            yawDelta = (float) SOURCE_MAX_AIR_YAW_DELTA;
        } else if (yawDelta < -SOURCE_MAX_AIR_YAW_DELTA) {
            yawDelta = (float) -SOURCE_MAX_AIR_YAW_DELTA;
        }
        float effectiveStartYaw = endYaw - yawDelta;
        Vec3 hv = horizontalVelocity;
        for (int i = 0; i < steps && i < 4096; i++) {
            float frac = (float) ((i + 1.0D) / steps);
            float yawI = Mth.rotLerp(frac, effectiveStartYaw, endYaw);
            Vec3 wishVector = MovementUtil.movementInputToVelocity(new Vec3(sI, 0.0D, fI), 1.0F, yawI);
            double wvl = wishVector.horizontalDistance();
            if (wvl <= 0.0D) {
                continue;
            }
            Vec3 wishDir = new Vec3(wishVector.x / wvl, 0.0D, wishVector.z / wvl);
            double wishSpeed = baseWishSpeed * wvl;
            // accelerateSource(hv, wishDir, wishSpeed, cap, accel, 1.0, true, subFrameFraction)
            double cappedWishSpeed = Math.min(wishSpeed, airWishSpeedCap);
            double addSpeed = cappedWishSpeed - hv.dot(wishDir);
            if (addSpeed <= 0.0D) {
                continue;
            }
            double accelSpeed = accel * SOURCE_FRAME_TIME * subFrameFraction * wishSpeed;
            if (accelSpeed > addSpeed) {
                accelSpeed = addSpeed;
            }
            hv = hv.add(wishDir.scale(accelSpeed));
        }
        return Math.sqrt(hv.x * hv.x + hv.z * hv.z);
    }

    /**
     * DEV (ReplayStoreHarness): nanoseconds one counted tick's optimum sweep takes (181 turns of 6 substeps), with the
     * default movement settings at {@code speed} blocks/tick.
     */
    public static double sweepNanos(double speed, int iterations) {
        Vec3 velocity = new Vec3(speed, 0.0D, 0.0D);
        double cap = 30.0D * SOURCE_UNIT_TO_BLOCKS_PER_TICK * 0.9D;
        double sink = 0.0D;
        long start = System.nanoTime();
        for (int n = 0; n < iterations; n++) {
            for (double d = -SOURCE_MAX_AIR_YAW_DELTA; d <= SOURCE_MAX_AIR_YAW_DELTA + 1.0E-6D; d += 1.0D) {
                sink += simAirStrafeSpeed(velocity, 1.0D, 0.0D, 1000.0D, 0.1D * 3.25D, cap, (float) n, (float) (n + d), 6);
            }
        }
        long nanos = System.nanoTime() - start;
        return sink == 12345.0D ? -1.0D : nanos / (double) iterations;
    }

    private static double normalizeAngle(double angle) {
        angle = angle % 360;
        if (angle > 180) {
            angle -= 360;
        } else if (angle < -180) {
            angle += 360;
        }
        return angle;
    }
}
