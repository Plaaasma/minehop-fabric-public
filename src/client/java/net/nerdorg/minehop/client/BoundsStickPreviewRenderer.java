package net.nerdorg.minehop.client;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.nerdorg.minehop.item.ModItems;
import net.nerdorg.minehop.item.custom.BoundsStickItem;
import net.nerdorg.minehop.render.RenderUtil;
import org.joml.Vector3f;

public final class BoundsStickPreviewRenderer {
    private static final int PREVIEW_LINE_WIDTH = 2;
    private static final int PREVIEW_ALPHA = 220;
    private static final double PREVIEW_EPSILON = 0.002D;

    private BoundsStickPreviewRenderer() {
    }

    public static void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null || client.world == null) {
                return;
            }
            if (!isHoldingBoundsStick(client)) {
                return;
            }

            BoundsStickPreviewState.Snapshot snapshot = BoundsStickPreviewState.snapshot();
            BlockPos first = snapshot.first();
            if (first == null) {
                return;
            }

            BlockPos second = snapshot.second();
            if (second == null) {
                if (!(client.crosshairTarget instanceof BlockHitResult hitResult)) {
                    return;
                }
                BlockPos hovered = hitResult.getBlockPos();
                if (hovered == null) {
                    return;
                }
                BlockPos[] converted = BoundsStickItem.resolveBoundsCorners(first, hovered);
                first = converted[0];
                second = converted[1];
            }

            if (second == null) {
                return;
            }

            MatrixStack matrices = context.matrixStack();
            VertexConsumerProvider consumers = context.consumers();
            Camera camera = context.camera();
            if (matrices == null || consumers == null || camera == null) {
                return;
            }

            Vec3d cameraPos = camera.getPos();
            Vec3d minCorner = new Vec3d(first.getX() - PREVIEW_EPSILON, first.getY() - PREVIEW_EPSILON, first.getZ() - PREVIEW_EPSILON);
            Vec3d maxCorner = new Vec3d(second.getX() + PREVIEW_EPSILON, second.getY() + PREVIEW_EPSILON, second.getZ() + PREVIEW_EPSILON);

            Vector3f from = new Vector3f(
                    (float) (minCorner.x - cameraPos.x),
                    (float) (minCorner.y - cameraPos.y),
                    (float) (minCorner.z - cameraPos.z)
            );
            Vector3f to = new Vector3f(
                    (float) (maxCorner.x - cameraPos.x),
                    (float) (maxCorner.y - cameraPos.y),
                    (float) (maxCorner.z - cameraPos.z)
            );

            RenderUtil.drawCuboid(consumers, matrices, from, to, PREVIEW_LINE_WIDTH, PREVIEW_ALPHA, 255, 255, 255);
        });
    }

    private static boolean isHoldingBoundsStick(MinecraftClient client) {
        return client.player != null
                && (client.player.getMainHandStack().isOf(ModItems.BOUNDS_STICK)
                || client.player.getOffHandStack().isOf(ModItems.BOUNDS_STICK));
    }
}
