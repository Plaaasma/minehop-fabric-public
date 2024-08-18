package net.nerdorg.minehop.block;

import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;
import net.nerdorg.minehop.Minehop;

public class ModBlocks {
    public static final Block BOOSTER_BLOCK = new BoostBlock(FabricBlockSettings.copyOf(Blocks.BEDROCK).slipperiness(0.8F).nonOpaque());

    private static Block registerBlock(String name, Block block) {
        registerBlockItem(name, block);
        return Registry.register(Registries.BLOCK, Identifier.of(Minehop.MOD_ID, name), block);
    }

    private static Item registerBlockItem(String name, Block block) {
        return Registry.register(Registries.ITEM, Identifier.of(Minehop.MOD_ID, name),
                new BlockItem(block, new Item.Settings()));
    }

    public static void registerModBlocks() {
        Minehop.LOGGER.info("Registering ModBlocks for " + Minehop.MOD_ID);
        registerBlock("boost_pad", BOOSTER_BLOCK);
    }
}
