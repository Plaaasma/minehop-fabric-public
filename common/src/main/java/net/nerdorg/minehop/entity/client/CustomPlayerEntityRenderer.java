package net.nerdorg.minehop.entity.client;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.resources.ResourceLocation;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.MinehopClient;
import net.nerdorg.minehop.config.ConfigWrapper;

import java.util.HashMap;

public class CustomPlayerEntityRenderer extends PlayerRenderer {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(Minehop.MOD_ID, "textures/entity/cheater_player_model_texture.png");
    private static final HashMap<String, PlayerModel> PlayerModels = new HashMap<>();

    public enum PlayerModel {
        Player,
        Cheater
    }


    public CustomPlayerEntityRenderer(EntityRendererProvider.Context ctx, boolean slim) {
        super(ctx, slim);
    }

    // 1.21.1: takes the player (no render state). Keyed by the profile name, which is what 1.21.4's
    // PlayerRenderState.name holds.
    @Override
    public ResourceLocation getTextureLocation(AbstractClientPlayer entity) {
        String name = entity.getGameProfile().getName();

        PlayerModels.putIfAbsent(name, PlayerModel.Player);
        PlayerModel model = PlayerModels.get(name);

        switch (model){

            case Player -> {
                return entity.getSkin().texture();
            }
            case Cheater -> {
                return TEXTURE;
            }
        }

        return entity.getSkin().texture();
    }

    public static void setPlayerModel(PlayerModel playerModel, String UUID) {
        PlayerModels.put(UUID, playerModel);
    }

    @Override
    public boolean shouldRender(AbstractClientPlayer entity, Frustum frustum, double x, double y, double z) {

        if (ConfigWrapper.config.hideOthers) {
            return false;
        }

        return super.shouldRender(entity, frustum, x, y, z);
    }

}
