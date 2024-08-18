package net.nerdorg.minehop.item;

import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroupEntries;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.block.ModBlocks;
import net.nerdorg.minehop.item.custom.BoundsStickItem;
import net.nerdorg.minehop.item.custom.InstagibItem;

public class ModItems {
    public static final Item BOUNDS_STICK = registerItem("bounds_stick", new BoundsStickItem(new Item.Settings()));
    public static final Item INSTAGIB_GUN = registerItem("instagib_gun", new InstagibItem(new Item.Settings()));

    private static void addItemsToOperatorTabItemGroup(FabricItemGroupEntries entries) {
        entries.add(BOUNDS_STICK);
        entries.add(ModBlocks.BOOSTER_BLOCK);
    }

    private static void addItemsToCombatTabItemGroup(FabricItemGroupEntries entries) {
        entries.add(INSTAGIB_GUN);
    }

    private static Item registerItem(String name, Item item) {
        return Registry.register(Registries.ITEM, Identifier.of(Minehop.MOD_ID, name), item);
    }

    public static void registerModItems() {
        Minehop.LOGGER.info("Registering Mod Items for " + Minehop.MOD_ID);

        ItemGroupEvents.modifyEntriesEvent(ItemGroups.OPERATOR).register(ModItems::addItemsToOperatorTabItemGroup);
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(ModItems::addItemsToCombatTabItemGroup);
    }
}
