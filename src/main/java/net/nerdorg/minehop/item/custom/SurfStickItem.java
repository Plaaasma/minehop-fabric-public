package net.nerdorg.minehop.item.custom;

import net.minecraft.item.Item;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.world.World;
import net.nerdorg.minehop.util.SurfRampPlacementManager;

public class SurfStickItem extends Item {
    public SurfStickItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        World world = context.getWorld();
        if (world.isClient) {
            return ActionResult.SUCCESS;
        }

        if (!(context.getPlayer() instanceof ServerPlayerEntity player)) {
            return ActionResult.FAIL;
        }

        SurfRampPlacementManager.setPoint(player, context.getBlockPos());
        return ActionResult.SUCCESS;
    }
}
