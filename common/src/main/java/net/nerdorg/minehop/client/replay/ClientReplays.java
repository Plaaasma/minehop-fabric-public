package net.nerdorg.minehop.client.replay;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.event.KeyInputHandler;
import net.nerdorg.minehop.networking.ReplayProtocol;
import net.nerdorg.minehop.networking.payloads.ReplayBeginPayload;
import net.nerdorg.minehop.networking.payloads.ReplayChunkPayload;
import net.nerdorg.minehop.networking.payloads.ReplayControlPayload;
import net.nerdorg.minehop.networking.payloads.ReplayErrorPayload;
import net.nerdorg.minehop.networking.payloads.ReplayWatchPayload;
import net.nerdorg.minehop.platform.ClientServices;
import org.lwjgl.glfw.GLFW;

/**
 * Client-side replays (1.1.7+): the payload receivers, the keys and the per-tick work of {@link ReplayPlayback}
 * (watching), {@link RaceGhost} (racing your best) and {@link ClientReplayStreams} (fetching). Nothing here runs
 * against a server that didn't say hello: an older server never gets a replay payload from this client.
 *
 * <p>Keys (Controls > Minehop, all rebindable): pause K, back/forward 5 s Left/Right, slower/faster Down/Up (only while
 * watching), stop watching and race ghost on/off unbound. Every control is also a server command (/replay pause,
 * resume, speed, seek, skip, stop, race; /hide race), which the server passes on to this client.
 */
public final class ClientReplays {
    public static final String KEY_PAUSE = "key.minehop.replay_pause";
    public static final String KEY_BACK = "key.minehop.replay_back";
    public static final String KEY_FORWARD = "key.minehop.replay_forward";
    public static final String KEY_SLOWER = "key.minehop.replay_slower";
    public static final String KEY_FASTER = "key.minehop.replay_faster";
    public static final String KEY_STOP = "key.minehop.replay_stop";
    public static final String KEY_RACE = "key.minehop.race_ghost";

    private static KeyMapping pauseKey;
    private static KeyMapping backKey;
    private static KeyMapping forwardKey;
    private static KeyMapping slowerKey;
    private static KeyMapping fasterKey;
    private static KeyMapping stopKey;
    private static KeyMapping raceKey;

    /** The server said hello on this connection: it streams replays (and takes replay requests). */
    private static boolean serverStreams;
    private static int serverVersion;

    private ClientReplays() {
    }

    public static boolean serverStreams() {
        return serverStreams;
    }

    /** Once, from the client init: keys, ticks, rendering. */
    public static void init() {
        pauseKey = key(KEY_PAUSE, GLFW.GLFW_KEY_K);
        backKey = key(KEY_BACK, GLFW.GLFW_KEY_LEFT);
        forwardKey = key(KEY_FORWARD, GLFW.GLFW_KEY_RIGHT);
        slowerKey = key(KEY_SLOWER, GLFW.GLFW_KEY_DOWN);
        fasterKey = key(KEY_FASTER, GLFW.GLFW_KEY_UP);
        stopKey = key(KEY_STOP, InputConstants.UNKNOWN.getValue());
        raceKey = key(KEY_RACE, InputConstants.UNKNOWN.getValue());
        ClientServices.CLIENT.onClientTickEnd(ClientReplays::tick);
        ReplayGhostRenderer.register();
    }

    private static KeyMapping key(String name, int defaultKey) {
        return ClientServices.CLIENT.registerKeyMapping(new KeyMapping(name, InputConstants.Type.KEYSYM, defaultKey,
                KeyInputHandler.KEY_CATEGORY_MINEHOP));
    }

    /** On every connection (with the other receivers). */
    public static void registerReceivers() {
        ClientServices.NETWORK.registerClientReceiver(ReplayControlPayload.ID, (payload, ctx) ->
                ctx.client().execute(() -> onControl(payload)));
        ClientServices.NETWORK.registerClientReceiver(ReplayWatchPayload.ID, (payload, ctx) ->
                ctx.client().execute(() -> ReplayPlayback.onWatch(payload)));
        ClientServices.NETWORK.registerClientReceiver(ReplayBeginPayload.ID, (payload, ctx) ->
                ctx.client().execute(() -> ClientReplayStreams.onBegin(payload)));
        ClientServices.NETWORK.registerClientReceiver(ReplayChunkPayload.ID, (payload, ctx) ->
                ctx.client().execute(() -> ClientReplayStreams.onChunk(payload)));
        ClientServices.NETWORK.registerClientReceiver(ReplayErrorPayload.ID, (payload, ctx) ->
                ctx.client().execute(() -> ClientReplayStreams.onError(payload)));
    }

    /** A new connection or a disconnect (also a proxy server switch): nothing carries over. */
    public static void reset() {
        serverStreams = false;
        serverVersion = 0;
        ReplayPlayback.reset();
        RaceGhost.reset();
        ClientReplayStreams.reset();
    }

    private static void onControl(ReplayControlPayload payload) {
        switch (payload.action()) {
            case ReplayProtocol.CONTROL_HELLO -> {
                serverStreams = true;
                serverVersion = (int) payload.value();
                Minehop.LOGGER.info("[Replay] the server ({}) streams replays: client playback and race ghosts enabled", serverVersion);
            }
            case ReplayProtocol.CONTROL_RACE -> RaceGhost.onControl((int) payload.value());
            case ReplayProtocol.CONTROL_HIDE_RACE -> RaceGhost.toggleHidden();
            default -> ReplayPlayback.control(payload.action(), payload.value());
        }
    }

    private static void tick(Minecraft mc) {
        ClientReplayStreams.tick();
        ReplayPlayback.tick(mc);
        RaceGhost.tick(mc);
        handleKeys();
    }

    private static void handleKeys() {
        boolean watching = ReplayPlayback.isActive();
        while (pauseKey.consumeClick()) {
            if (watching) {
                ReplayPlayback.togglePause();
            }
        }
        while (backKey.consumeClick()) {
            if (watching) {
                ReplayPlayback.skip(-ReplayPlayback.SEEK_STEP_SECONDS);
            }
        }
        while (forwardKey.consumeClick()) {
            if (watching) {
                ReplayPlayback.skip(ReplayPlayback.SEEK_STEP_SECONDS);
            }
        }
        while (slowerKey.consumeClick()) {
            if (watching) {
                ReplayPlayback.stepSpeed(-1);
            }
        }
        while (fasterKey.consumeClick()) {
            if (watching) {
                ReplayPlayback.stepSpeed(1);
            }
        }
        while (stopKey.consumeClick()) {
            if (watching) {
                ReplayPlayback.stop(true);
            }
        }
        while (raceKey.consumeClick()) {
            RaceGhost.toggleFromKey();
        }
    }
}
