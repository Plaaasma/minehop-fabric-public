package net.nerdorg.minehop.render;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.joml.Random;
import org.joml.Vector3f;

public class RenderUtil {
    public static void drawLine(VertexConsumerProvider pBuffer, MatrixStack pPoseStack, Vector3f startPoint, Vector3f endPoint, int width, int alpha, int r, int g, int b) {
        VertexConsumer vertexBuilder = pBuffer.getBuffer(ModRenderLayer.getLineOfWidth(width));
        Matrix4f positionMatrix = pPoseStack.peek().getPositionMatrix();

        vertexBuilder.vertex(positionMatrix, startPoint.x(), startPoint.y(), startPoint.z())
                .color(r, g, b, alpha)
                .normal(1, 1, 1) // Adjusted normal for clarity
                .next();

        vertexBuilder.vertex(positionMatrix, endPoint.x(), endPoint.y(), endPoint.z())
                .color(r, g, b, alpha)
                .normal(1, 1, 1) // Adjusted normal for clarity
                .next();
    }

    public static void drawCuboid(VertexConsumerProvider pBuffer, MatrixStack pPoseStack, Vector3f pointA, Vector3f pointB, int width, int alpha, int r, int g, int b) {
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
            drawLine(pBuffer, pPoseStack, points[edges[i]], points[edges[i + 1]], width, alpha, r, g, b);
        }
    }
}
