package net.nerdorg.minehop.forge.platform;

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
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RegisterPictureInPictureRendererEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.forge.MinehopForge;
import net.nerdorg.minehop.platform.services.IClientHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Forge implementation of {@link IClientHelper} (physical client only).
 *
 * <p>Registrations are buffered and applied in the matching mod-bus events, which fire after the mod constructor
 * (where Minehop's client init runs). Forge 61 (EventBus 7) has no {@code RenderLevelStageEvent}, so the two world-render
 * hooks are fired by this module's mixins at the points Fabric API fires {@code WorldRenderEvents.AFTER_ENTITIES}
 * ({@code OutlineBufferSourceMixin}) and {@code WorldRenderEvents.END_MAIN}/{@code END} ({@code LevelRendererMixin}).</p>
 */
public class ForgeClientHelper implements IClientHelper {
    private static final List<WorldRenderListener> AFTER_ENTITIES = new CopyOnWriteArrayList<>();
    private static final List<WorldRenderListener> END = new CopyOnWriteArrayList<>();
    private static final List<HudRenderListener> HUD = new CopyOnWriteArrayList<>();
    private static final Identifier HUD_LAYER = Identifier.fromNamespaceAndPath(Minehop.MOD_ID, "hud_render_listeners");
    private static final List<Runnable> CLIENT_SETUP = new ArrayList<>();
    private static final List<KeyMapping> KEY_MAPPINGS = new ArrayList<>();
    private static final List<LayerRegistration> LAYERS = new ArrayList<>();
    private static final List<RendererRegistration<?>> ENTITY_RENDERERS = new ArrayList<>();
    private static final List<Function<MultiBufferSource.BufferSource, ? extends PictureInPictureRenderer<?>>> PICTURE_IN_PICTURE = new ArrayList<>();
    private static boolean modBusListenersAdded;

    @Override
    public void onClientTickStart(ClientTickListener listener) {
        TickEvent.ClientTickEvent.Pre.BUS.addListener(event -> listener.onTick(Minecraft.getInstance()));
    }

    @Override
    public void onClientTickEnd(ClientTickListener listener) {
        TickEvent.ClientTickEvent.Post.BUS.addListener(event -> listener.onTick(Minecraft.getInstance()));
    }

    @Override
    public void onWorldRenderAfterEntities(WorldRenderListener listener) {
        AFTER_ENTITIES.add(listener);
    }

    @Override
    public void onWorldRenderEnd(WorldRenderListener listener) {
        END.add(listener);
    }

    @Override
    public void onHudRender(HudRenderListener listener) {
        // Fabric's HudRenderCallback fires at the tail of Gui#render, i.e. after every vanilla HUD layer. Forge builds
        // the HUD from layers: add one on top of the root (unused by Minehop today; its HUD is drawn by InGameHudMixin).
        addModBusListeners();
        HUD.add(listener);
    }

    @Override
    public <E extends Entity> void registerEntityRenderer(Supplier<? extends EntityType<? extends E>> type, EntityRendererProvider<E> provider) {
        addModBusListeners();
        ENTITY_RENDERERS.add(new RendererRegistration<>(type, provider));
    }

    @Override
    public void registerModelLayer(ModelLayerLocation layer, Supplier<LayerDefinition> definition) {
        addModBusListeners();
        LAYERS.add(new LayerRegistration(layer, definition));
    }

    @Override
    public KeyMapping registerKeyMapping(KeyMapping mapping) {
        addModBusListeners();
        KEY_MAPPINGS.add(mapping);
        return mapping;
    }

    @Override
    @SuppressWarnings("removal")
    public void setBlockRenderType(Supplier<? extends Block> block, ChunkSectionLayer layer) {
        addModBusListeners();
        CLIENT_SETUP.add(() -> ItemBlockRenderTypes.setRenderLayer(block.get(), layer));
    }

    @Override
    public <T extends PictureInPictureRenderState> void registerPictureInPictureRenderer(Class<T> stateClass,
            Function<MultiBufferSource.BufferSource, PictureInPictureRenderer<T>> factory) {
        addModBusListeners();
        PICTURE_IN_PICTURE.add(factory);
    }

    private static synchronized void addModBusListeners() {
        if (modBusListenersAdded) {
            return;
        }
        modBusListenersAdded = true;
        var modBus = MinehopForge.modBusGroup();
        EntityRenderersEvent.RegisterRenderers.BUS.addListener(event -> {
            for (RendererRegistration<?> registration : ENTITY_RENDERERS) {
                registration.register(event);
            }
        });
        EntityRenderersEvent.RegisterLayerDefinitions.BUS.addListener(event -> {
            for (LayerRegistration registration : LAYERS) {
                event.registerLayerDefinition(registration.layer(), registration.definition());
            }
        });
        RegisterKeyMappingsEvent.BUS.addListener(event -> {
            for (KeyMapping mapping : KEY_MAPPINGS) {
                event.register(mapping);
            }
        });
        AddGuiOverlayLayersEvent.BUS.addListener(event -> {
            if (!HUD.isEmpty()) {
                event.getLayeredDraw().add(HUD_LAYER, (graphics, deltaTracker) -> {
                    for (HudRenderListener listener : HUD) {
                        listener.onHudRender(graphics, deltaTracker);
                    }
                });
            }
        });
        FMLClientSetupEvent.getBus(modBus).addListener(event -> event.enqueueWork(() -> {
            for (Runnable task : CLIENT_SETUP) {
                task.run();
            }
        }));
        // Fired while GameRenderer builds its GuiRenderer (after mod construction).
        RegisterPictureInPictureRendererEvent.BUS.addListener(event -> {
            for (Function<MultiBufferSource.BufferSource, ? extends PictureInPictureRenderer<?>> factory : PICTURE_IN_PICTURE) {
                event.register(factory.apply(event.getBufferSource()));
            }
        });
    }

    // ---------------------------------------------------------------------------------------------
    // World render hooks, called by LevelRendererMixin
    // ---------------------------------------------------------------------------------------------

    /**
     * Fabric's {@code WorldRenderEvents.AFTER_ENTITIES}: entities and block entities are drawn, translucent terrain is
     * not. {@code poseStack} is the main pass's (identity) pose stack, {@code consumers} the main buffer source.
     */
    public static void fireAfterEntities(PoseStack poseStack, MultiBufferSource consumers, Camera camera) {
        if (AFTER_ENTITIES.isEmpty()) {
            return;
        }
        Context context = new Context(poseStack, consumers, camera);
        for (WorldRenderListener listener : AFTER_ENTITIES) {
            listener.onRender(context);
        }
    }

    /**
     * Fabric's {@code WorldRenderEvents.END_MAIN} (Minehop's END listener): end of {@code LevelRenderer#renderLevel},
     * once per rendered frame.
     */
    public static void fireEnd(Camera camera) {
        if (END.isEmpty()) {
            return;
        }
        Context context = new Context(new PoseStack(), Minecraft.getInstance().renderBuffers().bufferSource(), camera);
        for (WorldRenderListener listener : END) {
            listener.onRender(context);
        }
    }

    private record Context(PoseStack matrixStack, MultiBufferSource consumers, Camera camera) implements WorldRenderContext {
    }

    private record LayerRegistration(ModelLayerLocation layer, Supplier<LayerDefinition> definition) {
    }

    private record RendererRegistration<E extends Entity>(Supplier<? extends EntityType<? extends E>> type, EntityRendererProvider<E> provider) {
        void register(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(this.type.get(), this.provider);
        }
    }
}
