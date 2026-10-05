package net.nerdorg.minehop.forge.platform;

import com.mojang.blaze3d.vertex.PoseStack;
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
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RegisterPictureInPictureRendererEvent;
import net.minecraftforge.event.TickEvent;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.platform.services.IClientHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Forge implementation of {@link IClientHelper} (physical client only).
 *
 * <p>Registrations are buffered and applied in the matching registration events, which fire after the mod constructor
 * (where Minehop's client init runs); with EventBus 7 each of them has its own static bus. Forge 64 has no
 * {@code RenderLevelStageEvent}, so the two world-render hooks are fired by this module's {@code LevelRendererMixin} at
 * the points Fabric API fires {@code LevelRenderEvents.AFTER_TRANSLUCENT_FEATURES} and {@code END_MAIN}.</p>
 */
public class ForgeClientHelper implements IClientHelper {
    private static final List<WorldRenderListener> AFTER_TRANSLUCENT_FEATURES = new CopyOnWriteArrayList<>();
    private static final List<WorldRenderListener> END_MAIN = new CopyOnWriteArrayList<>();
    private static final List<HudRenderListener> HUD = new CopyOnWriteArrayList<>();
    private static final Identifier HUD_LAYER = Identifier.fromNamespaceAndPath(Minehop.MOD_ID, "hud_render_listeners");
    private static final List<KeyMapping> KEY_MAPPINGS = new ArrayList<>();
    private static final List<LayerRegistration> LAYERS = new ArrayList<>();
    private static final List<RendererRegistration<?>> ENTITY_RENDERERS = new ArrayList<>();
    private static final List<Function<MultiBufferSource.BufferSource, ? extends PictureInPictureRenderer<?>>> PICTURE_IN_PICTURE = new ArrayList<>();
    private static boolean registrationListenersAdded;

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
        AFTER_TRANSLUCENT_FEATURES.add(listener);
    }

    @Override
    public void onWorldRenderEnd(WorldRenderListener listener) {
        END_MAIN.add(listener);
    }

    @Override
    public void onHudRender(HudRenderListener listener) {
        // Fabric adds a HUD element after every vanilla one; Forge builds the HUD from layers: add one on top of the root
        // (unused by Minehop today; its HUD is drawn by InGameHudMixin).
        addRegistrationListeners();
        HUD.add(listener);
    }

    @Override
    public <E extends Entity> void registerEntityRenderer(Supplier<? extends EntityType<? extends E>> type, EntityRendererProvider<E> provider) {
        addRegistrationListeners();
        ENTITY_RENDERERS.add(new RendererRegistration<>(type, provider));
    }

    @Override
    public void registerModelLayer(ModelLayerLocation layer, Supplier<LayerDefinition> definition) {
        addRegistrationListeners();
        LAYERS.add(new LayerRegistration(layer, definition));
    }

    @Override
    public KeyMapping registerKeyMapping(KeyMapping mapping) {
        addRegistrationListeners();
        KEY_MAPPINGS.add(mapping);
        return mapping;
    }

    @Override
    public <S extends PictureInPictureRenderState> void registerPictureInPictureRenderer(
            Class<S> stateClass, Function<MultiBufferSource.BufferSource, PictureInPictureRenderer<S>> factory) {
        // Forge keys the renderer by PictureInPictureRenderer#getRenderStateClass (the same class).
        addRegistrationListeners();
        PICTURE_IN_PICTURE.add(factory);
    }

    private static synchronized void addRegistrationListeners() {
        if (registrationListenersAdded) {
            return;
        }
        registrationListenersAdded = true;
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
        RegisterPictureInPictureRendererEvent.BUS.addListener(event -> {
            for (Function<MultiBufferSource.BufferSource, ? extends PictureInPictureRenderer<?>> factory : PICTURE_IN_PICTURE) {
                event.register(factory.apply(event.getBufferSource()));
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
    }

    // ---------------------------------------------------------------------------------------------
    // World render hooks, called by LevelRendererMixin
    // ---------------------------------------------------------------------------------------------

    /**
     * Fabric's {@code LevelRenderEvents.AFTER_TRANSLUCENT_FEATURES}: in the main pass, right after the translucent
     * features (entities etc.) were drawn and before the buffer source is flushed. The main pass's pose stack is an
     * identity stack at that point; the buffer source is the main one.
     */
    public static void fireAfterTranslucentFeatures() {
        fire(AFTER_TRANSLUCENT_FEATURES);
    }

    /**
     * Fabric's {@code LevelRenderEvents.END_MAIN}: end of the main pass, once per rendered frame.
     */
    public static void fireEndMain() {
        fire(END_MAIN);
    }

    private static void fire(List<WorldRenderListener> listeners) {
        if (listeners.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Context context = new Context(new PoseStack(), minecraft.renderBuffers().bufferSource(), minecraft.gameRenderer.getMainCamera());
        for (WorldRenderListener listener : listeners) {
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
