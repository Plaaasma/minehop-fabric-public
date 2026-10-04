package net.nerdorg.minehop.anticheat.checks;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.nerdorg.minehop.anticheat.AntiCheatCheck;
import net.nerdorg.minehop.anticheat.AntiCheatContext;

import java.util.Locale;

public final class NoClipCheck extends AntiCheatCheck {
    private static final double BODY_CONTRACT = 0.05D;
    private static final double SNEAK_SHALLOW_CONTACT_GRACE = 0.10D;

    // Flag-only: vanilla already refuses moves into blocks (it teleports the client back), so this
    // only records evidence. Lagging back here once looped a player who was legitimately stuck.
    public NoClipCheck() {
        super("NoClip", 4.0D, Double.MAX_VALUE, 0.05D);
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
        if (context.player.noPhysics || context.player.isSpectator()) {
            return CheckResult.OK;
        }
        if (context.player.level() == null || context.player.level().isClientSide) {
            return CheckResult.OK;
        }

        AABB bb = context.player.getBoundingBox();
        Level world = context.player.level();
        SolidCollision collision = getSolidCollision(world, bb, context.player);
        if (collision != SolidCollision.NONE) {
            if (collision == SolidCollision.SHALLOW && hasSneakEdgeGrace(context)) {
                return CheckResult.OK;
            }
            Vec3 pos = context.postMovePosition;
            String details = String.format(
                    Locale.ROOT,
                    "feetInSolid pos=(%.2f,%.2f,%.2f)",
                    pos.x, pos.y, pos.z
            );
            return CheckResult.flag(3.0D, details);
        }
        return CheckResult.OK;
    }

    private static boolean hasSneakEdgeGrace(AntiCheatContext context) {
        return context.player.isShiftKeyDown()
                && (context.onGround || context.wasOnGround)
                && Math.abs(context.movedDelta.y) <= 0.25D;
    }

    private static SolidCollision getSolidCollision(Level world, AABB bb, net.minecraft.world.entity.player.Player player) {
        AABB shrunk = bb.deflate(BODY_CONTRACT);
        if (shrunk.maxX <= shrunk.minX || shrunk.maxY <= shrunk.minY || shrunk.maxZ <= shrunk.minZ) {
            return SolidCollision.NONE;
        }
        int minX = (int) Math.floor(shrunk.minX);
        int maxX = (int) Math.floor(shrunk.maxX);
        int minY = (int) Math.floor(shrunk.minY);
        int maxY = (int) Math.floor(shrunk.maxY);
        int minZ = (int) Math.floor(shrunk.minZ);
        int maxZ = (int) Math.floor(shrunk.maxZ);
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        CollisionContext shapeContext = CollisionContext.of(player);
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
                    AABB offsetBox = shrunk.move(-x, -y, -z);
                    for (AABB partial : shape.toAabbs()) {
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

    private static boolean isDeepCollision(AABB playerBox, AABB blockBox) {
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
