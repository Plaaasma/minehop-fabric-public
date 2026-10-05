package net.nerdorg.minehop.client;

import net.minecraft.client.Minecraft;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.platform.ClientServices;

import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.Locale;

/**
 * Dev-only client frame/tick timing probe (no-op unless {@code -Dminehop.clientperf=true}). Every
 * {@code minehop.clientperf.window} seconds (default 10) it logs "[CPERF]" lines with the frame time and client tick
 * time percentiles and the render thread's allocation rate; after {@code minehop.clientperf.windows} windows (default
 * 0 = never) of measuring in a world it closes the client.
 */
public final class ClientPerfProbe {
    private static final boolean ENABLED = Boolean.getBoolean("minehop.clientperf");
    private static final double WINDOW_SECONDS = Double.parseDouble(System.getProperty("minehop.clientperf.window", "10"));
    private static final int MAX_WINDOWS = Integer.getInteger("minehop.clientperf.windows", 0);
    private static final int MAX_SAMPLES = 100_000;
    // Window after which a world-only screenshot (GUI hidden) is saved as screenshots/<shotName>.png (0 = never).
    private static final int SHOT_AT = Integer.getInteger("minehop.clientperf.shotAt", 0);
    private static final String SHOT_NAME = System.getProperty("minehop.clientperf.shotName", "clientperf");

    private static final long[] FRAME_NANOS = new long[MAX_SAMPLES];
    private static final long[] TICK_NANOS = new long[MAX_SAMPLES];
    private static int frameCount;
    private static int tickCount;
    private static long lastFrameNanos = -1L;
    private static long windowStartNanos = -1L;
    private static long windowStartAlloc;
    private static long tickStartNanos;
    private static int windows;
    private static com.sun.management.ThreadMXBean threadBean;

    private ClientPerfProbe() {
    }

    public static void register() {
        if (!ENABLED) {
            return;
        }
        if (ManagementFactory.getThreadMXBean() instanceof com.sun.management.ThreadMXBean bean) {
            threadBean = bean;
            threadBean.setThreadAllocatedMemoryEnabled(true);
        }
        ClientServices.CLIENT.onClientTickStart(client -> tickStartNanos = System.nanoTime());
        ClientServices.CLIENT.onClientTickEnd(client -> {
            if (client.level != null && tickCount < MAX_SAMPLES) {
                TICK_NANOS[tickCount++] = System.nanoTime() - tickStartNanos;
            }
        });
        ClientServices.CLIENT.onWorldRenderEnd(context -> onFrame());
        Minehop.LOGGER.info("[CPERF] client perf probe enabled (window {}s)", WINDOW_SECONDS);
    }

    private static void onFrame() {
        Minecraft client = Minecraft.getInstance();
        long now = System.nanoTime();
        if (client.level == null || client.player == null) {
            lastFrameNanos = -1L;
            windowStartNanos = -1L;
            return;
        }
        if (windowStartNanos < 0L) {
            windowStartNanos = now;
            windowStartAlloc = allocatedBytes();
            frameCount = 0;
            tickCount = 0;
            lastFrameNanos = now;
            return;
        }
        if (frameCount < MAX_SAMPLES) {
            FRAME_NANOS[frameCount++] = now - lastFrameNanos;
        }
        lastFrameNanos = now;
        double elapsed = (now - windowStartNanos) / 1.0e9D;
        if (elapsed >= WINDOW_SECONDS) {
            long alloc = allocatedBytes() - windowStartAlloc;
            long rampNanos = net.nerdorg.minehop.entity.client.SurfRampRenderer.perfRenderNanos;
            long rampCalls = net.nerdorg.minehop.entity.client.SurfRampRenderer.perfRenderCalls;
            net.nerdorg.minehop.entity.client.SurfRampRenderer.perfRenderNanos = 0L;
            net.nerdorg.minehop.entity.client.SurfRampRenderer.perfRenderCalls = 0L;
            long[] frames = Arrays.copyOf(FRAME_NANOS, frameCount);
            long[] ticks = Arrays.copyOf(TICK_NANOS, tickCount);
            Arrays.sort(frames);
            Arrays.sort(ticks);
            windows++;
            Minehop.LOGGER.info(String.format(Locale.ROOT,
                    "[CPERF] window=%d rampRenderMs/frame=%.3f rampsDrawn/frame=%.1f fps=%.1f frameMs p50=%.2f p90=%.2f p99=%.2f max=%.2f tickMs p50=%.3f p99=%.3f max=%.3f renderThreadAlloc=%.1fMB/s pos=(%.1f,%.1f,%.1f) entities=%d",
                    windows, frameCount > 0 ? rampNanos / 1.0e6D / frameCount : 0.0D,
                    frameCount > 0 ? (double) rampCalls / frameCount : 0.0D, frameCount / elapsed,
                    pct(frames, 0.50D), pct(frames, 0.90D), pct(frames, 0.99D), pct(frames, 1.0D),
                    pct(ticks, 0.50D), pct(ticks, 0.99D), pct(ticks, 1.0D),
                    alloc / 1048576.0D / elapsed,
                    client.player.getX(), client.player.getY(), client.player.getZ(),
                    client.level.getEntityCount()));
            if (SHOT_AT > 0 && windows == SHOT_AT - 1) {
                client.options.hideGui = true;
            }
            if (SHOT_AT > 0 && windows == SHOT_AT) {
                net.minecraft.client.Screenshot.grab(client.gameDirectory, SHOT_NAME + ".png", client.getMainRenderTarget(), 1,
                        message -> Minehop.LOGGER.info("[CPERF] screenshot: {}", message.getString()));
                client.options.hideGui = false;
            }
            windowStartNanos = now;
            windowStartAlloc = allocatedBytes();
            frameCount = 0;
            tickCount = 0;
            if (MAX_WINDOWS > 0 && windows >= MAX_WINDOWS) {
                Minehop.LOGGER.info("[CPERF] === DONE ===");
                client.stop();
            }
        }
    }

    private static double pct(long[] sorted, double fraction) {
        if (sorted.length == 0) {
            return 0.0D;
        }
        int index = Math.min(sorted.length - 1, (int) (sorted.length * fraction));
        return sorted[index] / 1.0e6D;
    }

    private static long allocatedBytes() {
        return threadBean != null ? threadBean.getCurrentThreadAllocatedBytes() : 0L;
    }
}
