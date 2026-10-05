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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.nerdorg.minehop.forge.MinehopForge;
import net.nerdorg.minehop.platform.services.IClientHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Forge 1.20.1 implementation of {@link IClientHelper} (physical client only).
 *
 * <p>Registrations are buffered and applied in the matching mod-bus events, which fire after the mod constructor
 * (where Minehop's client init runs). The world-render hooks use {@code RenderLevelStageEvent}: Forge dispatches
 * {@code AFTER_ENTITIES} right before {@code popPush("blockentities")} in {@code LevelRenderer#renderLevel} (Fabric API's
 * {@code WorldRenderEvents.AFTER_ENTITIES} point, same pose stack) and {@code AFTER_LEVEL} right after
 * {@code renderLevel} returns (Fabric's {@code END} is its return).</p>
 */
public class ForgeClientHelper implements IClientHelper {
    private static final List<Runnable> CLIENT_SETUP = new ArrayList<>();
    private static final List<KeyMapping> KEY_MAPPINGS = new ArrayList<>();
    private static final List<LayerRegistration> LAYERS = new ArrayList<>();
    private static final List<RendererRegistration<?>> ENTITY_RENDERERS = new ArrayList<>();
    private static boolean modBusListenersAdded;

    // Forge 47 fires ClientTickEvent with phase START/END at the head/tail of Minecraft#tick (Fabric's START/END_CLIENT_TICK).
    @Override
    public void onClientTickStart(ClientTickListener listener) {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, TickEvent.ClientTickEvent.class, event -> {
            if (event.phase == TickEvent.Phase.START) {
                listener.onTick(Minecraft.getInstance());
            }
        });
    }

    @Override
    public void onClientTickEnd(ClientTickListener listener) {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, TickEvent.ClientTickEvent.class, event -> {
            if (event.phase == TickEvent.Phase.END) {
                listener.onTick(Minecraft.getInstance());
            }
        });
    }

    @Override
    public void onWorldRenderAfterEntities(WorldRenderListener listener) {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, RenderLevelStageEvent.class, event -> {
            if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
                listener.onRender(new Context(event.getPoseStack(), Minecraft.getInstance().renderBuffers().bufferSource(), event.getCamera()));
            }
        });
    }

    @Override
    public void onWorldRenderEnd(WorldRenderListener listener) {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, RenderLevelStageEvent.class, event -> {
            if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL) {
                // AFTER_LEVEL's pose stack is GameRenderer's projection stack, not the level's: hand out an identity one
                // (Minehop's END listeners only measure frame timing and do not draw).
                listener.onRender(new Context(new PoseStack(), Minecraft.getInstance().renderBuffers().bufferSource(), event.getCamera()));
            }
        });
    }

    @Override
    public void onHudRender(HudRenderListener listener) {
        // Fabric's HudRenderCallback fires once the vanilla HUD is drawn; Forge's ForgeGui fires RenderGuiEvent.Post after
        // every overlay. (Unused by Minehop today; its HUD is drawn by InGameHudMixin, see MinehopForgeClient.)
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, RenderGuiEvent.Post.class,
                event -> listener.onHudRender(event.getGuiGraphics(), event.getPartialTick()));
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
        modBus.addListener(EventPriority.NORMAL, false, FMLClientSetupEvent.class, event -> event.enqueueWork(() -> {
            for (Runnable task : CLIENT_SETUP) {
                task.run();
            }
        }));
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
