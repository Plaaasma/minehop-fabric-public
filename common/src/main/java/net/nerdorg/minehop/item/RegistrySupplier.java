package net.nerdorg.minehop.item;

import java.util.function.Supplier;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;

public class RegistrySupplier<T> {
	private final ResourceLocation id;
	private final Supplier<T> supplier;
	private T instance;

	public RegistrySupplier(ResourceLocation id, Supplier<T> supplier) {
		this.id = id;
		this.supplier = supplier;
	}

	public T get() {
		return instance;
	}

	public ResourceLocation getId() {
		return id;
	}

	public void register(Registry<T> registry) {
		instance = Registry.register(registry, id, supplier.get());
	}
}
