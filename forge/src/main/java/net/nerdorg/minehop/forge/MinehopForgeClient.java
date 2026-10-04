package net.nerdorg.minehop.forge;

import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.nerdorg.minehop.MinehopClient;
import net.nerdorg.minehop.config.MinehopConfigScreen;

/**
 * Forge client bootstrap (PHASE 3 SKELETON). Only loaded on the physical client.
 */
public final class MinehopForgeClient {
    private MinehopForgeClient() {
    }

    static void init(FMLJavaModLoadingContext context) {
        // Config screen (mods list "Config" button) - the shared cloth-config AutoConfig screen.
        context.registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((minecraft, parent) -> MinehopConfigScreen.create(parent)));

        // TODO(phase 3): same constraint as NeoForge - RegisterKeyMappingsEvent / EntityRenderersEvent.* fire before
        //  FMLClientSetupEvent, so the common client init runs here (mod constructor) and the client services buffer
        //  their registrations until the matching events fire.
        new MinehopClient().onInitializeClient();
    }
}
