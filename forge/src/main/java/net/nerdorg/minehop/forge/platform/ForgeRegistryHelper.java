package net.nerdorg.minehop.forge.platform;

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
 * PHASE 3 TODO: Forge implementation of {@link IRegistryHelper}.
 */
public class ForgeRegistryHelper implements IRegistryHelper {

    @Override
    public <R, T extends R> RegistryEntry<T> register(ResourceKey<R> key, Supplier<T> factory) {
        // TODO(phase 3): one net.minecraftforge.registries.DeferredRegister per registry key
        //  (DeferredRegister.create(key.registryKey(), key.location().getNamespace()), registered on
        //  MinehopForge.modEventBus() when created); wrap the returned RegistryObject. Keep call order = raw id order.
        throw Todo.notImplemented("IRegistryHelper.register");
    }

    @Override
    public <T extends BlockEntity> BlockEntityType<T> createBlockEntityType(BlockEntityFactory<T> factory, Block... validBlocks) {
        // TODO(phase 3): new BlockEntityType<>(factory::create, java.util.Set.of(validBlocks)) (opened by Forge's AT)
        throw Todo.notImplemented("IRegistryHelper.createBlockEntityType");
    }

    @Override
    public void registerEntityAttributes(Supplier<? extends EntityType<? extends LivingEntity>> type, AttributeSupplier.Builder attributes) {
        // TODO(phase 3): buffer; mod-bus net.minecraftforge.event.entity.EntityAttributeCreationEvent -> put(type.get(), attributes.build())
        throw Todo.notImplemented("IRegistryHelper.registerEntityAttributes");
    }

    @Override
    public CreativeModeTab.Builder creativeModeTabBuilder() {
        // TODO(phase 3): CreativeModeTab.builder() (Forge's no-arg builder)
        throw Todo.notImplemented("IRegistryHelper.creativeModeTabBuilder");
    }

    @Override
    public void modifyCreativeModeTab(ResourceKey<CreativeModeTab> tab, CreativeTabModifier modifier) {
        // TODO(phase 3): buffer; mod-bus net.minecraftforge.event.BuildCreativeModeTabContentsEvent:
        //  if (event.getTabKey() == tab) modifier.modifyEntries(event)
        throw Todo.notImplemented("IRegistryHelper.modifyCreativeModeTab");
    }
}
