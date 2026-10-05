package net.nerdorg.minehop.neoforge.platform;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.nerdorg.minehop.neoforge.MinehopNeoForge;
import net.nerdorg.minehop.platform.registry.RegistryEntry;
import net.nerdorg.minehop.platform.services.IRegistryHelper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * NeoForge implementation of {@link IRegistryHelper}: one {@link DeferredRegister} per (registry, namespace), registered
 * on the mod event bus when it is created. A DeferredRegister registers its entries in the order they were added, so
 * the raw ids inside each registry follow the order of the common code's {@code register} calls, exactly like the
 * eager Fabric registration (see docs/MULTILOADER.md, "Registries": the Fabric server's raw ids must match).
 */
public class NeoForgeRegistryHelper implements IRegistryHelper {
    private final Map<RegisterKey, DeferredRegister<?>> registers = new LinkedHashMap<>();
    private final List<AttributeRegistration> attributes = new ArrayList<>();
    private final List<TabModification> tabModifications = new ArrayList<>();
    private boolean modBusListenersAdded;

    @Override
    public synchronized <R, T extends R> RegistryEntry<T> register(ResourceKey<R> key, Supplier<T> factory) {
        DeferredRegister<R> deferredRegister = this.deferredRegister(key.registryKey(), key.location().getNamespace());
        DeferredHolder<R, T> holder = deferredRegister.register(key.location().getPath(), factory);
        return new Entry<>(holder);
    }

    @SuppressWarnings("unchecked")
    private <R> DeferredRegister<R> deferredRegister(ResourceKey<? extends Registry<R>> registryKey, String namespace) {
        return (DeferredRegister<R>) this.registers.computeIfAbsent(new RegisterKey(registryKey, namespace), k -> {
            DeferredRegister<R> created = DeferredRegister.create(registryKey, namespace);
            created.register(MinehopNeoForge.modEventBus());
            return created;
        });
    }

    @Override
    public <T extends BlockEntity> BlockEntityType<T> createBlockEntityType(BlockEntityFactory<T> factory, Block... validBlocks) {
        // 1.21.1: vanilla's builder (the constructor also takes a datafixer type there), as Fabric's
        // FabricBlockEntityTypeBuilder#build() does.
        return BlockEntityType.Builder.<T>of(factory::create, validBlocks).build(null);
    }

    @Override
    public synchronized void registerEntityAttributes(Supplier<? extends EntityType<? extends LivingEntity>> type, Supplier<AttributeSupplier.Builder> attributes) {
        this.addModBusListeners();
        this.attributes.add(new AttributeRegistration(type, attributes));
    }

    @Override
    public CreativeModeTab.Builder creativeModeTabBuilder() {
        // NeoForge's no-arg builder places mod tabs after the vanilla ones (its own paging), like FabricItemGroup.builder().
        return CreativeModeTab.builder();
    }

    @Override
    public synchronized void modifyCreativeModeTab(ResourceKey<CreativeModeTab> tab, CreativeTabModifier modifier) {
        this.addModBusListeners();
        this.tabModifications.add(new TabModification(tab, modifier));
    }

    private void addModBusListeners() {
        if (this.modBusListenersAdded) {
            return;
        }
        this.modBusListenersAdded = true;
        IEventBus modBus = MinehopNeoForge.modEventBus();
        modBus.addListener(this::onEntityAttributeCreation);
        modBus.addListener(this::onBuildCreativeModeTabContents);
    }

    private void onEntityAttributeCreation(EntityAttributeCreationEvent event) {
        for (AttributeRegistration registration : this.attributes) {
            event.put(registration.type().get(), registration.attributes().get().build());
        }
    }

    private void onBuildCreativeModeTabContents(BuildCreativeModeTabContentsEvent event) {
        for (TabModification modification : this.tabModifications) {
            if (event.getTabKey() == modification.tab()) {
                modification.modifier().modifyEntries(event);
            }
        }
    }

    private record RegisterKey(ResourceKey<? extends Registry<?>> registry, String namespace) {
    }

    private record AttributeRegistration(Supplier<? extends EntityType<? extends LivingEntity>> type, Supplier<AttributeSupplier.Builder> attributes) {
    }

    private record TabModification(ResourceKey<CreativeModeTab> tab, CreativeTabModifier modifier) {
    }

    private record Entry<R, T extends R>(DeferredHolder<R, T> holder) implements RegistryEntry<T> {
        @Override
        public ResourceKey<? super T> key() {
            return this.holder.getKey();
        }

        @Override
        public T get() {
            return this.holder.get();
        }
    }
}
