package net.nerdorg.minehop.entity.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.MinehopClient;
import net.nerdorg.minehop.entity.custom.StartEntity;
import net.nerdorg.minehop.render.RenderUtil;
import org.joml.Vector3f;

public class StartRenderer extends MobEntityRenderer<StartEntity, StartModel> {
    private static final Identifier TEXTURE = new Identifier(Minehop.MOD_ID, "textures/entity/zone.png");

    public StartRenderer(EntityRendererFactory.Context context) {
        super(context, new StartModel(context.getPart(ModModelLayers.START_ENTITY)), 0.001f);
    }

    @Override
    public Identifier getTexture(StartEntity entity) {
        return TEXTURE;
    }

    @Override
    public boolean shouldRender(StartEntity mobEntity, Frustum frustum, double d, double e, double f) {
        return true;
    }

    @Override
    public void render(StartEntity startEntity, float f, float g, MatrixStack matrixStack, VertexConsumerProvider vertexConsumerProvider, int i) {
        MinecraftClient client = MinecraftClient.getInstance();
        BlockPos corner1 = startEntity.getCorner1();
        BlockPos corner2 = startEntity.getCorner2();
        if (corner1 != null && corner2 != null) {
            Box colliderBox = new Box(new Vec3d(corner1.getX(), corner1.getY(), corner1.getZ()), new Vec3d(corner2.getX(), corner2.getY(), corner2.getZ()));
            if (!client.player.isCreative() && !client.player.isSpectator() && (Minehop.groundedList.contains(client.player.getNameForScoreboard()))) {
                if (colliderBox.contains(client.player.getPos())) {
                    MinehopClient.startTime = System.nanoTime();
                    MinehopClient.lastSendTime = 0;
                }
            }

            Vec3d corner1Offset = new Vec3d(corner1.getX(), corner1.getY(), corner1.getZ()).subtract(startEntity.getPos());
            Vec3d corner2Offset = new Vec3d(corner2.getX(), corner2.getY(), corner2.getZ()).subtract(startEntity.getPos());
            RenderUtil.drawCuboid(vertexConsumerProvider, matrixStack, new Vector3f((float) corner1Offset.getX(), (float) corner1Offset.getY(), (float) corner1Offset.getZ()), new Vector3f((float) corner2Offset.getX(), (float) corner2Offset.getY(), (float) corner2Offset.getZ()), 10, 255, 0, 255, 0);
        }
        super.render(startEntity, f, g, matrixStack, vertexConsumerProvider, i);
    }
}
