package net.nerdorg.minehop.item.custom;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.nerdorg.minehop.networking.PacketHandler;
import net.nerdorg.minehop.util.Logger;
import net.nerdorg.minehop.util.MapCreationManager;
import net.nerdorg.minehop.util.UserPlotManager;

import java.util.HashMap;

public class BoundsStickItem extends Item {
    public static final HashMap<String, BlockPos[]> playerPositions = new HashMap<>();

    public BoundsStickItem(Properties settings) {
        super(settings);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level world = context.getLevel();
        if (!world.isClientSide) {
            if (!(context.getPlayer() instanceof ServerPlayer player)) {
                return InteractionResult.FAIL;
            }

            if (player.isShiftKeyDown()) {
                if (UserPlotManager.canOpenMapManager(player)) {
                    MapCreationManager.openGui(player);
                } else {
                    Logger.logFailure(player, "Open map creator while standing in your own plot.");
                }
                return InteractionResult.SUCCESS;
            }

            if (!player.hasPermissions(4)
                    && world instanceof ServerLevel serverWorld
                    && !UserPlotManager.canBuildAt(player, serverWorld, context.getClickedPos())) {
                Logger.logFailure(player, "You can only use the zone stick inside your own plot.");
                return InteractionResult.FAIL;
            }

            BlockPos[] positions = updateSelection(player, context.getClickedPos());
            PacketHandler.sendBoundsStickSelection(player, positions[0], positions[1]);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.SUCCESS;
    }

    public static BlockPos[] resolveBoundsCorners(BlockPos first, BlockPos second) {
        if (first == null || second == null) {
            return new BlockPos[]{first, second};
        }

        int minX = Math.min(first.getX(), second.getX());
        int minY = Math.min(first.getY(), second.getY());
        int minZ = Math.min(first.getZ(), second.getZ());

        int maxX = Math.max(first.getX(), second.getX()) + 1;
        int maxY = Math.max(first.getY(), second.getY()) + 1;
        int maxZ = Math.max(first.getZ(), second.getZ()) + 1;

        return new BlockPos[]{
                new BlockPos(minX, minY, minZ),
                new BlockPos(maxX, maxY, maxZ)
        };
    }

    public static void clearSelection(ServerPlayer player) {
        if (player == null) {
            return;
        }
        playerPositions.remove(player.getScoreboardName());
        PacketHandler.sendBoundsStickSelection(player, null, null);
    }

    private static BlockPos[] updateSelection(Player player, BlockPos clickedPos) {
        String playerName = player.getScoreboardName();
        BlockPos[] positions = playerPositions.getOrDefault(playerName, new BlockPos[2]);

        if (positions[0] == null || positions[1] != null) {
            positions[0] = clickedPos.immutable();
            positions[1] = null;
            playerPositions.put(playerName, positions);
            Logger.logSuccess(player, "Zone corner 1 set to " + positions[0].toShortString());
            Logger.log(player, net.minecraft.network.chat.Component.literal("Zone Stick usage:"));
            Logger.log(player, net.minecraft.network.chat.Component.literal("1) Right-click another block to set corner 2."));
            Logger.log(player, net.minecraft.network.chat.Component.literal("2) Add zones with /plot zone start, /plot zone reset [checkpoint], and /plot zone end."));
            Logger.log(player, net.minecraft.network.chat.Component.literal("3) Optional: open map creator (sneak + right-click with the Zone Stick, /map plotmanage, or /map manage if op)."));
            return positions;
        }

        BlockPos[] converted = resolveBoundsCorners(positions[0], clickedPos);
        positions[0] = converted[0];
        positions[1] = converted[1];
        playerPositions.put(playerName, positions);
        Logger.logSuccess(player, "Position 2 set. Bounds: " + positions[0].toShortString() + " -> " + positions[1].toShortString());
        Logger.log(player, net.minecraft.network.chat.Component.literal("Zone bounds ready. Use map creator to add a start/reset/end zone."));
        return positions;
    }
}
