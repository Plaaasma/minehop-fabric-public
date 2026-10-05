package net.nerdorg.minehop.platform.services;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;

import java.util.function.Supplier;

/**
 * Client-only hooks: client tick, world/HUD rendering, renderer/model-layer/key-mapping/render-type registration.
 * Obtain through {@link net.nerdorg.minehop.platform.ClientServices#CLIENT}.
 *
 * <p>The registration methods are called from {@code MinehopClient#onInitializeClient}. Fabric applies them immediately;
 * NeoForge/Forge buffer them and apply them in their mod-bus events ({@code EntityRenderersEvent.RegisterRenderers},
 * {@code EntityRenderersEvent.RegisterLayerDefinitions}, {@code RegisterKeyMappingsEvent},
 * {@code FMLClientSetupEvent}), so the client init must run before those events (see docs/MULTILOADER.md).</p>
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
     * World rendering, right after entities were drawn (translucent geometry pass not yet run).
     * Fabric {@code WorldRenderEvents.AFTER_ENTITIES}; NeoForge/Forge {@code RenderLevelStageEvent} at
     * {@code Stage.AFTER_ENTITIES}.
     */
    void onWorldRenderAfterEntities(WorldRenderListener listener);

    /**
     * End of world rendering (once per rendered frame).
     * Fabric {@code WorldRenderEvents.END}; NeoForge/Forge {@code RenderLevelStageEvent} at {@code Stage.AFTER_LEVEL}.
     */
    void onWorldRenderEnd(WorldRenderListener listener);

    /**
     * After the vanilla HUD was drawn. (Minehop currently draws its HUD from InGameHudMixin and does not use this.)
     * Fabric {@code HudRenderCallback}; NeoForge/Forge {@code RenderGuiEvent.Post}.
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
     * Sets the chunk render layer of a block.
     * Fabric {@code BlockRenderLayerMap}; NeoForge/Forge {@code ItemBlockRenderTypes.setRenderLayer} inside
     * {@code FMLClientSetupEvent#enqueueWork} (or a {@code render_type} in the block model).
     */
    void setBlockRenderType(Supplier<? extends Block> block, RenderType renderType);

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
        void onHudRender(GuiGraphics graphics, DeltaTracker tickCounter);
    }

    /**
     * The world-render state Minehop's renderers use. Positions are camera-relative (subtract the camera position).
     */
    interface WorldRenderContext {
        /**
         * @return the pose stack to render with (Fabric {@code WorldRenderContext#matrixStack()},
         * NeoForge/Forge {@code RenderLevelStageEvent#getPoseStack()}).
         */
        PoseStack matrixStack();

        /**
         * @return the buffer source to draw into (Fabric {@code WorldRenderContext#consumers()},
         * NeoForge/Forge {@code Minecraft.getInstance().renderBuffers().bufferSource()}).
         */
        MultiBufferSource consumers();

        /**
         * @return the active camera.
         */
        Camera camera();
    }
}
