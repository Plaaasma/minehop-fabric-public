package net.nerdorg.minehop.entity.client;

import net.minecraft.client.model.*;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.render.entity.model.EntityModelPartNames;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.nerdorg.minehop.Minehop;
import org.spongepowered.include.com.google.common.collect.ImmutableList;

public class CheaterPlayerModel extends EntityModel<PlayerEntity> {
    public final ModelPart bb_main;
    public static final Identifier TEXTURE = new Identifier(Minehop.MOD_ID, "textures/entity/cheater_player_model_texture.png");
    public CheaterPlayerModel(ModelPart root) {
        this.bb_main = root.getChild("bb_main");
    }
    public static TexturedModelData getTexturedModelData() {
        ModelData modelData = new ModelData();
        ModelPartData modelPartData = modelData.getRoot();
        ModelPartData bb_main = modelPartData.addChild("bb_main", ModelPartBuilder.create().uv(2, 2).cuboid(1.0F, 0.0F, -1.0F, 0.0F, 0.0F, 0.0F, new Dilation(0.0F)), ModelTransform.pivot(0.0F, 24.0F, 0.0F));
        return TexturedModelData.of(modelData, 16, 16);
    }
    @Override
    public void render(MatrixStack matrices, VertexConsumer vertexConsumer, int light, int overlay, float red, float green, float blue, float alpha) {
        bb_main.render(matrices, vertexConsumer, light, overlay, red, green, blue, alpha);
    }
    @Override
    public void setAngles(PlayerEntity entity, float limbAngle, float limbDistance, float animationProgress, float headYaw, float headPitch) {

    }
}