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
import net.nerdorg.minehop.entity.custom.EndEntity;
import net.nerdorg.minehop.networking.ClientPacketHandler;
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
        state.endEntity = endEntity;
    }


    @Override
    public void render(EndEntityRenderState renderState, MatrixStack matrixStack, VertexConsumerProvider vertexConsumerProvider, int i) {
        float time = (((float) System.nanoTime() - (float) MinehopClient.startTime) / 1000000000f);
        MinecraftClient client = MinecraftClient.getInstance();

        if (MinehopClient.startTime != 0) {
            ClientPacketHandler.sendCurrentTime(time);
        }


        BlockPos corner1 = renderState.endEntity.getCorner1();
        BlockPos corner2 = renderState.endEntity.getCorner2();
        if (corner1 != null && corner2 != null) {
            Box colliderBox = new Box(new Vec3d(corner1.getX(), corner1.getY(), corner1.getZ()), new Vec3d(corner2.getX(), corner2.getY(), corner2.getZ()));
            if (!client.player.isCreative() && !client.player.isSpectator()) {
                final float partialTicks = client.getRenderTickCounter().getTickProgress(true); // Get the partial tick time

                // Current position
                Vec3d currentPosition = client.player.getPos();

                // Velocity (difference between current and last tick positions)
                Vec3d velocity = client.player.getVelocity();

                // Predict the next position using current position and velocity scaled by partialTicks
                Vec3d nextPosition = currentPosition.add(velocity.multiply(partialTicks));
                if (colliderBox.contains(nextPosition)) {
                    if (MinehopClient.startTime != 0) {
                        ClientPacketHandler.sendEndMapEvent(renderState.endEntity.getPairedMap(), time);
                        MinehopClient.startTime = 0;
                        MinehopClient.lastSendTime = 0;
                    }
                }
            }

            Vec3d corner1Offset = new Vec3d(corner1.getX(), corner1.getY(), corner1.getZ()).subtract(renderState.endEntity.getPos());
            Vec3d corner2Offset = new Vec3d(corner2.getX(), corner2.getY(), corner2.getZ()).subtract(renderState.endEntity.getPos());

            RenderUtil.drawCuboid(vertexConsumerProvider, matrixStack, new Vector3f((float) corner1Offset.getX(), (float) corner1Offset.getY(), (float) corner1Offset.getZ()), new Vector3f((float) corner2Offset.getX(), (float) corner2Offset.getY(), (float) corner2Offset.getZ()), 10, 255, 255, 0, 0);
        }
        super.render(renderState, matrixStack, vertexConsumerProvider, i);
    }

    @Override
    public Identifier getTexture(EndEntityRenderState state) {
        return TEXTURE;
    }
}