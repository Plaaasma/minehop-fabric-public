package net.nerdorg.minehop.client;

import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * The replay route sent by /spec ... path, as polylines. A non-finite point from the server starts a new polyline
 * (the run teleported there), so no line is drawn across a teleport. The renderer reads an immutable snapshot that
 * is rebuilt only when points arrive, not copied every frame.
 */
public final class ReplayPathState {
    /** Ignore anything beyond this (the server sends at most 4096 points per path). */
    private static final int MAX_POINTS = 65_536;
    private static final Object LOCK = new Object();
    private static final List<float[]> RAW = new ArrayList<>();
    private static volatile List<double[]> lines = List.of();
    private static volatile int pointCount = 0;

    private ReplayPathState() {
    }

    public static void clear() {
        synchronized (LOCK) {
            RAW.clear();
            lines = List.of();
            pointCount = 0;
        }
    }

    public static void append(List<Vector3f> points) {
        if (points == null || points.isEmpty()) {
            return;
        }
        synchronized (LOCK) {
            for (Vector3f point : points) {
                if (point == null || RAW.size() >= MAX_POINTS) {
                    continue;
                }
                RAW.add(new float[]{point.x(), point.y(), point.z()});
            }
            rebuild();
        }
    }

    /** The polylines (packed x,y,z), each with at least two points. Don't modify. */
    public static List<double[]> lines() {
        return lines;
    }

    /** Points over all polylines. */
    public static int pointCount() {
        return pointCount;
    }

    private static void rebuild() {
        List<double[]> built = new ArrayList<>();
        double[] current = new double[RAW.size() * 3];
        int size = 0;
        int total = 0;
        for (float[] p : RAW) {
            boolean finite = Float.isFinite(p[0]) && Float.isFinite(p[1]) && Float.isFinite(p[2]);
            if (!finite) {
                if (size >= 2) {
                    built.add(java.util.Arrays.copyOf(current, size * 3));
                    total += size;
                }
                size = 0;
                continue;
            }
            current[size * 3] = p[0];
            current[size * 3 + 1] = p[1];
            current[size * 3 + 2] = p[2];
            size++;
        }
        if (size >= 2) {
            built.add(java.util.Arrays.copyOf(current, size * 3));
            total += size;
        }
        lines = List.copyOf(built);
        pointCount = total;
    }
}
