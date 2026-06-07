package net.nerdorg.minehop.block.entity;

import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.block.ModBlocks;

public class ModBlockEntities {
    public static final BlockEntityType<BoostBlockEntity> BOOST_BE =
            Registry.register(Registries.BLOCK_ENTITY_TYPE, Identifier.of(Minehop.MOD_ID, "boost_be"),
                    FabricBlockEntityTypeBuilder.create(BoostBlockEntity::new, ModBlocks.BOOSTER_BLOCK).build());

    public static void registerBlockEntities() {
        Minehop.LOGGER.info("Registering Block Entities for " + Minehop.MOD_ID);
    }
}
