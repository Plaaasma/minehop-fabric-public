package net.nerdorg.minehop.entity.client;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
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

    @Override
    public ResourceLocation getTextureLocation(PlayerRenderState entity) {

        PlayerModels.putIfAbsent(entity.name, PlayerModel.Player);
        PlayerModel model = PlayerModels.get(entity.name);

        switch (model){

            case Player -> {
                return entity.skin.texture();
            }
            case Cheater -> {
                return TEXTURE;
            }
        }

        return entity.skin.texture();
    }

    public static void setPlayerModel(PlayerModel playerModel, String UUID) {
        PlayerModels.put(UUID, playerModel);
    }

    @Override
    public boolean shouldRender(AbstractClientPlayer entity, Frustum frustum, double x, double y, double z) {

        if (net.nerdorg.minehop.client.ClientVisibility.hideOthers()) {
            return false;
        }

        return super.shouldRender(entity, frustum, x, y, z);
    }

}
