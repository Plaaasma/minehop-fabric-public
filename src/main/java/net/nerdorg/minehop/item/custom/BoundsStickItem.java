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
        if (context.getWorld() instanceof ServerWorld) {
            ServerPlayerEntity serverPlayerEntity = (ServerPlayerEntity) context.getPlayer();
            if (serverPlayerEntity == null) {
                return ActionResult.FAIL;
            }

            BlockPos currentPos = context.getBlockPos();
            if (pos1 == null || pos2 != null) {
                pos2 = null;
                pos1 = currentPos;

                Logger.logSuccess(serverPlayerEntity, "Position 1 set to " + pos1.toShortString());
            } else {
                pos2 = currentPos;

                // Ensure pos1 is the min position and pos2 is the max position
                adjustPositions();

                Logger.logSuccess(serverPlayerEntity, "Position 2 set to " + pos2.toShortString());
            }
        }
        return ActionResult.SUCCESS;
    }

    private void adjustPositions() {
        int minX = Math.min(pos1.getX(), pos2.getX());
        int minY = Math.min(pos1.getY(), pos2.getY());
        int minZ = Math.min(pos1.getZ(), pos2.getZ());
        int maxX = Math.max(pos1.getX(), pos2.getX());
        int maxY = Math.max(pos1.getY(), pos2.getY());
        int maxZ = Math.max(pos1.getZ(), pos2.getZ());

        pos1 = new BlockPos(minX, minY, minZ).add(0, 1, 0);
        pos2 = new BlockPos(maxX, maxY, maxZ).add(0, 1, 0);
    }
}
