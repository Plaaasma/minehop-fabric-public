package net.nerdorg.minehop.client.replay;

import net.minecraft.client.Minecraft;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.networking.ReplayProtocol;
import net.nerdorg.minehop.networking.payloads.ReplayBeginPayload;
import net.nerdorg.minehop.networking.payloads.ReplayCancelPayload;
import net.nerdorg.minehop.networking.payloads.ReplayChunkPayload;
import net.nerdorg.minehop.networking.payloads.ReplayErrorPayload;
import net.nerdorg.minehop.networking.payloads.ReplayRequestPayload;
import net.nerdorg.minehop.platform.ClientServices;
import net.nerdorg.minehop.replays.storage.MhrpCodec;
import net.nerdorg.minehop.replays.storage.MhrpFormatException;
import net.nerdorg.minehop.replays.storage.MhrpHeader;
import net.nerdorg.minehop.replays.storage.ReplayFrames;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The client end of replay streaming (see ReplayProtocol): asks the server for replays, assembles their chunks, checks
 * and decodes the file off the client thread with the strict MHRP decoder, and keeps decoded replays in a small cache
 * for the connection (a replay id always names the same run, so a cached one is never fetched again).
 *
 * <p>Nothing the server sends is trusted: a stream must begin before its chunks, chunks must arrive in order without
 * gaps or overlap and not exceed the announced size, the size and frame count are capped, the file must pass its CRC
 * and every structural check, and it must be the run that was asked for. Anything else fails the request (and cancels
 * the stream) without affecting the game. Client thread, except the decoding.
 */
public final class ClientReplayStreams {
    /** A request without any progress for this long is given up. */
    private static final long TIMEOUT_NANOS = 30_000_000_000L;
    private static final long CACHE_BUDGET_BYTES = 48L << 20;
    /** Smallest possible MHRP file (prefix, empty header strings, block count, CRC). */
    private static final int MIN_FILE_BYTES = 16;

    private static final ExecutorService DECODER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Minehop replay decoder");
        thread.setDaemon(true);
        return thread;
    });

    private static final Map<Integer, Request> REQUESTS = new HashMap<>();
    private static final LinkedHashMap<String, Loaded> CACHE = new LinkedHashMap<>(16, 0.75F, true);
    private static long cacheBytes;
    private static int nextRequestId = (int) (System.nanoTime() & 0x3FFFFFFF);
    /** Bumped on every reset (disconnect): results of an earlier connection's decoding are dropped. */
    private static int generation;
    private static long statBytes;
    private static int statLoaded;
    private static int statFailed;

    private ClientReplayStreams() {
    }

    /** A decoded replay: its header (client copy) and frames (with their layout). */
    public record Loaded(MhrpHeader header, ReplayFrames frames) {
    }

    /** Result of a request, on the client thread. */
    public interface Callback {
        void loaded(Loaded replay);

        void failed(String message);
    }

    private static final class Request {
        final int id;
        final byte kind;
        final String map;
        final String expectedId;
        final Callback callback;
        long lastProgress;
        String replayId;
        byte[] buffer;
        int received;
        boolean retriedWithoutCache;

        Request(int id, byte kind, String map, String expectedId, Callback callback) {
            this.id = id;
            this.kind = kind;
            this.map = map;
            this.expectedId = expectedId;
            this.callback = callback;
            this.lastProgress = System.nanoTime();
        }
    }

    /** The cached replay with this id, or null. */
    public static Loaded cached(String replayId) {
        return replayId == null || replayId.isEmpty() ? null : CACHE.get(replayId);
    }

    /**
     * Asks the server for a replay (a ReplayProtocol KIND_*); {@code callback} runs on the client thread, unless the
     * request is cancelled or the connection ends first. A KIND_SESSION replay already in the cache is handed over at
     * once. Returns the request id (-1 if nothing was sent).
     */
    public static int request(byte kind, String map, String replayId, String cachedId, Callback callback) {
        if (kind == ReplayProtocol.KIND_SESSION) {
            Loaded cached = cached(replayId);
            if (cached != null) {
                callback.loaded(cached);
                return -1;
            }
        }
        if (!ClientReplays.serverStreams()) {
            callback.failed("This server doesn't send replays.");
            return -1;
        }
        int id = nextRequestId++ & 0x3FFFFFFF;
        Request request = new Request(id, kind, clip(map, ReplayProtocol.MAX_MAP_CHARS), clip(replayId, ReplayProtocol.MAX_ID_CHARS), callback);
        REQUESTS.put(id, request);
        send(request, clip(cachedId, ReplayProtocol.MAX_ID_CHARS));
        return id;
    }

    private static void send(Request request, String cachedId) {
        try {
            ClientServices.NETWORK.sendToServer(new ReplayRequestPayload(request.id, request.kind, request.map, request.expectedId, cachedId));
        } catch (RuntimeException e) {
            REQUESTS.remove(request.id);
            request.callback.failed("Not connected.");
        }
    }

    /** Stops a request: no callback, and the server is told to stop sending it. */
    public static void cancel(int requestId) {
        if (REQUESTS.remove(requestId) != null) {
            try {
                ClientServices.NETWORK.sendToServer(new ReplayCancelPayload(requestId));
            } catch (RuntimeException ignored) {
                // not connected any more: nothing to cancel
            }
        }
    }

    /** Progress of a request in [0, 1] (0 before its stream began), or -1 if it isn't pending. */
    public static float progress(int requestId) {
        Request request = REQUESTS.get(requestId);
        if (request == null) {
            return -1.0F;
        }
        return request.buffer == null || request.buffer.length == 0 ? 0.0F : request.received / (float) request.buffer.length;
    }

    // ------------------------------------------------------------------------------------------
    // Receiving (client thread)
    // ------------------------------------------------------------------------------------------

    static void onBegin(ReplayBeginPayload begin) {
        Request request = REQUESTS.get(begin.requestId());
        if (request == null) {
            return; // cancelled meanwhile
        }
        if (request.replayId != null) {
            fail(request, "The server sent a replay twice.", true);
            return;
        }
        if (request.kind == ReplayProtocol.KIND_SESSION && !request.expectedId.equals(begin.replayId())) {
            fail(request, "The server sent the wrong replay.", true);
            return;
        }
        request.lastProgress = System.nanoTime();
        if ((begin.flags() & ReplayProtocol.BEGIN_CACHED) != 0) {
            Loaded cached = cached(begin.replayId());
            if (cached != null) {
                REQUESTS.remove(request.id);
                request.callback.loaded(cached);
            } else if (!request.retriedWithoutCache) {
                // Evicted since: ask again for the bytes.
                request.retriedWithoutCache = true;
                send(request, "");
            } else {
                fail(request, "The replay could not be loaded.", false);
            }
            return;
        }
        int total = begin.totalBytes();
        if (total < MIN_FILE_BYTES || total > ReplayProtocol.MAX_STREAM_BYTES || begin.replayId().isEmpty()) {
            fail(request, "The server sent a replay of an invalid size (" + total + " bytes).", true);
            return;
        }
        request.replayId = begin.replayId();
        request.buffer = new byte[total];
    }

    static void onChunk(ReplayChunkPayload chunk) {
        Request request = REQUESTS.get(chunk.requestId());
        if (request == null) {
            return; // cancelled meanwhile; the server stops on our cancel
        }
        byte[] data = chunk.data();
        if (request.buffer == null || request.received == request.buffer.length) {
            fail(request, "The server sent replay data out of order.", true);
            return;
        }
        if (chunk.offset() != request.received || data.length == 0 || data.length > request.buffer.length - request.received) {
            fail(request, "The server sent malformed replay data.", true);
            return;
        }
        System.arraycopy(data, 0, request.buffer, request.received, data.length);
        request.received += data.length;
        request.lastProgress = System.nanoTime();
        statBytes += data.length;
        if (request.received == request.buffer.length) {
            decode(request);
        }
    }

    static void onError(ReplayErrorPayload error) {
        Request request = REQUESTS.remove(error.requestId());
        if (request != null) {
            statFailed++;
            request.callback.failed(error.message().isBlank() ? "The server refused the replay." : error.message());
        }
    }

    private static void decode(Request request) {
        byte[] file = request.buffer;
        String replayId = request.replayId;
        int decodeGeneration = generation;
        request.lastProgress = System.nanoTime();
        DECODER.execute(() -> {
            Loaded loaded = null;
            String problem = null;
            try {
                MhrpHeader header = MhrpCodec.decodeHeader(file, true);
                if (!replayId.equals(header.replayId)) {
                    problem = "The server sent the wrong replay.";
                } else if (header.frameCount <= 0 || header.frameCount > ReplayProtocol.MAX_STREAM_FRAMES) {
                    problem = "The replay has an invalid number of frames (" + header.frameCount + ").";
                } else {
                    MhrpCodec.Decoded decoded = MhrpCodec.decode(file);
                    loaded = new Loaded(decoded.header(), decoded.frames());
                }
            } catch (MhrpFormatException e) {
                problem = "The replay is damaged (" + e.getMessage() + ").";
            } catch (RuntimeException | OutOfMemoryError e) {
                problem = "The replay could not be read (" + e + ").";
            }
            Loaded result = loaded;
            String failure = problem;
            Minecraft.getInstance().execute(() -> {
                if (decodeGeneration != generation || REQUESTS.get(request.id) != request) {
                    return;
                }
                if (result == null) {
                    fail(request, failure, false);
                    return;
                }
                REQUESTS.remove(request.id);
                cachePut(replayId, result);
                statLoaded++;
                request.callback.loaded(result);
            });
        });
    }

    private static void fail(Request request, String message, boolean cancelStream) {
        statFailed++;
        Minehop.LOGGER.warn("Replay stream {} failed: {}", request.id, message);
        if (cancelStream) {
            cancel(request.id);
        } else {
            REQUESTS.remove(request.id);
        }
        request.callback.failed(message);
    }

    /** Gives up requests that made no progress for {@link #TIMEOUT_NANOS}. Client tick. */
    static void tick() {
        if (REQUESTS.isEmpty()) {
            return;
        }
        long now = System.nanoTime();
        List<Request> stale = new ArrayList<>();
        for (Request request : REQUESTS.values()) {
            if (now - request.lastProgress > TIMEOUT_NANOS) {
                stale.add(request);
            }
        }
        for (Request request : stale) {
            fail(request, "The replay took too long to arrive.", true);
        }
    }

    /** Forgets every request (without callbacks) and the cache: the connection ended. */
    static void reset() {
        generation++;
        REQUESTS.clear();
        CACHE.clear();
        cacheBytes = 0L;
    }

    private static void cachePut(String replayId, Loaded loaded) {
        Loaded previous = CACHE.put(replayId, loaded);
        if (previous != null) {
            cacheBytes -= previous.frames().approxBytes();
        }
        cacheBytes += loaded.frames().approxBytes();
        Iterator<Map.Entry<String, Loaded>> it = CACHE.entrySet().iterator();
        while (cacheBytes > CACHE_BUDGET_BYTES && it.hasNext()) {
            Map.Entry<String, Loaded> eldest = it.next();
            if (eldest.getKey().equals(replayId)) {
                continue;
            }
            cacheBytes -= eldest.getValue().frames().approxBytes();
            it.remove();
        }
    }

    private static String clip(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    /** One line for the debug log: requests, cache and totals of this connection. */
    static String status() {
        return String.format(java.util.Locale.ROOT, "%d pending, cache %d runs / %.1f MB, %d loaded, %d failed, %.1f KB received",
                REQUESTS.size(), CACHE.size(), cacheBytes / 1048576.0D, statLoaded, statFailed, statBytes / 1024.0D);
    }
}
