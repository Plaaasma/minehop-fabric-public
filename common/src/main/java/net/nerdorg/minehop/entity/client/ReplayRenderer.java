package net.nerdorg.minehop.entity.client;

import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.config.ConfigWrapper;
import net.nerdorg.minehop.entity.custom.ReplayEntity;

public class ReplayRenderer extends MobRenderer<ReplayEntity, ReplayModel> {
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
        // Hidden on request, otherwise culled like any entity (this used to skip the frustum check entirely).
        return !net.nerdorg.minehop.client.ClientVisibility.hideReplay() && super.shouldRender(mobEntity, frustum, d, e, f);
    }

    public ReplayEntityRenderState createRenderState() {
        return new ReplayEntityRenderState();
    }

    public void extractRenderState(ReplayEntity replayEntity, ReplayEntityRenderState state, float tickDelta) {
        state.replayEntity = replayEntity;
        state.renderHead = replayEntity.shouldRenderHead();
    }
}
