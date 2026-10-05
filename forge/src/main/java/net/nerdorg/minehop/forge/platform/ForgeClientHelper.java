package net.nerdorg.minehop.forge.platform;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.forge.MinehopForge;
import net.nerdorg.minehop.platform.services.IClientHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

/**
 * Forge implementation of {@link IClientHelper} (physical client only).
 *
 * <p>Registrations are buffered and applied in the matching mod-bus events, which fire after the mod constructor
 * (where Minehop's client init runs). The two world-render hooks use Forge 52's {@code RenderLevelStageEvent}
 * ({@code AFTER_ENTITIES} / {@code AFTER_LEVEL}), like the NeoForge module. (Forge 54, the 1.21.4 build, has no such
 * event and fires them from a {@code LevelRenderer} mixin instead.)</p>
 */
public class ForgeClientHelper implements IClientHelper {
    private static final List<WorldRenderListener> AFTER_ENTITIES = new CopyOnWriteArrayList<>();
    private static final List<WorldRenderListener> END = new CopyOnWriteArrayList<>();
    private static final List<HudRenderListener> HUD = new CopyOnWriteArrayList<>();
    private static final ResourceLocation HUD_LAYER = ResourceLocation.fromNamespaceAndPath(Minehop.MOD_ID, "hud_render_listeners");
    private static final List<Runnable> CLIENT_SETUP = new ArrayList<>();
    private static final List<KeyMapping> KEY_MAPPINGS = new ArrayList<>();
    private static final List<LayerRegistration> LAYERS = new ArrayList<>();
    private static final List<RendererRegistration<?>> ENTITY_RENDERERS = new ArrayList<>();
    private static boolean modBusListenersAdded;
    private static boolean worldRenderListenerAdded;

    @Override
    public void onClientTickStart(ClientTickListener listener) {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, TickEvent.ClientTickEvent.Pre.class,
                event -> listener.onTick(Minecraft.getInstance()));
    }

    @Override
    public void onClientTickEnd(ClientTickListener listener) {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, TickEvent.ClientTickEvent.Post.class,
                event -> listener.onTick(Minecraft.getInstance()));
    }

    @Override
    public void onWorldRenderAfterEntities(WorldRenderListener listener) {
        addWorldRenderListener();
        AFTER_ENTITIES.add(listener);
    }

    @Override
    public void onWorldRenderEnd(WorldRenderListener listener) {
        addWorldRenderListener();
        END.add(listener);
    }

    private static synchronized void addWorldRenderListener() {
        if (worldRenderListenerAdded) {
            return;
        }
        worldRenderListenerAdded = true;
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, RenderLevelStageEvent.class, event -> {
            if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
                fireAfterEntities(new PoseStack(), Minecraft.getInstance().renderBuffers().bufferSource(), event.getCamera());
            } else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL) {
                fireEnd(event.getCamera());
            }
        });
    }

    @Override
    public void onHudRender(HudRenderListener listener) {
        // Fabric's HudRenderCallback fires at the tail of Gui#render, i.e. after every vanilla HUD layer. Forge 54 builds
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
    public void setBlockRenderType(Supplier<? extends Block> block, RenderType renderType) {
        addModBusListeners();
        CLIENT_SETUP.add(() -> ItemBlockRenderTypes.setRenderLayer(block.get(), renderType));
    }

    private static synchronized void addModBusListeners() {
        if (modBusListenersAdded) {
            return;
        }
        modBusListenersAdded = true;
        var modBus = MinehopForge.modEventBus();
        modBus.addListener(EventPriority.NORMAL, false, EntityRenderersEvent.RegisterRenderers.class, event -> {
            for (RendererRegistration<?> registration : ENTITY_RENDERERS) {
                registration.register(event);
            }
        });
        modBus.addListener(EventPriority.NORMAL, false, EntityRenderersEvent.RegisterLayerDefinitions.class, event -> {
            for (LayerRegistration registration : LAYERS) {
                event.registerLayerDefinition(registration.layer(), registration.definition());
            }
        });
        modBus.addListener(EventPriority.NORMAL, false, RegisterKeyMappingsEvent.class, event -> {
            for (KeyMapping mapping : KEY_MAPPINGS) {
                event.register(mapping);
            }
        });
        modBus.addListener(EventPriority.NORMAL, false, AddGuiOverlayLayersEvent.class, event -> {
            if (!HUD.isEmpty()) {
                event.getLayeredDraw().add(HUD_LAYER, (graphics, deltaTracker) -> {
                    for (HudRenderListener listener : HUD) {
                        listener.onHudRender(graphics, deltaTracker);
                    }
                });
            }
        });
        modBus.addListener(EventPriority.NORMAL, false, FMLClientSetupEvent.class, event -> event.enqueueWork(() -> {
            for (Runnable task : CLIENT_SETUP) {
                task.run();
            }
        }));
    }

    // ---------------------------------------------------------------------------------------------
    // World render hooks, fired from RenderLevelStageEvent
    // ---------------------------------------------------------------------------------------------

    /**
     * Fabric's {@code WorldRenderEvents.AFTER_ENTITIES}: entities are drawn, block entities and translucent terrain are
     * not. {@code poseStack} is an identity pose stack, like the one 1.21.1's {@code LevelRenderer#renderLevel} draws
     * entities with (the camera transform is on RenderSystem's model-view stack), {@code consumers} the main buffer
     * source. Forge 52's event only carries the model-view matrix, not that pose stack.
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
     * Fabric's {@code WorldRenderEvents.END}: end of {@code LevelRenderer#renderLevel}, once per rendered frame
     * ({@code AFTER_LEVEL}).
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
