package net.nerdorg.minehop.entity.client;

import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.config.ConfigWrapper;
import net.nerdorg.minehop.entity.custom.ReplayEntity;

public class ReplayRenderer extends MobRenderer<ReplayEntity, ReplayEntityRenderState, ReplayModel> {
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
        return !ConfigWrapper.config.hideReplay;
    }

    @Override
    public ReplayEntityRenderState createRenderState() {
        return new ReplayEntityRenderState();
    }

    @Override
    public void extractRenderState(ReplayEntity replayEntity, ReplayEntityRenderState state, float tickDelta) {
        super.extractRenderState(replayEntity, state, tickDelta);
        state.replayEntity = replayEntity;
        state.renderHead = replayEntity.shouldRenderHead();
    }
}
