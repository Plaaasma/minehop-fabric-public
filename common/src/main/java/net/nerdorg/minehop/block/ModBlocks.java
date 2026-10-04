package net.nerdorg.minehop.block;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.platform.Services;
import net.nerdorg.minehop.platform.registry.RegistryEntry;

import java.util.function.Function;

public class ModBlocks {
    public static final RegistryEntry<Block> BOOSTER_BLOCK = register(
            "boost_pad",
            BoostBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.BEDROCK).friction(0.8F).noOcclusion(),
            true
            );

    private static RegistryEntry<Block> register(String name, Function<BlockBehaviour.Properties, Block> blockFactory, BlockBehaviour.Properties settings, boolean shouldRegisterItem) {
        // Create a registry key for the block
        ResourceKey<Block> blockKey = keyOfBlock(name);
        // Create the block instance (inside the registry factory, so it is deferred on NeoForge/Forge)
        RegistryEntry<Block> block = Services.REGISTRY.register(blockKey, () -> blockFactory.apply(settings.setId(blockKey)));

        // Sometimes, you may not want to register an item for the block.
        // Eg: if it's a technical block like `minecraft:moving_piston` or `minecraft:end_gateway`
        if (shouldRegisterItem) {
            // Items need to be registered with a different type of registry key, but the ID
            // can be the same.
            ResourceKey<Item> itemKey = keyOfItem(name);

            Services.REGISTRY.register(itemKey, () -> new BlockItem(block.get(), new Item.Properties().setId(itemKey)));
        }

        return block;
    }

    private static ResourceKey<Block> keyOfBlock(String name) {
        return ResourceKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(Minehop.MOD_ID, name));
    }

    private static ResourceKey<Item> keyOfItem(String name) {
        return ResourceKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(Minehop.MOD_ID, name));
    }
}
