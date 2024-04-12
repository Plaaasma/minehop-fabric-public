package net.nerdorg.minehop.item.custom;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import net.nerdorg.minehop.util.Logger;

import java.util.HashMap;

public class BoundsStickItem extends Item {
    public static final HashMap<String, BlockPos[]> playerPositions = new HashMap<>();

    public BoundsStickItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        World world = context.getWorld();
        if (!world.isClient) {
            ServerPlayerEntity player = (ServerPlayerEntity) context.getPlayer();
            if (player == null) {
                return ActionResult.FAIL;
            }

            BlockPos currentPos = context.getBlockPos();
            String playerName = player.getNameForScoreboard();
            BlockPos[] positions = playerPositions.getOrDefault(playerName, new BlockPos[2]);

            // If first position is not set or both positions are already set, reset to first position
            if (positions[0] == null || positions[1] != null) {
                positions[0] = currentPos;
                positions[1] = null;
                playerPositions.put(playerName, positions); // Update the map with the new positions

                Logger.logSuccess(player, "Position 1 set to " + positions[0].toShortString());
            } else { // Set second position
                positions[1] = currentPos;
                playerPositions.put(playerName, positions); // Update the map with the new positions

                // No need to adjust positions here since we're storing them directly in the Box map later
                Logger.logSuccess(player, "Position 2 set to " + positions[1].toShortString());
            }

            return ActionResult.SUCCESS;
        }
        return ActionResult.PASS;
    }
}
