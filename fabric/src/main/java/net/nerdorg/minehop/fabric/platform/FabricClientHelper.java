package net.nerdorg.minehop.fabric.platform;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.SpecialGuiElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.gui.render.state.pip.PictureInPictureRenderState;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.nerdorg.minehop.platform.services.IClientHelper;

import java.util.function.Function;
import java.util.function.Supplier;

@Environment(EnvType.CLIENT)
public class FabricClientHelper implements IClientHelper {

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
        WorldRenderEvents.AFTER_ENTITIES.register(context -> listener.onRender(new Context(context)));
    }

    @Override
    public void onWorldRenderEnd(WorldRenderListener listener) {
        // Fabric API 0.141 (1.21.9+) has no END event any more; END_MAIN is the end of the main world pass.
        WorldRenderEvents.END_MAIN.register(context -> listener.onRender(new Context(context)));
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onHudRender(HudRenderListener listener) {
        HudRenderCallback.EVENT.register(listener::onHudRender);
    }

    @Override
    public <E extends Entity> void registerEntityRenderer(Supplier<? extends EntityType<? extends E>> type, EntityRendererProvider<E> provider) {
        EntityRendererRegistry.register(type.get(), provider);
    }

    @Override
    public void registerModelLayer(ModelLayerLocation layer, Supplier<LayerDefinition> definition) {
        EntityModelLayerRegistry.registerModelLayer(layer, definition::get);
    }

    @Override
    public KeyMapping registerKeyMapping(KeyMapping mapping) {
        return KeyBindingHelper.registerKeyBinding(mapping);
    }

    @Override
    public void setBlockRenderType(Supplier<? extends Block> block, ChunkSectionLayer layer) {
        BlockRenderLayerMap.putBlock(block.get(), layer);
    }

    @Override
    public <T extends PictureInPictureRenderState> void registerPictureInPictureRenderer(Class<T> stateClass,
            Function<MultiBufferSource.BufferSource, PictureInPictureRenderer<T>> factory) {
        SpecialGuiElementRegistry.register(context -> factory.apply(context.vertexConsumers()));
    }

    private record Context(net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext fabric) implements WorldRenderContext {
        @Override
        public PoseStack matrixStack() {
            return this.fabric.matrices();
        }

        @Override
        public MultiBufferSource consumers() {
            return this.fabric.consumers();
        }

        @Override
        public Camera camera() {
            // Fabric API 0.141's world render context no longer exposes the camera.
            return Minecraft.getInstance().gameRenderer.getMainCamera();
        }
    }
}
