package net.nerdorg.minehop.item;

import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroupEntries;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.block.ModBlocks;
import net.nerdorg.minehop.item.custom.BoundsStickItem;
import net.nerdorg.minehop.item.custom.InstagibItem;
import net.nerdorg.minehop.item.custom.SurfStickItem;

import java.util.function.Function;

public class ModItems {
    public static final Item BOUNDS_STICK = registerItem("bounds_stick", BoundsStickItem::new, new Item.Settings());
    public static final Item SURF_STICK = registerItem("surf_stick", SurfStickItem::new, new Item.Settings().maxCount(1));
    public static final Item INSTAGIB_GUN = registerItem("instagib_gun", InstagibItem::new, new Item.Settings());
    public static final RegistryKey<ItemGroup> MINEHOP_ITEM_GROUP_KEY = RegistryKey.of(
            RegistryKeys.ITEM_GROUP,
            Identifier.of(Minehop.MOD_ID, "minehop")
    );
    public static final ItemGroup MINEHOP_ITEM_GROUP = FabricItemGroup.builder()
            .icon(() -> new ItemStack(SURF_STICK))
            .displayName(Text.translatable("itemGroup.minehop.minehop"))
            .entries((displayContext, entries) -> {
                entries.add(SURF_STICK);
                entries.add(BOUNDS_STICK);
                entries.add(INSTAGIB_GUN);
                entries.add(ModBlocks.BOOSTER_BLOCK);
            })
            .build();

    private static void addItemsToOperatorTabItemGroup(FabricItemGroupEntries entries) {
        entries.add(BOUNDS_STICK);
        entries.add(SURF_STICK);
        entries.add(ModBlocks.BOOSTER_BLOCK);
    }

    private static void addItemsToCombatTabItemGroup(FabricItemGroupEntries entries) {
        entries.add(INSTAGIB_GUN);
    }

    public static Item registerItem(String path, Function<Item.Settings, Item> factory, Item.Settings settings) {
        final RegistryKey<Item> registryKey = RegistryKey.of(RegistryKeys.ITEM, Identifier.of(Minehop.MOD_ID, path));
        Item item = factory.apply(settings.registryKey(registryKey));

        Registry.register(Registries.ITEM, registryKey, item);

        return item;
    }

    public static void initialize() {
    }

    public static void registerModItems() {
        Minehop.LOGGER.info("Registering Mod Items for " + Minehop.MOD_ID);

        Registry.register(Registries.ITEM_GROUP, MINEHOP_ITEM_GROUP_KEY, MINEHOP_ITEM_GROUP);
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.OPERATOR).register(ModItems::addItemsToOperatorTabItemGroup);
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(ModItems::addItemsToCombatTabItemGroup);
    }
}
