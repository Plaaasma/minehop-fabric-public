package net.nerdorg.minehop.block.entity;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.block.ModBlocks;
import net.nerdorg.minehop.platform.Services;
import net.nerdorg.minehop.platform.registry.RegistryEntry;

public class ModBlockEntities {
    public static final RegistryEntry<BlockEntityType<BoostBlockEntity>> BOOST_BE =
            Services.REGISTRY.register(ResourceKey.create(Registries.BLOCK_ENTITY_TYPE, Identifier.fromNamespaceAndPath(Minehop.MOD_ID, "boost_be")),
                    () -> Services.REGISTRY.createBlockEntityType(BoostBlockEntity::new, ModBlocks.BOOSTER_BLOCK.get()));

    public static void registerBlockEntities() {
        Minehop.LOGGER.info("Registering Block Entities for " + Minehop.MOD_ID);
    }
}
