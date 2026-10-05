package net.nerdorg.minehop.neoforge.platform;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.gui.render.state.pip.PictureInPictureRenderState;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterPictureInPictureRenderersEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.neoforge.MinehopNeoForge;
import net.nerdorg.minehop.platform.services.IClientHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * NeoForge implementation of {@link IClientHelper} (physical client only).
 *
 * <p>The registration methods are called from the common client init, which the NeoForge entrypoint runs from the mod
 * constructor (see {@code MinehopNeoForgeClient}); they are buffered here and applied in the matching mod-bus events,
 * which all fire after mod construction.</p>
 */
public class NeoForgeClientHelper implements IClientHelper {
    private final List<Consumer<EntityRenderersEvent.RegisterRenderers>> renderers = new ArrayList<>();
    private final List<Consumer<EntityRenderersEvent.RegisterLayerDefinitions>> layerDefinitions = new ArrayList<>();
    private final List<KeyMapping> keyMappings = new ArrayList<>();
    private final List<Runnable> renderTypes = new ArrayList<>();
    private final List<Consumer<RegisterPictureInPictureRenderersEvent>> pictureInPictureRenderers = new ArrayList<>();
    private boolean modBusListenersAdded;
    private boolean keyMappingsRegistered;

    // ---------------------------------------------------------------------------------------------
    // Ticks
    // ---------------------------------------------------------------------------------------------

    @Override
    public void onClientTickStart(ClientTickListener listener) {
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Pre.class, event -> listener.onTick(Minecraft.getInstance()));
    }

    @Override
    public void onClientTickEnd(ClientTickListener listener) {
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> listener.onTick(Minecraft.getInstance()));
    }

    // ---------------------------------------------------------------------------------------------
    // Rendering
    // ---------------------------------------------------------------------------------------------

    @Override
    public void onWorldRenderAfterEntities(WorldRenderListener listener) {
        // Right after LevelRenderer drew the entities and block entities (Fabric's AFTER_ENTITIES point), with the same
        // (identity) pose stack and the main buffer source, flushed later in the main pass. NeoForge 21.11 fires one
        // event class per stage and no longer exposes the camera.
        NeoForge.EVENT_BUS.addListener(RenderLevelStageEvent.AfterEntities.class,
                event -> listener.onRender(new Context(event.getPoseStack(), mainCamera())));
    }

    @Override
    public void onWorldRenderEnd(WorldRenderListener listener) {
        // Once per frame after the level was rendered (Fabric's END_MAIN / END).
        NeoForge.EVENT_BUS.addListener(RenderLevelStageEvent.AfterLevel.class,
                event -> listener.onRender(new Context(event.getPoseStack(), mainCamera())));
    }

    @Override
    public void onHudRender(HudRenderListener listener) {
        NeoForge.EVENT_BUS.addListener(RenderGuiEvent.Post.class, event -> listener.onHudRender(event.getGuiGraphics(), event.getPartialTick()));
    }

    // ---------------------------------------------------------------------------------------------
    // Registration (buffered until the mod-bus events)
    // ---------------------------------------------------------------------------------------------

    @Override
    public synchronized <E extends Entity> void registerEntityRenderer(Supplier<? extends EntityType<? extends E>> type, EntityRendererProvider<E> provider) {
        this.addModBusListeners();
        this.renderers.add(event -> event.registerEntityRenderer(type.get(), provider));
    }

    @Override
    public synchronized void registerModelLayer(ModelLayerLocation layer, Supplier<LayerDefinition> definition) {
        this.addModBusListeners();
        this.layerDefinitions.add(event -> event.registerLayerDefinition(layer, definition));
    }

    @Override
    public synchronized KeyMapping registerKeyMapping(KeyMapping mapping) {
        this.addModBusListeners();
        if (this.keyMappingsRegistered) {
            Minehop.LOGGER.error("Key mapping {} registered after RegisterKeyMappingsEvent; it will not show in the controls screen", mapping.getName());
        }
        this.keyMappings.add(mapping);
        return mapping;
    }

    @Override
    public synchronized void setBlockRenderType(Supplier<? extends Block> block, ChunkSectionLayer layer) {
        this.addModBusListeners();
        this.renderTypes.add(() -> ItemBlockRenderTypes.setRenderLayer(block.get(), layer));
    }

    @Override
    public synchronized <T extends PictureInPictureRenderState> void registerPictureInPictureRenderer(Class<T> stateClass,
            Function<MultiBufferSource.BufferSource, PictureInPictureRenderer<T>> factory) {
        this.addModBusListeners();
        this.pictureInPictureRenderers.add(event -> event.register(stateClass, factory));
    }

    private static Camera mainCamera() {
        return Minecraft.getInstance().gameRenderer.getMainCamera();
    }

    private void addModBusListeners() {
        if (this.modBusListenersAdded) {
            return;
        }
        this.modBusListenersAdded = true;
        IEventBus modBus = MinehopNeoForge.modEventBus();
        modBus.addListener(EntityRenderersEvent.RegisterRenderers.class, event -> this.renderers.forEach(r -> r.accept(event)));
        modBus.addListener(EntityRenderersEvent.RegisterLayerDefinitions.class, event -> this.layerDefinitions.forEach(l -> l.accept(event)));
        modBus.addListener(RegisterKeyMappingsEvent.class, event -> {
            synchronized (this) {
                this.keyMappingsRegistered = true;
                this.keyMappings.forEach(event::register);
            }
        });
        modBus.addListener(FMLClientSetupEvent.class, event -> event.enqueueWork(() -> this.renderTypes.forEach(Runnable::run)));
        modBus.addListener(RegisterPictureInPictureRenderersEvent.class, event -> this.pictureInPictureRenderers.forEach(r -> r.accept(event)));
    }

    private record Context(PoseStack matrixStack, Camera camera) implements WorldRenderContext {
        private Context(PoseStack matrixStack, Camera camera) {
            // AFTER_LEVEL has no pose stack (NeoForge passes null); Minehop's END listener does not draw.
            this.matrixStack = matrixStack != null ? matrixStack : new PoseStack();
            this.camera = camera;
        }

        @Override
        public MultiBufferSource consumers() {
            return Minecraft.getInstance().renderBuffers().bufferSource();
        }
    }
}
