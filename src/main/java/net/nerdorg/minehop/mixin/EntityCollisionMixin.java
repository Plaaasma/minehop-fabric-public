package net.nerdorg.minehop.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.entity.custom.SurfRampEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(Entity.class)
public abstract class EntityCollisionMixin {
    private static final int MAX_NEARBY_RAMPS_PER_COLLISION_QUERY = 8;
    private static final double SURF_APPROACH_LATERAL_EXTRA = 1.10D;
    private static final double SURF_APPROACH_CONTACT_VERTICAL_DISTANCE = 1.80D;
    private static final double SURF_APPROACH_SKIP_MAX_DESCENT_SPEED = 0.90D;
    private static final double SURF_APPROACH_SKIP_MIN_HORIZONTAL_SPEED = 0.045D;
    private static final double SURF_FAST_BYPASS_MIN_HORIZONTAL_SPEED = 0.22D;
    private static final double SURF_FAST_BYPASS_MIN_DESCENT_SPEED = -0.14D;
    private static final double SURF_FAST_BYPASS_MIN_DESCENT_SPEED_HIGH = -0.34D;
    private static final double SURF_FAST_BYPASS_HIGH_HORIZONTAL_SPEED = 0.55D;
    private static final double SURF_FAST_BYPASS_MAX_ASCENT_SPEED = 0.36D;
    private static final double SURF_FAST_BYPASS_MAX_ASCENT_SPEED_HIGH = 1.05D;
    private static final double SURF_FAST_BYPASS_HIGH_ASCENT_MIN_HORIZONTAL_SPEED = 0.80D;
    private static final double SURF_FORCE_COLLISION_MIN_DESCENT_SPEED = 0.45D;
    private static final double SURF_FORCE_COLLISION_MIN_HORIZONTAL_SPEED = 0.20D;
    private static final double SURF_FORCE_COLLISION_HIGH_SPEED_MIN_DESCENT = 0.35D;
    private static final double SURF_FORCE_COLLISION_HIGH_SPEED_MIN_HORIZONTAL = 1.40D;
    private static final double SURF_FORCE_COLLISION_HIGH_SPEED_MIN_TOTAL = 2.20D;
    private static final double SURF_FORCE_COLLISION_EXTREME_TOTAL_SPEED = 4.50D;
    private static final int PERF_REPORT_INTERVAL_TICKS = 1;
    private static final int PERF_TICK = 0;
    private static final int PERF_LAST_REPORTED_TICK = 1;
    private static final int PERF_CALLS = 2;
    private static final int PERF_RAMPS_PROCESSED = 3;
    private static final int PERF_SEGMENTS_TESTED = 4;
    private static final int PERF_SEGMENTS_INTERSECTED = 5;
    private static final int PERF_PATCHES_TESTED = 6;
    private static final int PERF_PATCHES_APPENDED = 7;
    private static final int PERF_SKIPPED_CALLS = 8;
    private static final int PERF_ELAPSED_NANOS = 9;
    private static final int PERF_FPS_CURRENT = 10;
    private static final int PERF_FRAME_NANOS_MAX = 11;
    private static final int PERF_SIZE = 12;
    private static final double PERF_LOG_COLLISION_MS_THRESHOLD = 0.50D;
    private static final double PERF_LOG_FRAME_MS_THRESHOLD = 5.00D;
    private static final int PERF_LOG_FPS_THRESHOLD = 300;
    private static final Map<Integer, long[]> SURF_COLLISION_PERF = new ConcurrentHashMap<>();

    @ModifyVariable(
            method = "adjustMovementForCollisions(Lnet/minecraft/entity/Entity;Lnet/minecraft/util/math/Vec3d;Lnet/minecraft/util/math/Box;Lnet/minecraft/world/World;Ljava/util/List;)Lnet/minecraft/util/math/Vec3d;",
            at = @At("HEAD"),
            argsOnly = true,
            index = 4
    )
    private static List<VoxelShape> minehop$appendRampCollisionShapes(
            List<VoxelShape> collisions,
            Entity entity,
            Vec3d movement,
            Box entityBoundingBox,
            World world
    ) {
        List<VoxelShape> base = collisions == null ? new ArrayList<>() : collisions;
        if (entity == null || world == null || entityBoundingBox == null) {
            return base;
        }
        if (!(entity instanceof PlayerEntity)) {
            return base;
        }
        if (Minehop.surfHullSolverEnabled) {
            return base;
        }
        PlayerEntity player = (PlayerEntity) entity;
        boolean forceRampCollision = minehop$shouldForceRampCollisions(entity, movement);
        boolean surfBypassMarked = Minehop.surfCollisionBypassEntities.contains(entity.getId());
        if (!forceRampCollision
                && surfBypassMarked
                && minehop$canFastBypassCollisionQuery(entity, movement)) {
            return base;
        }
        boolean clientSide = world.isClient;
        boolean shouldLogPerf = clientSide && Minehop.surfPerfLoggingEnabled;
        int currentFps = shouldLogPerf ? minehop$getClientFps() : -1;
        long currentFrameNanos = shouldLogPerf ? minehop$getClientFrameNanos() : -1L;
        long worldTick = world.getTime();
        Box rampQueryBox = movement == null
                ? entityBoundingBox.expand(0.15D)
                : entityBoundingBox.stretch(movement).expand(0.25D);

        long startNanos = System.nanoTime();
        List<SurfRampEntity> nearbyRamps = SurfRampEntity.collectNearbyRamps(world, rampQueryBox, 1.6D);
        if (nearbyRamps.isEmpty()) {
            return base;
        }
        nearbyRamps = minehop$limitRampsByProximity(nearbyRamps, rampQueryBox);
        if (!forceRampCollision
                && minehop$shouldSkipRampCollisionsOnSurfApproach(entity, movement, entityBoundingBox, nearbyRamps)) {
            int prewarmBudget = 4;
            for (SurfRampEntity ramp : nearbyRamps) {
                if (prewarmBudget <= 0) {
                    break;
                }
                if (ramp == null || !ramp.isAlive() || ramp.isRemoved()) {
                    continue;
                }
                ramp.prewarmSurfaceSearchCache();
                int built = ramp.prewarmCollisionShapeCache(rampQueryBox, Math.min(2, prewarmBudget));
                prewarmBudget -= built;
            }
            if (shouldLogPerf) {
                long elapsedNanos = System.nanoTime() - startNanos;
                minehop$recordSurfCollisionPerf(
                        player,
                        clientSide,
                        currentFps,
                        currentFrameNanos,
                        worldTick,
                        nearbyRamps.size(),
                        0,
                        0,
                        0,
                        0,
                        1,
                        elapsedNanos
                );
            }
            return base;
        }

        List<VoxelShape> merged = new ArrayList<>(base);
        int rampsProcessed = 0;
        int segmentsTested = 0;
        int segmentsIntersected = 0;
        int patchesTested = 0;
        int patchesAppended = 0;
        for (SurfRampEntity ramp : nearbyRamps) {
            if (ramp == null || !ramp.isAlive() || ramp.isRemoved()) {
                continue;
            }
            ramp.prewarmSurfaceSearchCache();
            rampsProcessed++;
            SurfRampEntity.CollisionAppendStats stats = ramp.appendCollisionShapes(rampQueryBox, merged);
            segmentsTested += stats.segmentsTested();
            segmentsIntersected += stats.segmentsIntersected();
            patchesTested += stats.patchesTested();
            patchesAppended += stats.patchesAppended();
        }
        if (shouldLogPerf) {
            long elapsedNanos = System.nanoTime() - startNanos;
            minehop$recordSurfCollisionPerf(
                    player,
                    clientSide,
                    currentFps,
                    currentFrameNanos,
                    worldTick,
                    rampsProcessed,
                    segmentsTested,
                    segmentsIntersected,
                    patchesTested,
                    patchesAppended,
                    0,
                    elapsedNanos
            );
        }
        return merged;
    }

    private static boolean minehop$shouldForceRampCollisions(Entity entity, Vec3d movement) {
        if (entity == null || movement == null || entity.isOnGround()) {
            return false;
        }
        double descentSpeed = -movement.y;
        double horizontalSpeed = movement.horizontalLength();
        if (descentSpeed >= SURF_FORCE_COLLISION_MIN_DESCENT_SPEED
                && horizontalSpeed >= SURF_FORCE_COLLISION_MIN_HORIZONTAL_SPEED) {
            return true;
        }
        if (descentSpeed >= SURF_FORCE_COLLISION_HIGH_SPEED_MIN_DESCENT
                && horizontalSpeed >= SURF_FORCE_COLLISION_HIGH_SPEED_MIN_HORIZONTAL
                && movement.length() >= SURF_FORCE_COLLISION_HIGH_SPEED_MIN_TOTAL) {
            return true;
        }
        return movement.length() >= SURF_FORCE_COLLISION_EXTREME_TOTAL_SPEED;
    }

    private static boolean minehop$canFastBypassCollisionQuery(Entity entity, Vec3d movement) {
        if (entity == null || movement == null || entity.isOnGround()) {
            return false;
        }
        double horizontalSpeed = movement.horizontalLength();
        if (horizontalSpeed < SURF_FAST_BYPASS_MIN_HORIZONTAL_SPEED) {
            return false;
        }
        double minDescent = horizontalSpeed >= SURF_FAST_BYPASS_HIGH_HORIZONTAL_SPEED
                ? SURF_FAST_BYPASS_MIN_DESCENT_SPEED_HIGH
                : SURF_FAST_BYPASS_MIN_DESCENT_SPEED;
        double maxAscent = horizontalSpeed >= SURF_FAST_BYPASS_HIGH_ASCENT_MIN_HORIZONTAL_SPEED
                ? SURF_FAST_BYPASS_MAX_ASCENT_SPEED_HIGH
                : SURF_FAST_BYPASS_MAX_ASCENT_SPEED;
        return movement.y >= minDescent
                && movement.y <= maxAscent;
    }

    private static boolean minehop$shouldSkipRampCollisionsOnSurfApproach(
            Entity entity,
            Vec3d movement,
            Box entityBoundingBox,
            List<SurfRampEntity> nearbyRamps
    ) {
        if (entity == null || movement == null || entityBoundingBox == null || nearbyRamps == null || nearbyRamps.isEmpty()) {
            return false;
        }
        if (entity.isOnGround() || movement.y >= -0.03D) {
            return false;
        }
        if (-movement.y > SURF_APPROACH_SKIP_MAX_DESCENT_SPEED) {
            return false;
        }
        if (movement.horizontalLength() < SURF_APPROACH_SKIP_MIN_HORIZONTAL_SPEED) {
            return false;
        }

        double currentX = entity.getX();
        double currentZ = entity.getZ();
        double currentFeetY = entityBoundingBox.minY;

        double predictedX = currentX + movement.x;
        double predictedZ = currentZ + movement.z;
        double predictedFeetY = currentFeetY + movement.y;
        double minFeetY = Math.min(currentFeetY, predictedFeetY) - SURF_APPROACH_CONTACT_VERTICAL_DISTANCE - 0.6D;
        double maxFeetY = Math.max(currentFeetY, predictedFeetY) + SURF_APPROACH_CONTACT_VERTICAL_DISTANCE + 0.6D;

        for (SurfRampEntity ramp : nearbyRamps) {
            if (ramp == null || !ramp.isAlive() || ramp.isRemoved()) {
                continue;
            }
            Box rampBounds = ramp.getBoundingBox();
            if (rampBounds.maxY < minFeetY || rampBounds.minY > maxFeetY) {
                continue;
            }

            boolean nearCurrent = minehop$isNearRampBoundsXZ(rampBounds, currentX, currentZ, SURF_APPROACH_LATERAL_EXTRA);
            boolean nearPredicted = minehop$isNearRampBoundsXZ(rampBounds, predictedX, predictedZ, SURF_APPROACH_LATERAL_EXTRA);
            if (!nearCurrent && !nearPredicted) {
                continue;
            }
            if (predictedFeetY <= rampBounds.maxY + SURF_APPROACH_CONTACT_VERTICAL_DISTANCE
                    && currentFeetY >= rampBounds.minY - SURF_APPROACH_CONTACT_VERTICAL_DISTANCE) {
                return true;
            }
        }
        return false;
    }

    private static boolean minehop$isNearRampBoundsXZ(Box bounds, double x, double z, double lateralExtra) {
        if (bounds == null) {
            return false;
        }
        double minX = bounds.minX - lateralExtra;
        double maxX = bounds.maxX + lateralExtra;
        double minZ = bounds.minZ - lateralExtra;
        double maxZ = bounds.maxZ + lateralExtra;
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }

    private static List<SurfRampEntity> minehop$limitRampsByProximity(List<SurfRampEntity> ramps, Box queryBox) {
        if (ramps == null || queryBox == null || ramps.size() <= MAX_NEARBY_RAMPS_PER_COLLISION_QUERY) {
            return ramps;
        }
        double centerX = (queryBox.minX + queryBox.maxX) * 0.5D;
        double centerZ = (queryBox.minZ + queryBox.maxZ) * 0.5D;
        List<SurfRampEntity> sorted = new ArrayList<>(ramps);
        sorted.sort((first, second) -> Double.compare(
                minehop$getRampDistanceSquaredXZ(first, centerX, centerZ),
                minehop$getRampDistanceSquaredXZ(second, centerX, centerZ)
        ));
        return new ArrayList<>(sorted.subList(0, MAX_NEARBY_RAMPS_PER_COLLISION_QUERY));
    }

    private static double minehop$getRampDistanceSquaredXZ(SurfRampEntity ramp, double x, double z) {
        if (ramp == null || !ramp.isAlive() || ramp.isRemoved()) {
            return Double.POSITIVE_INFINITY;
        }
        Box bounds = ramp.getBoundingBox();
        double dx = 0.0D;
        if (x < bounds.minX) {
            dx = bounds.minX - x;
        } else if (x > bounds.maxX) {
            dx = x - bounds.maxX;
        }
        double dz = 0.0D;
        if (z < bounds.minZ) {
            dz = bounds.minZ - z;
        } else if (z > bounds.maxZ) {
            dz = z - bounds.maxZ;
        }
        return dx * dx + dz * dz;
    }

    private static void minehop$recordSurfCollisionPerf(
            PlayerEntity player,
            boolean clientSide,
            int currentFps,
            long currentFrameNanos,
            long tick,
            int rampsProcessed,
            int segmentsTested,
            int segmentsIntersected,
            int patchesTested,
            int patchesAppended,
            int skippedCalls,
            long elapsedNanos
    ) {
        if (player == null) {
            return;
        }
        int accumulatorKey = (player.getId() << 1) | (clientSide ? 1 : 0);
        long[] accumulator = SURF_COLLISION_PERF.computeIfAbsent(accumulatorKey, id -> {
            long[] state = new long[PERF_SIZE];
            state[PERF_TICK] = Long.MIN_VALUE;
            state[PERF_LAST_REPORTED_TICK] = Long.MIN_VALUE / 4L;
            state[PERF_FPS_CURRENT] = -1L;
            state[PERF_FRAME_NANOS_MAX] = -1L;
            return state;
        });

        if (accumulator[PERF_TICK] != tick) {
            if (accumulator[PERF_TICK] >= 0
                    && accumulator[PERF_CALLS] > 0
                    && (accumulator[PERF_TICK] - accumulator[PERF_LAST_REPORTED_TICK]) >= PERF_REPORT_INTERVAL_TICKS) {
                String fpsPart = accumulator[PERF_FPS_CURRENT] >= 0L
                        ? Long.toString(accumulator[PERF_FPS_CURRENT])
                        : "n/a";
                String frameMsPart = accumulator[PERF_FRAME_NANOS_MAX] > 0L
                        ? String.format(Locale.ROOT, "%.2f", accumulator[PERF_FRAME_NANOS_MAX] / 1_000_000.0D)
                        : "n/a";
                double totalCollisionMs = accumulator[PERF_ELAPSED_NANOS] / 1_000_000.0D;
                double maxFrameMs = accumulator[PERF_FRAME_NANOS_MAX] > 0L
                        ? accumulator[PERF_FRAME_NANOS_MAX] / 1_000_000.0D
                        : -1.0D;
                boolean hasRampInteraction = accumulator[PERF_SEGMENTS_TESTED] > 0
                        || accumulator[PERF_PATCHES_TESTED] > 0
                        || accumulator[PERF_SKIPPED_CALLS] > 0;
                boolean collisionSpike = totalCollisionMs >= PERF_LOG_COLLISION_MS_THRESHOLD;
                boolean frameSpike = maxFrameMs >= PERF_LOG_FRAME_MS_THRESHOLD;
                boolean fpsSpike = accumulator[PERF_FPS_CURRENT] > 0 && accumulator[PERF_FPS_CURRENT] <= PERF_LOG_FPS_THRESHOLD;
                boolean shouldEmit = hasRampInteraction && (collisionSpike || frameSpike || fpsSpike);
                String message = String.format(
                        Locale.ROOT,
                        "SurfPerf c:%d r:%d s:%d/%d p:%d/%d skip:%d t:%.3fms fps:%s frame:%sms",
                        accumulator[PERF_CALLS],
                        accumulator[PERF_RAMPS_PROCESSED],
                        accumulator[PERF_SEGMENTS_INTERSECTED],
                        accumulator[PERF_SEGMENTS_TESTED],
                        accumulator[PERF_PATCHES_APPENDED],
                        accumulator[PERF_PATCHES_TESTED],
                        accumulator[PERF_SKIPPED_CALLS],
                        totalCollisionMs,
                        fpsPart,
                        frameMsPart
                );
                if (shouldEmit) {
                    Minehop.LOGGER.info(
                            "SurfPerf side={} player={} tick={} {}",
                            "client",
                            player.getNameForScoreboard(),
                            accumulator[PERF_TICK],
                            message
                    );
                    accumulator[PERF_LAST_REPORTED_TICK] = accumulator[PERF_TICK];
                }
            }
            accumulator[PERF_TICK] = tick;
            accumulator[PERF_CALLS] = 0L;
            accumulator[PERF_RAMPS_PROCESSED] = 0L;
            accumulator[PERF_SEGMENTS_TESTED] = 0L;
            accumulator[PERF_SEGMENTS_INTERSECTED] = 0L;
            accumulator[PERF_PATCHES_TESTED] = 0L;
            accumulator[PERF_PATCHES_APPENDED] = 0L;
            accumulator[PERF_SKIPPED_CALLS] = 0L;
            accumulator[PERF_ELAPSED_NANOS] = 0L;
            accumulator[PERF_FPS_CURRENT] = -1L;
            accumulator[PERF_FRAME_NANOS_MAX] = -1L;
        }

        accumulator[PERF_CALLS]++;
        accumulator[PERF_RAMPS_PROCESSED] += rampsProcessed;
        accumulator[PERF_SEGMENTS_TESTED] += segmentsTested;
        accumulator[PERF_SEGMENTS_INTERSECTED] += segmentsIntersected;
        accumulator[PERF_PATCHES_TESTED] += patchesTested;
        accumulator[PERF_PATCHES_APPENDED] += patchesAppended;
        accumulator[PERF_SKIPPED_CALLS] += skippedCalls;
        accumulator[PERF_ELAPSED_NANOS] += elapsedNanos;
        if (currentFps >= 0) {
            accumulator[PERF_FPS_CURRENT] = currentFps;
        }
        if (currentFrameNanos > 0L) {
            accumulator[PERF_FRAME_NANOS_MAX] = Math.max(accumulator[PERF_FRAME_NANOS_MAX], currentFrameNanos);
        }
    }

    private static int minehop$getClientFps() {
        int fps = Minehop.clientRenderFps;
        return fps > 0 ? fps : -1;
    }

    private static long minehop$getClientFrameNanos() {
        long frameNanos = Minehop.clientRenderFrameNanos;
        return frameNanos > 0L ? frameNanos : -1L;
    }
}
