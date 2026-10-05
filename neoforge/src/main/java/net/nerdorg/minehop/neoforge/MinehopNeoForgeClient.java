package net.nerdorg.minehop.neoforge;

import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;
import net.nerdorg.minehop.MinehopClient;
import net.nerdorg.minehop.config.ConfigWrapper;
import net.nerdorg.minehop.config.MinehopConfigScreen;

import java.util.Set;

/**
 * NeoForge client bootstrap. Only loaded on the physical client.
 */
public final class MinehopNeoForgeClient {
    /**
     * The layers vanilla's {@code Gui#renderPlayerHealth} draws (hearts, armor, food, air). With "hide self" on,
     * Minehop's {@code InGameHudMixin} cancels {@code renderPlayerHealth}; NeoForge renders these as separate GUI layers
     * and never calls that method, so they are hidden here instead.
     */
    private static final Set<Identifier> HIDE_SELF_LAYERS = Set.of(
            VanillaGuiLayers.PLAYER_HEALTH,
            VanillaGuiLayers.ARMOR_LEVEL,
            VanillaGuiLayers.FOOD_LEVEL,
            VanillaGuiLayers.AIR_LEVEL);

    private MinehopNeoForgeClient() {
    }

    static void init(IEventBus modEventBus, ModContainer container) {
        // Config screen (mods list "Config" button) - the shared cloth-config AutoConfig screen.
        container.registerExtensionPoint(IConfigScreenFactory.class, (modContainer, parent) -> MinehopConfigScreen.create(parent));

        NeoForge.EVENT_BUS.addListener(RenderGuiLayerEvent.Pre.class, event -> {
            if (HIDE_SELF_LAYERS.contains(event.getName()) && ConfigWrapper.config != null && ConfigWrapper.config.hideSelf) {
                event.setCanceled(true);
            }
        });

        // The common client init registers key mappings, entity renderers, model layers and the booster render type
        // through ClientServices. NeoForge fires the matching mod-bus events (RegisterKeyMappingsEvent,
        // EntityRenderersEvent.*) before FMLClientSetupEvent, so it runs here, from the mod constructor (like Fabric's
        // client entrypoint, which also runs inside Minecraft's constructor); the client services buffer the
        // registrations until their events fire.
        new MinehopClient().onInitializeClient();
    }
}
