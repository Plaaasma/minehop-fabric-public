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
import net.nerdorg.minehop.entity.custom.EndEntity;
import net.nerdorg.minehop.render.ModRenderLayer;
import net.nerdorg.minehop.render.RenderUtil;
import org.joml.Vector3f;

public class EndRenderer extends MobEntityRenderer<EndEntity, EndEntityRenderState, EndModel> {
    private static final Identifier TEXTURE = Identifier.of(Minehop.MOD_ID, "textures/entity/zone.png");

    public EndRenderer(EntityRendererFactory.Context context) {
        super(context, new EndModel(context.getPart(ModModelLayers.START_ENTITY)), 0.001f);
    }

    @Override
    public EndEntityRenderState createRenderState() {
        return new EndEntityRenderState();
    }

    @Override
    public boolean shouldRender(EndEntity entity, Frustum frustum, double x, double y, double z) {
        return true;
    }

    @Override
    public void updateRenderState(EndEntity endEntity, EndEntityRenderState state, float tickDelta) {
        // 1.21.9+: entity position/light are taken from the render state.
        super.updateRenderState(endEntity, state, tickDelta);
        state.endEntity = endEntity;
    }


    @Override
    public void render(EndEntityRenderState renderState, MatrixStack matrixStack, OrderedRenderCommandQueue queue, CameraRenderState cameraState) {
        BlockPos corner1 = renderState.endEntity.getCorner1();
        BlockPos corner2 = renderState.endEntity.getCorner2();
        if (corner1 != null && corner2 != null) {
            Vec3d corner1Offset = new Vec3d(corner1.getX(), corner1.getY(), corner1.getZ()).subtract(renderState.endEntity.getEntityPos());
            Vec3d corner2Offset = new Vec3d(corner2.getX(), corner2.getY(), corner2.getZ()).subtract(renderState.endEntity.getEntityPos());

            queue.submitCustom(matrixStack, ModRenderLayer.getLineOfWidth(1), (entry, consumer) -> RenderUtil.drawCuboid(consumer, entry, new Vector3f((float) corner1Offset.getX(), (float) corner1Offset.getY(), (float) corner1Offset.getZ()), new Vector3f((float) corner2Offset.getX(), (float) corner2Offset.getY(), (float) corner2Offset.getZ()), 1, 255, 255, 0, 0));
        }
        super.render(renderState, matrixStack, queue, cameraState);
    }

    @Override
    public Identifier getTexture(EndEntityRenderState state) {
        return TEXTURE;
    }
}
