package net.nerdorg.minehop.entity.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.entity.custom.ResetEntity;
import net.nerdorg.minehop.render.RenderUtil;
import org.joml.Vector3f;

// 1.21.1: no render state; render() receives the entity directly.
public class ResetRenderer extends MobEntityRenderer<ResetEntity, ResetModel> {
    private static final Identifier TEXTURE = Identifier.of(Minehop.MOD_ID, "textures/entity/zone.png");

    public ResetRenderer(EntityRendererFactory.Context context) {
        super(context, new ResetModel(context.getPart(ModModelLayers.RESET_ENTITY)), 0.001f);
    }

    @Override
    public Identifier getTexture(ResetEntity entity) {
        return TEXTURE;
    }

    @Override
    public boolean shouldRender(ResetEntity mobEntity, Frustum frustum, double d, double e, double f) {
        return true;
    }

    @Override
    public void render(ResetEntity resetEntity, float yaw, float tickDelta, MatrixStack matrixStack, VertexConsumerProvider vertexConsumerProvider, int i) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player.isCreative()) {
            BlockPos corner1 = resetEntity.getCorner1();
            BlockPos corner2 = resetEntity.getCorner2();
            if (corner1 != null && corner2 != null) {
                Vec3d corner1Offset = new Vec3d(corner1.getX(), corner1.getY(), corner1.getZ()).subtract(resetEntity.getPos());
                Vec3d corner2Offset = new Vec3d(corner2.getX(), corner2.getY(), corner2.getZ()).subtract(resetEntity.getPos());
                RenderUtil.drawCuboid(vertexConsumerProvider, matrixStack, new Vector3f((float) corner1Offset.getX(), (float) corner1Offset.getY(), (float) corner1Offset.getZ()), new Vector3f((float) corner2Offset.getX(), (float) corner2Offset.getY(), (float) corner2Offset.getZ()), 1, 255, 140, 140, 140);
            }
        }
        super.render(resetEntity, yaw, tickDelta, matrixStack, vertexConsumerProvider, i);
    }
}
