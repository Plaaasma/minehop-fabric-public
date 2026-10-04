package net.nerdorg.minehop.fabric;

import net.fabricmc.api.ModInitializer;
import net.nerdorg.minehop.Minehop;

/**
 * Fabric {@code main} entrypoint: runs the common initialization.
 */
public class MinehopFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        new Minehop().onInitialize();
    }
}
