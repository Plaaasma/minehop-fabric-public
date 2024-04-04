package net.nerdorg.minehop.mixin.client;

import com.google.common.collect.ImmutableMap;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.EntityRenderers;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.nerdorg.minehop.entity.client.CustomPlayerEntityRenderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Map;

@Mixin(EntityRenderers.class)
public abstract class EntityRenderDispatcherMixin {

    @Shadow @Final private static Map<SkinTextures.Model, EntityRendererFactory<AbstractClientPlayerEntity>> PLAYER_RENDERER_FACTORIES;

    /**
     * @author Moriz
     * @reason pp
     */
    @Overwrite
    public static Map<SkinTextures.Model, EntityRenderer<? extends PlayerEntity>> reloadPlayerRenderers(EntityRendererFactory.Context ctx) {
        ImmutableMap.Builder<SkinTextures.Model, EntityRenderer<? extends PlayerEntity>> builder = ImmutableMap.builder();
        PLAYER_RENDERER_FACTORIES.forEach((model, factory) -> {
            try {
                builder.put(model, new CustomPlayerEntityRenderer(ctx, true));
            } catch (Exception var5) {
                throw new IllegalArgumentException("Failed to create player model for " + model, var5);
            }
        });
        return builder.build();
    }
}

