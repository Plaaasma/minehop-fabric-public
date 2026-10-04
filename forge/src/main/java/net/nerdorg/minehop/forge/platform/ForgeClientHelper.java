package net.nerdorg.minehop.forge.platform;

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
 * PHASE 3 TODO: Forge implementation of {@link IClientHelper} (client only).
 */
public class ForgeClientHelper implements IClientHelper {

    @Override
    public void onClientTickStart(ClientTickListener listener) {
        // TODO(phase 3): net.minecraftforge.event.TickEvent.ClientTickEvent.Pre -> Minecraft.getInstance()
        throw Todo.notImplemented("IClientHelper.onClientTickStart");
    }

    @Override
    public void onClientTickEnd(ClientTickListener listener) {
        // TODO(phase 3): TickEvent.ClientTickEvent.Post -> Minecraft.getInstance()
        throw Todo.notImplemented("IClientHelper.onClientTickEnd");
    }

    @Override
    public void onWorldRenderAfterEntities(WorldRenderListener listener) {
        // TODO(phase 3): Forge 54 (1.21.4) has NO RenderLevelStageEvent (only AddFramePassEvent for the new frame graph).
        //  Add a forge-only client mixin into LevelRenderer at the same injection point Fabric API uses for
        //  WorldRenderEvents.AFTER_ENTITIES (fabric-rendering-v1 LevelRendererMixin) and call the listeners with
        //  (poseStack, Minecraft.getInstance().renderBuffers().bufferSource(), camera).
        throw Todo.notImplemented("IClientHelper.onWorldRenderAfterEntities");
    }

    @Override
    public void onWorldRenderEnd(WorldRenderListener listener) {
        // TODO(phase 3): same forge-only LevelRenderer mixin, at the end of LevelRenderer#renderLevel (Fabric's
        //  WorldRenderEvents.END point). Once per frame; used for FPS + finish-zone timing.
        throw Todo.notImplemented("IClientHelper.onWorldRenderEnd");
    }

    @Override
    public void onHudRender(HudRenderListener listener) {
        // TODO(phase 3): net.minecraftforge.client.event.RenderGuiEvent.Post -> (event.getGuiGraphics(), event.getPartialTick())
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
        // TODO(phase 3): FMLClientSetupEvent -> event.enqueueWork(() -> ItemBlockRenderTypes.setRenderLayer(block.get(), renderType))
        throw Todo.notImplemented("IClientHelper.setBlockRenderType");
    }
}
