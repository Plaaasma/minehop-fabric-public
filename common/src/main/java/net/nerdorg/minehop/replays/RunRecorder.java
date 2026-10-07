package net.nerdorg.minehop.replays;

import net.minecraft.server.level.ServerPlayer;
import net.nerdorg.minehop.anticheat.stream.Input;
import net.minecraft.world.phys.Vec3;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.anticheat.stream.ClientTick;
import net.nerdorg.minehop.replays.storage.ReplayFrames;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Records runs for replays: one frame per client tick, from the movement the server accepted (see {@link ClientTick}),
 * with the per-frame flags filled in (ground, jump, sneak, keys, and a discontinuity on every teleport).
 *
 * <p>Frames used to be sampled once per server tick from the player's server-side position, while the run's time is the
 * client's clock: under lag or a packet burst a replay held too few frames (it played up to 6x too fast) and showed
 * several ticks of movement as one jump. A client tick's frame is the position the client ended that tick at, in packet
 * order, however late or bunched the server thread handles it, so a run of N client ticks is N+1 frames 50 ms apart.
 *
 * <p>Like the timers of mature CS servers (shavit, GOKZ), a replay also shows the {@link #PRE_FRAMES} ticks (1.5 s)
 * before the launch from the start zone (a ring buffer, cut at the last teleport) and the {@link #POST_FRAMES} ticks
 * (1 s) after the end-zone entry. The finish itself (PB, WR, messages) is handled at once: the run's metadata is live
 * immediately and its frames are completed in the background of the next second of play (see
 * ReplayManager#beginSave): the post window ends early, keeping what it has, when the player teleports, arms a new
 * run, disconnects, or the server stops.
 *
 * <p>Players without a packet stream (fake players, test bots) are still sampled once per server tick
 * ({@link #recordServerTick}), without flags, pre- or post-run frames.
 *
 * <p>Server thread only.
 */
public final class RunRecorder {
    /** Client ticks kept from before the run's launch (1.5 s). */
    public static final int PRE_FRAMES = 30;
    /** Client ticks recorded after the run's end-zone entry (1 s). */
    public static final int POST_FRAMES = 20;
    private static final int RING_SIZE = 32;

    private static final Map<UUID, Recorder> RECORDERS = new HashMap<>();

    /** A finished run still taking its post-run frames. */
    private static final class Finished {
        final ReplayManager.PendingSave save;
        final ReplayFrames.Builder frames;
        final int preFrames;
        /** Index after the run's last frame (the end-zone entry). */
        final int runEnd;
        final boolean tickStream;

        Finished(ReplayManager.PendingSave save, ReplayFrames.Builder frames, int preFrames, int runEnd, boolean tickStream) {
            this.save = save;
            this.frames = frames;
            this.preFrames = preFrames;
            this.runEnd = runEnd;
            this.tickStream = tickStream;
        }
    }

    private static final class Recorder {
        // The latest frames, oldest first from ringStart (the pre-run frames come from here).
        final double[] x = new double[RING_SIZE];
        final double[] y = new double[RING_SIZE];
        final double[] z = new double[RING_SIZE];
        final float[] yaw = new float[RING_SIZE];
        final float[] pitch = new float[RING_SIZE];
        final int[] jumps = new int[RING_SIZE];
        final float[] speed = new float[RING_SIZE];
        final float[] efficiency = new float[RING_SIZE];
        final int[] flags = new int[RING_SIZE];
        int ringStart;
        int ringSize;

        /** The run being recorded (from its start tick on), or null. */
        ReplayFrames.Builder run;
        boolean tickStream;
        int preFrames;
        /** Index after the frame of the run's end-zone entry, once the stream saw it; -1 before. */
        int runEnd = -1;
        /** The run outgrew the recording cap: it is no longer recorded and gets no replay. */
        boolean capped;
        Finished finished;

        void push(double fx, double fy, double fz, float fyaw, float fpitch, int fjumps, float fspeed, float feff, int fflags) {
            int i = (this.ringStart + this.ringSize) % RING_SIZE;
            if (this.ringSize == RING_SIZE) {
                this.ringStart = (this.ringStart + 1) % RING_SIZE;
            } else {
                this.ringSize++;
            }
            this.x[i] = fx;
            this.y[i] = fy;
            this.z[i] = fz;
            this.yaw[i] = fyaw;
            this.pitch[i] = fpitch;
            this.jumps[i] = fjumps;
            this.speed[i] = fspeed;
            this.efficiency[i] = feff;
            this.flags[i] = fflags;
        }

        /** Index into the ring arrays of the k-th newest frame (k = 0 is the newest). */
        int newest(int k) {
            return (this.ringStart + this.ringSize - 1 - k + RING_SIZE * 2) % RING_SIZE;
        }
    }

    private RunRecorder() {
    }

    private static Recorder recorder(ServerPlayer player) {
        return RECORDERS.computeIfAbsent(player.getUUID(), uuid -> new Recorder());
    }

    /** The per-frame flags of a client tick. */
    static int flagsOf(ClientTick tick) {
        Input input = tick.input();
        int flags = 0;
        if (tick.onGround()) {
            flags |= ReplayFrames.FLAG_ON_GROUND;
        }
        if (tick.jumped()) {
            flags |= ReplayFrames.FLAG_JUMPED;
        }
        if (input.shift()) {
            flags |= ReplayFrames.FLAG_SNEAKING | ReplayFrames.FLAG_INPUT_SNEAK;
        }
        if (tick.discontinuity()) {
            flags |= ReplayFrames.FLAG_DISCONTINUITY;
        }
        if (input.forward()) {
            flags |= ReplayFrames.FLAG_INPUT_FORWARD;
        }
        if (input.backward()) {
            flags |= ReplayFrames.FLAG_INPUT_BACK;
        }
        if (input.left()) {
            flags |= ReplayFrames.FLAG_INPUT_LEFT;
        }
        if (input.right()) {
            flags |= ReplayFrames.FLAG_INPUT_RIGHT;
        }
        if (input.jump()) {
            flags |= ReplayFrames.FLAG_INPUT_JUMP;
        }
        if (input.sprint()) {
            flags |= ReplayFrames.FLAG_INPUT_SPRINT;
        }
        return flags;
    }

    /**
     * One client tick of a player with a packet stream (ReplayEvents#onClientTick), after the run clock decided what it
     * did to the run ({@code event}).
     */
    static void onClientTick(ServerPlayer player, ClientTick tick, RunClock.Event event, float efficiency) {
        Recorder rec = recorder(player);
        Vec3 pos = tick.position();
        int flags = flagsOf(tick);
        float yaw = tick.yaw();
        float pitch = tick.pitch();
        int jumps = tick.jumpCount();
        float speed = (float) tick.lastJumpSpeed();

        Finished finished = rec.finished;
        if (finished != null) {
            if (tick.discontinuity() || event == RunClock.Event.START) {
                // Teleported away or already on the next run: the post-run frames end before this tick.
                completeFinished(rec);
            } else {
                finished.frames.add(pos.x, pos.y, pos.z, yaw, pitch, jumps, speed, efficiency, flags);
                if (finished.frames.size() >= finished.runEnd + POST_FRAMES) {
                    completeFinished(rec);
                }
            }
        }

        if (event == RunClock.Event.START) {
            restart(rec, true);
            // Pre-run frames: the ring's newest frames back to (and including) the last teleport.
            int count = 0;
            while (count < Math.min(PRE_FRAMES, rec.ringSize)) {
                int i = rec.newest(count);
                count++;
                if ((rec.flags[i] & ReplayFrames.FLAG_DISCONTINUITY) != 0) {
                    break;
                }
            }
            if ((flags & ReplayFrames.FLAG_DISCONTINUITY) != 0) {
                count = 0; // the start tick itself follows a teleport: nothing before it belongs to this place
            }
            for (int k = count - 1; k >= 0; k--) {
                int i = rec.newest(k);
                // The first frame of a replay has nothing to be discontinuous with.
                int frameFlags = k == count - 1 ? rec.flags[i] & ~ReplayFrames.FLAG_DISCONTINUITY : rec.flags[i];
                rec.run.add(rec.x[i], rec.y[i], rec.z[i], rec.yaw[i], rec.pitch[i], rec.jumps[i], rec.speed[i],
                        rec.efficiency[i], frameFlags);
            }
            rec.preFrames = count;
            rec.run.add(pos.x, pos.y, pos.z, yaw, pitch, jumps, speed, efficiency,
                    count == 0 ? flags & ~ReplayFrames.FLAG_DISCONTINUITY : flags);
        } else if (rec.run != null) {
            if (!Minehop.timerManager.containsKey(player.getScoreboardName())) {
                // The run ended without a finish (reset, /spawn, game mode change, invalidation...).
                rec.run = null;
                rec.runEnd = -1;
            } else if (!rec.capped) {
                if (rec.run.size() - rec.preFrames >= ReplayEvents.MAX_RECORDED_FRAMES) {
                    // Too long to keep: let the frames go, remember only that the run outgrew the cap.
                    rec.capped = true;
                    rec.run = ReplayFrames.builder(0);
                } else {
                    rec.run.add(pos.x, pos.y, pos.z, yaw, pitch, jumps, speed, efficiency, flags);
                    if (event == RunClock.Event.FINISH && rec.runEnd < 0) {
                        rec.runEnd = rec.run.size();
                    }
                }
            }
        }
        rec.push(pos.x, pos.y, pos.z, yaw, pitch, jumps, speed, efficiency, flags);
    }

    private static void restart(Recorder rec, boolean tickStream) {
        if (rec.run == null) {
            rec.run = ReplayFrames.builder(256);
        } else {
            rec.run.clear();
        }
        rec.tickStream = tickStream;
        rec.preFrames = 0;
        rec.runEnd = -1;
        rec.capped = false;
    }

    /** A run was armed by StartEntity for a player without a packet stream: record it from now on, per server tick. */
    public static void restartServerTickRecording(ServerPlayer player) {
        if (player != null && !RunClock.usesStream(player)) {
            restart(recorder(player), false);
        }
    }

    /**
     * Server thread, once per server tick: records a frame for every player without a packet stream whose run is
     * active (their position as the server sees it; no flags).
     */
    static void recordServerTick(ServerPlayer player) {
        Recorder rec = RECORDERS.get(player.getUUID());
        if (!Minehop.timerManager.containsKey(player.getScoreboardName())) {
            if (rec != null) {
                rec.run = null;
            }
            return;
        }
        if (rec == null) {
            rec = recorder(player);
        }
        if (rec.run == null) {
            restart(rec, false);
        }
        if (rec.capped) {
            return;
        }
        if (rec.run.size() >= ReplayEvents.MAX_RECORDED_FRAMES) {
            rec.capped = true;
            rec.run = ReplayFrames.builder(0);
            return;
        }
        RunStats.Snapshot stats = RunStats.of(player);
        rec.run.add(player.getX(), player.getY(), player.getZ(), player.getYHeadRot(), player.getXRot(), stats.jumpCount(),
                (float) stats.lastJumpSpeed(), (float) stats.efficiency(), 0);
    }

    /** The outcome of {@link #finishRun}. */
    public enum Outcome {
        SAVING,
        NO_RECORDING,
        TOO_LONG
    }

    /**
     * The player's run was accepted: saves its replay. The metadata is in the run list at once; the frames follow
     * when the post-run window is complete (at most {@link #POST_FRAMES} client ticks later).
     */
    public static Outcome finishRun(ServerPlayer player, ReplayManager.Replay replay, double serverTime, long clientTicks,
                                    int headerFlags) {
        Recorder rec = RECORDERS.get(player.getUUID());
        if (rec != null && rec.capped) {
            rec.run = null;
            rec.capped = false;
            return Outcome.TOO_LONG;
        }
        if (rec == null || rec.run == null || rec.run.size() <= rec.preFrames) {
            return Outcome.NO_RECORDING;
        }
        if (rec.finished != null) {
            completeFinished(rec); // a previous run's post window (can't overlap a new run; just in case)
        }
        ReplayFrames.Builder frames = rec.run;
        int runEnd = rec.runEnd >= 0 ? rec.runEnd : frames.size();
        boolean tickStream = rec.tickStream;
        int preFrames = rec.preFrames;
        rec.run = null;
        rec.runEnd = -1;
        ReplayManager.PendingSave save = ReplayManager.beginSave(player.serverLevel(), replay, serverTime, clientTicks,
                headerFlags, runEnd);
        if (save == null) {
            return Outcome.SAVING; // not savable (logged by ReplayManager)
        }
        rec.finished = new Finished(save, frames, preFrames, runEnd, tickStream);
        if (!tickStream || !save.wantsPostFrames() || frames.size() >= runEnd + POST_FRAMES) {
            completeFinished(rec);
        }
        return Outcome.SAVING;
    }

    private static void completeFinished(Recorder rec) {
        Finished finished = rec.finished;
        rec.finished = null;
        if (finished == null) {
            return;
        }
        int keep = finished.tickStream ? Math.min(finished.frames.size(), finished.runEnd + POST_FRAMES) : finished.runEnd;
        finished.frames.truncate(keep);
        int post = finished.frames.size() - finished.runEnd;
        ReplayFrames frames = finished.frames.build().withLayout(finished.preFrames, post, finished.tickStream);
        finished.save.complete(frames);
    }

    /** Drops the player's current recording (the run ended without a replay); a finished run's post window goes on. */
    public static void discardRun(ServerPlayer player) {
        Recorder rec = player == null ? null : RECORDERS.get(player.getUUID());
        if (rec != null) {
            rec.run = null;
            rec.runEnd = -1;
            rec.capped = false;
        }
    }

    /** The player leaves: a finished run keeps what its post window has; the rest is dropped. */
    public static void onDisconnect(ServerPlayer player) {
        Recorder rec = player == null ? null : RECORDERS.remove(player.getUUID());
        if (rec != null) {
            completeFinished(rec);
        }
    }

    /** The server stops: every finished run is completed with the post-run frames it has. */
    public static void completeAll() {
        for (Recorder rec : new ArrayList<>(RECORDERS.values())) {
            completeFinished(rec);
        }
    }

    public static void clear() {
        RECORDERS.clear();
    }

    /** Frames recorded so far for the player's current run, including pre-run frames (diagnostics); -1 if none. */
    public static int recordedFrames(ServerPlayer player) {
        Recorder rec = player == null ? null : RECORDERS.get(player.getUUID());
        return rec == null || rec.run == null ? -1 : rec.run.size();
    }

    /** {preFrames, runEnd} of the player's current recording (diagnostics). */
    public static List<Integer> layout(ServerPlayer player) {
        Recorder rec = player == null ? null : RECORDERS.get(player.getUUID());
        List<Integer> out = new ArrayList<>();
        if (rec != null && rec.run != null) {
            out.add(rec.preFrames);
            out.add(rec.runEnd);
        }
        return out;
    }
}
