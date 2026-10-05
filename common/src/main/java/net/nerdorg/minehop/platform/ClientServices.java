package net.nerdorg.minehop.platform;

import net.nerdorg.minehop.platform.services.IClientHelper;
import net.nerdorg.minehop.platform.services.IClientNetworkHelper;

/**
 * Client-only services. Only reference this class from code that runs on the physical client (it loads classes that
 * do not exist on a dedicated server).
 */
public final class ClientServices {
    public static final IClientHelper CLIENT = Services.load(IClientHelper.class);
    public static final IClientNetworkHelper NETWORK = Services.load(IClientNetworkHelper.class);

    private ClientServices() {
    }
}
