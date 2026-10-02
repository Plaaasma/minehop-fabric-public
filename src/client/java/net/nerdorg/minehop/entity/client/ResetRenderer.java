package net.nerdorg.minehop.entity.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.entity.custom.ResetEntity;
import net.nerdorg.minehop.render.ModRenderLayer;
import net.nerdorg.minehop.render.RenderUtil;
import org.joml.Vector3f;

public class ResetRenderer extends MobEntityRenderer<ResetEntity, ResetEntityRenderState, ResetModel> {
    private static final Identifier TEXTURE = Identifier.of(Minehop.MOD_ID, "textures/entity/zone.png");

    public ResetRenderer(EntityRendererFactory.Context context) {
        super(context, new ResetModel(context.getPart(ModModelLayers.RESET_ENTITY)), 0.001f);
    }

    @Override
    public Identifier getTexture(ResetEntityRenderState entity) {
        return TEXTURE;
    }

    @Override
    public boolean shouldRender(ResetEntity mobEntity, Frustum frustum, double d, double e, double f) {
        return true;
    }

    @Override
    public void updateRenderState(ResetEntity resetEntity, ResetEntityRenderState state, float tickDelta) {
        // 1.21.9+: entity position/light are taken from the render state.
        super.updateRenderState(resetEntity, state, tickDelta);
        state.resetEntity = resetEntity;
    }

    @Override
    public void render(ResetEntityRenderState renderState, MatrixStack matrixStack, OrderedRenderCommandQueue queue, CameraRenderState cameraState) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player.isCreative()) {
            BlockPos corner1 = renderState.resetEntity.getCorner1();
            BlockPos corner2 = renderState.resetEntity.getCorner2();
            if (corner1 != null && corner2 != null) {
                Vec3d corner1Offset = new Vec3d(corner1.getX(), corner1.getY(), corner1.getZ()).subtract(renderState.resetEntity.getEntityPos());
                Vec3d corner2Offset = new Vec3d(corner2.getX(), corner2.getY(), corner2.getZ()).subtract(renderState.resetEntity.getEntityPos());
                queue.submitCustom(matrixStack, ModRenderLayer.getLineOfWidth(1), (entry, consumer) -> RenderUtil.drawCuboid(consumer, entry, new Vector3f((float) corner1Offset.getX(), (float) corner1Offset.getY(), (float) corner1Offset.getZ()), new Vector3f((float) corner2Offset.getX(), (float) corner2Offset.getY(), (float) corner2Offset.getZ()), 1, 255, 140, 140, 140));
            }
        }
        super.render(renderState, matrixStack, queue, cameraState);
    }

    @Override
    public ResetEntityRenderState createRenderState() {
        return new ResetEntityRenderState();
    }
}
