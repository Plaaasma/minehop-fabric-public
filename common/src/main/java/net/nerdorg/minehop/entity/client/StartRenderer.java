package net.nerdorg.minehop.entity.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.entity.custom.StartEntity;
import net.nerdorg.minehop.render.RenderUtil;
import org.joml.Vector3f;

public class StartRenderer extends MobRenderer<StartEntity, StartModel> {
    private static final ResourceLocation TEXTURE = new ResourceLocation(Minehop.MOD_ID, "textures/entity/zone.png");

    public StartRenderer(EntityRendererProvider.Context context) {
        super(context, new StartModel(context.bakeLayer(ModModelLayers.START_ENTITY)), 0.001f);
    }

    @Override
    public ResourceLocation getTextureLocation(StartEntity entity) {
        return TEXTURE;
    }

    @Override
    public boolean shouldRender(StartEntity mobEntity, Frustum frustum, double d, double e, double f) {
        return true;
    }

    public StartEntityRenderState createRenderState() {
        return new StartEntityRenderState();
    }

    public void extractRenderState(StartEntity startEntity, StartEntityRenderState state, float tickDelta) {
        state.startEntity = startEntity;
    }

    @Override
    public void render(StartEntity entity, float yaw, float tickDelta, PoseStack matrixStack, MultiBufferSource vertexConsumerProvider, int i) {
        StartEntityRenderState renderState = this.createRenderState();
        this.extractRenderState(entity, renderState, tickDelta);
        BlockPos corner1 = renderState.startEntity.getCorner1();
        BlockPos corner2 = renderState.startEntity.getCorner2();
        if (corner1 != null && corner2 != null) {
            Vec3 corner1Offset = new Vec3(corner1.getX(), corner1.getY(), corner1.getZ()).subtract(renderState.startEntity.position());
            Vec3 corner2Offset = new Vec3(corner2.getX(), corner2.getY(), corner2.getZ()).subtract(renderState.startEntity.position());
            RenderUtil.drawCuboid(
                    vertexConsumerProvider,
                    matrixStack,
                    new Vector3f((float) corner1Offset.x(),
                            (float) corner1Offset.y(),
                            (float) corner1Offset.z()),
                    new Vector3f((float) corner2Offset.x(),
                            (float) corner2Offset.y(),
                            (float) corner2Offset.z()),
                    1, 255, 0, 255, 0);
        }
        super.render(entity, yaw, tickDelta, matrixStack, vertexConsumerProvider, i);
    }
}
