package net.nerdorg.minehop.client;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.nerdorg.minehop.entity.custom.SurfRampEntity;
import net.nerdorg.minehop.render.RenderUtil;
import org.joml.Vector3f;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;

public final class SurfStickPreviewRenderer {
    private static final double ONE_SIDED_BLOCK_FACE_CLEARANCE = 1.0D / 32.0D;
    private static final double HALF_BLOCK_EDGE_LENGTH = 0.5D;

    private SurfStickPreviewRenderer() {
    }

    public static void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            PoseStack matrices = context.matrixStack();
            MultiBufferSource consumers = context.consumers();
            Camera camera = context.camera();
            if (matrices == null || consumers == null || camera == null) {
                return;
            }

            SurfStickPreviewState.Snapshot snapshot = SurfStickPreviewState.snapshot();
            List<BlockPos> confirmedPoints = snapshot.points();
            if (confirmedPoints.isEmpty()) {
                return;
            }

            Vec3 cameraPos = camera.getPosition();
            matrices.pushPose();
            if (confirmedPoints.size() >= 2) {
                drawPath(
                        confirmedPoints,
                        snapshot.width(),
                        snapshot.drop(),
                        snapshot.oneSided(),
                        snapshot.outsideCurve(),
                        cameraPos,
                        consumers,
                        matrices,
                        210,
                        70,
                        255,
                        170
                );
            }

            Minecraft client = Minecraft.getInstance();
            if (client.hitResult instanceof BlockHitResult blockHitResult) {
                BlockPos lastPoint = confirmedPoints.get(confirmedPoints.size() - 1);
                BlockPos hovered = blockHitResult.getBlockPos();
                if (hovered != null && !hovered.equals(lastPoint)) {
                    List<BlockPos> candidate = new ArrayList<>(confirmedPoints.size() + 1);
                    candidate.addAll(confirmedPoints);
                    candidate.add(hovered.immutable());
                    drawPath(
                            candidate,
                            snapshot.width(),
                            snapshot.drop(),
                            snapshot.oneSided(),
                            snapshot.outsideCurve(),
                            cameraPos,
                            consumers,
                            matrices,
                            255,
                            190,
                            40,
                            210
                    );
                }
            }
            matrices.popPose();
        });
    }

    private static void drawPath(
            List<BlockPos> points,
            double width,
            double drop,
            boolean oneSided,
            boolean outsideCurve,
            Vec3 cameraPos,
            MultiBufferSource consumers,
            PoseStack matrices,
            int red,
            int green,
            int blue,
            int alpha
    ) {
        if (points == null || points.size() < 2) {
            return;
        }

        double clampedWidth = Math.max(0.15D, width);
        double clampedDrop = Math.max(0.1D, drop);
        List<Vec3> centerline = toCenterlinePoints(points);
        if (centerline.size() < 2) {
            return;
        }
        centerline = extendCenterlineToSelectedBlockEdges(centerline);
        if (oneSided) {
            int sideSign = resolvePreviewSideSign(centerline, outsideCurve);
            centerline = snapOneSidedCenterlineToBlockFaces(centerline, sideSign);
            drawContinuousOneSided(centerline, clampedWidth, clampedDrop, sideSign, cameraPos, consumers, matrices, red, green, blue, alpha);
        } else {
            drawContinuousDoubleSided(centerline, clampedWidth, clampedDrop, cameraPos, consumers, matrices, red, green, blue, alpha);
        }
    }

    private static void drawContinuousOneSided(
            List<Vec3> centerlinePoints,
            double width,
            double drop,
            int sideSign,
            Vec3 cameraPos,
            MultiBufferSource consumers,
            PoseStack matrices,
            int red,
            int green,
            int blue,
            int alpha
    ) {
        if (centerlinePoints == null || centerlinePoints.size() < 2) {
            return;
        }
        double horizontalLength = getHorizontalLength(centerlinePoints);
        int segments = Mth.clamp(
                (int) Math.ceil(Math.max(horizontalLength * 18.0D, centerlinePoints.size() * 18.0D)),
                40,
                420
        );
        int ribStride = Math.max(6, segments / 16);

        Vec3 previousTop = null;
        Vec3 previousOuter = null;
        Vec3 previousInner = null;

        for (int i = 0; i <= segments; i++) {
            double t = (double) i / (double) segments;
            Vec3 center = samplePathCenterline(centerlinePoints, t);
            Vec3 left = samplePathLeft(centerlinePoints, t);
            double baseY = center.y;
            double topY = baseY + drop;

            Vec3 top = new Vec3(center.x, topY, center.z);
            Vec3 outer = new Vec3(
                    center.x + left.x * width * sideSign,
                    baseY,
                    center.z + left.z * width * sideSign
            );
            Vec3 inner = new Vec3(center.x, baseY, center.z);

            if (previousTop != null) {
                drawLine(consumers, matrices, previousTop, top, cameraPos, red, green, blue, alpha, 2);
                drawLine(consumers, matrices, previousOuter, outer, cameraPos, red, green, blue, alpha, 2);
                drawLine(consumers, matrices, previousInner, inner, cameraPos, red, green, blue, alpha, 2);
            }

            if (i == 0 || i == segments || i % ribStride == 0) {
                drawLine(consumers, matrices, top, outer, cameraPos, red, green, blue, alpha, 1);
                drawLine(consumers, matrices, top, inner, cameraPos, red, green, blue, alpha, 1);
                drawLine(consumers, matrices, outer, inner, cameraPos, red, green, blue, alpha, 1);
            }

            previousTop = top;
            previousOuter = outer;
            previousInner = inner;
        }
    }

    private static void drawContinuousDoubleSided(
            List<Vec3> centerlinePoints,
            double width,
            double drop,
            Vec3 cameraPos,
            MultiBufferSource consumers,
            PoseStack matrices,
            int red,
            int green,
            int blue,
            int alpha
    ) {
        if (centerlinePoints == null || centerlinePoints.size() < 2) {
            return;
        }
        double horizontalLength = getHorizontalLength(centerlinePoints);
        int segments = Mth.clamp(
                (int) Math.ceil(Math.max(horizontalLength * 18.0D, centerlinePoints.size() * 18.0D)),
                40,
                420
        );
        int ribStride = Math.max(6, segments / 16);

        Vec3 previousRidge = null;
        Vec3 previousLeft = null;
        Vec3 previousRight = null;

        for (int i = 0; i <= segments; i++) {
            double t = (double) i / (double) segments;
            Vec3 center = samplePathCenterline(centerlinePoints, t);
            Vec3 left = samplePathLeft(centerlinePoints, t);
            double baseY = center.y;
            double topY = baseY + drop;

            Vec3 ridge = new Vec3(center.x, topY, center.z);
            Vec3 leftBase = new Vec3(center.x + left.x * width, baseY, center.z + left.z * width);
            Vec3 rightBase = new Vec3(center.x - left.x * width, baseY, center.z - left.z * width);

            if (previousRidge != null) {
                drawLine(consumers, matrices, previousRidge, ridge, cameraPos, red, green, blue, alpha, 2);
                drawLine(consumers, matrices, previousLeft, leftBase, cameraPos, red, green, blue, alpha, 2);
                drawLine(consumers, matrices, previousRight, rightBase, cameraPos, red, green, blue, alpha, 2);
            }

            if (i == 0 || i == segments || i % ribStride == 0) {
                drawLine(consumers, matrices, ridge, leftBase, cameraPos, red, green, blue, alpha, 1);
                drawLine(consumers, matrices, ridge, rightBase, cameraPos, red, green, blue, alpha, 1);
                drawLine(consumers, matrices, leftBase, rightBase, cameraPos, red, green, blue, alpha, 1);
            }

            previousRidge = ridge;
            previousLeft = leftBase;
            previousRight = rightBase;
        }
    }

    private static void drawLine(
            MultiBufferSource consumers,
            PoseStack matrices,
            Vec3 a,
            Vec3 b,
            Vec3 cameraPos,
            int red,
            int green,
            int blue,
            int alpha,
            int width
    ) {
        Vector3f from = new Vector3f(
                (float) (a.x - cameraPos.x),
                (float) (a.y - cameraPos.y),
                (float) (a.z - cameraPos.z)
        );
        Vector3f to = new Vector3f(
                (float) (b.x - cameraPos.x),
                (float) (b.y - cameraPos.y),
                (float) (b.z - cameraPos.z)
        );
        RenderUtil.drawLine(consumers, matrices, from, to, width, alpha, red, green, blue);
    }

    private static List<Vec3> toCenterlinePoints(List<BlockPos> points) {
        List<Vec3> centerline = new ArrayList<>();
        Vec3 previous = null;
        for (BlockPos point : points) {
            if (point == null) {
                continue;
            }
            Vec3 centered = SurfRampEntity.blockCenter(point);
            if (previous != null && centered.distanceToSqr(previous) < 1.0E-4D) {
                continue;
            }
            centerline.add(centered);
            previous = centered;
        }
        return centerline;
    }

    private static List<Vec3> extendCenterlineToSelectedBlockEdges(List<Vec3> points) {
        if (points == null || points.size() < 2) {
            return points == null ? List.of() : List.copyOf(points);
        }

        List<Vec3> extended = new ArrayList<>(points);
        Vec3 startDirection = horizontalDirection(extended.get(0), extended.get(1));
        if (startDirection.lengthSqr() > 1.0E-8D) {
            extended.set(0, extended.get(0).subtract(startDirection.scale(distanceToBlockEdge(startDirection))));
        }

        int lastIndex = extended.size() - 1;
        Vec3 endDirection = horizontalDirection(extended.get(lastIndex - 1), extended.get(lastIndex));
        if (endDirection.lengthSqr() > 1.0E-8D) {
            extended.set(lastIndex, extended.get(lastIndex).add(endDirection.scale(distanceToBlockEdge(endDirection))));
        }
        return List.copyOf(extended);
    }

    private static double distanceToBlockEdge(Vec3 direction) {
        if (direction == null) {
            return 0.0D;
        }
        double dominantAxis = Math.max(Math.abs(direction.x), Math.abs(direction.z));
        if (dominantAxis < 1.0E-8D) {
            return 0.0D;
        }
        return HALF_BLOCK_EDGE_LENGTH / dominantAxis;
    }

    private static Vec3 horizontalDirection(Vec3 from, Vec3 to) {
        if (from == null || to == null) {
            return Vec3.ZERO;
        }
        Vec3 delta = new Vec3(to.x - from.x, 0.0D, to.z - from.z);
        if (delta.lengthSqr() < 1.0E-8D) {
            return Vec3.ZERO;
        }
        return delta.normalize();
    }

    private static List<Vec3> snapOneSidedCenterlineToBlockFaces(List<Vec3> points, int sideSign) {
        if (points == null || points.size() < 2) {
            return points == null ? List.of() : List.copyOf(points);
        }

        int normalizedSideSign = sideSign >= 0 ? 1 : -1;
        List<Vec3> snapped = new ArrayList<>();
        Vec3 previousSnapped = null;
        for (int i = 0; i < points.size(); i++) {
            Vec3 point = points.get(i);
            Vec3 left = getPointLeft(points, i);
            Vec3 snappedPoint = point.add(left.scale((-0.5D + ONE_SIDED_BLOCK_FACE_CLEARANCE) * normalizedSideSign));
            if (previousSnapped == null || snappedPoint.distanceToSqr(previousSnapped) > 1.0E-4D) {
                snapped.add(snappedPoint);
                previousSnapped = snappedPoint;
            }
        }
        return List.copyOf(snapped);
    }

    private static Vec3 getPointLeft(List<Vec3> points, int index) {
        if (points == null || points.size() < 2) {
            return new Vec3(1.0D, 0.0D, 0.0D);
        }

        Vec3 previous = points.get(Math.max(0, index - 1));
        Vec3 next = points.get(Math.min(points.size() - 1, index + 1));
        Vec3 tangent = new Vec3(next.x - previous.x, 0.0D, next.z - previous.z);
        if (tangent.lengthSqr() < 1.0E-8D && index > 0) {
            Vec3 current = points.get(index);
            previous = points.get(index - 1);
            tangent = new Vec3(current.x - previous.x, 0.0D, current.z - previous.z);
        }
        if (tangent.lengthSqr() < 1.0E-8D && index < points.size() - 1) {
            Vec3 current = points.get(index);
            next = points.get(index + 1);
            tangent = new Vec3(next.x - current.x, 0.0D, next.z - current.z);
        }
        if (tangent.lengthSqr() < 1.0E-8D) {
            return new Vec3(1.0D, 0.0D, 0.0D);
        }

        tangent = tangent.normalize();
        return new Vec3(-tangent.z, 0.0D, tangent.x).normalize();
    }

    private static double getHorizontalLength(List<Vec3> points) {
        if (points == null || points.size() < 2) {
            return 0.0D;
        }
        double length = 0.0D;
        Vec3 previous = points.get(0);
        for (int i = 1; i < points.size(); i++) {
            Vec3 current = points.get(i);
            double dx = current.x - previous.x;
            double dz = current.z - previous.z;
            length += Math.sqrt(dx * dx + dz * dz);
            previous = current;
        }
        return length;
    }

    private static Vec3 samplePathCenterline(List<Vec3> points, double t) {
        double clampedT = Mth.clamp(t, 0.0D, 1.0D);
        if (points.size() == 2) {
            return points.get(0).lerp(points.get(1), clampedT);
        }
        int segmentCount = points.size() - 1;
        double scaled = clampedT * segmentCount;
        int segmentIndex = Mth.clamp((int) Math.floor(scaled), 0, segmentCount - 1);
        double localT = scaled - segmentIndex;

        Vec3 p0 = points.get(Math.max(segmentIndex - 1, 0));
        Vec3 p1 = points.get(segmentIndex);
        Vec3 p2 = points.get(segmentIndex + 1);
        Vec3 p3 = points.get(Math.min(segmentIndex + 2, points.size() - 1));
        return catmullRom(p0, p1, p2, p3, localT);
    }

    private static int resolvePreviewSideSign(List<Vec3> centerlinePoints, boolean outsideCurve) {
        int insideCurveSign;
        if (hasCurvedPath(centerlinePoints)) {
            insideCurveSign = resolvePathInsideCurveSideSign(centerlinePoints);
        } else {
            insideCurveSign = findPlayerSideSign(centerlinePoints);
        }
        return outsideCurve ? -insideCurveSign : insideCurveSign;
    }

    private static int findPlayerSideSign(List<Vec3> centerlinePoints) {
        if (centerlinePoints == null || centerlinePoints.size() < 2) {
            return 1;
        }
        Minecraft client = Minecraft.getInstance();
        Vec3 playerPos = client.player != null ? client.player.position() : Vec3.ZERO;

        Vec3 start = centerlinePoints.get(0);
        Vec3 end = centerlinePoints.get(centerlinePoints.size() - 1);
        Vec3 tangent = new Vec3(end.x - start.x, 0.0D, end.z - start.z);
        if (tangent.lengthSqr() < 1.0E-8D) {
            return 1;
        }
        tangent = tangent.normalize();
        Vec3 left = new Vec3(-tangent.z, 0.0D, tangent.x).normalize();
        Vec3 midpoint = start.add(end).scale(0.5D);
        Vec3 toPlayer = new Vec3(playerPos.x - midpoint.x, 0.0D, playerPos.z - midpoint.z);
        double lateral = toPlayer.dot(left);
        return lateral >= 0.0D ? 1 : -1;
    }

    private static boolean hasCurvedPath(List<Vec3> points) {
        if (points == null || points.size() < 3) {
            return false;
        }
        for (int i = 0; i < points.size() - 2; i++) {
            Vec3 a = points.get(i);
            Vec3 b = points.get(i + 1);
            Vec3 c = points.get(i + 2);
            double abx = b.x - a.x;
            double abz = b.z - a.z;
            double bcx = c.x - b.x;
            double bcz = c.z - b.z;
            double cross = abx * bcz - abz * bcx;
            if (Math.abs(cross) > 1.0E-4D) {
                return true;
            }
        }
        return false;
    }

    private static int resolvePathInsideCurveSideSign(List<Vec3> points) {
        if (points == null || points.size() < 3) {
            return 1;
        }
        double weightedCross = 0.0D;
        for (int i = 0; i < points.size() - 2; i++) {
            Vec3 a = points.get(i);
            Vec3 b = points.get(i + 1);
            Vec3 c = points.get(i + 2);
            double abx = b.x - a.x;
            double abz = b.z - a.z;
            double bcx = c.x - b.x;
            double bcz = c.z - b.z;
            weightedCross += abx * bcz - abz * bcx;
        }
        if (Math.abs(weightedCross) < 1.0E-6D) {
            return 1;
        }
        return weightedCross > 0.0D ? 1 : -1;
    }

    private static Vec3 samplePathLeft(List<Vec3> points, double t) {
        double dt = 1.0D / Math.max(64.0D, points.size() * 32.0D);
        double t0 = Math.max(0.0D, t - dt);
        double t1 = Math.min(1.0D, t + dt);
        Vec3 before = samplePathCenterline(points, t0);
        Vec3 after = samplePathCenterline(points, t1);
        Vec3 tangent = new Vec3(after.x - before.x, 0.0D, after.z - before.z);
        if (tangent.lengthSqr() < 1.0E-8D) {
            Vec3 start = points.get(0);
            Vec3 end = points.get(points.size() - 1);
            tangent = new Vec3(end.x - start.x, 0.0D, end.z - start.z);
        }
        if (tangent.lengthSqr() < 1.0E-8D) {
            return new Vec3(1.0D, 0.0D, 0.0D);
        }
        tangent = tangent.normalize();
        return new Vec3(-tangent.z, 0.0D, tangent.x).normalize();
    }

    private static Vec3 catmullRom(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, double t) {
        double t2 = t * t;
        double t3 = t2 * t;

        double x = 0.5D * (
                (2.0D * p1.x)
                        + (-p0.x + p2.x) * t
                        + (2.0D * p0.x - 5.0D * p1.x + 4.0D * p2.x - p3.x) * t2
                        + (-p0.x + 3.0D * p1.x - 3.0D * p2.x + p3.x) * t3
        );
        double y = 0.5D * (
                (2.0D * p1.y)
                        + (-p0.y + p2.y) * t
                        + (2.0D * p0.y - 5.0D * p1.y + 4.0D * p2.y - p3.y) * t2
                        + (-p0.y + 3.0D * p1.y - 3.0D * p2.y + p3.y) * t3
        );
        double z = 0.5D * (
                (2.0D * p1.z)
                        + (-p0.z + p2.z) * t
                        + (2.0D * p0.z - 5.0D * p1.z + 4.0D * p2.z - p3.z) * t2
                        + (-p0.z + 3.0D * p1.z - 3.0D * p2.z + p3.z) * t3
        );
        return new Vec3(x, y, z);
    }
}
