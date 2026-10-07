package net.nerdorg.minehop.client;

import net.nerdorg.minehop.platform.ClientServices;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import net.nerdorg.minehop.render.RenderUtil;
import org.joml.Vector3f;
import com.mojang.blaze3d.vertex.PoseStack;
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
            MultiBufferSource consumers = context.consumers();
            Camera camera = context.camera();
            if (matrices == null || consumers == null || camera == null) {
                return;
            }

            List<double[]> lines = ReplayPathState.lines();
            int total = ReplayPathState.pointCount();
            if (lines.isEmpty() || total < 2) {
                return;
            }

            Vec3 cameraPos = camera.getPosition();
            Vector3f look = camera.getLookVector();
            double cx = cameraPos.x;
            double cy = cameraPos.y;
            double cz = cameraPos.z;
            matrices.pushPose();
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
                        Vector3f start = new Vector3f((float) fx, (float) fy, (float) fz);
                        Vector3f end = new Vector3f((float) tx, (float) ty, (float) tz);
                        if (nearDist <= GLOW_DISTANCE) {
                            RenderUtil.drawLine(consumers, matrices, start, end, 7, 80, red, green, 35);
                        }
                        RenderUtil.drawLine(consumers, matrices, start, end, 3, 230, red, green, 35);
                    }
                    from = i;
                }
                done += n;
            }
            matrices.popPose();
        });
    }
}
