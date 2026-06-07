package net.nerdorg.minehop.client;

import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

public final class SurfStickPreviewState {
    private static final Object LOCK = new Object();
    private static List<BlockPos> points = List.of();
    private static double width = 1.0D;
    private static double drop = 1.0D;
    private static boolean oneSided = true;
    private static boolean outsideCurve = false;

    private SurfStickPreviewState() {
    }

    public static void update(
            List<BlockPos> newPoints,
            double newWidth,
            double newDrop,
            boolean newOneSided,
            boolean newOutsideCurve
    ) {
        synchronized (LOCK) {
            List<BlockPos> immutable = new ArrayList<>();
            if (newPoints != null) {
                for (BlockPos point : newPoints) {
                    if (point != null) {
                        immutable.add(point.toImmutable());
                    }
                }
            }
            points = immutable;
            width = newWidth;
            drop = newDrop;
            oneSided = newOneSided;
            outsideCurve = newOutsideCurve;
        }
    }

    public static void clear() {
        synchronized (LOCK) {
            points = List.of();
            width = 1.0D;
            drop = 1.0D;
            oneSided = true;
            outsideCurve = false;
        }
    }

    public static Snapshot snapshot() {
        synchronized (LOCK) {
            return new Snapshot(new ArrayList<>(points), width, drop, oneSided, outsideCurve);
        }
    }

    public record Snapshot(List<BlockPos> points, double width, double drop, boolean oneSided, boolean outsideCurve) {
    }
}
