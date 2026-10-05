package net.nerdorg.minehop.platform.services;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Client-only hooks: client tick, world/HUD rendering, renderer/model-layer/key-mapping/picture-in-picture registration.
 * Obtain through {@link net.nerdorg.minehop.platform.ClientServices#CLIENT}.
 *
 * <p>The registration methods are called from {@code MinehopClient#onInitializeClient}. Fabric applies them immediately;
 * NeoForge/Forge buffer them and apply them in their mod-bus events ({@code EntityRenderersEvent.RegisterRenderers},
 * {@code EntityRenderersEvent.RegisterLayerDefinitions}, {@code RegisterKeyMappingsEvent},
 * {@code RegisterPictureInPictureRenderersEvent}), so the client init must run before those events (see
 * docs/MULTILOADER.md).</p>
 *
 * <p>26.1: there is no render-type registration any more; the boost pad is translucent through its block model
 * ({@code "force_translucent"} texture), which every loader reads.</p>
 */
public interface IClientHelper {

    // ---------------------------------------------------------------------------------------------
    // Ticks (Fabric ClientTickEvents.START/END_CLIENT_TICK; NeoForge ClientTickEvent.Pre/Post;
    // Forge TickEvent.ClientTickEvent.Pre/Post)
    // ---------------------------------------------------------------------------------------------

    void onClientTickStart(ClientTickListener listener);

    void onClientTickEnd(ClientTickListener listener);

    // ---------------------------------------------------------------------------------------------
    // Rendering
    // ---------------------------------------------------------------------------------------------

    /**
     * World overlays (bounds/surf stick previews, replay path). 26.1 has no "after entities" hook any more: entities
     * are submitted as features and drawn later, so the overlays draw after the translucent features (the 26.1 Fabric
     * port's choice). Fabric {@code LevelRenderEvents.AFTER_TRANSLUCENT_FEATURES}; NeoForge
     * {@code RenderLevelStageEvent.AfterTranslucentFeatures}; Forge: see the forge module.
     */
    void onWorldRenderAfterEntities(WorldRenderListener listener);

    /**
     * End of the main world rendering pass (once per rendered frame).
     * Fabric {@code LevelRenderEvents.END_MAIN}; NeoForge {@code RenderLevelStageEvent.AfterLevel}; Forge: see the forge
     * module.
     */
    void onWorldRenderEnd(WorldRenderListener listener);

    /**
     * After the vanilla HUD was drawn. (Minehop currently draws its HUD from InGameHudMixin and does not use this.)
     * Fabric {@code HudElementRegistry} (last element); NeoForge/Forge {@code RenderGuiEvent.Post}.
     */
    void onHudRender(HudRenderListener listener);

    // ---------------------------------------------------------------------------------------------
    // Registration
    // ---------------------------------------------------------------------------------------------

    /**
     * Fabric {@code EntityRendererRegistry}; NeoForge/Forge {@code EntityRenderersEvent.RegisterRenderers}.
     */
    <E extends Entity> void registerEntityRenderer(Supplier<? extends EntityType<? extends E>> type, EntityRendererProvider<E> provider);

    /**
     * Fabric {@code EntityModelLayerRegistry}; NeoForge/Forge {@code EntityRenderersEvent.RegisterLayerDefinitions}.
     */
    void registerModelLayer(ModelLayerLocation layer, Supplier<LayerDefinition> definition);

    /**
     * Registers a key mapping (shown in the controls screen) and returns it.
     * Fabric {@code KeyBindingHelper.registerKeyBinding}; NeoForge/Forge {@code RegisterKeyMappingsEvent}.
     */
    KeyMapping registerKeyMapping(KeyMapping mapping);

    /**
     * Registers a GUI picture-in-picture renderer (1.21.6+ GUI 3D elements, e.g. the surf stick ramp preview).
     * Fabric {@code PictureInPictureRendererRegistry}; NeoForge/Forge {@code RegisterPictureInPictureRenderersEvent}.
     */
    <S extends PictureInPictureRenderState> void registerPictureInPictureRenderer(
            Class<S> stateClass, Function<MultiBufferSource.BufferSource, PictureInPictureRenderer<S>> factory);

    // ---------------------------------------------------------------------------------------------
    // Listener types
    // ---------------------------------------------------------------------------------------------

    @FunctionalInterface
    interface ClientTickListener {
        void onTick(Minecraft client);
    }

    @FunctionalInterface
    interface WorldRenderListener {
        void onRender(WorldRenderContext context);
    }

    @FunctionalInterface
    interface HudRenderListener {
        void onHudRender(GuiGraphicsExtractor graphics, DeltaTracker tickCounter);
    }

    /**
     * The world-render state Minehop's renderers use. Positions are camera-relative (subtract the camera position).
     */
    interface WorldRenderContext {
        /**
         * @return the pose stack to render with (Fabric {@code LevelRenderContext#poseStack()},
         * NeoForge {@code RenderLevelStageEvent#getPoseStack()}).
         */
        PoseStack matrixStack();

        /**
         * @return the buffer source to draw into (Fabric {@code LevelRenderContext#bufferSource()},
         * NeoForge/Forge {@code Minecraft.getInstance().renderBuffers().bufferSource()}).
         */
        MultiBufferSource consumers();

        /**
         * @return the active camera ({@code gameRenderer.getMainCamera()}: 26.1's level render contexts no longer
         * carry it).
         */
        Camera camera();
    }
}
