package net.nerdorg.minehop.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public class RenderUtil {
    // 26.2 removed MultiBufferSource: world overlays submit custom geometry to the frame's SubmitNodeCollector, which
    // draws it later in the frame. The callback runs then, so the vectors handed in must not be changed afterwards.
    public static void drawLine(SubmitNodeCollector collector, PoseStack pPoseStack, Vector3f startPoint, Vector3f endPoint, int width, int alpha, int r, int g, int b) {
        collector.submitCustomGeometry(pPoseStack, ModRenderLayer.getLineOfWidth(width),
                (entry, vertexBuilder) -> drawLine(vertexBuilder, entry, startPoint, endPoint, width, alpha, r, g, b));
    }

    // Command-queue variant (entity renderers submit custom geometry with a MatrixStack.Entry since 1.21.9).
    public static void drawLine(VertexConsumer vertexBuilder, PoseStack.Pose entry, Vector3f startPoint, Vector3f endPoint, int width, int alpha, int r, int g, int b) {
        Matrix4f positionMatrix = entry.pose();

        // Line width is a per-vertex attribute since 1.21.11.
        vertexBuilder.addVertex(positionMatrix, startPoint.x(), startPoint.y(), startPoint.z())
                .setColor(r, g, b, alpha)
                .setNormal(1, 1, 1) // Adjusted normal for clarity
                .setLineWidth(width);

        vertexBuilder.addVertex(positionMatrix, endPoint.x(), endPoint.y(), endPoint.z())
                .setColor(r, g, b, alpha)
                .setNormal(1, 1, 1) // Adjusted normal for clarity
                .setLineWidth(width);
    }

    public static void drawCuboid(SubmitNodeCollector collector, PoseStack pPoseStack, Vector3f pointA, Vector3f pointB, int width, int alpha, int r, int g, int b) {
        collector.submitCustomGeometry(pPoseStack, ModRenderLayer.getLineOfWidth(width),
                (entry, vertexBuilder) -> drawCuboid(vertexBuilder, entry, pointA, pointB, width, alpha, r, g, b));
    }

    public static void drawCuboid(VertexConsumer vertexBuilder, PoseStack.Pose entry, Vector3f pointA, Vector3f pointB, int width, int alpha, int r, int g, int b) {
        Vector3f[] points = new Vector3f[8];

        points[0] = pointA;
        points[1] = new Vector3f(pointB.x, pointA.y, pointA.z);
        points[2] = new Vector3f(pointB.x, pointB.y, pointA.z);
        points[3] = new Vector3f(pointA.x, pointB.y, pointA.z);
        points[4] = new Vector3f(pointA.x, pointA.y, pointB.z);
        points[5] = new Vector3f(pointB.x, pointA.y, pointB.z);
        points[6] = pointB;
        points[7] = new Vector3f(pointA.x, pointB.y, pointB.z);

        int[] edges = {
                0, 1,  1, 2,  2, 3,  3, 0,  // Bottom face
                4, 5,  5, 6,  6, 7,  7, 4,  // Top face
                0, 4,  1, 5,  2, 6,  3, 7   // Side faces
        };

        for (int i = 0; i < edges.length; i += 2) {
            drawLine(vertexBuilder, entry, points[edges[i]], points[edges[i + 1]], width, alpha, r, g, b);
        }
    }

    /**
     * A pose stack whose top is {@code pose}: for drawing code written against a {@link PoseStack} that now runs in a
     * submitted custom-geometry callback (which hands over the submitted pose).
     */
    public static PoseStack poseStackOf(PoseStack.Pose pose) {
        PoseStack stack = new PoseStack();
        stack.last().set(pose);
        return stack;
    }
}
