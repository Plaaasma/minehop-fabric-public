package net.nerdorg.minehop.item;

import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;
import java.util.function.Supplier;

public class RegistrySupplier<T> {
	private final Identifier id;
	private final Supplier<T> supplier;
	private T instance;

	public RegistrySupplier(Identifier id, Supplier<T> supplier) {
		this.id = id;
		this.supplier = supplier;
	}

	public T get() {
		return instance;
	}

	public Identifier getId() {
		return id;
	}

	public void register(Registry<T> registry) {
		instance = Registry.register(registry, id, supplier.get());
	}
}
