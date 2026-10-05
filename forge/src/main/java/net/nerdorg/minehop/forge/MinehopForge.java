package net.nerdorg.minehop.forge;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.block.ModBlocks;
import net.nerdorg.minehop.forge.network.FabricRegistrySyncClient;
import net.nerdorg.minehop.forge.platform.ForgeNetworkHelper;

/**
 * Forge entrypoint: runs the common initialization (and the client initialization on the physical client) from the mod
 * constructor, then builds the Forge network channels from the payloads the common code declared.
 */
@Mod(Minehop.MOD_ID)
public class MinehopForge {
    private static BusGroup modBusGroup;

    public MinehopForge(FMLJavaModLoadingContext context) {
        // The registry/client services register DeferredRegisters and mod-bus listeners while the common init runs.
        MinehopForge.modBusGroup = context.getModBusGroup();

        new Minehop().onInitialize();

        // On Fabric, registering ModBlockEntities runs its factory immediately, which loads ModBlocks and registers the
        // boost pad block and block item right then. Forge defers that factory to the block entity RegisterEvent, which
        // is too late to add a block or an item. Load ModBlocks now instead: on Fabric it also comes after ModItems'
        // items, so the item raw ids stay identical (bounds_stick, surf_stick, instagib_gun, boost_pad).
        ModBlocks.BOOSTER_BLOCK.id();

        if (FMLEnvironment.dist == Dist.CLIENT) {
            MinehopForgeClient.init(context);
            FabricRegistrySyncClient.register();
        }

        ForgeNetworkHelper.buildChannel();
    }

    /**
     * @return the mod bus group (EventBus 7), for the platform services.
     */
    public static BusGroup modBusGroup() {
        if (modBusGroup == null) {
            throw new IllegalStateException("Minehop's Forge entrypoint has not been constructed yet");
        }
        return modBusGroup;
    }
}
