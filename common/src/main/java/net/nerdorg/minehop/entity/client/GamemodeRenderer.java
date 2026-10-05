package net.nerdorg.minehop.entity.client;

import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.MinehopClient;
import net.nerdorg.minehop.entity.custom.GamemodeEntity;
import net.nerdorg.minehop.entity.custom.StartEntity;
import net.nerdorg.minehop.networking.ClientPacketHandler;
import net.nerdorg.minehop.render.RenderUtil;
import org.joml.Vector3f;

public class GamemodeRenderer extends MobRenderer<GamemodeEntity, GamemodeEntityRenderState, GamemodeModel> {
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(Minehop.MOD_ID, "textures/entity/zone.png");

    public GamemodeRenderer(EntityRendererProvider.Context context) {
        super(context, new GamemodeModel(context.bakeLayer(ModModelLayers.GAMEMODE_ENTITY)), 0.001f);
    }

    @Override
    public Identifier getTextureLocation(GamemodeEntityRenderState state) {
        return TEXTURE;
    }

    @Override
    public boolean shouldRender(GamemodeEntity mobEntity, Frustum frustum, double d, double e, double f) {
        return true;
    }

    @Override
    public GamemodeEntityRenderState createRenderState() {
        return new GamemodeEntityRenderState();
    }

    @Override
    public void extractRenderState(GamemodeEntity gamemodeEntity, GamemodeEntityRenderState state, float tickDelta) {
        // 1.21.9+: entity position/light are taken from the render state.
        super.extractRenderState(gamemodeEntity, state, tickDelta);
        state.gamemodeEntity = gamemodeEntity;
    }
}
