package net.nerdorg.minehop.client;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.nerdorg.minehop.entity.custom.SurfRampEntity;
import net.nerdorg.minehop.render.RenderUtil;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

public final class SurfStickPreviewRenderer {
    private SurfStickPreviewRenderer() {
    }

    public static void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            MatrixStack matrices = context.matrixStack();
            VertexConsumerProvider consumers = context.consumers();
            Camera camera = context.camera();
            if (matrices == null || consumers == null || camera == null) {
                return;
            }

            SurfStickPreviewState.Snapshot snapshot = SurfStickPreviewState.snapshot();
            List<BlockPos> confirmedPoints = snapshot.points();
            if (confirmedPoints.isEmpty()) {
                return;
            }

            Vec3d cameraPos = camera.getPos();
            matrices.push();
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

            MinecraftClient client = MinecraftClient.getInstance();
            if (client.crosshairTarget instanceof BlockHitResult blockHitResult) {
                BlockPos lastPoint = confirmedPoints.get(confirmedPoints.size() - 1);
                BlockPos hovered = blockHitResult.getBlockPos();
                if (hovered != null && !hovered.equals(lastPoint)) {
                    List<BlockPos> candidate = new ArrayList<>(confirmedPoints.size() + 1);
                    candidate.addAll(confirmedPoints);
                    candidate.add(hovered.toImmutable());
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
            matrices.pop();
        });
    }

    private static void drawPath(
            List<BlockPos> points,
            double width,
            double drop,
            boolean oneSided,
            boolean outsideCurve,
            Vec3d cameraPos,
            VertexConsumerProvider consumers,
            MatrixStack matrices,
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
        List<Vec3d> centerline = toCenterlinePoints(points);
        if (centerline.size() < 2) {
            return;
        }
        if (oneSided) {
            int sideSign = resolvePreviewSideSign(centerline, outsideCurve);
            drawContinuousOneSided(centerline, clampedWidth, clampedDrop, sideSign, cameraPos, consumers, matrices, red, green, blue, alpha);
        } else {
            drawContinuousDoubleSided(centerline, clampedWidth, clampedDrop, cameraPos, consumers, matrices, red, green, blue, alpha);
        }
    }

    private static void drawContinuousOneSided(
            List<Vec3d> centerlinePoints,
            double width,
            double drop,
            int sideSign,
            Vec3d cameraPos,
            VertexConsumerProvider consumers,
            MatrixStack matrices,
            int red,
            int green,
            int blue,
            int alpha
    ) {
        if (centerlinePoints == null || centerlinePoints.size() < 2) {
            return;
        }
        double horizontalLength = getHorizontalLength(centerlinePoints);
        int segments = MathHelper.clamp(
                (int) Math.ceil(Math.max(horizontalLength * 18.0D, centerlinePoints.size() * 18.0D)),
                40,
                420
        );
        int ribStride = Math.max(6, segments / 16);

        Vec3d previousTop = null;
        Vec3d previousOuter = null;
        Vec3d previousInner = null;

        for (int i = 0; i <= segments; i++) {
            double t = (double) i / (double) segments;
            Vec3d center = samplePathCenterline(centerlinePoints, t);
            Vec3d left = samplePathLeft(centerlinePoints, t);
            double baseY = center.y;
            double topY = baseY + drop;

            Vec3d top = new Vec3d(center.x, topY, center.z);
            Vec3d outer = new Vec3d(
                    center.x + left.x * width * sideSign,
                    baseY,
                    center.z + left.z * width * sideSign
            );
            Vec3d inner = new Vec3d(center.x, baseY, center.z);

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
            List<Vec3d> centerlinePoints,
            double width,
            double drop,
            Vec3d cameraPos,
            VertexConsumerProvider consumers,
            MatrixStack matrices,
            int red,
            int green,
            int blue,
            int alpha
    ) {
        if (centerlinePoints == null || centerlinePoints.size() < 2) {
            return;
        }
        double horizontalLength = getHorizontalLength(centerlinePoints);
        int segments = MathHelper.clamp(
                (int) Math.ceil(Math.max(horizontalLength * 18.0D, centerlinePoints.size() * 18.0D)),
                40,
                420
        );
        int ribStride = Math.max(6, segments / 16);

        Vec3d previousRidge = null;
        Vec3d previousLeft = null;
        Vec3d previousRight = null;

        for (int i = 0; i <= segments; i++) {
            double t = (double) i / (double) segments;
            Vec3d center = samplePathCenterline(centerlinePoints, t);
            Vec3d left = samplePathLeft(centerlinePoints, t);
            double baseY = center.y;
            double topY = baseY + drop;

            Vec3d ridge = new Vec3d(center.x, topY, center.z);
            Vec3d leftBase = new Vec3d(center.x + left.x * width, baseY, center.z + left.z * width);
            Vec3d rightBase = new Vec3d(center.x - left.x * width, baseY, center.z - left.z * width);

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
            VertexConsumerProvider consumers,
            MatrixStack matrices,
            Vec3d a,
            Vec3d b,
            Vec3d cameraPos,
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

    private static List<Vec3d> toCenterlinePoints(List<BlockPos> points) {
        List<Vec3d> centerline = new ArrayList<>();
        Vec3d previous = null;
        for (BlockPos point : points) {
            if (point == null) {
                continue;
            }
            Vec3d centered = SurfRampEntity.blockCenter(point);
            if (previous != null && centered.squaredDistanceTo(previous) < 1.0E-4D) {
                continue;
            }
            centerline.add(centered);
            previous = centered;
        }
        return centerline;
    }

    private static double getHorizontalLength(List<Vec3d> points) {
        if (points == null || points.size() < 2) {
            return 0.0D;
        }
        double length = 0.0D;
        Vec3d previous = points.get(0);
        for (int i = 1; i < points.size(); i++) {
            Vec3d current = points.get(i);
            double dx = current.x - previous.x;
            double dz = current.z - previous.z;
            length += Math.sqrt(dx * dx + dz * dz);
            previous = current;
        }
        return length;
    }

    private static Vec3d samplePathCenterline(List<Vec3d> points, double t) {
        double clampedT = MathHelper.clamp(t, 0.0D, 1.0D);
        if (points.size() == 2) {
            return points.get(0).lerp(points.get(1), clampedT);
        }
        int segmentCount = points.size() - 1;
        double scaled = clampedT * segmentCount;
        int segmentIndex = MathHelper.clamp((int) Math.floor(scaled), 0, segmentCount - 1);
        double localT = scaled - segmentIndex;

        Vec3d p0 = points.get(Math.max(segmentIndex - 1, 0));
        Vec3d p1 = points.get(segmentIndex);
        Vec3d p2 = points.get(segmentIndex + 1);
        Vec3d p3 = points.get(Math.min(segmentIndex + 2, points.size() - 1));
        return catmullRom(p0, p1, p2, p3, localT);
    }

    private static int resolvePreviewSideSign(List<Vec3d> centerlinePoints, boolean outsideCurve) {
        int insideCurveSign;
        if (hasCurvedPath(centerlinePoints)) {
            insideCurveSign = resolvePathInsideCurveSideSign(centerlinePoints);
        } else {
            insideCurveSign = findPlayerSideSign(centerlinePoints);
        }
        return outsideCurve ? -insideCurveSign : insideCurveSign;
    }

    private static int findPlayerSideSign(List<Vec3d> centerlinePoints) {
        if (centerlinePoints == null || centerlinePoints.size() < 2) {
            return 1;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        Vec3d playerPos = client.player != null ? client.player.getPos() : Vec3d.ZERO;

        Vec3d start = centerlinePoints.get(0);
        Vec3d end = centerlinePoints.get(centerlinePoints.size() - 1);
        Vec3d tangent = new Vec3d(end.x - start.x, 0.0D, end.z - start.z);
        if (tangent.lengthSquared() < 1.0E-8D) {
            return 1;
        }
        tangent = tangent.normalize();
        Vec3d left = new Vec3d(-tangent.z, 0.0D, tangent.x).normalize();
        Vec3d midpoint = start.add(end).multiply(0.5D);
        Vec3d toPlayer = new Vec3d(playerPos.x - midpoint.x, 0.0D, playerPos.z - midpoint.z);
        double lateral = toPlayer.dotProduct(left);
        return lateral >= 0.0D ? 1 : -1;
    }

    private static boolean hasCurvedPath(List<Vec3d> points) {
        if (points == null || points.size() < 3) {
            return false;
        }
        for (int i = 0; i < points.size() - 2; i++) {
            Vec3d a = points.get(i);
            Vec3d b = points.get(i + 1);
            Vec3d c = points.get(i + 2);
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

    private static int resolvePathInsideCurveSideSign(List<Vec3d> points) {
        if (points == null || points.size() < 3) {
            return 1;
        }
        double weightedCross = 0.0D;
        for (int i = 0; i < points.size() - 2; i++) {
            Vec3d a = points.get(i);
            Vec3d b = points.get(i + 1);
            Vec3d c = points.get(i + 2);
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

    private static Vec3d samplePathLeft(List<Vec3d> points, double t) {
        double dt = 1.0D / Math.max(64.0D, points.size() * 32.0D);
        double t0 = Math.max(0.0D, t - dt);
        double t1 = Math.min(1.0D, t + dt);
        Vec3d before = samplePathCenterline(points, t0);
        Vec3d after = samplePathCenterline(points, t1);
        Vec3d tangent = new Vec3d(after.x - before.x, 0.0D, after.z - before.z);
        if (tangent.lengthSquared() < 1.0E-8D) {
            Vec3d start = points.get(0);
            Vec3d end = points.get(points.size() - 1);
            tangent = new Vec3d(end.x - start.x, 0.0D, end.z - start.z);
        }
        if (tangent.lengthSquared() < 1.0E-8D) {
            return new Vec3d(1.0D, 0.0D, 0.0D);
        }
        tangent = tangent.normalize();
        return new Vec3d(-tangent.z, 0.0D, tangent.x).normalize();
    }

    private static Vec3d catmullRom(Vec3d p0, Vec3d p1, Vec3d p2, Vec3d p3, double t) {
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
        return new Vec3d(x, y, z);
    }
}
