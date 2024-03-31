package net.nerdorg.minehop.mixin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.PlayerModelPart;
import net.minecraft.client.render.entity.feature.*;
import net.minecraft.client.render.entity.model.ArmorEntityModel;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.UseAction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.nerdorg.minehop.MinehopClient;
import net.nerdorg.minehop.entity.client.CheaterPlayerModel;
import net.nerdorg.minehop.entity.client.ModModelLayers;
import net.nerdorg.minehop.render.RenderUtil;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.include.com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.HashMap;

@Mixin(value = PlayerEntityRenderer.class, priority = 900)
public class PlayerEntityRendererMixin extends LivingEntityRenderer<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>>{

    EntityRendererFactory.Context ctx;

    public PlayerEntityRendererMixin(EntityRendererFactory.Context ctx, PlayerEntityModel<AbstractClientPlayerEntity> model, float shadowRadius) {
        super(ctx, model, shadowRadius);
    }

    @Inject(at = @At("TAIL"), method = "<init>")
    private void init(EntityRendererFactory.Context ctx, boolean slim, CallbackInfo ci) {
        this.ctx = ctx;
    }

    /**
     * @author Moriz
     * @reason Override texture
     */
    @Overwrite()
    public Identifier getTexture(AbstractClientPlayerEntity abstractClientPlayerEntity) {
        return abstractClientPlayerEntity.getSkinTextures().texture();
//return CheaterPlayerModel.TEXTURE;
    }

    /**
     * @author Plaaasma
     */
    @Override
    public boolean shouldRender(AbstractClientPlayerEntity entity, Frustum frustum, double x, double y, double z) {
        if (!entity.getNameForScoreboard().equals(MinecraftClient.getInstance().player.getNameForScoreboard())) {  //Hide self
            if (MinehopClient.hideOthers) {
                return false;
            }
        }

        return super.shouldRender(entity, frustum, x, y, z);
    }
}