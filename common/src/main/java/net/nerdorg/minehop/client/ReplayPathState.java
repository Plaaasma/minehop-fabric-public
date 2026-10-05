package net.nerdorg.minehop.client;

import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.Vec3;

public final class ReplayPathState {
    private static final Object LOCK = new Object();
    private static final List<Vec3> POINTS = new ArrayList<>();

    private ReplayPathState() {
    }

    public static void clear() {
        synchronized (LOCK) {
            POINTS.clear();
        }
    }

    public static void append(List<Vector3f> points) {
        if (points == null || points.isEmpty()) {
            return;
        }
        synchronized (LOCK) {
            for (Vector3f point : points) {
                if (point == null) {
                    continue;
                }
                double x = point.x();
                double y = point.y();
                double z = point.z();
                if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
                    continue;
                }
                POINTS.add(new Vec3(x, y, z));
            }
        }
    }

    public static List<Vec3> snapshot() {
        synchronized (LOCK) {
            return List.copyOf(POINTS);
        }
    }
}
