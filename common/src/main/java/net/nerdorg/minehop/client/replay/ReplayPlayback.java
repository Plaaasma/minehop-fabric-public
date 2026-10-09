package net.nerdorg.minehop.client.replay;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.MinehopClient;
import net.nerdorg.minehop.data.DataManager;
import net.nerdorg.minehop.networking.ReplayProtocol;
import net.nerdorg.minehop.networking.payloads.ReplayStatePayload;
import net.nerdorg.minehop.networking.payloads.ReplayWatchPayload;
import net.nerdorg.minehop.platform.ClientServices;
import net.nerdorg.minehop.replays.storage.ReplayFrames;
import net.nerdorg.minehop.replays.storage.ReplayTiming;

import java.util.Locale;

/**
 * Plays the replay of a watch session on this client (1.1.7+; the server's CLIENT_REPLAY session, see
 * SpectateSessions): the replay is streamed once (ClientReplayStreams), then played on the client's own clock with the
 * view interpolated between the recorded frames every rendered frame, so there is no 50 ms stepping or network jitter.
 *
 * <ul>
 *   <li>Interpolation: positions and pitch linearly, yaw along the shorter way round (recordings keep it unwrapped);
 *   never across a teleport of the recording (flagged in tick-stream recordings, guessed from the step length in older
 *   ones): playback jumps there (ReplayTiming#jumpsInto).</li>
 *   <li>Timing: 50 ms per frame; an older recording that lag left with too few frames is stretched to its time; the
 *   timer shows 0 during the pre-run frames and the run's time from its finish on (ReplayTiming). After the last frame
 *   it holds for 2 s and starts over.</li>
 *   <li>Controls: pause, speed (0.1x-8x; keys step through 0.25x-4x), seek (absolute or relative), stop: keys
 *   (ReplayKeys) and the server's /replay commands (ReplayControlPayload).</li>
 *   <li>Chunks: the client reports its position (frame, speed, paused) and the server keeps the viewer near it. While
 *   the chunk the view is in isn't loaded on this client (start, a seek, or playback outrunning the chunk loading) the
 *   clock waits ("buffering"), at most {@link #BUFFER_TIMEOUT_NANOS} per chunk.</li>
 *   <li>Camera: a client-only {@link ReplayCameraEntity} placed at the interpolated view right before the level renders
 *   (GameRendererMixin), set as the camera entity while the player is a spectator; F5 shows the ghost model.</li>
 * </ul>
 * Client thread only.
 */
public final class ReplayPlayback {
    /** Speeds the slower/faster keys step through. */
    static final float[] SPEED_STEPS = {0.25F, 0.5F, 1.0F, 2.0F, 4.0F};
    static final double SEEK_STEP_SECONDS = 5.0D;
    private static final long LOOP_HOLD_NANOS = 2_000_000_000L;
    private static final long BUFFER_TIMEOUT_NANOS = 5_000_000_000L;
    /** After a wait timed out (the chunks never came), playback doesn't wait again for this long. */
    private static final long BUFFER_GRACE_NANOS = 15_000_000_000L;
    /** A render hitch longer than this doesn't move playback further (it continues where it was). */
    private static final long MAX_STEP_NANOS = 250_000_000L;
    private static final int REPORT_INTERVAL_TICKS = 5;
    /** Ticks without spectator mode after which the session is taken as over even without the server saying so. */
    private static final int NOT_SPECTATOR_TICKS = 40;
    /** DEV: -Dminehop.replayDebug=true logs every rendered frame of client playback (smoothness checks). */
    static final boolean DEBUG = Boolean.getBoolean("minehop.replayDebug");

    /** A view of the replay: the recorded player's feet position and view angles at a (fractional) frame. */
    public record ViewPose(double x, double y, double z, float yaw, float pitch, boolean sneaking, int frame) {
    }

    private static Watch watch;

    private ReplayPlayback() {
    }

    /** The watch session being played. */
    static final class Watch {
        final int sessionId;
        final byte kind;
        final String replayId;
        final String map;
        final String player;
        final double time;
        int requestId = -1;
        ReplayFrames frames;
        long frameNanos = ReplayTiming.FRAME_NANOS;
        String failure;
        double position;
        float speed = 1.0F;
        boolean paused;
        boolean buffering = true;
        long bufferingSince = System.nanoTime();
        long graceChunk = Long.MIN_VALUE;
        long graceUntil;
        long clock;
        long holdUntil;
        ReplayCameraEntity camera;
        ViewPose pose;
        boolean reportDirty = true;
        int ticksSinceReport;
        int notSpectatorTicks;
        double pendingSeekSeconds = Double.NaN;
        long debugFrames;

        Watch(ReplayWatchPayload payload) {
            this.sessionId = payload.sessionId();
            this.kind = payload.kind();
            this.replayId = payload.replayId();
            this.map = payload.map();
            this.player = payload.player();
            this.time = payload.time();
        }

        boolean worldRecord() {
            return this.kind == ReplayProtocol.WATCH_WORLD_RECORD;
        }

        double runSeconds() {
            return this.frames == null ? 0.0D : ReplayTiming.runSeconds(this.frames, this.position, this.frameNanos, this.time);
        }

        /** Seconds from the first to the last frame (incl. pre/post frames) at normal speed. */
        double totalSeconds() {
            return this.frames == null ? 0.0D : (this.frames.size() - 1) * (this.frameNanos / 1_000_000_000.0D);
        }
    }

    /** True while a watch session is being played (or loaded) on this client. */
    public static boolean isActive() {
        return watch != null;
    }

    /** The playing session (HUD, keys), or null. */
    static Watch current() {
        return watch;
    }

    // ------------------------------------------------------------------------------------------
    // Sessions
    // ------------------------------------------------------------------------------------------

    static void onWatch(ReplayWatchPayload payload) {
        if (!payload.active()) {
            if (watch != null && watch.sessionId == payload.sessionId()) {
                stop(false);
            }
            return;
        }
        if (watch != null) {
            stop(false);
        }
        Watch started = new Watch(payload);
        watch = started;
        Minehop.LOGGER.info("[Replay] watching {}'s {} on {} ({} s, {} frames, session {})", payload.player(),
                started.worldRecord() ? "world record" : "personal best", payload.map(),
                String.format(Locale.ROOT, "%.3f", payload.time()), payload.frameCount(), payload.sessionId());
        int requestId = ClientReplayStreams.request(ReplayProtocol.KIND_SESSION, payload.map(), payload.replayId(), "",
                new ClientReplayStreams.Callback() {
                    @Override
                    public void loaded(ClientReplayStreams.Loaded replay) {
                        if (watch == started) {
                            started.requestId = -1;
                            begin(started, replay);
                        }
                    }

                    @Override
                    public void failed(String message) {
                        if (watch == started) {
                            started.requestId = -1;
                            started.failure = message;
                            chat("Could not play the replay: " + message);
                            stop(true);
                        }
                    }
                });
        if (watch == started && started.frames == null) {
            started.requestId = requestId;
        }
    }

    private static void begin(Watch w, ClientReplayStreams.Loaded replay) {
        ReplayFrames frames = replay.frames();
        if (frames == null || frames.isEmpty()) {
            chat("Could not play the replay: it has no frames.");
            stop(true);
            return;
        }
        w.frames = frames;
        w.frameNanos = ReplayTiming.frameNanos(frames, w.time);
        w.position = 0.0D;
        if (!Double.isNaN(w.pendingSeekSeconds)) {
            w.position = clampPosition(w, ReplayTiming.positionAt(frames, w.pendingSeekSeconds, w.frameNanos));
        }
        startBuffering(w, System.nanoTime());
        Minehop.LOGGER.info("[Replay] loaded {} frames ({} pre, {} post, {}, {} ms per frame); {}", frames.size(), frames.preFrames(),
                frames.postFrames(), frames.tickStream() ? "tick stream" : "legacy", w.frameNanos / 1_000_000L, ClientReplayStreams.status());
    }

    /**
     * Stops playing. {@code tellServer}: the player stopped it (the server ends the session); otherwise the server
     * already did. The camera goes back to the player.
     */
    static void stop(boolean tellServer) {
        Watch w = watch;
        if (w == null) {
            return;
        }
        watch = null;
        if (w.requestId >= 0) {
            ClientReplayStreams.cancel(w.requestId);
        }
        if (tellServer) {
            sendState(w, ReplayProtocol.STATE_STOP);
        }
        Minecraft mc = Minecraft.getInstance();
        if (w.camera != null && mc.getCameraEntity() == w.camera && mc.player != null) {
            mc.setCameraEntity(mc.player);
        }
        MinehopClient.runTimerHudVisible = false;
        MinehopClient.jump_count = 0;
        MinehopClient.last_jump_speed = 0.0D;
        MinehopClient.last_efficiency = 0.0D;
        Minehop.LOGGER.info("[Replay] stopped watching (session {})", w.sessionId);
    }

    /** The connection ended: forget the session (no message to the server). */
    static void reset() {
        Watch w = watch;
        watch = null;
        if (w != null && w.camera != null) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.getCameraEntity() == w.camera) {
                mc.setCameraEntity(mc.player);
            }
        }
    }

    // ------------------------------------------------------------------------------------------
    // Controls (keys and the server's /replay commands)
    // ------------------------------------------------------------------------------------------

    static void control(byte action, double value) {
        Watch w = watch;
        if (w == null) {
            return;
        }
        switch (action) {
            case ReplayProtocol.CONTROL_PAUSE -> setPaused(w, value >= 2.0D ? !w.paused : value != 0.0D);
            case ReplayProtocol.CONTROL_SPEED -> setSpeed(w, (float) value);
            case ReplayProtocol.CONTROL_SEEK -> seekTo(w, value);
            case ReplayProtocol.CONTROL_SEEK_BY -> seekBy(w, value);
            case ReplayProtocol.CONTROL_STOP -> stop(true);
            default -> {
            }
        }
    }

    static void togglePause() {
        if (watch != null) {
            setPaused(watch, !watch.paused);
        }
    }

    /** One step along {@link #SPEED_STEPS}: {@code direction} > 0 faster, < 0 slower. */
    static void stepSpeed(int direction) {
        Watch w = watch;
        if (w == null) {
            return;
        }
        float next = w.speed;
        if (direction > 0) {
            for (float step : SPEED_STEPS) {
                if (step > w.speed + 1.0E-4F) {
                    next = step;
                    break;
                }
            }
        } else {
            for (int i = SPEED_STEPS.length - 1; i >= 0; i--) {
                if (SPEED_STEPS[i] < w.speed - 1.0E-4F) {
                    next = SPEED_STEPS[i];
                    break;
                }
            }
        }
        setSpeed(w, next);
    }

    static void skip(double seconds) {
        if (watch != null) {
            seekBy(watch, seconds);
        }
    }

    private static void setPaused(Watch w, boolean paused) {
        w.paused = paused;
        w.clock = System.nanoTime();
        w.reportDirty = true;
    }

    private static void setSpeed(Watch w, float speed) {
        if (!Float.isFinite(speed)) {
            return;
        }
        w.speed = Mth.clamp(speed, ReplayProtocol.MIN_SPEED, ReplayProtocol.MAX_SPEED);
        w.reportDirty = true;
    }

    private static void seekTo(Watch w, double seconds) {
        if (!Double.isFinite(seconds)) {
            return;
        }
        if (w.frames == null) {
            w.pendingSeekSeconds = seconds;
            return;
        }
        moveTo(w, ReplayTiming.positionAt(w.frames, seconds, w.frameNanos));
    }

    private static void seekBy(Watch w, double seconds) {
        if (!Double.isFinite(seconds) || w.frames == null) {
            return;
        }
        moveTo(w, w.position + seconds * 1_000_000_000.0D / w.frameNanos);
    }

    private static void moveTo(Watch w, double position) {
        w.position = clampPosition(w, position);
        w.holdUntil = 0L;
        startBuffering(w, System.nanoTime());
    }

    private static double clampPosition(Watch w, double position) {
        return Mth.clamp(position, 0.0D, Math.max(0, w.frames.size() - 1));
    }

    private static void startBuffering(Watch w, long now) {
        w.buffering = true;
        w.bufferingSince = now;
        w.graceChunk = Long.MIN_VALUE;
        w.reportDirty = true;
    }

    // ------------------------------------------------------------------------------------------
    // Per tick and per frame
    // ------------------------------------------------------------------------------------------

    static void tick(Minecraft mc) {
        Watch w = watch;
        if (w == null) {
            return;
        }
        if (mc.player == null || mc.level == null) {
            reset();
            return;
        }
        // The server ends sessions (and says so); if the player stops being a spectator for a while without that
        // message, the session is over all the same.
        if (!mc.player.isSpectator()) {
            if (++w.notSpectatorTicks > NOT_SPECTATOR_TICKS) {
                stop(false);
                return;
            }
        } else {
            w.notSpectatorTicks = 0;
        }
        if (w.frames == null) {
            return;
        }
        long now = System.nanoTime();
        updateBuffering(mc, w, now);
        updateHudFields(w);
        report(w);
    }

    /** Waits while the chunk the view is in isn't loaded here (at most BUFFER_TIMEOUT_NANOS per chunk). */
    private static void updateBuffering(Minecraft mc, Watch w, long now) {
        ViewPose pose = poseAt(w.frames, w.position);
        int chunkX = Mth.floor(pose.x()) >> 4;
        int chunkZ = Mth.floor(pose.z()) >> 4;
        long chunk = ChunkPos.pack(chunkX, chunkZ);
        boolean loaded = inMapLevel(mc, w.map) && mc.level.getChunkSource().hasChunk(chunkX, chunkZ);
        if (w.buffering) {
            boolean timedOut = now - w.bufferingSince > BUFFER_TIMEOUT_NANOS;
            if (loaded || timedOut) {
                w.buffering = false;
                w.graceChunk = loaded ? Long.MIN_VALUE : chunk;
                if (!loaded) {
                    // The chunks don't come (e.g. spectators may not load chunks on this server): play on without them.
                    w.graceUntil = now + BUFFER_GRACE_NANOS;
                }
                w.clock = now;
                w.reportDirty = true;
            }
        } else if (!loaded && chunk != w.graceChunk && now - w.graceUntil >= 0L) {
            startBuffering(w, now);
        }
    }

    /** False if the client knows the map's dimension and is in another one (the server is moving the viewer there). */
    private static boolean inMapLevel(Minecraft mc, String mapName) {
        DataManager.MapData map = null;
        if (Minehop.mapList != null) {
            for (DataManager.MapData candidate : Minehop.mapList) {
                if (candidate != null && candidate.name != null && candidate.name.equals(mapName)) {
                    map = candidate;
                    break;
                }
            }
        }
        return map == null || map.worldKey == null || map.worldKey.isBlank() || map.worldKey.equals(mc.level.dimension().toString());
    }

    /** The timer and jump stats HUD show the replay's (as for a spectated run). */
    private static void updateHudFields(Watch w) {
        int frame = (int) Mth.clamp(Math.floor(w.position), 0, w.frames.size() - 1);
        MinehopClient.runTimerHudVisible = true;
        MinehopClient.runTimerHudTime = (float) w.runSeconds();
        MinehopClient.runTimerHudPb = (float) w.time;
        MinehopClient.runTimerHudUpdatedAtMs = System.currentTimeMillis();
        MinehopClient.jump_count = w.frames.jumpCount(frame);
        MinehopClient.last_jump_speed = w.frames.lastJumpSpeed(frame);
        MinehopClient.last_efficiency = w.frames.efficiency(frame);
    }

    /** Tells the server where playback is: on every change at once, else every REPORT_INTERVAL_TICKS while playing. */
    private static void report(Watch w) {
        w.ticksSinceReport++;
        if (!w.reportDirty && (w.paused || w.ticksSinceReport < REPORT_INTERVAL_TICKS)) {
            return;
        }
        byte flags = (byte) ((w.paused ? ReplayProtocol.STATE_PAUSED : 0) | (w.buffering ? ReplayProtocol.STATE_BUFFERING : 0));
        sendState(w, flags);
        w.reportDirty = false;
        w.ticksSinceReport = 0;
    }

    private static void sendState(Watch w, byte flags) {
        try {
            ClientServices.NETWORK.sendToServer(new ReplayStatePayload(w.sessionId, (float) w.position, w.speed, flags));
        } catch (RuntimeException ignored) {
            // not connected
        }
    }

    /**
     * Right before the level is rendered (GameRendererMixin): advances the clock and puts the camera at the
     * interpolated view of this exact moment.
     */
    public static void onRenderFrame() {
        Watch w = watch;
        if (w == null) {
            return;
        }
        long now = System.nanoTime();
        advance(w, now);
        Minecraft mc = Minecraft.getInstance();
        if (w.frames == null || mc.level == null || mc.player == null) {
            return;
        }
        w.pose = poseAt(w.frames, w.position);
        if (!mc.player.isSpectator()) {
            if (w.camera != null && mc.getCameraEntity() == w.camera) {
                mc.setCameraEntity(mc.player);
            }
            return;
        }
        if (w.camera == null || w.camera.level() != mc.level) {
            w.camera = new ReplayCameraEntity(mc.level);
        }
        w.camera.place(w.pose);
        if (mc.getCameraEntity() != w.camera) {
            mc.setCameraEntity(w.camera);
        }
        if (DEBUG) {
            w.debugFrames++;
            Minehop.LOGGER.info(String.format(Locale.ROOT, "[REPLAYDBG] client t=%d pos=%.4f frame=%d x=%.4f y=%.4f z=%.4f yaw=%.3f pitch=%.3f speed=%.2f%s%s",
                    now, w.position, w.pose.frame(), w.pose.x(), w.pose.y(), w.pose.z(), w.pose.yaw(), w.pose.pitch(), w.speed,
                    w.paused ? " paused" : "", w.buffering ? " buffering" : ""));
        }
    }

    private static void advance(Watch w, long now) {
        long last = w.clock;
        w.clock = now;
        if (w.frames == null || last == 0L || w.paused || w.buffering) {
            return;
        }
        if (w.holdUntil != 0L) {
            if (now - w.holdUntil >= 0L) {
                // Start over (the server is told, and the start's chunks load like after a seek).
                w.holdUntil = 0L;
                moveTo(w, 0.0D);
            }
            return;
        }
        long step = Math.min(Math.max(0L, now - last), MAX_STEP_NANOS);
        w.position += step * (double) w.speed / w.frameNanos;
        int lastFrame = w.frames.size() - 1;
        if (w.position >= lastFrame) {
            w.position = lastFrame;
            w.holdUntil = now + LOOP_HOLD_NANOS;
            w.reportDirty = true;
        }
    }

    /**
     * The view at fractional frame {@code position}: interpolated between its two frames, except into a teleport
     * (playback stays on the frame before it until it gets there).
     */
    public static ViewPose poseAt(ReplayFrames frames, double position) {
        int lastFrame = frames.size() - 1;
        double p = Mth.clamp(position, 0.0D, lastFrame);
        int i = (int) Math.floor(p);
        double t = p - i;
        boolean sneaking = frames.tickStream() && (frames.flags(i) & ReplayFrames.FLAG_SNEAKING) != 0;
        if (i >= lastFrame || t <= 0.0D || ReplayTiming.jumpsInto(frames, i + 1)) {
            return new ViewPose(frames.x(i), frames.y(i), frames.z(i), frames.yaw(i), frames.pitch(i), sneaking, i);
        }
        float yaw0 = frames.yaw(i);
        float yaw = yaw0 + Mth.wrapDegrees(frames.yaw(i + 1) - yaw0) * (float) t;
        float pitch = (float) Mth.lerp(t, frames.pitch(i), frames.pitch(i + 1));
        return new ViewPose(Mth.lerp(t, frames.x(i), frames.x(i + 1)), Mth.lerp(t, frames.y(i), frames.y(i + 1)),
                Mth.lerp(t, frames.z(i), frames.z(i + 1)), yaw, pitch, sneaking, i);
    }

    /** Horizontal speed of the watched run at the current frame, blocks per second (NaN if nothing is playing). */
    public static double watchedSpeedBlocksPerSecond() {
        Watch w = watch;
        if (w == null || w.frames == null || w.frames.size() < 2) {
            return Double.NaN;
        }
        int i = (int) Mth.clamp(Math.floor(w.position), 0, w.frames.size() - 2);
        if (ReplayTiming.jumpsInto(w.frames, i + 1)) {
            i = Math.max(0, i - 1);
            if (ReplayTiming.jumpsInto(w.frames, i + 1)) {
                return 0.0D;
            }
        }
        double dx = w.frames.x(i + 1) - w.frames.x(i);
        double dz = w.frames.z(i + 1) - w.frames.z(i);
        return Math.sqrt(dx * dx + dz * dz) * 1_000_000_000.0D / w.frameNanos;
    }

    /** True if the camera currently looks through the watched replay (first person, no ghost model needed). */
    static boolean cameraIsReplay() {
        Watch w = watch;
        return w != null && w.camera != null && Minecraft.getInstance().getCameraEntity() == w.camera;
    }

    private static void chat(String message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.sendSystemMessage(Component.literal(message));
        }
    }
}
