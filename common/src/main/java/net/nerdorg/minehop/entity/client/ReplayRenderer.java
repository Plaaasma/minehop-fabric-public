package net.nerdorg.minehop.entity.client;

import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.config.ConfigWrapper;
import net.nerdorg.minehop.entity.custom.ReplayEntity;

public class ReplayRenderer extends MobRenderer<ReplayEntity, ReplayEntityRenderState, ReplayModel> {
    private static final boolean DEBUG = Boolean.getBoolean("minehop.replayDebug");
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(Minehop.MOD_ID, "textures/entity/replay_texture.png");

    public ReplayRenderer(EntityRendererProvider.Context context) {
        super(context, new ReplayModel(context.bakeLayer(ModModelLayers.REPLAY_ENTITY)), 0.001f);
    }

    @Override
    public Identifier getTextureLocation(ReplayEntityRenderState entity) {
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
    public ReplayEntityRenderState createRenderState() {
        return new ReplayEntityRenderState();
    }

    @Override
    public void extractRenderState(ReplayEntity replayEntity, ReplayEntityRenderState state, float tickDelta) {
        super.extractRenderState(replayEntity, state, tickDelta);
        if (DEBUG) {
            // DEV (-Dminehop.replayDebug=true): the server ghost's rendered position, to compare with client playback.
            Minehop.LOGGER.info(String.format(java.util.Locale.ROOT, "[REPLAYDBG] entity t=%d id=%d x=%.4f y=%.4f z=%.4f partial=%.3f",
                    System.nanoTime(), replayEntity.getId(), state.x, state.y, state.z, tickDelta));
        }
        state.replayEntity = replayEntity;
        state.renderHead = replayEntity.shouldRenderHead();
    }
}
