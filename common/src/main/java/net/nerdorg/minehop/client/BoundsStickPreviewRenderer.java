package net.nerdorg.minehop.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.nerdorg.minehop.platform.ClientServices;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
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
        ClientServices.CLIENT.onWorldRenderAfterEntities(context -> {
            Minecraft client = Minecraft.getInstance();
            if (client.player == null || client.level == null) {
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
                if (!(client.hitResult instanceof BlockHitResult hitResult)) {
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

            PoseStack matrices = context.matrixStack();
            MultiBufferSource consumers = context.consumers();
            Camera camera = context.camera();
            if (matrices == null || consumers == null || camera == null) {
                return;
            }

            Vec3 cameraPos = camera.position();
            Vec3 minCorner = new Vec3(first.getX() - PREVIEW_EPSILON, first.getY() - PREVIEW_EPSILON, first.getZ() - PREVIEW_EPSILON);
            Vec3 maxCorner = new Vec3(second.getX() + PREVIEW_EPSILON, second.getY() + PREVIEW_EPSILON, second.getZ() + PREVIEW_EPSILON);

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

    private static boolean isHoldingBoundsStick(Minecraft client) {
        return client.player != null
                && (client.player.getMainHandItem().is(ModItems.BOUNDS_STICK.get())
                || client.player.getOffhandItem().is(ModItems.BOUNDS_STICK.get()));
    }
}
