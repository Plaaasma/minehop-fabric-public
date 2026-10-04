package net.nerdorg.minehop.neoforge.platform;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.nerdorg.minehop.platform.services.IClientHelper;

import java.util.function.Supplier;

/**
 * PHASE 3 TODO: NeoForge implementation of {@link IClientHelper} (client only).
 */
public class NeoForgeClientHelper implements IClientHelper {

    @Override
    public void onClientTickStart(ClientTickListener listener) {
        // TODO(phase 3): NeoForge.EVENT_BUS net.neoforged.neoforge.client.event.ClientTickEvent.Pre -> Minecraft.getInstance()
        throw Todo.notImplemented("IClientHelper.onClientTickStart");
    }

    @Override
    public void onClientTickEnd(ClientTickListener listener) {
        // TODO(phase 3): ClientTickEvent.Post -> Minecraft.getInstance()
        throw Todo.notImplemented("IClientHelper.onClientTickEnd");
    }

    @Override
    public void onWorldRenderAfterEntities(WorldRenderListener listener) {
        // TODO(phase 3): RenderLevelStageEvent with event.getStage() == RenderLevelStageEvent.Stage.AFTER_ENTITIES;
        //  context = (event.getPoseStack(), Minecraft.getInstance().renderBuffers().bufferSource(), event.getCamera()).
        //  Check the drawn lines/quads match Fabric (pose stack state, and flush the buffer source if needed).
        throw Todo.notImplemented("IClientHelper.onWorldRenderAfterEntities");
    }

    @Override
    public void onWorldRenderEnd(WorldRenderListener listener) {
        // TODO(phase 3): RenderLevelStageEvent with Stage.AFTER_LEVEL (once per frame; used for FPS + finish-zone timing)
        throw Todo.notImplemented("IClientHelper.onWorldRenderEnd");
    }

    @Override
    public void onHudRender(HudRenderListener listener) {
        // TODO(phase 3): net.neoforged.neoforge.client.event.RenderGuiEvent.Post -> (event.getGuiGraphics(), event.getPartialTick())
        throw Todo.notImplemented("IClientHelper.onHudRender");
    }

    @Override
    public <E extends Entity> void registerEntityRenderer(Supplier<? extends EntityType<? extends E>> type, EntityRendererProvider<E> provider) {
        // TODO(phase 3): buffer; mod-bus EntityRenderersEvent.RegisterRenderers -> event.registerEntityRenderer(type.get(), provider)
        throw Todo.notImplemented("IClientHelper.registerEntityRenderer");
    }

    @Override
    public void registerModelLayer(ModelLayerLocation layer, Supplier<LayerDefinition> definition) {
        // TODO(phase 3): buffer; mod-bus EntityRenderersEvent.RegisterLayerDefinitions -> event.registerLayerDefinition(layer, definition)
        throw Todo.notImplemented("IClientHelper.registerModelLayer");
    }

    @Override
    public KeyMapping registerKeyMapping(KeyMapping mapping) {
        // TODO(phase 3): buffer and return the mapping; mod-bus RegisterKeyMappingsEvent -> event.register(mapping)
        throw Todo.notImplemented("IClientHelper.registerKeyMapping");
    }

    @Override
    public void setBlockRenderType(Supplier<? extends Block> block, RenderType renderType) {
        // TODO(phase 3): mod-bus FMLClientSetupEvent -> event.enqueueWork(() ->
        //  ItemBlockRenderTypes.setRenderLayer(block.get(), renderType)), or "render_type": "minecraft:translucent" in
        //  assets/minehop/models/block/boost_pad.json (that file is shared with Fabric: keep Fabric output identical).
        throw Todo.notImplemented("IClientHelper.setBlockRenderType");
    }
}
