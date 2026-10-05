package net.nerdorg.minehop.fabric;

import net.fabricmc.api.ClientModInitializer;
import net.nerdorg.minehop.MinehopClient;

/**
 * Fabric {@code client} entrypoint: runs the common client initialization.
 */
public class MinehopFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        new MinehopClient().onInitializeClient();
    }
}
