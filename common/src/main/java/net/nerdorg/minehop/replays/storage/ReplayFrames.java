package net.nerdorg.minehop.replays.storage;

import java.util.Arrays;

/**
 * The recorded frames of one run, one array per field. Immutable once built, so it can be handed between the server
 * thread and the replay IO threads and shared by every ghost and cache that shows the same run. About 48 bytes per
 * frame (a List of ReplayManager.ReplayEntry objects took about 100).
 *
 * <p>Pure Java (no Minecraft classes): the codec and its self-test run without a game.
 */
public final class ReplayFrames {
    /** Per-frame flag bits. Present in the MHRP format from version 1; zero until recording fills them (phase 3). */
    public static final int FLAG_ON_GROUND = 1;
    public static final int FLAG_JUMPED = 1 << 1;
    public static final int FLAG_SNEAKING = 1 << 2;
    /** The player was moved without travelling (teleport, reset, checkpoint): don't interpolate into this frame. */
    public static final int FLAG_DISCONTINUITY = 1 << 3;
    public static final int FLAG_INPUT_FORWARD = 1 << 4;
    public static final int FLAG_INPUT_BACK = 1 << 5;
    public static final int FLAG_INPUT_LEFT = 1 << 6;
    public static final int FLAG_INPUT_RIGHT = 1 << 7;
    public static final int FLAG_INPUT_JUMP = 1 << 8;
    public static final int FLAG_INPUT_SNEAK = 1 << 9;
    public static final int FLAG_INPUT_SPRINT = 1 << 10;
    /** Set by the encoder on a frame whose recorded values were not finite (or out of range) and were replaced. */
    public static final int FLAG_REPAIRED = 1 << 15;

    public static final ReplayFrames EMPTY = new Builder(0).build();

    private final int size;
    private final double[] x;
    private final double[] y;
    private final double[] z;
    private final float[] yaw;
    private final float[] pitch;
    private final int[] jumpCount;
    private final float[] lastJumpSpeed;
    private final float[] efficiency;
    private final int[] flags;
    private final double[] bounds;

    private ReplayFrames(int size, double[] x, double[] y, double[] z, float[] yaw, float[] pitch, int[] jumpCount,
                         float[] lastJumpSpeed, float[] efficiency, int[] flags) {
        this.size = size;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.jumpCount = jumpCount;
        this.lastJumpSpeed = lastJumpSpeed;
        this.efficiency = efficiency;
        this.flags = flags;
        this.bounds = computeBounds();
    }

    public int size() {
        return this.size;
    }

    public boolean isEmpty() {
        return this.size == 0;
    }

    public double x(int i) {
        return this.x[i];
    }

    public double y(int i) {
        return this.y[i];
    }

    public double z(int i) {
        return this.z[i];
    }

    /** Head yaw in degrees (as recorded, not wrapped). */
    public float yaw(int i) {
        return this.yaw[i];
    }

    /** Pitch in degrees. */
    public float pitch(int i) {
        return this.pitch[i];
    }

    public int jumpCount(int i) {
        return this.jumpCount[i];
    }

    /** Horizontal speed at the last jump, blocks per tick. */
    public float lastJumpSpeed(int i) {
        return this.lastJumpSpeed[i];
    }

    /** Strafe efficiency, 0..100. */
    public float efficiency(int i) {
        return this.efficiency[i];
    }

    public int flags(int i) {
        return this.flags[i];
    }

    /**
     * {minX, minY, minZ, maxX, maxY, maxZ} of the finite positions, or null if there are none. The returned array is
     * a copy.
     */
    public double[] bounds() {
        return this.bounds == null ? null : this.bounds.clone();
    }

    /** Rough heap size, for the frame cache's budget. */
    public long approxBytes() {
        return 96L + this.x.length * 48L;
    }

    private double[] computeBounds() {
        double[] b = null;
        for (int i = 0; i < this.size; i++) {
            double px = this.x[i];
            double py = this.y[i];
            double pz = this.z[i];
            if (!Double.isFinite(px) || !Double.isFinite(py) || !Double.isFinite(pz)) {
                continue;
            }
            if (b == null) {
                b = new double[]{px, py, pz, px, py, pz};
            } else {
                b[0] = Math.min(b[0], px);
                b[1] = Math.min(b[1], py);
                b[2] = Math.min(b[2], pz);
                b[3] = Math.max(b[3], px);
                b[4] = Math.max(b[4], py);
                b[5] = Math.max(b[5], pz);
            }
        }
        return b;
    }

    /** Field-by-field equality (positions and floats compared exactly). */
    public boolean sameAs(ReplayFrames other) {
        if (other == null || other.size != this.size) {
            return false;
        }
        for (int i = 0; i < this.size; i++) {
            if (Double.compare(this.x[i], other.x[i]) != 0 || Double.compare(this.y[i], other.y[i]) != 0
                    || Double.compare(this.z[i], other.z[i]) != 0 || Float.compare(this.yaw[i], other.yaw[i]) != 0
                    || Float.compare(this.pitch[i], other.pitch[i]) != 0 || this.jumpCount[i] != other.jumpCount[i]
                    || Float.compare(this.lastJumpSpeed[i], other.lastJumpSpeed[i]) != 0
                    || Float.compare(this.efficiency[i], other.efficiency[i]) != 0 || this.flags[i] != other.flags[i]) {
                return false;
            }
        }
        return true;
    }

    public static Builder builder(int expectedSize) {
        return new Builder(expectedSize);
    }

    /** Appends frames in order; {@link #build()} trims the arrays. Not thread safe. */
    public static final class Builder {
        private int size;
        private double[] x;
        private double[] y;
        private double[] z;
        private float[] yaw;
        private float[] pitch;
        private int[] jumpCount;
        private float[] lastJumpSpeed;
        private float[] efficiency;
        private int[] flags;

        private Builder(int expectedSize) {
            int capacity = Math.max(0, expectedSize);
            this.x = new double[capacity];
            this.y = new double[capacity];
            this.z = new double[capacity];
            this.yaw = new float[capacity];
            this.pitch = new float[capacity];
            this.jumpCount = new int[capacity];
            this.lastJumpSpeed = new float[capacity];
            this.efficiency = new float[capacity];
            this.flags = new int[capacity];
        }

        public int size() {
            return this.size;
        }

        public Builder add(double x, double y, double z, float yaw, float pitch, int jumpCount, float lastJumpSpeed,
                           float efficiency, int flags) {
            if (this.size == this.x.length) {
                grow();
            }
            int i = this.size++;
            this.x[i] = x;
            this.y[i] = y;
            this.z[i] = z;
            this.yaw[i] = yaw;
            this.pitch[i] = pitch;
            this.jumpCount[i] = jumpCount;
            this.lastJumpSpeed[i] = lastJumpSpeed;
            this.efficiency[i] = efficiency;
            this.flags[i] = flags;
            return this;
        }

        private void grow() {
            int capacity = Math.max(16, this.x.length + (this.x.length >> 1));
            this.x = Arrays.copyOf(this.x, capacity);
            this.y = Arrays.copyOf(this.y, capacity);
            this.z = Arrays.copyOf(this.z, capacity);
            this.yaw = Arrays.copyOf(this.yaw, capacity);
            this.pitch = Arrays.copyOf(this.pitch, capacity);
            this.jumpCount = Arrays.copyOf(this.jumpCount, capacity);
            this.lastJumpSpeed = Arrays.copyOf(this.lastJumpSpeed, capacity);
            this.efficiency = Arrays.copyOf(this.efficiency, capacity);
            this.flags = Arrays.copyOf(this.flags, capacity);
        }

        public ReplayFrames build() {
            int n = this.size;
            if (this.x.length != n) {
                this.x = Arrays.copyOf(this.x, n);
                this.y = Arrays.copyOf(this.y, n);
                this.z = Arrays.copyOf(this.z, n);
                this.yaw = Arrays.copyOf(this.yaw, n);
                this.pitch = Arrays.copyOf(this.pitch, n);
                this.jumpCount = Arrays.copyOf(this.jumpCount, n);
                this.lastJumpSpeed = Arrays.copyOf(this.lastJumpSpeed, n);
                this.efficiency = Arrays.copyOf(this.efficiency, n);
                this.flags = Arrays.copyOf(this.flags, n);
            }
            ReplayFrames frames = new ReplayFrames(n, this.x, this.y, this.z, this.yaw, this.pitch, this.jumpCount,
                    this.lastJumpSpeed, this.efficiency, this.flags);
            // The arrays now belong to the frames; a reused builder must not write into them.
            this.x = new double[0];
            this.y = new double[0];
            this.z = new double[0];
            this.yaw = new float[0];
            this.pitch = new float[0];
            this.jumpCount = new int[0];
            this.lastJumpSpeed = new float[0];
            this.efficiency = new float[0];
            this.flags = new int[0];
            this.size = 0;
            return frames;
        }
    }
}
