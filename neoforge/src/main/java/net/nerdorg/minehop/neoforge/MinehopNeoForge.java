package net.nerdorg.minehop.neoforge;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.nerdorg.minehop.Minehop;

/**
 * NeoForge entrypoint (PHASE 3 SKELETON). Only calls into common; the platform services in
 * {@code net.nerdorg.minehop.neoforge.platform} are TODO stubs.
 */
@Mod(Minehop.MOD_ID)
public class MinehopNeoForge {
    private static IEventBus modEventBus;

    public MinehopNeoForge(IEventBus modEventBus, ModContainer container) {
        // TODO(phase 3): the registry/network/client services need the mod event bus (DeferredRegister#register,
        //  RegisterPayloadHandlersEvent, EntityAttributeCreationEvent, BuildCreativeModeTabContentsEvent, client
        //  registration events). It must be available BEFORE the common init below runs.
        MinehopNeoForge.modEventBus = modEventBus;

        new Minehop().onInitialize();

        if (FMLEnvironment.dist == Dist.CLIENT) {
            MinehopNeoForgeClient.init(modEventBus, container);
        }
    }

    /**
     * @return the mod event bus, for the platform services.
     */
    public static IEventBus modEventBus() {
        if (modEventBus == null) {
            throw new IllegalStateException("Minehop's NeoForge entrypoint has not been constructed yet");
        }
        return modEventBus;
    }
}
