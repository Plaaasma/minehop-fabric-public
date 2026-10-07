package net.nerdorg.minehop.replays;

import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns a replay into the point list sent for /spec ... path: at most {@link #MAX_POINTS} points (one point per
 * recorded frame was up to 72k points, 1.44 MB in 59 packets), split at teleports (reset zones, checkpoints) so no
 * line is drawn across them, and simplified with Ramer-Douglas-Peucker using the smallest tolerance that fits, so
 * the drawn route stays within a few centimetres of the recorded one for normal runs.
 *
 * <p>The polylines are separated by a NaN point. Clients up to 1.1.6 skip non-finite points (they then draw a line
 * across the teleport, as before); newer clients break the line there.
 */
public final class ReplayPathSimplifier {
    public static final int MAX_POINTS = 4096;
    /** A step longer than this between two frames is a teleport, not movement (~160 blocks/s). */
    public static final double TELEPORT_STEP = 8.0D;
    private static final double MIN_TOLERANCE = 0.02D;
    private static final double MAX_TOLERANCE = 64.0D;

    private ReplayPathSimplifier() {
    }

    /** Points to send, plus how they were made: the RDP tolerance used (blocks) and the number of polylines. */
    public record Result(List<Vector3f> points, double tolerance, int polylines) {
    }

    public static List<Vector3f> simplify(List<ReplayManager.ReplayEntry> entries) {
        return simplify(entries, MAX_POINTS).points();
    }

    public static Result simplify(List<ReplayManager.ReplayEntry> entries, int maxPoints) {
        List<double[]> lines = splitAtTeleports(entries);
        if (lines.isEmpty()) {
            return new Result(List.of(), 0.0D, 0);
        }
        double tolerance = MIN_TOLERANCE;
        List<boolean[]> kept = keep(lines, tolerance);
        if (count(lines, kept) > maxPoints) {
            // Smallest tolerance that fits: binary search (the count only falls as the tolerance grows).
            double low = MIN_TOLERANCE;
            double high = MAX_TOLERANCE;
            List<boolean[]> best = null;
            for (int i = 0; i < 24; i++) {
                double mid = (low + high) / 2.0D;
                List<boolean[]> candidate = keep(lines, mid);
                if (count(lines, candidate) <= maxPoints) {
                    best = candidate;
                    high = mid;
                } else {
                    low = mid;
                }
            }
            kept = best != null ? best : keep(lines, MAX_TOLERANCE);
            tolerance = best != null ? high : MAX_TOLERANCE;
        }
        List<Vector3f> out = new ArrayList<>();
        for (int l = 0; l < lines.size(); l++) {
            double[] line = lines.get(l);
            boolean[] keep = kept.get(l);
            if (l > 0) {
                out.add(new Vector3f(Float.NaN, Float.NaN, Float.NaN));
            }
            for (int i = 0; i < keep.length; i++) {
                if (keep[i]) {
                    out.add(new Vector3f((float) line[i * 3], (float) line[i * 3 + 1], (float) line[i * 3 + 2]));
                }
            }
        }
        // Still too many only if there are more teleports than the budget allows: drop whole trailing segments.
        List<Vector3f> points = out.size() > maxPoints ? new ArrayList<>(out.subList(0, maxPoints)) : out;
        return new Result(points, tolerance, lines.size());
    }

    /** Finite frames as polylines (x,y,z packed), split where the step is a teleport; repeated points dropped. */
    private static List<double[]> splitAtTeleports(List<ReplayManager.ReplayEntry> entries) {
        List<double[]> lines = new ArrayList<>();
        if (entries == null) {
            return lines;
        }
        double[] current = new double[Math.min(entries.size(), 4096) * 3];
        int size = 0;
        double lastX = Double.NaN;
        double lastY = Double.NaN;
        double lastZ = Double.NaN;
        for (ReplayManager.ReplayEntry entry : entries) {
            if (entry == null || !Double.isFinite(entry.x) || !Double.isFinite(entry.y) || !Double.isFinite(entry.z)) {
                continue;
            }
            if (size > 0) {
                double dx = entry.x - lastX;
                double dy = entry.y - lastY;
                double dz = entry.z - lastZ;
                double d2 = dx * dx + dy * dy + dz * dz;
                if (d2 < 1.0E-8D) {
                    continue;
                }
                if (d2 > TELEPORT_STEP * TELEPORT_STEP) {
                    if (size >= 2) {
                        lines.add(java.util.Arrays.copyOf(current, size * 3));
                    }
                    size = 0;
                }
            }
            if ((size + 1) * 3 > current.length) {
                current = java.util.Arrays.copyOf(current, current.length * 2);
            }
            current[size * 3] = entry.x;
            current[size * 3 + 1] = entry.y;
            current[size * 3 + 2] = entry.z;
            size++;
            lastX = entry.x;
            lastY = entry.y;
            lastZ = entry.z;
        }
        if (size >= 2) {
            lines.add(java.util.Arrays.copyOf(current, size * 3));
        }
        return lines;
    }

    private static int count(List<double[]> lines, List<boolean[]> kept) {
        int total = lines.size() - 1; // separators
        for (boolean[] keep : kept) {
            for (boolean k : keep) {
                if (k) {
                    total++;
                }
            }
        }
        return total;
    }

    private static List<boolean[]> keep(List<double[]> lines, double tolerance) {
        List<boolean[]> kept = new ArrayList<>(lines.size());
        for (double[] line : lines) {
            kept.add(douglasPeucker(line, tolerance));
        }
        return kept;
    }

    /** Ramer-Douglas-Peucker on one polyline (iterative), keeping points farther than tolerance from the chord. */
    private static boolean[] douglasPeucker(double[] p, double tolerance) {
        int n = p.length / 3;
        boolean[] keep = new boolean[n];
        keep[0] = true;
        keep[n - 1] = true;
        double tolerance2 = tolerance * tolerance;
        int[] stack = new int[Math.max(16, n * 2)];
        int top = 0;
        stack[top++] = 0;
        stack[top++] = n - 1;
        while (top > 0) {
            int last = stack[--top];
            int first = stack[--top];
            if (last - first < 2) {
                continue;
            }
            double ax = p[first * 3], ay = p[first * 3 + 1], az = p[first * 3 + 2];
            double bx = p[last * 3] - ax, by = p[last * 3 + 1] - ay, bz = p[last * 3 + 2] - az;
            double len2 = bx * bx + by * by + bz * bz;
            double maxD2 = -1.0D;
            int index = -1;
            for (int i = first + 1; i < last; i++) {
                double vx = p[i * 3] - ax, vy = p[i * 3 + 1] - ay, vz = p[i * 3 + 2] - az;
                double d2;
                if (len2 < 1.0E-12D) {
                    d2 = vx * vx + vy * vy + vz * vz;
                } else {
                    double t = (vx * bx + vy * by + vz * bz) / len2;
                    t = t < 0.0D ? 0.0D : (t > 1.0D ? 1.0D : t);
                    double cx = vx - t * bx, cy = vy - t * by, cz = vz - t * bz;
                    d2 = cx * cx + cy * cy + cz * cz;
                }
                if (d2 > maxD2) {
                    maxD2 = d2;
                    index = i;
                }
            }
            if (maxD2 > tolerance2) {
                keep[index] = true;
                if (top + 4 > stack.length) {
                    stack = java.util.Arrays.copyOf(stack, stack.length * 2);
                }
                stack[top++] = first;
                stack[top++] = index;
                stack[top++] = index;
                stack[top++] = last;
            }
        }
        return keep;
    }
}
