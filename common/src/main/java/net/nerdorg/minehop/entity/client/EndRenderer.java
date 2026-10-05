package net.nerdorg.minehop.entity.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.entity.custom.EndEntity;
import net.nerdorg.minehop.render.ModRenderLayer;
import net.nerdorg.minehop.render.RenderUtil;
import org.joml.Vector3f;

public class EndRenderer extends MobRenderer<EndEntity, EndEntityRenderState, EndModel> {
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(Minehop.MOD_ID, "textures/entity/zone.png");

    public EndRenderer(EntityRendererProvider.Context context) {
        super(context, new EndModel(context.bakeLayer(ModModelLayers.START_ENTITY)), 0.001f);
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
    public void extractRenderState(EndEntity endEntity, EndEntityRenderState state, float tickDelta) {
        // 1.21.9+: entity position/light are taken from the render state.
        super.extractRenderState(endEntity, state, tickDelta);
        state.endEntity = endEntity;
    }


    @Override
    public void submit(EndEntityRenderState renderState, PoseStack matrixStack, SubmitNodeCollector queue, CameraRenderState cameraState) {
        BlockPos corner1 = renderState.endEntity.getCorner1();
        BlockPos corner2 = renderState.endEntity.getCorner2();
        if (corner1 != null && corner2 != null) {
            Vec3 corner1Offset = new Vec3(corner1.getX(), corner1.getY(), corner1.getZ()).subtract(renderState.endEntity.position());
            Vec3 corner2Offset = new Vec3(corner2.getX(), corner2.getY(), corner2.getZ()).subtract(renderState.endEntity.position());

            queue.submitCustomGeometry(matrixStack, ModRenderLayer.getLineOfWidth(1), (entry, consumer) -> RenderUtil.drawCuboid(consumer, entry, new Vector3f((float) corner1Offset.x(), (float) corner1Offset.y(), (float) corner1Offset.z()), new Vector3f((float) corner2Offset.x(), (float) corner2Offset.y(), (float) corner2Offset.z()), 1, 255, 255, 0, 0));
        }
        super.submit(renderState, matrixStack, queue, cameraState);
    }

    @Override
    public Identifier getTextureLocation(EndEntityRenderState state) {
        return TEXTURE;
    }
}
