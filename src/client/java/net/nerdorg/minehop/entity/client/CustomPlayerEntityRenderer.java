package net.nerdorg.minehop.entity.client;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.util.Identifier;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.MinehopClient;
import net.nerdorg.minehop.config.ConfigWrapper;

import java.util.HashMap;

public class CustomPlayerEntityRenderer extends PlayerEntityRenderer {
    private static final Identifier TEXTURE = new Identifier(Minehop.MOD_ID, "textures/entity/cheater_player_model_texture.png");
    private static final HashMap<String, PlayerModel> PlayerModels = new HashMap<>();

    public enum PlayerModel {
        Player,
        Cheater
    }


    public CustomPlayerEntityRenderer(EntityRendererFactory.Context ctx, boolean slim) {
        super(ctx, slim);
    }

    @Override
    public Identifier getTexture(AbstractClientPlayerEntity entity) {
        // 1.20.1: renderers get the entity (no render state); state.name = the player's name string.
        String name = entity.getName().getString();
        PlayerModels.putIfAbsent(name, PlayerModel.Player);
        PlayerModel model = PlayerModels.get(name);

        switch (model){

            case Player -> {
                return entity.getSkinTexture();
            }
            case Cheater -> {
                return TEXTURE;
            }
        }

        return entity.getSkinTexture();
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
