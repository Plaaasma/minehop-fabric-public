package net.nerdorg.minehop.fabric.platform;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.nerdorg.minehop.platform.registry.RegistryEntry;
import net.nerdorg.minehop.platform.services.IRegistryHelper;

import java.util.function.Supplier;

/**
 * Fabric registers eagerly: every call is the Fabric/vanilla registration call the single-module mod made, at the
 * same moment, so registry contents and raw ids are unchanged.
 */
public class FabricRegistryHelper implements IRegistryHelper {

    @Override
    public <R, T extends R> RegistryEntry<T> register(ResourceKey<R> key, Supplier<T> factory) {
        @SuppressWarnings("unchecked")
        Registry<R> registry = (Registry<R>) BuiltInRegistries.REGISTRY.getValue(key.registry());
        if (registry == null) {
            throw new IllegalArgumentException("Unknown registry " + key.registry() + " for " + key);
        }
        T value = Registry.register(registry, key, factory.get());
        return new Entry<>(key, value);
    }

    @Override
    public <T extends BlockEntity> BlockEntityType<T> createBlockEntityType(BlockEntityFactory<T> factory, Block... validBlocks) {
        return FabricBlockEntityTypeBuilder.<T>create(factory::create, validBlocks).build();
    }

    @Override
    public void registerEntityAttributes(Supplier<? extends EntityType<? extends LivingEntity>> type, Supplier<AttributeSupplier.Builder> attributes) {
        FabricDefaultAttributeRegistry.register(type.get(), attributes.get());
    }

    @Override
    public CreativeModeTab.Builder creativeModeTabBuilder() {
        return FabricCreativeModeTab.builder();
    }

    @Override
    public void modifyCreativeModeTab(ResourceKey<CreativeModeTab> tab, CreativeTabModifier modifier) {
        CreativeModeTabEvents.modifyOutputEvent(tab).register(modifier::modifyEntries);
    }

    private record Entry<T>(ResourceKey<? super T> key, T value) implements RegistryEntry<T> {
        @Override
        public T get() {
            return this.value;
        }
    }
}
