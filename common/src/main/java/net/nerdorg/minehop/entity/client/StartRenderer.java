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

// 1.21.1: no render state; render() receives the entity directly.
public class StartRenderer extends MobRenderer<StartEntity, StartModel> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(Minehop.MOD_ID, "textures/entity/zone.png");

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

    @Override
    public void render(StartEntity startEntity, float entityYaw, float partialTicks, PoseStack matrixStack, MultiBufferSource vertexConsumerProvider, int i) {
        BlockPos corner1 = startEntity.getCorner1();
        BlockPos corner2 = startEntity.getCorner2();
        if (corner1 != null && corner2 != null) {
            Vec3 corner1Offset = new Vec3(corner1.getX(), corner1.getY(), corner1.getZ()).subtract(startEntity.position());
            Vec3 corner2Offset = new Vec3(corner2.getX(), corner2.getY(), corner2.getZ()).subtract(startEntity.position());
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
        super.render(startEntity, entityYaw, partialTicks, matrixStack, vertexConsumerProvider, i);
    }
}
