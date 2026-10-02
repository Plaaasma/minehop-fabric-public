package net.nerdorg.minehop.render;

import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public class RenderUtil {
    public static void drawLine(VertexConsumerProvider pBuffer, MatrixStack pPoseStack, Vector3f startPoint, Vector3f endPoint, int width, int alpha, int r, int g, int b) {
        drawLine(pBuffer.getBuffer(ModRenderLayer.getLineOfWidth(width)), pPoseStack.peek(), startPoint, endPoint, width, alpha, r, g, b);
    }

    // Command-queue variant (entity renderers submit custom geometry with a MatrixStack.Entry since 1.21.9).
    public static void drawLine(VertexConsumer vertexBuilder, MatrixStack.Entry entry, Vector3f startPoint, Vector3f endPoint, int width, int alpha, int r, int g, int b) {
        Matrix4f positionMatrix = entry.getPositionMatrix();

        // Line width is a per-vertex attribute since 1.21.11.
        vertexBuilder.vertex(positionMatrix, startPoint.x(), startPoint.y(), startPoint.z())
                .color(r, g, b, alpha)
                .normal(1, 1, 1) // Adjusted normal for clarity
                .lineWidth(width);

        vertexBuilder.vertex(positionMatrix, endPoint.x(), endPoint.y(), endPoint.z())
                .color(r, g, b, alpha)
                .normal(1, 1, 1) // Adjusted normal for clarity
                .lineWidth(width);
    }

    public static void drawCuboid(VertexConsumerProvider pBuffer, MatrixStack pPoseStack, Vector3f pointA, Vector3f pointB, int width, int alpha, int r, int g, int b) {
        drawCuboid(pBuffer.getBuffer(ModRenderLayer.getLineOfWidth(width)), pPoseStack.peek(), pointA, pointB, width, alpha, r, g, b);
    }

    public static void drawCuboid(VertexConsumer vertexBuilder, MatrixStack.Entry entry, Vector3f pointA, Vector3f pointB, int width, int alpha, int r, int g, int b) {
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
}
