package net.nerdorg.minehop.entity.client;

import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.util.Identifier;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.entity.custom.GamemodeEntity;

// 1.21.1: no render state; the renderer works on the entity directly.
public class GamemodeRenderer extends MobEntityRenderer<GamemodeEntity, GamemodeModel> {
    private static final Identifier TEXTURE = Identifier.of(Minehop.MOD_ID, "textures/entity/zone.png");

    public GamemodeRenderer(EntityRendererFactory.Context context) {
        super(context, new GamemodeModel(context.getPart(ModModelLayers.GAMEMODE_ENTITY)), 0.001f);
    }

    @Override
    public Identifier getTexture(GamemodeEntity entity) {
        return TEXTURE;
    }

    @Override
    public boolean shouldRender(GamemodeEntity mobEntity, Frustum frustum, double d, double e, double f) {
        return true;
    }
}
