package net.nerdorg.minehop.client;

import net.minecraft.util.math.BlockPos;

public final class BoundsStickPreviewState {
    private static BlockPos firstPos;
    private static BlockPos secondPos;

    private BoundsStickPreviewState() {
    }

    public static void update(BlockPos first, BlockPos second) {
        firstPos = first == null ? null : first.toImmutable();
        secondPos = second == null ? null : second.toImmutable();
    }

    public static void clear() {
        firstPos = null;
        secondPos = null;
    }

    public static Snapshot snapshot() {
        return new Snapshot(firstPos, secondPos);
    }

    public record Snapshot(BlockPos first, BlockPos second) {
    }
}
