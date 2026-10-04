package net.nerdorg.minehop.entity.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.entity.custom.ResetEntity;
import net.nerdorg.minehop.render.RenderUtil;
import org.joml.Vector3f;

public class ResetRenderer extends MobRenderer<ResetEntity, ResetEntityRenderState, ResetModel> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(Minehop.MOD_ID, "textures/entity/zone.png");

    public ResetRenderer(EntityRendererProvider.Context context) {
        super(context, new ResetModel(context.bakeLayer(ModModelLayers.RESET_ENTITY)), 0.001f);
    }

    @Override
    public ResourceLocation getTextureLocation(ResetEntityRenderState entity) {
        return TEXTURE;
    }

    @Override
    public boolean shouldRender(ResetEntity mobEntity, Frustum frustum, double d, double e, double f) {
        return true;
    }

    @Override
    public void extractRenderState(ResetEntity resetEntity, ResetEntityRenderState state, float tickDelta) {
        state.resetEntity = resetEntity;
    }

    @Override
    public void render(ResetEntityRenderState renderState, PoseStack matrixStack, MultiBufferSource vertexConsumerProvider, int i) {
        Minecraft client = Minecraft.getInstance();
        if (client.player.isCreative()) {
            BlockPos corner1 = renderState.resetEntity.getCorner1();
            BlockPos corner2 = renderState.resetEntity.getCorner2();
            if (corner1 != null && corner2 != null) {
                Vec3 corner1Offset = new Vec3(corner1.getX(), corner1.getY(), corner1.getZ()).subtract(renderState.resetEntity.position());
                Vec3 corner2Offset = new Vec3(corner2.getX(), corner2.getY(), corner2.getZ()).subtract(renderState.resetEntity.position());
                RenderUtil.drawCuboid(vertexConsumerProvider, matrixStack, new Vector3f((float) corner1Offset.x(), (float) corner1Offset.y(), (float) corner1Offset.z()), new Vector3f((float) corner2Offset.x(), (float) corner2Offset.y(), (float) corner2Offset.z()), 1, 255, 140, 140, 140);
            }
        }
        super.render(renderState, matrixStack, vertexConsumerProvider, i);
    }

    @Override
    public ResetEntityRenderState createRenderState() {
        return new ResetEntityRenderState();
    }
}
