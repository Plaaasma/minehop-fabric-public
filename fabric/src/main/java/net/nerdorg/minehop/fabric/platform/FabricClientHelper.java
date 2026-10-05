package net.nerdorg.minehop.fabric.platform;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.PictureInPictureRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.platform.services.IClientHelper;

import java.util.function.Function;
import java.util.function.Supplier;

@Environment(EnvType.CLIENT)
public class FabricClientHelper implements IClientHelper {
    private int hudElements;

    @Override
    public void onClientTickStart(ClientTickListener listener) {
        ClientTickEvents.START_CLIENT_TICK.register(listener::onTick);
    }

    @Override
    public void onClientTickEnd(ClientTickListener listener) {
        ClientTickEvents.END_CLIENT_TICK.register(listener::onTick);
    }

    @Override
    public void onWorldRenderAfterEntities(WorldRenderListener listener) {
        // 26.1: Fabric removed AFTER_ENTITIES; the overlays draw after the translucent features (as in the 26.1 port).
        LevelRenderEvents.AFTER_TRANSLUCENT_FEATURES.register(context -> listener.onRender(new Context(context)));
    }

    @Override
    public void onWorldRenderEnd(WorldRenderListener listener) {
        LevelRenderEvents.END_MAIN.register(context -> listener.onRender(new Context(context)));
    }

    @Override
    public void onHudRender(HudRenderListener listener) {
        // 26.1: HudRenderCallback is gone; a HUD element added last draws after the vanilla HUD.
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(Minehop.MOD_ID, "hud_" + this.hudElements++), listener::onHudRender);
    }

    @Override
    public <E extends Entity> void registerEntityRenderer(Supplier<? extends EntityType<? extends E>> type, EntityRendererProvider<E> provider) {
        EntityRendererRegistry.register(type.get(), provider);
    }

    @Override
    public void registerModelLayer(ModelLayerLocation layer, Supplier<LayerDefinition> definition) {
        ModelLayerRegistry.registerModelLayer(layer, definition::get);
    }

    @Override
    public KeyMapping registerKeyMapping(KeyMapping mapping) {
        return KeyMappingHelper.registerKeyMapping(mapping);
    }

    @Override
    public <S extends PictureInPictureRenderState> void registerPictureInPictureRenderer(
            Class<S> stateClass, Function<MultiBufferSource.BufferSource, PictureInPictureRenderer<S>> factory) {
        PictureInPictureRendererRegistry.register(context -> factory.apply(context.bufferSource()));
    }

    private record Context(LevelRenderContext fabric) implements WorldRenderContext {
        @Override
        public PoseStack matrixStack() {
            return this.fabric.poseStack();
        }

        @Override
        public MultiBufferSource consumers() {
            return this.fabric.bufferSource();
        }

        @Override
        public Camera camera() {
            return Minecraft.getInstance().gameRenderer.getMainCamera();
        }
    }
}
