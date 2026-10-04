package net.nerdorg.minehop.neoforge.platform;

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
 * PHASE 3 TODO: NeoForge implementation of {@link IRegistryHelper}.
 */
public class NeoForgeRegistryHelper implements IRegistryHelper {

    @Override
    public <R, T extends R> RegistryEntry<T> register(ResourceKey<R> key, Supplier<T> factory) {
        // TODO(phase 3): keep one DeferredRegister per registry key, created with
        //  DeferredRegister.create(key.registryKey(), key.location().getNamespace()) and registered on
        //  MinehopNeoForge.modEventBus() when created; return a RegistryEntry wrapping
        //  deferredRegister.register(key.location().getPath(), factory) (DeferredHolder#get / #getKey).
        //  Registration order inside a registry must stay the call order (same raw ids as the Fabric server).
        throw Todo.notImplemented("IRegistryHelper.register");
    }

    @Override
    public <T extends BlockEntity> BlockEntityType<T> createBlockEntityType(BlockEntityFactory<T> factory, Block... validBlocks) {
        // TODO(phase 3): new BlockEntityType<>(factory::create, java.util.Set.of(validBlocks)) (public via NeoForge AT)
        throw Todo.notImplemented("IRegistryHelper.createBlockEntityType");
    }

    @Override
    public void registerEntityAttributes(Supplier<? extends EntityType<? extends LivingEntity>> type, AttributeSupplier.Builder attributes) {
        // TODO(phase 3): buffer; on the mod-bus EntityAttributeCreationEvent: event.put(type.get(), attributes.build())
        throw Todo.notImplemented("IRegistryHelper.registerEntityAttributes");
    }

    @Override
    public CreativeModeTab.Builder creativeModeTabBuilder() {
        // TODO(phase 3): CreativeModeTab.builder() (NeoForge's no-arg builder handles the tab paging)
        throw Todo.notImplemented("IRegistryHelper.creativeModeTabBuilder");
    }

    @Override
    public void modifyCreativeModeTab(ResourceKey<CreativeModeTab> tab, CreativeTabModifier modifier) {
        // TODO(phase 3): buffer; on the mod-bus BuildCreativeModeTabContentsEvent:
        //  if (event.getTabKey() == tab) modifier.modifyEntries(event)   (the event is a CreativeModeTab.Output)
        throw Todo.notImplemented("IRegistryHelper.modifyCreativeModeTab");
    }
}
