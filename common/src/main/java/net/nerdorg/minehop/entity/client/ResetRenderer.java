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

// 1.21.1: no render state; render() receives the entity directly.
public class ResetRenderer extends MobRenderer<ResetEntity, ResetModel> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(Minehop.MOD_ID, "textures/entity/zone.png");

    public ResetRenderer(EntityRendererProvider.Context context) {
        super(context, new ResetModel(context.bakeLayer(ModModelLayers.RESET_ENTITY)), 0.001f);
    }

    @Override
    public ResourceLocation getTextureLocation(ResetEntity entity) {
        return TEXTURE;
    }

    @Override
    public boolean shouldRender(ResetEntity mobEntity, Frustum frustum, double d, double e, double f) {
        return true;
    }

    @Override
    public void render(ResetEntity resetEntity, float entityYaw, float partialTicks, PoseStack matrixStack, MultiBufferSource vertexConsumerProvider, int i) {
        Minecraft client = Minecraft.getInstance();
        if (client.player.isCreative()) {
            BlockPos corner1 = resetEntity.getCorner1();
            BlockPos corner2 = resetEntity.getCorner2();
            if (corner1 != null && corner2 != null) {
                Vec3 corner1Offset = new Vec3(corner1.getX(), corner1.getY(), corner1.getZ()).subtract(resetEntity.position());
                Vec3 corner2Offset = new Vec3(corner2.getX(), corner2.getY(), corner2.getZ()).subtract(resetEntity.position());
                RenderUtil.drawCuboid(vertexConsumerProvider, matrixStack, new Vector3f((float) corner1Offset.x(), (float) corner1Offset.y(), (float) corner1Offset.z()), new Vector3f((float) corner2Offset.x(), (float) corner2Offset.y(), (float) corner2Offset.z()), 1, 255, 140, 140, 140);
            }
        }
        super.render(resetEntity, entityYaw, partialTicks, matrixStack, vertexConsumerProvider, i);
    }

}
