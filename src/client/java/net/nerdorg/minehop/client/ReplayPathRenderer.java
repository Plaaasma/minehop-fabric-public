package net.nerdorg.minehop.client;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import net.nerdorg.minehop.render.RenderUtil;
import org.joml.Vector3f;

import java.util.List;

public final class ReplayPathRenderer {
    private static final double Y_OFFSET = 0.06D;

    private ReplayPathRenderer() {
    }

    public static void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            MatrixStack matrices = context.matrixStack();
            VertexConsumerProvider consumers = context.consumers();
            Camera camera = context.camera();
            if (matrices == null || consumers == null || camera == null) {
                return;
            }

            List<Vec3d> points = ReplayPathState.snapshot();
            if (points.size() < 2) {
                return;
            }

            Vec3d cameraPos = camera.getPos();
            matrices.push();
            for (int i = 1; i < points.size(); i++) {
                Vec3d from = points.get(i - 1).add(0.0D, Y_OFFSET, 0.0D);
                Vec3d to = points.get(i).add(0.0D, Y_OFFSET, 0.0D);
                if (from.squaredDistanceTo(to) < 1.0E-6D) {
                    continue;
                }

                double progress = points.size() <= 2 ? 1.0D : (double) (i - 1) / (double) (points.size() - 2);
                int red = (int) Math.round(255.0D * (1.0D - progress));
                int green = (int) Math.round(255.0D * progress);
                int blue = 35;

                drawLine(consumers, matrices, from, to, cameraPos, red, green, blue, 80, 7);
                drawLine(consumers, matrices, from, to, cameraPos, red, green, blue, 230, 3);
            }
            matrices.pop();
        });
    }

    private static void drawLine(
            VertexConsumerProvider consumers,
            MatrixStack matrices,
            Vec3d from,
            Vec3d to,
            Vec3d cameraPos,
            int red,
            int green,
            int blue,
            int alpha,
            int width
    ) {
        RenderUtil.drawLine(
                consumers,
                matrices,
                new Vector3f(
                        (float) (from.x - cameraPos.x),
                        (float) (from.y - cameraPos.y),
                        (float) (from.z - cameraPos.z)
                ),
                new Vector3f(
                        (float) (to.x - cameraPos.x),
                        (float) (to.y - cameraPos.y),
                        (float) (to.z - cameraPos.z)
                ),
                width,
                alpha,
                red,
                green,
                blue
        );
    }
}
