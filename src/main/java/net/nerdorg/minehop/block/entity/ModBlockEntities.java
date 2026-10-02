package net.nerdorg.minehop.block.entity;

import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.block.ModBlocks;

public class ModBlockEntities {
    public static final BlockEntityType<BoostBlockEntity> BOOST_BE =
            Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, Identifier.fromNamespaceAndPath(Minehop.MOD_ID, "boost_be"),
                    FabricBlockEntityTypeBuilder.create(BoostBlockEntity::new, ModBlocks.BOOSTER_BLOCK).build());

    public static void registerBlockEntities() {
        Minehop.LOGGER.info("Registering Block Entities for " + Minehop.MOD_ID);
    }
}
