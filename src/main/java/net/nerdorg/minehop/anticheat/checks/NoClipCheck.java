package net.nerdorg.minehop.anticheat.checks;

import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;
import net.nerdorg.minehop.anticheat.AntiCheatCheck;
import net.nerdorg.minehop.anticheat.AntiCheatContext;

import java.util.Locale;

public final class NoClipCheck extends AntiCheatCheck {
    private static final double BODY_CONTRACT = 0.05D;
    private static final double SNEAK_SHALLOW_CONTACT_GRACE = 0.10D;

    public NoClipCheck() {
        super("NoClip", 4.0D, 8.0D, 0.05D);
    }

    @Override
    public CheckResult run(AntiCheatContext context) {
        if (context == null) {
            return CheckResult.OK;
        }
        if (context.justTeleported || context.creativeOrSpectator || context.usingPlotCreative) {
            return CheckResult.OK;
        }
        if (context.surfing || context.climbing) {
            return CheckResult.OK;
        }
        if (context.player.noClip || context.player.isSpectator()) {
            return CheckResult.OK;
        }
        if (context.player.getWorld() == null || context.player.getWorld().isClient) {
            return CheckResult.OK;
        }

        Box bb = context.player.getBoundingBox();
        World world = context.player.getWorld();
        SolidCollision collision = getSolidCollision(world, bb, context.player);
        if (collision != SolidCollision.NONE) {
            if (collision == SolidCollision.SHALLOW && hasSneakEdgeGrace(context)) {
                return CheckResult.OK;
            }
            Vec3d pos = context.postMovePosition;
            String details = String.format(
                    Locale.ROOT,
                    "feetInSolid pos=(%.2f,%.2f,%.2f)",
                    pos.x, pos.y, pos.z
            );
            return CheckResult.flagAndLagback(3.0D, details);
        }
        return CheckResult.OK;
    }

    private static boolean hasSneakEdgeGrace(AntiCheatContext context) {
        return context.player.isSneaking()
                && (context.onGround || context.wasOnGround)
                && Math.abs(context.movedDelta.y) <= 0.25D;
    }

    private static SolidCollision getSolidCollision(World world, Box bb, net.minecraft.entity.player.PlayerEntity player) {
        Box shrunk = bb.contract(BODY_CONTRACT);
        if (shrunk.maxX <= shrunk.minX || shrunk.maxY <= shrunk.minY || shrunk.maxZ <= shrunk.minZ) {
            return SolidCollision.NONE;
        }
        int minX = (int) Math.floor(shrunk.minX);
        int maxX = (int) Math.floor(shrunk.maxX);
        int minY = (int) Math.floor(shrunk.minY);
        int maxY = (int) Math.floor(shrunk.maxY);
        int minZ = (int) Math.floor(shrunk.minZ);
        int maxZ = (int) Math.floor(shrunk.maxZ);
        BlockPos.Mutable mutable = new BlockPos.Mutable();
        ShapeContext shapeContext = ShapeContext.of(player);
        boolean hasShallowCollision = false;
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    mutable.set(x, y, z);
                    BlockState state = world.getBlockState(mutable);
                    if (state.isAir()) {
                        continue;
                    }
                    VoxelShape shape = state.getCollisionShape(world, mutable, shapeContext);
                    if (shape.isEmpty()) {
                        continue;
                    }
                    Box offsetBox = shrunk.offset(-x, -y, -z);
                    for (Box partial : shape.getBoundingBoxes()) {
                        if (partial.intersects(offsetBox)) {
                            if (isDeepCollision(offsetBox, partial)) {
                                return SolidCollision.DEEP;
                            }
                            hasShallowCollision = true;
                        }
                    }
                }
            }
        }
        return hasShallowCollision ? SolidCollision.SHALLOW : SolidCollision.NONE;
    }

    private static boolean isDeepCollision(Box playerBox, Box blockBox) {
        double overlapX = Math.min(playerBox.maxX, blockBox.maxX) - Math.max(playerBox.minX, blockBox.minX);
        double overlapY = Math.min(playerBox.maxY, blockBox.maxY) - Math.max(playerBox.minY, blockBox.minY);
        double overlapZ = Math.min(playerBox.maxZ, blockBox.maxZ) - Math.max(playerBox.minZ, blockBox.minZ);
        if (overlapX <= 0.0D || overlapY <= 0.0D || overlapZ <= 0.0D) {
            return false;
        }
        double smallestPenetration = Math.min(overlapY, Math.min(overlapX, overlapZ));
        return smallestPenetration > SNEAK_SHALLOW_CONTACT_GRACE;
    }

    private enum SolidCollision {
        NONE,
        SHALLOW,
        DEEP
    }
}
