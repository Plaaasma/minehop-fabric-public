package net.nerdorg.minehop.client;

import net.nerdorg.minehop.platform.ClientServices;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.nerdorg.minehop.render.ModRenderLayer;
import net.minecraft.world.phys.Vec3;
import net.nerdorg.minehop.render.RenderUtil;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Arrays;
import java.util.List;

/**
 * Draws the /spec ... path route, red at the start to green at the end. Segments behind the camera or farther than
 * {@link #MAX_DISTANCE} are skipped, far parts are drawn with fewer points (the step between drawn points grows
 * with distance) and without the wide glow line, and polylines are never joined across a teleport.
 */
public final class ReplayPathRenderer {
    private static final double Y_OFFSET = 0.06D;
    private static final double MAX_DISTANCE = 256.0D;
    /** Beyond this, consecutive points closer than distance / LOD_DIVISOR are merged. */
    private static final double LOD_START = 32.0D;
    private static final double LOD_DIVISOR = 48.0D;
    private static final double GLOW_DISTANCE = 64.0D;

    private ReplayPathRenderer() {
    }

    public static void register() {
        ClientServices.CLIENT.onWorldRenderAfterEntities(context -> {
            PoseStack matrices = context.matrixStack();
            SubmitNodeCollector consumers = context.submitNodeCollector();
            Camera camera = context.camera();
            if (matrices == null || consumers == null || camera == null) {
                return;
            }

            List<double[]> lines = ReplayPathState.lines();
            int total = ReplayPathState.pointCount();
            if (lines.isEmpty() || total < 2) {
                return;
            }

            Vec3 cameraPos = camera.position();
            Vector3fc look = camera.forwardVector();
            double cx = cameraPos.x;
            double cy = cameraPos.y;
            double cz = cameraPos.z;
            // 26.2: the segments are collected and submitted as one custom geometry (drawn later in the frame), in the
            // order they were drawn before: per segment the glow line, then the line.
            float[] segments = new float[64 * SEGMENT_FLOATS];
            int count = 0;
            int done = 0;
            for (double[] line : lines) {
                int n = line.length / 3;
                int from = 0;
                for (int i = 1; i < n; i++) {
                    double fx = line[from * 3] - cx, fy = line[from * 3 + 1] + Y_OFFSET - cy, fz = line[from * 3 + 2] - cz;
                    double tx = line[i * 3] - cx, ty = line[i * 3 + 1] + Y_OFFSET - cy, tz = line[i * 3 + 2] - cz;
                    double fromDist2 = fx * fx + fy * fy + fz * fz;
                    double toDist2 = tx * tx + ty * ty + tz * tz;
                    double nearDist = Math.sqrt(Math.min(fromDist2, toDist2));
                    if (i < n - 1 && nearDist > LOD_START) {
                        double sx = tx - fx, sy = ty - fy, sz = tz - fz;
                        double minStep = nearDist / LOD_DIVISOR;
                        if (sx * sx + sy * sy + sz * sz < minStep * minStep) {
                            continue; // merge into the next segment
                        }
                    }
                    boolean visible = nearDist <= MAX_DISTANCE
                            && !(fx * look.x() + fy * look.y() + fz * look.z() < -2.0D
                                && tx * look.x() + ty * look.y() + tz * look.z() < -2.0D);
                    if (visible) {
                        double progress = (double) (done + i) / (double) (total - 1);
                        int red = (int) Math.round(255.0D * (1.0D - progress));
                        int green = (int) Math.round(255.0D * progress);
                        if (nearDist <= GLOW_DISTANCE) {
                            segments = add(segments, count++, fx, fy, fz, tx, ty, tz, 7, 80, red, green);
                        }
                        segments = add(segments, count++, fx, fy, fz, tx, ty, tz, 3, 230, red, green);
                    }
                    from = i;
                }
                done += n;
            }
            if (count == 0) {
                return;
            }
            float[] drawn = segments;
            int drawnCount = count;
            consumers.submitCustomGeometry(matrices, ModRenderLayer.getLineOfWidth(3), (pose, consumer) -> {
                Vector3f start = new Vector3f();
                Vector3f end = new Vector3f();
                for (int s = 0; s < drawnCount; s++) {
                    int o = s * SEGMENT_FLOATS;
                    start.set(drawn[o], drawn[o + 1], drawn[o + 2]);
                    end.set(drawn[o + 3], drawn[o + 4], drawn[o + 5]);
                    RenderUtil.drawLine(consumer, pose, start, end, (int) drawn[o + 6], (int) drawn[o + 7],
                            (int) drawn[o + 8], (int) drawn[o + 9], 35);
                }
            });
        });
    }

    /** x0 y0 z0 x1 y1 z1 width alpha red green */
    private static final int SEGMENT_FLOATS = 10;

    private static float[] add(float[] segments, int index, double fx, double fy, double fz, double tx, double ty, double tz,
                               int width, int alpha, int red, int green) {
        int o = index * SEGMENT_FLOATS;
        if (o + SEGMENT_FLOATS > segments.length) {
            segments = Arrays.copyOf(segments, segments.length * 2);
        }
        segments[o] = (float) fx;
        segments[o + 1] = (float) fy;
        segments[o + 2] = (float) fz;
        segments[o + 3] = (float) tx;
        segments[o + 4] = (float) ty;
        segments[o + 5] = (float) tz;
        segments[o + 6] = width;
        segments[o + 7] = alpha;
        segments[o + 8] = red;
        segments[o + 9] = green;
        return segments;
    }
}
