package net.nerdorg.minehop.forge.platform;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import net.nerdorg.minehop.forge.MinehopForge;
import net.nerdorg.minehop.platform.registry.RegistryEntry;
import net.nerdorg.minehop.platform.services.IRegistryHelper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Forge registration: one {@link DeferredRegister} per registry, registered on the mod event bus as soon as it is
 * created (during the mod constructor). Entries keep their call order inside each registry, so the raw ids match the
 * Fabric build (which registers eagerly in the same order).
 */
public class ForgeRegistryHelper implements IRegistryHelper {
    private static final Map<ResourceKey<? extends Registry<?>>, DeferredRegister<?>> REGISTERS = new LinkedHashMap<>();
    private static final List<AttributeRegistration> ATTRIBUTES = new ArrayList<>();
    private static final List<TabModification> TAB_MODIFICATIONS = new ArrayList<>();
    private static boolean modBusListenersAdded;

    @Override
    public <R, T extends R> RegistryEntry<T> register(ResourceKey<R> key, Supplier<T> factory) {
        DeferredRegister<R> register = deferredRegister(key);
        RegistryObject<T> object = register.register(key.identifier().getPath(), factory);
        return new Entry<>(key, object);
    }

    @SuppressWarnings("unchecked")
    private static synchronized <R> DeferredRegister<R> deferredRegister(ResourceKey<R> key) {
        return (DeferredRegister<R>) REGISTERS.computeIfAbsent(key.registryKey(), registryKey -> {
            DeferredRegister<R> register = DeferredRegister.create((ResourceKey<? extends Registry<R>>) registryKey, key.identifier().getNamespace());
            register.register(MinehopForge.modBusGroup());
            return register;
        });
    }

    @Override
    public <T extends BlockEntity> BlockEntityType<T> createBlockEntityType(BlockEntityFactory<T> factory, Block... validBlocks) {
        return new BlockEntityType<>(factory::create, Set.of(validBlocks));
    }

    @Override
    public void registerEntityAttributes(Supplier<? extends EntityType<? extends LivingEntity>> type, Supplier<AttributeSupplier.Builder> attributes) {
        addModBusListeners();
        ATTRIBUTES.add(new AttributeRegistration(type, attributes));
    }

    @Override
    public CreativeModeTab.Builder creativeModeTabBuilder() {
        return CreativeModeTab.builder();
    }

    @Override
    public void modifyCreativeModeTab(ResourceKey<CreativeModeTab> tab, CreativeTabModifier modifier) {
        addModBusListeners();
        TAB_MODIFICATIONS.add(new TabModification(tab, modifier));
    }

    private static synchronized void addModBusListeners() {
        if (modBusListenersAdded) {
            return;
        }
        modBusListenersAdded = true;
        // Forge 1.21.6+: both events are no longer mod-bus events, they have their own static BUS.
        EntityAttributeCreationEvent.BUS.addListener(event -> {
            for (AttributeRegistration registration : ATTRIBUTES) {
                // Built only now: attribute builders read registry holders that aren't bound at mod construction.
                event.put(registration.type().get(), registration.attributes().get().build());
            }
        });
        BuildCreativeModeTabContentsEvent.BUS.addListener(event -> {
            for (TabModification modification : TAB_MODIFICATIONS) {
                if (modification.tab().equals(event.getTabKey())) {
                    modification.modifier().modifyEntries(event);
                }
            }
        });
    }

    private record AttributeRegistration(Supplier<? extends EntityType<? extends LivingEntity>> type, Supplier<AttributeSupplier.Builder> attributes) {
    }

    private record TabModification(ResourceKey<CreativeModeTab> tab, CreativeTabModifier modifier) {
    }

    private record Entry<T>(ResourceKey<? super T> key, RegistryObject<T> object) implements RegistryEntry<T> {
        @Override
        public T get() {
            return this.object.get();
        }
    }
}
