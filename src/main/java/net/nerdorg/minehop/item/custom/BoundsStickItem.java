package net.nerdorg.minehop.item.custom;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.nerdorg.minehop.util.Logger;

public class BoundsStickItem extends Item {
    public boolean first_coord = true;
    public BlockPos pos1;
    public BlockPos pos2;

    public BoundsStickItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        if (context.getWorld() instanceof ServerWorld serverWorld) {
            ServerPlayerEntity serverPlayerEntity = (ServerPlayerEntity) context.getPlayer();
            if (this.first_coord) {
                this.pos1 = context.getBlockPos();
                Logger.logSuccess(serverPlayerEntity, "Position 1 set to " + this.pos1.toShortString());
                this.first_coord = false;
            }
            else {
                this.pos2 = context.getBlockPos();
                Logger.logSuccess(serverPlayerEntity, "Position 2 set to " + this.pos2.toShortString());
                first_coord = true;
            }
        }
        return super.useOnBlock(context);
    }
}
