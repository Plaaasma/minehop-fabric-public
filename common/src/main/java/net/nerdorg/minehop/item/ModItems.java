package net.nerdorg.minehop.item;

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
import net.nerdorg.minehop.platform.Services;
import net.nerdorg.minehop.platform.registry.RegistryEntry;

import java.util.function.Function;

public class ModItems {
    public static final RegistryEntry<Item> BOUNDS_STICK = registerItem("bounds_stick", BoundsStickItem::new, new Item.Properties());
    public static final RegistryEntry<Item> SURF_STICK = registerItem("surf_stick", SurfStickItem::new, new Item.Properties().stacksTo(1));
    public static final RegistryEntry<Item> INSTAGIB_GUN = registerItem("instagib_gun", InstagibItem::new, new Item.Properties());
    public static final ResourceKey<CreativeModeTab> MINEHOP_ITEM_GROUP_KEY = ResourceKey.create(
            Registries.CREATIVE_MODE_TAB,
            Identifier.fromNamespaceAndPath(Minehop.MOD_ID, "minehop")
    );
    // Registered in registerModItems(), like before the multiloader migration.
    public static RegistryEntry<CreativeModeTab> MINEHOP_ITEM_GROUP;

    private static CreativeModeTab buildItemGroup() {
        return Services.REGISTRY.creativeModeTabBuilder()
            .icon(() -> new ItemStack(SURF_STICK.get()))
            .title(Component.translatable("itemGroup.minehop.minehop"))
            .displayItems((displayContext, entries) -> {
                entries.accept(SURF_STICK.get());
                entries.accept(BOUNDS_STICK.get());
                entries.accept(INSTAGIB_GUN.get());
                entries.accept(ModBlocks.BOOSTER_BLOCK.get());
            })
            .build();
    }

    private static void addItemsToOperatorTabItemGroup(CreativeModeTab.Output entries) {
        entries.accept(BOUNDS_STICK.get());
        entries.accept(SURF_STICK.get());
        entries.accept(ModBlocks.BOOSTER_BLOCK.get());
    }

    private static void addItemsToCombatTabItemGroup(CreativeModeTab.Output entries) {
        entries.accept(INSTAGIB_GUN.get());
    }

    public static RegistryEntry<Item> registerItem(String path, Function<Item.Properties, Item> factory, Item.Properties settings) {
        final ResourceKey<Item> registryKey = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(Minehop.MOD_ID, path));

        return Services.REGISTRY.register(registryKey, () -> factory.apply(settings.setId(registryKey)));
    }

    public static void initialize() {
    }

    public static void registerModItems() {
        Minehop.LOGGER.info("Registering Mod Items for " + Minehop.MOD_ID);

        MINEHOP_ITEM_GROUP = Services.REGISTRY.register(MINEHOP_ITEM_GROUP_KEY, ModItems::buildItemGroup);
        Services.REGISTRY.modifyCreativeModeTab(CreativeModeTabs.OP_BLOCKS, ModItems::addItemsToOperatorTabItemGroup);
        Services.REGISTRY.modifyCreativeModeTab(CreativeModeTabs.COMBAT, ModItems::addItemsToCombatTabItemGroup);
    }
}
