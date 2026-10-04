package net.nerdorg.minehop.forge;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.nerdorg.minehop.Minehop;

/**
 * Forge entrypoint (PHASE 3 SKELETON). Only calls into common; the platform services in
 * {@code net.nerdorg.minehop.forge.platform} are TODO stubs.
 */
@Mod(Minehop.MOD_ID)
public class MinehopForge {
    private static IEventBus modEventBus;

    public MinehopForge(FMLJavaModLoadingContext context) {
        // TODO(phase 3): the registry/network/client services need the mod event bus (DeferredRegister#register,
        //  EntityAttributeCreationEvent, BuildCreativeModeTabContentsEvent, client registration events). It must be
        //  available BEFORE the common init below runs.
        MinehopForge.modEventBus = context.getModEventBus();

        new Minehop().onInitialize();

        if (FMLEnvironment.dist == Dist.CLIENT) {
            MinehopForgeClient.init(context);
        }
    }

    /**
     * @return the mod event bus, for the platform services.
     */
    public static IEventBus modEventBus() {
        if (modEventBus == null) {
            throw new IllegalStateException("Minehop's Forge entrypoint has not been constructed yet");
        }
        return modEventBus;
    }
}
