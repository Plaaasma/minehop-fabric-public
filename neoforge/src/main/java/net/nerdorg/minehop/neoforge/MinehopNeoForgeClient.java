package net.nerdorg.minehop.neoforge;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.nerdorg.minehop.MinehopClient;
import net.nerdorg.minehop.config.MinehopConfigScreen;

/**
 * NeoForge client bootstrap (PHASE 3 SKELETON). Only loaded on the physical client.
 */
public final class MinehopNeoForgeClient {
    private MinehopNeoForgeClient() {
    }

    static void init(IEventBus modEventBus, ModContainer container) {
        // Config screen (mods list "Config" button) - the shared cloth-config AutoConfig screen.
        container.registerExtensionPoint(IConfigScreenFactory.class, (modContainer, parent) -> MinehopConfigScreen.create(parent));

        // TODO(phase 3): MinehopClient#onInitializeClient registers key mappings, entity renderers, model layers and the
        //  booster render type through ClientServices. Those NeoForge events (RegisterKeyMappingsEvent,
        //  EntityRenderersEvent.*) fire BEFORE FMLClientSetupEvent, so the client init has to run here, from the mod
        //  constructor (Minecraft.getInstance() is already set; the game is not fully initialised yet), and the client
        //  services must buffer the registrations until their events fire. Verify everything in onInitializeClient
        //  is safe this early (it is on Fabric, whose client entrypoint also runs inside Minecraft's constructor).
        new MinehopClient().onInitializeClient();
    }
}
