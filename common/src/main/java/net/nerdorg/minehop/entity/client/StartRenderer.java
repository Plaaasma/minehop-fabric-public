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
import net.nerdorg.minehop.entity.custom.StartEntity;
import net.nerdorg.minehop.render.ModRenderLayer;
import net.nerdorg.minehop.render.RenderUtil;
import org.joml.Vector3f;

public class StartRenderer extends MobRenderer<StartEntity, StartEntityRenderState, StartModel> {
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(Minehop.MOD_ID, "textures/entity/zone.png");

    public StartRenderer(EntityRendererProvider.Context context) {
        super(context, new StartModel(context.bakeLayer(ModModelLayers.START_ENTITY)), 0.001f);
    }

    @Override
    public Identifier getTextureLocation(StartEntityRenderState state) {
        return TEXTURE;
    }

    @Override
    public boolean shouldRender(StartEntity mobEntity, Frustum frustum, double d, double e, double f) {
        return true;
    }

    @Override
    public StartEntityRenderState createRenderState() {
        return new StartEntityRenderState();
    }

    @Override
    public void extractRenderState(StartEntity startEntity, StartEntityRenderState state, float tickDelta) {
        // 1.21.9+: entity position/light are taken from the render state.
        super.extractRenderState(startEntity, state, tickDelta);
        state.startEntity = startEntity;
    }

    @Override
    public void submit(StartEntityRenderState renderState, PoseStack matrixStack, SubmitNodeCollector queue, CameraRenderState cameraState) {
        BlockPos corner1 = renderState.startEntity.getCorner1();
        BlockPos corner2 = renderState.startEntity.getCorner2();
        if (corner1 != null && corner2 != null) {
            Vec3 corner1Offset = new Vec3(corner1.getX(), corner1.getY(), corner1.getZ()).subtract(renderState.startEntity.position());
            Vec3 corner2Offset = new Vec3(corner2.getX(), corner2.getY(), corner2.getZ()).subtract(renderState.startEntity.position());
            queue.submitCustomGeometry(matrixStack, ModRenderLayer.getLineOfWidth(1), (entry, consumer) -> RenderUtil.drawCuboid(
                    consumer,
                    entry,
                    new Vector3f((float) corner1Offset.x(),
                            (float) corner1Offset.y(),
                            (float) corner1Offset.z()),
                    new Vector3f((float) corner2Offset.x(),
                            (float) corner2Offset.y(),
                            (float) corner2Offset.z()),
                    1, 255, 0, 255, 0));
        }
        super.submit(renderState, matrixStack, queue, cameraState);
    }
}
