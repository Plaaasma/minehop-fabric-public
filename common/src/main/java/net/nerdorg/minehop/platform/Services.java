package net.nerdorg.minehop.platform;

import net.nerdorg.minehop.platform.services.IEventHelper;
import net.nerdorg.minehop.platform.services.INetworkHelper;
import net.nerdorg.minehop.platform.services.IPlatformHelper;
import net.nerdorg.minehop.platform.services.IRegistryHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ServiceLoader;

/**
 * Entry point to the loader-specific code (MultiLoader-Template pattern). Each loader module ships one implementation
 * of every service interface and lists it in {@code META-INF/services/<interface FQN>}.
 *
 * <p>These services exist on both physical sides. Client-only services live in {@link ClientServices}, which must only
 * be touched from client code.</p>
 *
 * <p>Contract shared by every service (the Fabric implementation is the reference, production runs it):
 * <ul>
 *     <li>Registration methods ({@code on...}/{@code register...}) may be called at any time from the mod's init code;
 *     on Fabric they take effect immediately (1:1 with the Fabric API call they replace), other loaders may buffer
 *     them until the matching (mod-bus) event fires.</li>
 *     <li>Listeners of the same event run in registration order, on the logical side's main thread, exactly where
 *     the corresponding Fabric API event fires.</li>
 * </ul>
 */
public final class Services {
    private static final Logger LOGGER = LoggerFactory.getLogger("minehop");

    public static final IPlatformHelper PLATFORM = load(IPlatformHelper.class);
    public static final IRegistryHelper REGISTRY = load(IRegistryHelper.class);
    public static final INetworkHelper NETWORK = load(INetworkHelper.class);
    public static final IEventHelper EVENTS = load(IEventHelper.class);

    private Services() {
    }

    /**
     * Loads the single implementation of {@code clazz} provided by the running loader module.
     */
    public static <T> T load(Class<T> clazz) {
        final T loadedService = ServiceLoader.load(clazz)
                .findFirst()
                .orElseThrow(() -> new NullPointerException("Failed to load service for " + clazz.getName()));
        LOGGER.debug("Loaded {} for service {}", loadedService, clazz);
        return loadedService;
    }
}
