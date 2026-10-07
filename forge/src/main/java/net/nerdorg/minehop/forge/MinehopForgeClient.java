package net.nerdorg.minehop.forge;

import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.ModLoadingContext;
import net.nerdorg.minehop.MinehopClient;
import net.nerdorg.minehop.client.MinehopHudOverlay;
import net.nerdorg.minehop.config.MinehopConfigScreen;

import java.util.Set;

/**
 * Forge client bootstrap. Only loaded on the physical client.
 */
public final class MinehopForgeClient {
    /**
     * What vanilla's {@code Gui#renderPlayerHealth} draws (armor, hearts, food, air). {@code ForgeGui} draws these as
     * separate overlays and never calls {@code renderPlayerHealth}, which {@code InGameHudMixin} cancels for hide-self.
     */
    private static final Set<ResourceLocation> PLAYER_HEALTH_OVERLAYS = Set.of(
            VanillaGuiOverlay.PLAYER_HEALTH.id(),
            VanillaGuiOverlay.ARMOR_LEVEL.id(),
            VanillaGuiOverlay.FOOD_LEVEL.id(),
            VanillaGuiOverlay.AIR_LEVEL.id());

    private MinehopForgeClient() {
    }

    static void init() {
        // Config screen (mods list "Config" button) - the shared cloth-config AutoConfig screen.
        ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((minecraft, parent) -> MinehopConfigScreen.create(parent)));

        // Forge 1.20.1 draws the HUD with ForgeGui (overlays) instead of Gui#render: draw Minehop's HUD where
        // InGameHudMixin draws it on Fabric (after the whole vanilla HUD), and hide the status bars for hide-self.
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, RenderGuiEvent.Post.class,
                event -> MinehopHudOverlay.render(event.getGuiGraphics(), event.getPartialTick()));
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, RenderGuiOverlayEvent.Pre.class, event -> {
            if (net.nerdorg.minehop.client.ClientVisibility.hideSelf() && PLAYER_HEALTH_OVERLAYS.contains(event.getOverlay().id())) {
                event.setCanceled(true);
            }
        });

        // RegisterKeyMappingsEvent and EntityRenderersEvent.* fire before FMLClientSetupEvent, so the common client init
        // runs here (mod constructor); the client services buffer their registrations until those mod-bus events.
        new MinehopClient().onInitializeClient();
    }
}
