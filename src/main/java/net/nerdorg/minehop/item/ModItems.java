package net.nerdorg.minehop.item;

import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTabOutput;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.block.ModBlocks;
import net.nerdorg.minehop.item.custom.BoundsStickItem;
import net.nerdorg.minehop.item.custom.InstagibItem;
import net.nerdorg.minehop.item.custom.SurfStickItem;

import java.util.function.Function;

public class ModItems {
    public static final Item BOUNDS_STICK = registerItem("bounds_stick", BoundsStickItem::new, new Item.Properties());
    public static final Item SURF_STICK = registerItem("surf_stick", SurfStickItem::new, new Item.Properties().stacksTo(1));
    public static final Item INSTAGIB_GUN = registerItem("instagib_gun", InstagibItem::new, new Item.Properties());
    public static final ResourceKey<CreativeModeTab> MINEHOP_ITEM_GROUP_KEY = ResourceKey.create(
            Registries.CREATIVE_MODE_TAB,
            Identifier.fromNamespaceAndPath(Minehop.MOD_ID, "minehop")
    );
    public static final CreativeModeTab MINEHOP_ITEM_GROUP = FabricCreativeModeTab.builder()
            .icon(() -> new ItemStack(SURF_STICK))
            .title(Component.translatable("itemGroup.minehop.minehop"))
            .displayItems((displayContext, entries) -> {
                entries.accept(SURF_STICK);
                entries.accept(BOUNDS_STICK);
                entries.accept(INSTAGIB_GUN);
                entries.accept(ModBlocks.BOOSTER_BLOCK);
            })
            .build();

    private static void addItemsToOperatorTabItemGroup(FabricCreativeModeTabOutput entries) {
        entries.accept(BOUNDS_STICK);
        entries.accept(SURF_STICK);
        entries.accept(ModBlocks.BOOSTER_BLOCK);
    }

    private static void addItemsToCombatTabItemGroup(FabricCreativeModeTabOutput entries) {
        entries.accept(INSTAGIB_GUN);
    }

    public static Item registerItem(String path, Function<Item.Properties, Item> factory, Item.Properties settings) {
        final ResourceKey<Item> registryKey = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(Minehop.MOD_ID, path));
        Item item = factory.apply(settings.setId(registryKey));

        Registry.register(BuiltInRegistries.ITEM, registryKey, item);

        return item;
    }

    public static void initialize() {
    }

    public static void registerModItems() {
        Minehop.LOGGER.info("Registering Mod Items for " + Minehop.MOD_ID);

        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, MINEHOP_ITEM_GROUP_KEY, MINEHOP_ITEM_GROUP);
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.OP_BLOCKS).register(ModItems::addItemsToOperatorTabItemGroup);
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(ModItems::addItemsToCombatTabItemGroup);
    }
}
