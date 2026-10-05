package net.nerdorg.minehop.entity.client;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.resources.Identifier;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.MinehopClient;
import net.nerdorg.minehop.config.ConfigWrapper;

import java.util.HashMap;

public class CustomPlayerEntityRenderer extends AvatarRenderer<AbstractClientPlayer> {
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(Minehop.MOD_ID, "textures/entity/cheater_player_model_texture.png");
    private static final HashMap<String, PlayerModel> PlayerModels = new HashMap<>();

    public enum PlayerModel {
        Player,
        Cheater
    }


    public CustomPlayerEntityRenderer(EntityRendererProvider.Context ctx, boolean slim) {
        super(ctx, slim);
    }

    @Override
    public Identifier getTextureLocation(AvatarRenderState entity) {
        // 1.21.9+: the player render state no longer carries a plain "name" string; use the rendered
        // display name (this renderer is not registered anywhere, it only holds the cheater-model map).
        String name = entity.nameTag == null ? "" : entity.nameTag.getString();
        PlayerModels.putIfAbsent(name, PlayerModel.Player);
        PlayerModel model = PlayerModels.get(name);

        switch (model){

            case Player -> {
                return entity.skin.body().texturePath();
            }
            case Cheater -> {
                return TEXTURE;
            }
        }

        return entity.skin.body().texturePath();
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
