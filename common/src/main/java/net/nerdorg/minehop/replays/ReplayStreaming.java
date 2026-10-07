package net.nerdorg.minehop.replays;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.data.DataManager;
import net.nerdorg.minehop.networking.HandshakeHandler;
import net.nerdorg.minehop.networking.ReplayProtocol;
import net.nerdorg.minehop.networking.payloads.ReplayBeginPayload;
import net.nerdorg.minehop.networking.payloads.ReplayChunkPayload;
import net.nerdorg.minehop.networking.payloads.ReplayControlPayload;
import net.nerdorg.minehop.networking.payloads.ReplayErrorPayload;
import net.nerdorg.minehop.networking.payloads.ReplayRequestPayload;
import net.nerdorg.minehop.platform.Services;
import net.nerdorg.minehop.replays.storage.MhrpCodec;
import net.nerdorg.minehop.replays.storage.MhrpFormatException;
import net.nerdorg.minehop.replays.storage.MhrpHeader;
import net.nerdorg.minehop.spectate.SpectateSessions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Streams replay files to clients that play replays themselves (1.1.7+, see {@link ReplayProtocol} for the payloads):
 * the replay of a watch session (SpectateSessions, kind CLIENT_REPLAY) and race ghosts (the requester's own personal
 * best or a map's world record). Older clients are never sent any of this; they keep the server's ghost entities.
 *
 * <p>What is sent is a complete MHRP file: a stored run's column blocks byte for byte under a header without the
 * server-only fields ({@link MhrpCodec#rewriteHeader}, {@link MhrpHeader#forClients}); a run whose file isn't written yet
 * (a finish in its first second, the legacy store) is encoded from its frames instead. Files are prepared on one worker
 * thread and kept in a small cache (the same WR replay is asked for by everyone racing it).
 *
 * <p>Limits: every request is validated (the replay must be playable and be the requester's session replay, their own
 * PB or a visible WR: no invalidated, orphaned or hidden runs); {@link #REQUEST_BURST} requests at once and one per
 * {@link #REQUEST_REFILL_TICKS} ticks after that per player (on top of the per-packet throttle); one stream per slot
 * (session, race) per player, a new request replaces the old one; files above {@link ReplayProtocol#MAX_STREAM_BYTES} or
 * {@link ReplayProtocol#MAX_STREAM_FRAMES} frames are refused; chunks of {@link ReplayProtocol#CHUNK_BYTES}, at most
 * {@link #PLAYER_CHUNKS_PER_TICK} per player and {@link #SERVER_CHUNKS_PER_TICK} in total per tick (round robin), at most
 * {@link #MAX_PREPARING} files being prepared and {@link #MAX_IN_FLIGHT_BYTES} being sent at once.
 *
 * <p>Server thread only, except the file preparation on the worker.
 */
public final class ReplayStreaming {
    /** 8 KiB per tick, 160 KiB/s: a typical replay (10-60 KB) arrives within a few ticks, the longest in seconds. */
    private static final int PLAYER_CHUNKS_PER_TICK = 1;
    /** 2.5 MiB/s for all players together. */
    private static final int SERVER_CHUNKS_PER_TICK = 16;
    private static final int MAX_PREPARING = 8;
    private static final int MAX_SENDING = 128;
    private static final long MAX_IN_FLIGHT_BYTES = 64L << 20;
    private static final int REQUEST_BURST = 6;
    private static final int REQUEST_REFILL_TICKS = 40;
    private static final long CACHE_BUDGET_BYTES = 16L << 20;
    private static final int REFUSAL_LOG_TICKS = 600;
    private static final int SLOT_SESSION = 0;
    private static final int SLOT_RACE = 1;

    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Minehop replay streaming");
        thread.setDaemon(true);
        return thread;
    });

    private static final Map<UUID, PlayerStreams> PLAYERS = new HashMap<>();
    private static final List<Stream> SENDING = new ArrayList<>();
    private static final LinkedHashMap<String, byte[]> CACHE = new LinkedHashMap<>(16, 0.75F, true);
    private static long cacheBytes;
    private static long inFlightBytes;
    private static int preparing;
    private static int roundRobin;
    private static long statAccepted;
    private static long statCached;
    private static long statRefused;
    private static long statCompleted;
    private static long statBytesSent;

    private ReplayStreaming() {
    }

    /** One player's streams (one per slot) and request budget. */
    private static final class PlayerStreams {
        final Stream[] slots = new Stream[2];
        int tokens = REQUEST_BURST;
        long refilledAt;
        int refusalsSinceLog;
        long lastRefusalLog = Long.MIN_VALUE / 2;

        PlayerStreams(long tick) {
            this.refilledAt = tick;
        }

        void refill(long tick) {
            if (tick < this.refilledAt) {
                this.refilledAt = tick;
            }
            long earned = (tick - this.refilledAt) / REQUEST_REFILL_TICKS;
            if (earned > 0) {
                this.tokens = (int) Math.min(REQUEST_BURST, this.tokens + earned);
                this.refilledAt += earned * REQUEST_REFILL_TICKS;
            }
        }
    }

    /** One accepted request: preparing (no bytes yet) or sending. */
    private static final class Stream {
        final UUID player;
        final int requestId;
        final int slot;
        final ReplayManager.Replay replay;
        final String replayId;
        byte[] bytes;
        int sent;
        boolean cancelled;

        Stream(UUID player, int requestId, int slot, ReplayManager.Replay replay) {
            this.player = player;
            this.requestId = requestId;
            this.slot = slot;
            this.replay = replay;
            this.replayId = replay.replay_id;
        }
    }

    private record Prepared(byte[] bytes, String problem) {
    }

    public static void register() {
        Services.EVENTS.onServerTickEnd(ReplayStreaming::tick);
        Services.EVENTS.onServerStopped(server -> reset());
    }

    private static void reset() {
        PLAYERS.clear();
        SENDING.clear();
        CACHE.clear();
        cacheBytes = 0L;
        inFlightBytes = 0L;
        preparing = 0;
    }

    /** Sends a replay payload, only ever to a client that knows it (1.1.7+). */
    public static void send(ServerPlayer player, CustomPacketPayload payload) {
        if (player != null && !player.hasDisconnected() && HandshakeHandler.supportsClientReplays(player)) {
            Services.NETWORK.sendToPlayer(player, payload);
        }
    }

    /** The client announced 1.1.7+: tell it this server streams replays (it may then ask for race ghosts). */
    public static void onClientReady(ServerPlayer player) {
        send(player, new ReplayControlPayload(ReplayProtocol.CONTROL_HELLO, Minehop.MOD_VERSION));
    }

    public static void onDisconnect(ServerPlayer player) {
        PlayerStreams streams = player == null ? null : PLAYERS.remove(player.getUUID());
        if (streams != null) {
            for (Stream stream : streams.slots) {
                if (stream != null) {
                    stream.cancelled = true;
                }
            }
        }
    }

    /** Stops sending the replay of the player's watch session (the session ended). */
    public static void cancelSession(ServerPlayer player) {
        PlayerStreams streams = player == null ? null : PLAYERS.get(player.getUUID());
        if (streams != null) {
            cancel(streams, SLOT_SESSION);
        }
    }

    public static void onCancel(ServerPlayer player, int requestId) {
        PlayerStreams streams = player == null ? null : PLAYERS.get(player.getUUID());
        if (streams == null) {
            return;
        }
        for (int slot = 0; slot < streams.slots.length; slot++) {
            if (streams.slots[slot] != null && streams.slots[slot].requestId == requestId) {
                cancel(streams, slot);
            }
        }
    }

    private static void cancel(PlayerStreams streams, int slot) {
        Stream stream = streams.slots[slot];
        if (stream != null) {
            stream.cancelled = true;
            streams.slots[slot] = null;
        }
    }

    // ------------------------------------------------------------------------------------------
    // Requests
    // ------------------------------------------------------------------------------------------

    public static void onRequest(ServerPlayer player, ReplayRequestPayload request) {
        MinecraftServer server = player == null ? null : player.getServer();
        if (server == null || player.hasDisconnected() || !HandshakeHandler.supportsClientReplays(player)) {
            return;
        }
        long tick = server.getTickCount();
        PlayerStreams streams = PLAYERS.computeIfAbsent(player.getUUID(), uuid -> new PlayerStreams(tick));
        streams.refill(tick);
        int requestId = request.requestId();
        if (streams.tokens <= 0) {
            refuse(server, player, streams, requestId, "Too many replay requests; please wait a moment.", "rate limited");
            return;
        }
        streams.tokens--;

        int slot;
        ReplayManager.Replay replay = null;
        String problem = null;
        switch (request.kind()) {
            case ReplayProtocol.KIND_SESSION -> {
                slot = SLOT_SESSION;
                replay = SpectateSessions.clientReplayOf(player);
                if (replay == null || replay.replay_id == null || !replay.replay_id.equals(request.replayId())) {
                    problem = "You are not watching that replay.";
                    replay = null;
                }
            }
            case ReplayProtocol.KIND_RACE_PB, ReplayProtocol.KIND_RACE_WR -> {
                slot = SLOT_RACE;
                DataManager.MapData map = DataManager.getMap(request.map());
                if (map == null || map.name == null) {
                    problem = "That map doesn't exist.";
                } else if (request.kind() == ReplayProtocol.KIND_RACE_PB) {
                    replay = ReplayManager.getPersonalBestReplay(map.name, player.getScoreboardName(), player.getStringUUID());
                    if (replay == null) {
                        problem = "You have no saved personal best replay on " + map.name + ".";
                    }
                } else if (map.replay_ghost_disabled) {
                    problem = "The world record replay of " + map.name + " is hidden.";
                } else {
                    replay = ReplayManager.getReplay(map.name);
                    if (replay == null) {
                        problem = map.name + " has no world record replay.";
                    }
                }
            }
            default -> {
                refuse(server, player, streams, requestId, "Bad replay request.", "unknown kind " + request.kind());
                return;
            }
        }
        if (problem == null && (!ReplayManager.isPlayable(replay) || replay.replay_id == null)) {
            problem = "That replay is no longer available.";
        }
        cancel(streams, slot); // a new request replaces the slot's previous one
        if (problem != null) {
            refuse(server, player, streams, requestId, problem, null);
            return;
        }
        if (replay.replay_id.equals(request.cachedId())) {
            statCached++;
            send(player, new ReplayBeginPayload(requestId, replay.replay_id, 0, ReplayProtocol.BEGIN_CACHED));
            return;
        }
        int frames = ReplayManager.frameCount(replay);
        if (frames > ReplayProtocol.MAX_STREAM_FRAMES) {
            refuse(server, player, streams, requestId, "That replay is too long to stream.", frames + " frames");
            return;
        }
        Stream stream = new Stream(player.getUUID(), requestId, slot, replay);
        byte[] cached = cacheGet(replay.replay_id);
        if (cached != null) {
            streams.slots[slot] = stream;
            startSending(server, player, streams, stream, cached);
            return;
        }
        if (preparing >= MAX_PREPARING) {
            refuse(server, player, streams, requestId, "The server is busy sending replays; please try again in a moment.", "busy (preparing)");
            return;
        }
        streams.slots[slot] = stream;
        prepare(server, stream);
    }

    private static void refuse(MinecraftServer server, ServerPlayer player, PlayerStreams streams, int requestId, String message,
                               String logReason) {
        statRefused++;
        send(player, new ReplayErrorPayload(requestId, clip(message, ReplayProtocol.MAX_MESSAGE_CHARS)));
        if (logReason == null) {
            return;
        }
        // Refusals that point at a broken or abusive client are logged, at most every 30 s per player.
        streams.refusalsSinceLog++;
        long tick = server.getTickCount();
        if (tick - streams.lastRefusalLog >= REFUSAL_LOG_TICKS) {
            Minehop.LOGGER.warn("Replay streaming: refused {} request(s) from {} ({})", streams.refusalsSinceLog,
                    player.getScoreboardName(), logReason);
            streams.refusalsSinceLog = 0;
            streams.lastRefusalLog = tick;
        }
    }

    // ------------------------------------------------------------------------------------------
    // Preparing a file
    // ------------------------------------------------------------------------------------------

    private static void prepare(MinecraftServer server, Stream stream) {
        preparing++;
        Path file = ReplayManager.writtenFile(stream.replay);
        if (file == null) {
            encodeFromFrames(server, stream);
            return;
        }
        WORKER.execute(() -> {
            Prepared result = readStored(file, stream.replayId);
            server.execute(() -> {
                if (result.bytes() != null) {
                    prepared(server, stream, result);
                } else {
                    // Not readable as it is (a quarantine, a race with the writer): go through the frames, which
                    // also lets the store handle a bad file the way it always does.
                    encodeFromFrames(server, stream);
                }
            });
        });
    }

    /** The stored file under the client header, or why it can't be sent as it is. Worker thread. */
    private static Prepared readStored(Path file, String replayId) {
        try {
            long size = Files.size(file);
            if (size > MhrpCodec.MAX_FILE_BYTES) {
                return new Prepared(null, "file too large (" + size + " bytes)");
            }
            byte[] stored = Files.readAllBytes(file);
            String[] storedId = new String[1];
            byte[] rewritten = MhrpCodec.rewriteHeader(stored, header -> {
                storedId[0] = header.replayId;
                return header.forClients();
            });
            if (!replayId.equals(storedId[0])) {
                return new Prepared(null, "file holds run " + storedId[0]);
            }
            return new Prepared(rewritten, null);
        } catch (MhrpFormatException | IOException e) {
            return new Prepared(null, e.toString());
        } catch (RuntimeException | OutOfMemoryError e) {
            return new Prepared(null, e.toString());
        }
    }

    private static void encodeFromFrames(MinecraftServer server, Stream stream) {
        ReplayManager.loadFrames(stream.replay, frames -> {
            if (frames == null || frames.isEmpty()) {
                prepared(server, stream, new Prepared(null, "frames unavailable"));
                return;
            }
            MhrpHeader header = ReplayManager.streamHeader(stream.replay, frames);
            WORKER.execute(() -> {
                Prepared result;
                try {
                    result = new Prepared(MhrpCodec.encode(header, frames), null);
                } catch (RuntimeException | OutOfMemoryError e) {
                    result = new Prepared(null, e.toString());
                }
                Prepared done = result;
                server.execute(() -> prepared(server, stream, done));
            });
        });
    }

    private static void prepared(MinecraftServer server, Stream stream, Prepared result) {
        preparing = Math.max(0, preparing - 1);
        byte[] bytes = result.bytes();
        if (bytes != null && bytes.length <= ReplayProtocol.MAX_STREAM_BYTES) {
            cachePut(stream.replayId, bytes);
        }
        PlayerStreams streams = PLAYERS.get(stream.player);
        ServerPlayer player = server.getPlayerList().getPlayer(stream.player);
        if (stream.cancelled || streams == null || streams.slots[stream.slot] != stream || player == null) {
            return;
        }
        if (bytes == null) {
            streams.slots[stream.slot] = null;
            Minehop.LOGGER.warn("Replay streaming: the replay {} ({} on {}) could not be prepared: {}", stream.replayId,
                    stream.replay.player_name, stream.replay.map_name, result.problem());
            refuse(server, player, streams, stream.requestId, "That replay can't be loaded.", null);
            return;
        }
        if (bytes.length > ReplayProtocol.MAX_STREAM_BYTES) {
            streams.slots[stream.slot] = null;
            refuse(server, player, streams, stream.requestId, "That replay is too large to stream.", bytes.length + " bytes");
            return;
        }
        startSending(server, player, streams, stream, bytes);
    }

    private static void startSending(MinecraftServer server, ServerPlayer player, PlayerStreams streams, Stream stream, byte[] bytes) {
        if (SENDING.size() >= MAX_SENDING || inFlightBytes + bytes.length > MAX_IN_FLIGHT_BYTES) {
            streams.slots[stream.slot] = null;
            refuse(server, player, streams, stream.requestId, "The server is busy sending replays; please try again in a moment.", "busy (sending)");
            return;
        }
        stream.bytes = bytes;
        statAccepted++;
        send(player, new ReplayBeginPayload(stream.requestId, stream.replayId, bytes.length, (byte) 0));
        SENDING.add(stream);
        inFlightBytes += bytes.length;
    }

    // ------------------------------------------------------------------------------------------
    // Sending
    // ------------------------------------------------------------------------------------------

    private static void tick(MinecraftServer server) {
        if (SENDING.isEmpty()) {
            return;
        }
        int budget = SERVER_CHUNKS_PER_TICK;
        Map<UUID, Integer> sentTo = new HashMap<>();
        Set<Stream> finished = new HashSet<>();
        int count = SENDING.size();
        int start = Math.floorMod(roundRobin, count);
        for (int k = 0; k < count && budget > 0; k++) {
            Stream stream = SENDING.get((start + k) % count);
            if (stream.cancelled) {
                finished.add(stream);
                continue;
            }
            int already = sentTo.getOrDefault(stream.player, 0);
            if (already >= PLAYER_CHUNKS_PER_TICK) {
                continue;
            }
            ServerPlayer player = server.getPlayerList().getPlayer(stream.player);
            if (player == null) {
                stream.cancelled = true;
                finished.add(stream);
                continue;
            }
            int length = Math.min(ReplayProtocol.CHUNK_BYTES, stream.bytes.length - stream.sent);
            send(player, new ReplayChunkPayload(stream.requestId, stream.sent, Arrays.copyOfRange(stream.bytes, stream.sent, stream.sent + length)));
            stream.sent += length;
            statBytesSent += length;
            budget--;
            sentTo.put(stream.player, already + 1);
            if (stream.sent >= stream.bytes.length) {
                statCompleted++;
                finished.add(stream);
            }
        }
        roundRobin = start + 1;
        if (finished.isEmpty()) {
            return;
        }
        for (Iterator<Stream> it = SENDING.iterator(); it.hasNext(); ) {
            Stream stream = it.next();
            if (finished.contains(stream)) {
                it.remove();
                inFlightBytes -= stream.bytes.length;
                PlayerStreams streams = PLAYERS.get(stream.player);
                if (streams != null && streams.slots[stream.slot] == stream) {
                    streams.slots[stream.slot] = null;
                }
            }
        }
    }

    // ------------------------------------------------------------------------------------------
    // Cache
    // ------------------------------------------------------------------------------------------

    private static byte[] cacheGet(String replayId) {
        return replayId == null ? null : CACHE.get(replayId);
    }

    private static void cachePut(String replayId, byte[] bytes) {
        byte[] previous = CACHE.put(replayId, bytes);
        if (previous != null) {
            cacheBytes -= previous.length;
        }
        cacheBytes += bytes.length;
        Iterator<Map.Entry<String, byte[]>> it = CACHE.entrySet().iterator();
        while (cacheBytes > CACHE_BUDGET_BYTES && it.hasNext()) {
            Map.Entry<String, byte[]> eldest = it.next();
            if (eldest.getKey().equals(replayId)) {
                continue;
            }
            cacheBytes -= eldest.getValue().length;
            it.remove();
        }
    }

    /** A replay's file as it would be streamed (dev harness); null if it isn't cached. */
    public static byte[] cachedFile(String replayId) {
        return cacheGet(replayId);
    }

    // ------------------------------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------------------------------

    /** {@code value} cut to {@code max} characters (payload strings are length-checked when encoded). */
    public static String clip(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    /** One line for /replay ghosts: streams, cache and totals since the start. */
    public static String status() {
        int players = 0;
        for (PlayerStreams streams : PLAYERS.values()) {
            if (streams.slots[0] != null || streams.slots[1] != null) {
                players++;
            }
        }
        return String.format(java.util.Locale.ROOT,
                "replay streaming: %d sending (%.1f KB), %d preparing, %d player(s); cache %d runs / %.1f MB; %d accepted, %d cached, %d refused, %d completed, %.1f MB sent",
                SENDING.size(), inFlightBytes / 1024.0D, preparing, players, CACHE.size(), cacheBytes / 1048576.0D, statAccepted,
                statCached, statRefused, statCompleted, statBytesSent / 1048576.0D);
    }
}
