package net.nerdorg.minehop.entity.client;

import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.config.ConfigWrapper;
import net.nerdorg.minehop.entity.custom.ReplayEntity;

public class ReplayRenderer extends MobRenderer<ReplayEntity, ReplayModel> {
    private static final boolean DEBUG = Boolean.getBoolean("minehop.replayDebug");
    private static final ResourceLocation TEXTURE = new ResourceLocation(Minehop.MOD_ID, "textures/entity/replay_texture.png");

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
    public void render(ReplayEntity replayEntity, float yaw, float tickDelta, com.mojang.blaze3d.vertex.PoseStack poseStack,
                       net.minecraft.client.renderer.MultiBufferSource buffers, int light) {
        if (DEBUG) {
            // DEV (-Dminehop.replayDebug=true): the server ghost's rendered position, to compare with client playback.
            // (1.20.1: the renderer takes the entity, so the position is lerped here instead of read from a render state.)
            Minehop.LOGGER.info(String.format(java.util.Locale.ROOT, "[REPLAYDBG] entity t=%d id=%d x=%.4f y=%.4f z=%.4f partial=%.3f",
                    System.nanoTime(), replayEntity.getId(), net.minecraft.util.Mth.lerp(tickDelta, replayEntity.xo, replayEntity.getX()),
                    net.minecraft.util.Mth.lerp(tickDelta, replayEntity.yo, replayEntity.getY()),
                    net.minecraft.util.Mth.lerp(tickDelta, replayEntity.zo, replayEntity.getZ()), tickDelta));
        }
        super.render(replayEntity, yaw, tickDelta, poseStack, buffers, light);
    }

    public ReplayEntityRenderState createRenderState() {
        return new ReplayEntityRenderState();
    }

    public void extractRenderState(ReplayEntity replayEntity, ReplayEntityRenderState state, float tickDelta) {
        state.replayEntity = replayEntity;
        state.renderHead = replayEntity.shouldRenderHead();
    }
}
