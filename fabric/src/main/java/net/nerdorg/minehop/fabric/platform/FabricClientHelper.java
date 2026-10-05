package net.nerdorg.minehop.fabric.platform;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.nerdorg.minehop.platform.services.IClientHelper;

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
        WorldRenderEvents.END.register(context -> listener.onRender(new Context(context)));
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
    public void setBlockRenderType(Supplier<? extends Block> block, RenderType renderType) {
        BlockRenderLayerMap.INSTANCE.putBlock(block.get(), renderType);
    }

    private record Context(net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext fabric) implements WorldRenderContext {
        @Override
        public PoseStack matrixStack() {
            return this.fabric.matrixStack();
        }

        @Override
        public MultiBufferSource consumers() {
            return this.fabric.consumers();
        }

        @Override
        public Camera camera() {
            return this.fabric.camera();
        }
    }
}
