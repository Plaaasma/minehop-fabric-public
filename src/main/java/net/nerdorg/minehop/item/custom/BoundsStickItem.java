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

                if (this.pos2 != null) {
                    BlockPos newPos1 = this.pos1;
                    BlockPos newPos2 = this.pos2;

                    if (newPos2.getX() < newPos1.getX()) {
                        newPos2 = newPos2.add(1, 0, 0);
                    } else {
                        newPos1 = newPos1.add(1, 0, 0);
                    }

                    if (newPos2.getY() < newPos1.getY()) {
                        newPos2 = newPos2.add(0, 1, 0);
                    } else {
                        newPos1 = newPos1.add(0, 1, 0);
                    }

                    if (newPos2.getZ() < newPos1.getZ()) {
                        newPos2 = newPos2.add(0, 0, 1);
                    } else {
                        newPos1 = newPos1.add(0, 0, 1);
                    }

                    this.pos1 = newPos1;
                    this.pos2 = newPos2;
                }

                Logger.logSuccess(serverPlayerEntity, "Position 1 set to " + this.pos1.toShortString());
                this.first_coord = false;
            }
            else {
                this.pos2 = context.getBlockPos();

                BlockPos newPos1 = this.pos1;
                BlockPos newPos2 = this.pos2;

                if (newPos2.getX() < newPos1.getX()) {
                    newPos2 = newPos2.add(1, 0, 0);
                }
                else {
                    newPos1 = newPos1.add(1, 0, 0);
                }

                if (newPos2.getY() < newPos1.getY()) {
                    newPos2 = newPos2.add(0, 1, 0);
                }
                else {
                    newPos1 = newPos1.add(0, 1, 0);
                }

                if (newPos2.getZ() < newPos1.getZ()) {
                    newPos2 = newPos2.add(0, 0, 1);
                }
                else {
                    newPos1 = newPos1.add(0, 0, 1);
                }

                this.pos1 = newPos1;
                this.pos2 = newPos2;

                Logger.logSuccess(serverPlayerEntity, "Position 2 set to " + this.pos2.toShortString());
                first_coord = true;
            }
        }
        return super.useOnBlock(context);
    }
}
