package net.nerdorg.minehop.client.replay;

import net.minecraft.client.Minecraft;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.MinehopClient;
import net.nerdorg.minehop.client.ClientVisibility;
import net.nerdorg.minehop.config.ConfigWrapper;
import net.nerdorg.minehop.config.MinehopConfig;
import net.nerdorg.minehop.data.DataManager;
import net.nerdorg.minehop.networking.ReplayProtocol;
import net.nerdorg.minehop.replays.storage.ReplayFrames;
import net.nerdorg.minehop.replays.storage.ReplayTiming;

import java.util.Locale;
import java.util.Objects;

/**
 * Race your best: while the player runs a map, their personal best on it (or the world record, a client setting) plays
 * next to them as a translucent ghost, synchronised to their own run: its run starts when the player's run timer
 * starts (it waits on its launch frame while the player is armed in the start zone) and it is hidden whenever the
 * player isn't running. The replay is fetched from the server once per map and target (and again after a new personal
 * best or record) and cached. Purely visual: nothing about it touches movement, collisions or the anticheat.
 * Client thread only.
 */
public final class RaceGhost {
    /**
     * Wait before asking again after a refusal (e.g. no PB on the map yet, the server busy); a new PB or WR on the map
     * (as the client's record lists show it) asks again at once.
     */
    private static final long RETRY_NANOS = 120_000_000_000L;

    private static String loadedMap = "";
    private static boolean loadedWorldRecord;
    private static String loadedReplayId = "";
    private static double loadedRecordTime = Double.NaN;
    private static ReplayFrames frames;
    private static long frameNanos = ReplayTiming.FRAME_NANOS;
    private static double runTime;
    private static int requestId = -1;
    private static String requestKey = "";
    private static String failedKey = "";
    private static long retryAt;
    private static boolean hiddenThisSession;
    private static boolean wasRunning;

    private RaceGhost() {
    }

    private static MinehopConfig.ReplaySettings settings() {
        MinehopConfig config = ConfigWrapper.config;
        return config == null || config.replay == null ? new MinehopConfig.ReplaySettings() : config.replay;
    }

    /** Whether the race ghost is switched on (config) and not hidden (/hide race, /hide replay). */
    public static boolean visible() {
        return settings().race_ghost && !hiddenThisSession && !ClientVisibility.hideReplay();
    }

    /** True while a world-record race ghost is shown: the server's world-record ghost entities are hidden meanwhile. */
    public static boolean showingWorldRecord() {
        return loadedWorldRecord && currentPose() != null;
    }

    static void onControl(int mode) {
        MinehopConfig config = ConfigWrapper.config;
        if (config == null) {
            return;
        }
        if (config.replay == null) {
            config.replay = new MinehopConfig.ReplaySettings();
        }
        switch (mode) {
            case ReplayProtocol.RACE_OFF -> config.replay.race_ghost = false;
            case ReplayProtocol.RACE_ON -> config.replay.race_ghost = true;
            case ReplayProtocol.RACE_TOGGLE -> config.replay.race_ghost = !config.replay.race_ghost;
            case ReplayProtocol.RACE_PB -> {
                config.replay.race_ghost = true;
                config.replay.race_against_world_record = false;
            }
            case ReplayProtocol.RACE_WR -> {
                config.replay.race_ghost = true;
                config.replay.race_against_world_record = true;
            }
            default -> {
                return;
            }
        }
        if (mode != ReplayProtocol.RACE_TOGGLE || config.replay.race_ghost) {
            hiddenThisSession = false;
        }
        ConfigWrapper.saveConfig(config);
        Minehop.LOGGER.info("[Replay] race ghost {}, against the {}", config.replay.race_ghost ? "on" : "off",
                config.replay.race_against_world_record ? "world record" : "personal best");
    }

    static void toggleHidden() {
        hiddenThisSession = !hiddenThisSession;
    }

    /** The key toggles the setting (saved), like /replay race. */
    static void toggleFromKey() {
        onControl(ReplayProtocol.RACE_TOGGLE);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.sendOverlayMessage(net.minecraft.network.chat.Component.literal(
                    settings().race_ghost ? "Race ghost on." : "Race ghost off."));
        }
    }

    static void reset() {
        loadedMap = "";
        loadedWorldRecord = false;
        loadedReplayId = "";
        loadedRecordTime = Double.NaN;
        frames = null;
        requestId = -1;
        requestKey = "";
        failedKey = "";
        retryAt = 0L;
        hiddenThisSession = false;
        wasRunning = false;
    }

    /** Fetches the replay to race on the map being run (when the map, the target or the PB/WR changed). */
    static void tick(Minecraft mc) {
        if (mc.player == null || !ClientReplays.serverStreams() || !settings().race_ghost) {
            return;
        }
        String map = MinehopClient.activeRunMap();
        boolean running = MinehopClient.startTime != 0L && !map.isBlank() && !mc.player.isSpectator();
        if (running != wasRunning) {
            wasRunning = running;
            if (running && Boolean.getBoolean("minehop.replayDebug")) {
                Minehop.LOGGER.info("[REPLAYDBG] race: run armed/started on {} (ghost {})", map, frames == null ? "not loaded" : loadedReplayId);
            }
        }
        if (map.isBlank() || mc.player.isSpectator() || ReplayPlayback.isActive()) {
            return;
        }
        boolean worldRecord = settings().race_against_world_record;
        double recordTime = recordTime(mc, map, worldRecord);
        String key = map + "|" + worldRecord + "|" + recordTime;
        boolean current = frames != null && map.equals(loadedMap) && worldRecord == loadedWorldRecord
                && (Double.compare(recordTime, loadedRecordTime) == 0);
        if (current || requestId >= 0 && key.equals(requestKey)) {
            return;
        }
        if (key.equals(failedKey) && System.nanoTime() - retryAt < 0L) {
            return;
        }
        if (requestId >= 0) {
            ClientReplayStreams.cancel(requestId);
            requestId = -1;
        }
        String cachedId = map.equals(loadedMap) && worldRecord == loadedWorldRecord ? loadedReplayId : "";
        requestKey = key;
        byte kind = worldRecord ? ReplayProtocol.KIND_RACE_WR : ReplayProtocol.KIND_RACE_PB;
        int[] issued = {-2};
        issued[0] = ClientReplayStreams.request(kind, map, "", cachedId, new ClientReplayStreams.Callback() {
            @Override
            public void loaded(ClientReplayStreams.Loaded replay) {
                if (issued[0] != -2 && requestId != issued[0]) {
                    return;
                }
                requestId = -1;
                frames = replay.frames();
                runTime = replay.header().time;
                frameNanos = ReplayTiming.frameNanos(frames, runTime);
                loadedMap = map;
                loadedWorldRecord = worldRecord;
                loadedReplayId = replay.header().replayId;
                loadedRecordTime = recordTime;
                failedKey = "";
                Minehop.LOGGER.info("[Replay] race ghost: {}'s {} on {} ({} s, {} frames)", replay.header().playerName,
                        worldRecord ? "world record" : "personal best", map, String.format(Locale.ROOT, "%.3f", runTime), frames.size());
            }

            @Override
            public void failed(String message) {
                if (issued[0] != -2 && requestId != issued[0]) {
                    return;
                }
                requestId = -1;
                failedKey = key;
                retryAt = System.nanoTime() + RETRY_NANOS;
                if (!map.equals(loadedMap) || worldRecord != loadedWorldRecord) {
                    frames = null;
                    loadedMap = "";
                    loadedReplayId = "";
                }
                Minehop.LOGGER.info("[Replay] no race ghost on {}: {}", map, message);
            }
        });
        if (issued[0] >= 0) {
            requestId = issued[0];
        }
    }

    /** The player's PB (or the map's WR) time as this client knows it, NaN if none: a change means a new replay. */
    private static double recordTime(Minecraft mc, String map, boolean worldRecord) {
        DataManager.RecordData record = worldRecord ? DataManager.getRecord(map) : DataManager.getPersonalRecord(mc.player.getScoreboardName(), map);
        return record == null ? Double.NaN : record.time;
    }

    /**
     * Where the ghost is now, or null when it isn't shown: frame = the replay's run start + the player's run time so
     * far (0 while armed in the start zone), held on the last frame after the replay's end.
     */
    static ReplayPlayback.ViewPose currentPose() {
        ReplayFrames shown = frames;
        Minecraft mc = Minecraft.getInstance();
        if (shown == null || shown.isEmpty() || !visible() || mc.player == null || mc.player.isSpectator()
                || MinehopClient.startTime == 0L || ReplayPlayback.isActive() || !Objects.equals(MinehopClient.activeRunMap(), loadedMap)) {
            return null;
        }
        double seconds = Math.max(0.0D, (System.nanoTime() - MinehopClient.startTime) / 1_000_000_000.0D);
        double position = ReplayTiming.positionAt(shown, seconds, frameNanos);
        ReplayPlayback.ViewPose pose = ReplayPlayback.poseAt(shown, position);
        if (ReplayPlayback.DEBUG) {
            Minehop.LOGGER.info(String.format(Locale.ROOT, "[REPLAYDBG] race t=%d run=%.3f frame=%d x=%.3f y=%.3f z=%.3f player=%.3f %.3f %.3f",
                    System.nanoTime(), seconds, pose.frame(), pose.x(), pose.y(), pose.z(), mc.player.getX(), mc.player.getY(), mc.player.getZ()));
        }
        return pose;
    }

    static String describe() {
        return frames == null ? "none" : loadedReplayId + " on " + loadedMap + (loadedWorldRecord ? " (WR)" : " (PB)");
    }
}
