package net.nerdorg.minehop.forge;

import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.nerdorg.minehop.MinehopClient;
import net.nerdorg.minehop.config.MinehopConfigScreen;

/**
 * Forge client bootstrap. Only loaded on the physical client.
 */
public final class MinehopForgeClient {
    private MinehopForgeClient() {
    }

    static void init(FMLJavaModLoadingContext context) {
        // Config screen (mods list "Config" button) - the shared cloth-config AutoConfig screen.
        context.registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((minecraft, parent) -> MinehopConfigScreen.create(parent)));

        // RegisterKeyMappingsEvent and EntityRenderersEvent.* fire before FMLClientSetupEvent, so the common client init
        // runs here (mod constructor); the client services buffer their registrations until those mod-bus events.
        new MinehopClient().onInitializeClient();
    }
}
