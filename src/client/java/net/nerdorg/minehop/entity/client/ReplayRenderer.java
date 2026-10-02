package net.nerdorg.minehop.entity.client;

import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.util.Identifier;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.config.ConfigWrapper;
import net.nerdorg.minehop.entity.custom.ReplayEntity;

public class ReplayRenderer extends MobEntityRenderer<ReplayEntity, ReplayModel> {
    private static final Identifier TEXTURE = new Identifier(Minehop.MOD_ID, "textures/entity/replay_texture.png");

    public ReplayRenderer(EntityRendererFactory.Context context) {
        super(context, new ReplayModel(context.getPart(ModModelLayers.REPLAY_ENTITY)), 0.001f);
    }

    @Override
    public Identifier getTexture(ReplayEntity entity) {
        return TEXTURE;
    }

    @Override
    public boolean shouldRender(ReplayEntity mobEntity, Frustum frustum, double d, double e, double f) {
        return !ConfigWrapper.config.hideReplay;
    }

    public ReplayEntityRenderState createRenderState() {
        return new ReplayEntityRenderState();
    }

    public void updateRenderState(ReplayEntity replayEntity, ReplayEntityRenderState state, float tickDelta) {
        state.replayEntity = replayEntity;
        state.renderHead = replayEntity.shouldRenderHead();
    }
}
