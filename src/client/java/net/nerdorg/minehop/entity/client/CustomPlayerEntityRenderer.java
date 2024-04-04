package net.nerdorg.minehop.entity.client;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.MinehopClient;

public class CustomPlayerEntityRenderer extends PlayerEntityRenderer {
    private static final Identifier TEXTURE = new Identifier(Minehop.MOD_ID, "textures/entity/cheater_player_model_texture.png");

    public enum PlayerModel {
        Player,
        Cube
    }

    public PlayerModel playerModel = PlayerModel.Player;

    public CustomPlayerEntityRenderer(EntityRendererFactory.Context ctx, boolean slim) {
        super(ctx, slim);
    }

    @Override
    public Identifier getTexture(AbstractClientPlayerEntity entity) {

        switch (playerModel){

            case Player -> {
                return entity.getSkinTextures().texture();
            }
            case Cube -> {
                return TEXTURE;
            }
        }

        return entity.getSkinTextures().texture();
    }

    public void setPlayerModel(PlayerModel playerModel) {
        this.playerModel = playerModel;

        if (playerModel != PlayerModel.Player) {
            getModel().setVisible(false);
            getModel().leftLeg.visible = false;
        }
    }

    @Override
    public boolean shouldRender(AbstractClientPlayerEntity entity, Frustum frustum, double x, double y, double z) {

        if (MinehopClient.hideOthers) {
            return false;
        }

        return super.shouldRender(entity, frustum, x, y, z);
    }

}
