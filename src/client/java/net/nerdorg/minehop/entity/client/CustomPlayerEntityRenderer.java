package net.nerdorg.minehop.entity.client;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.util.Identifier;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.MinehopClient;
import net.nerdorg.minehop.config.ConfigWrapper;

import java.util.HashMap;

public class CustomPlayerEntityRenderer extends PlayerEntityRenderer {
    private static final Identifier TEXTURE = Identifier.of(Minehop.MOD_ID, "textures/entity/cheater_player_model_texture.png");
    private static final HashMap<String, PlayerModel> PlayerModels = new HashMap<>();

    public enum PlayerModel {
        Player,
        Cheater
    }


    public CustomPlayerEntityRenderer(EntityRendererFactory.Context ctx, boolean slim) {
        super(ctx, slim);
    }

    @Override
    public Identifier getTexture(PlayerEntityRenderState entity) {

        PlayerModels.putIfAbsent(entity.name, PlayerModel.Player);
        PlayerModel model = PlayerModels.get(entity.name);

        switch (model){

            case Player -> {
                return entity.skinTextures.texture();
            }
            case Cheater -> {
                return TEXTURE;
            }
        }

        return entity.skinTextures.texture();
    }

    public static void setPlayerModel(PlayerModel playerModel, String UUID) {
        PlayerModels.put(UUID, playerModel);
    }

    @Override
    public boolean shouldRender(AbstractClientPlayerEntity entity, Frustum frustum, double x, double y, double z) {

        if (ConfigWrapper.config.hideOthers) {
            return false;
        }

        return super.shouldRender(entity, frustum, x, y, z);
    }

}
