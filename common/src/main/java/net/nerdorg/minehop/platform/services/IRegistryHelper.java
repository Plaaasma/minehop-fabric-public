package net.nerdorg.minehop.platform.services;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.nerdorg.minehop.platform.registry.RegistryEntry;

import java.util.function.Supplier;

/**
 * Deferred-friendly registration of game objects (entity types, blocks, block entity types, items, creative tabs) and
 * the registration-adjacent hooks (default entity attributes, creative tab contents).
 *
 * <p>Rules for common code: every game object is created INSIDE the factory {@link Supplier} (NeoForge/Forge create
 * them during their RegisterEvent; creating an Item/Block/EntityType outside it fails there because the registry is
 * frozen), and {@link RegistryEntry#get()} is only called once the game is running. All registration calls must
 * happen during mod initialization (the common {@code Minehop#onInitialize}), never lazily later.</p>
 *
 * <ul>
 *     <li>Fabric: {@code Registry.register} immediately (same order and raw ids as before the migration).</li>
 *     <li>NeoForge: one {@code DeferredRegister} per registry, registered on the mod event bus.</li>
 *     <li>Forge: one {@code DeferredRegister} per registry, registered on the mod event bus.</li>
 * </ul>
 */
public interface IRegistryHelper {

    /**
     * Registers the object created by {@code factory} under {@code key} in the built-in registry {@code key} belongs
     * to ({@code key.registry()}).
     *
     * @param key     the full key of the new entry, e.g. {@code ResourceKey.create(Registries.ITEM, minehop:surf_stick)}
     * @param factory creates the object; called exactly once (Fabric: immediately, NeoForge/Forge: in RegisterEvent)
     * @return a handle to the registered object
     */
    <R, T extends R> RegistryEntry<T> register(ResourceKey<R> key, Supplier<T> factory);

    /**
     * Creates a block entity type (vanilla's constructor and factory interface are not public). Only call this from
     * inside a {@link #register} factory.
     */
    <T extends BlockEntity> BlockEntityType<T> createBlockEntityType(BlockEntityFactory<T> factory, Block... validBlocks);

    /**
     * Registers the default attributes of a living entity type.
     * Fabric: {@code FabricDefaultAttributeRegistry} (the builder is created immediately); NeoForge/Forge:
     * {@code EntityAttributeCreationEvent} (mod bus). The builder is supplied lazily because it cannot be created during
     * mod construction on NeoForge: {@code LivingEntity#createLivingAttributes} adds NeoForge's own attributes, which
     * are only bound once their registry event fired.
     */
    void registerEntityAttributes(Supplier<? extends EntityType<? extends LivingEntity>> type, Supplier<AttributeSupplier.Builder> attributes);

    /**
     * @return a builder for a mod creative tab (vanilla's builder needs a row/column on Fabric's terms; every loader has
     * its own paging for mod tabs). Register the built tab with {@link #register} under {@code Registries.CREATIVE_MODE_TAB}.
     */
    CreativeModeTab.Builder creativeModeTabBuilder();

    /**
     * Adds entries to an existing (vanilla or modded) creative tab.
     * Fabric: {@code ItemGroupEvents.modifyEntriesEvent}; NeoForge/Forge: {@code BuildCreativeModeTabContentsEvent} (mod bus).
     */
    void modifyCreativeModeTab(ResourceKey<CreativeModeTab> tab, CreativeTabModifier modifier);

    @FunctionalInterface
    interface BlockEntityFactory<T extends BlockEntity> {
        T create(BlockPos pos, BlockState state);
    }

    @FunctionalInterface
    interface CreativeTabModifier {
        /**
         * @param entries the tab's entries; {@code accept(...)} appends to the end of the tab
         */
        void modifyEntries(CreativeModeTab.Output entries);
    }
}
