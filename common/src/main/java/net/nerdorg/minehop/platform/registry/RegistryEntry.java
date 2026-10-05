package net.nerdorg.minehop.platform.registry;

import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;

import java.util.function.Supplier;

/**
 * Handle to an object registered through {@link net.nerdorg.minehop.platform.services.IRegistryHelper}.
 *
 * <p>On Fabric the object is created and registered immediately, so {@link #get()} works right away. On NeoForge and
 * Forge registration is deferred (DeferredRegister): {@link #get()} only works once the loader fired the registry event
 * for this registry, i.e. never call it from static initializers or the mod constructor.</p>
 *
 * @param <T> the registered object type
 */
public interface RegistryEntry<T> extends Supplier<T> {

    /**
     * @return the registry key of the entry ({@code minehop:<path>} in the target registry).
     */
    ResourceKey<? super T> key();

    /**
     * @return the id of the entry.
     */
    default Identifier id() {
        return key().identifier();
    }

    /**
     * @return the registered object.
     * @throws IllegalStateException (NeoForge/Forge) if the registry event for this registry has not fired yet
     */
    @Override
    T get();
}
