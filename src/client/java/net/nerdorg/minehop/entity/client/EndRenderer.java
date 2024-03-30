package net.nerdorg.minehop.entity.client;

import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.entity.custom.EndEntity;
import net.nerdorg.minehop.entity.custom.StartEntity;
import net.nerdorg.minehop.render.RenderUtil;
import org.joml.Vector3f;

public class EndRenderer extends MobEntityRenderer<EndEntity, EndModel> {
    private static final Identifier TEXTURE = new Identifier(Minehop.MOD_ID, "textures/entity/zone.png");

    public EndRenderer(EntityRendererFactory.Context context) {
        super(context, new EndModel(context.getPart(ModModelLayers.START_ENTITY)), 0.001f);
    }

    @Override
    public Identifier getTexture(EndEntity entity) {
        return TEXTURE;
    }

    @Override
    public void render(EndEntity endEntity, float f, float g, MatrixStack matrixStack, VertexConsumerProvider vertexConsumerProvider, int i) {
        BlockPos corner1 = endEntity.getCorner1();
        BlockPos corner2 = endEntity.getCorner2();
        if (corner1 != null && corner2 != null) {
            Vec3d corner1Offset = new Vec3d(corner1.getX(), corner1.getY(), corner1.getZ()).subtract(endEntity.getPos());
            Vec3d corner2Offset = new Vec3d(corner2.getX(), corner2.getY(), corner2.getZ()).subtract(endEntity.getPos());
            RenderUtil.drawCuboid(vertexConsumerProvider, matrixStack, new Vector3f((float) corner1Offset.getX(), (float) corner1Offset.getY(), (float) corner1Offset.getZ()), new Vector3f((float) corner2Offset.getX(), (float) corner2Offset.getY(), (float) corner2Offset.getZ()), 10, 255, 255, 0, 0);
        }
        super.render(endEntity, f, g, matrixStack, vertexConsumerProvider, i);
    }
}
