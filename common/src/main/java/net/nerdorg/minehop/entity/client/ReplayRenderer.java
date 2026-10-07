package net.nerdorg.minehop.entity.client;

import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.config.ConfigWrapper;
import net.nerdorg.minehop.entity.custom.ReplayEntity;

// 1.21.1: no render state; ReplayModel.setupAnim reads shouldRenderHead() from the entity.
public class ReplayRenderer extends MobRenderer<ReplayEntity, ReplayModel> {
    private static final boolean DEBUG = Boolean.getBoolean("minehop.replayDebug");
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(Minehop.MOD_ID, "textures/entity/replay_texture.png");

    public ReplayRenderer(EntityRendererProvider.Context context) {
        super(context, new ReplayModel(context.bakeLayer(ModModelLayers.REPLAY_ENTITY)), 0.001f);
    }

    @Override
    public ResourceLocation getTextureLocation(ReplayEntity entity) {
        return TEXTURE;
    }

    @Override
    public boolean shouldRender(ReplayEntity mobEntity, Frustum frustum, double d, double e, double f) {
        // Hidden on request, otherwise culled like any entity (this used to skip the frustum check entirely). Also
        // hidden while this client plays a replay itself or shows a world-record race ghost: no second ghost of it.
        return !net.nerdorg.minehop.client.ClientVisibility.hideReplay()
                && !net.nerdorg.minehop.client.replay.ReplayPlayback.isActive()
                && !net.nerdorg.minehop.client.replay.RaceGhost.showingWorldRecord()
                && super.shouldRender(mobEntity, frustum, d, e, f);
    }

    @Override
    public void render(ReplayEntity replayEntity, float entityYaw, float tickDelta, com.mojang.blaze3d.vertex.PoseStack poseStack,
                       net.minecraft.client.renderer.MultiBufferSource buffers, int light) {
        if (DEBUG) {
            // DEV (-Dminehop.replayDebug=true): the server ghost's rendered position, to compare with client playback.
            // (1.21.1: no render state; the same interpolated position 1.21.4's extractRenderState stores.)
            Minehop.LOGGER.info(String.format(java.util.Locale.ROOT, "[REPLAYDBG] entity t=%d id=%d x=%.4f y=%.4f z=%.4f partial=%.3f",
                    System.nanoTime(), replayEntity.getId(),
                    net.minecraft.util.Mth.lerp((double) tickDelta, replayEntity.xOld, replayEntity.getX()),
                    net.minecraft.util.Mth.lerp((double) tickDelta, replayEntity.yOld, replayEntity.getY()),
                    net.minecraft.util.Mth.lerp((double) tickDelta, replayEntity.zOld, replayEntity.getZ()), tickDelta));
        }
        super.render(replayEntity, entityYaw, tickDelta, poseStack, buffers, light);
    }

}
