package net.nerdorg.minehop.item.custom;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.nerdorg.minehop.util.SurfRampPlacementManager;

public class SurfStickItem extends Item {
    public SurfStickItem(Properties settings) {
        super(settings);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level world = context.getLevel();
        if (world.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        if (!(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.FAIL;
        }

        SurfRampPlacementManager.setPoint(player, context.getClickedPos());
        return InteractionResult.SUCCESS;
    }
}
