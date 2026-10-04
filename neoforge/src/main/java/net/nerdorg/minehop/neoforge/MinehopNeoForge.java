package net.nerdorg.minehop.neoforge;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.block.ModBlocks;

/**
 * NeoForge entrypoint: runs the common initialization (and the common client initialization on the physical client)
 * from the mod constructor. The platform services in {@code net.nerdorg.minehop.neoforge.platform} buffer what they
 * receive there and apply it in the mod-bus events (registries, payloads, attributes, creative tabs, client
 * registrations), which all fire after mod construction.
 */
@Mod(Minehop.MOD_ID)
public class MinehopNeoForge {
    private static IEventBus modEventBus;

    public MinehopNeoForge(IEventBus modEventBus, ModContainer container) {
        // The services need the mod bus (DeferredRegister#register, RegisterPayloadHandlersEvent, ...): store it before
        // anything touches them.
        MinehopNeoForge.modEventBus = modEventBus;

        new Minehop().onInitialize();

        // On Fabric the block entity type's factory runs immediately (eager registration) and its
        // ModBlocks.BOOSTER_BLOCK.get() class-initialises ModBlocks, which registers the boost pad block and block item
        // during Minehop#onInitialize. Here that factory only runs in RegisterEvent, after the BLOCK and ITEM
        // DeferredRegisters fired, so initialise ModBlocks now - after ModItems, i.e. in the same registration order
        // (same raw ids) as on Fabric.
        ModBlocks.BOOSTER_BLOCK.id();

        if (FMLEnvironment.dist.isClient()) {
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
