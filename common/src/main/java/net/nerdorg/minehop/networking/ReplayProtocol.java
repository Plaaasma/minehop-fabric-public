package net.nerdorg.minehop.networking;

/**
 * Constants of the replay streaming protocol (1.1.7+ clients play replays themselves), shared by the server
 * ({@link net.nerdorg.minehop.replays.ReplayStreaming}, SpectateSessions) and the client
 * ({@code net.nerdorg.minehop.client.replay}).
 *
 * <pre>
 * Server -> client, only ever sent to a client whose handshake announced 1.1.7+ (HandshakeHandler):
 *   minehop:replay_control  (action, value)            HELLO after the handshake (the server streams replays);
 *                                                      PAUSE / SPEED / SEEK / SEEK_BY / STOP / RACE / HIDE_RACE from
 *                                                      the /replay and /hide commands
 *   minehop:replay_watch    (session, active, kind, replay id, map, player, time, frames)
 *                                                      a watch session started (play this replay) or ended
 *   minehop:replay_begin    (request, replay id, total bytes, flags)   a request was accepted: total bytes of an
 *                                                      MHRP file follow (FLAG_CACHED: none, the client has it)
 *   minehop:replay_chunk    (request, offset, bytes)   the next CHUNK_BYTES (or fewer) of that file, in order
 *   minehop:replay_error    (request, message)         a request was refused or its stream aborted
 * Client -> server, only after HELLO:
 *   minehop:replay_request  (request, kind, map, replay id, cached id)  KIND_SESSION: the replay of the client's
 *                                                      watch session (by id); KIND_RACE_PB / KIND_RACE_WR: the
 *                                                      requester's own personal best / the world record on a map
 *   minehop:replay_cancel   (request)                  stop sending that stream
 *   minehop:replay_state    (session, frame, speed, flags)  the watch playback's position (the server keeps the
 *                                                      viewer near it so the chunks there are loaded); FLAG_STOP ends it
 * </pre>
 *
 * The file is a complete MHRP file (MhrpCodec): the stored run's column blocks byte for byte under a header without
 * the server-only fields (MhrpHeader#forClients), checked by the client's strict decoder (CRC, every length).
 */
public final class ReplayProtocol {
    /** Bytes of the replay file per chunk payload (far below the 1 MiB custom payload limit). */
    public static final int CHUNK_BYTES = 8 * 1024;
    /** Largest replay file the server streams and the client accepts (a 60-minute recording is about 0.5 MB). */
    public static final int MAX_STREAM_BYTES = 4 << 20;
    /** Most frames a streamed replay may have (3.6 hours at 20 frames/s; the longest stored run has 119,775). */
    public static final int MAX_STREAM_FRAMES = 1 << 18;

    public static final int MAX_MAP_CHARS = 256;
    public static final int MAX_ID_CHARS = 64;
    public static final int MAX_NAME_CHARS = 64;
    public static final int MAX_MESSAGE_CHARS = 256;

    /** Playback speed range (the client's keys step through 0.25x .. 4x). */
    public static final float MIN_SPEED = 0.1F;
    public static final float MAX_SPEED = 8.0F;

    // replay_request kinds
    public static final byte KIND_SESSION = 0;
    public static final byte KIND_RACE_PB = 1;
    public static final byte KIND_RACE_WR = 2;

    // replay_watch kinds
    public static final byte WATCH_WORLD_RECORD = 1;
    public static final byte WATCH_PERSONAL_BEST = 2;

    // replay_begin flags
    /** The client already has this replay (the cached id it sent): no bytes follow. */
    public static final byte BEGIN_CACHED = 1;

    // replay_state flags
    public static final byte STATE_PAUSED = 1;
    /** The client is waiting (for the replay's bytes or for the chunks where it plays): its clock is stopped. */
    public static final byte STATE_BUFFERING = 2;
    /** The viewer stopped watching (ends the session). */
    public static final byte STATE_STOP = 4;

    // replay_control actions
    /** The server streams replays to this client; value = the server's MOD_VERSION. */
    public static final byte CONTROL_HELLO = 0;
    /** value: 0 = resume, 1 = pause, 2 = toggle. */
    public static final byte CONTROL_PAUSE = 1;
    /** value: the playback speed (clamped to MIN_SPEED..MAX_SPEED). */
    public static final byte CONTROL_SPEED = 2;
    /** value: seconds into the run (negative = into the pre-run frames). */
    public static final byte CONTROL_SEEK = 3;
    /** value: seconds to move forward (negative = back). */
    public static final byte CONTROL_SEEK_BY = 4;
    public static final byte CONTROL_STOP = 5;
    /** value: RACE_OFF, RACE_ON, RACE_TOGGLE, RACE_PB or RACE_WR. */
    public static final byte CONTROL_RACE = 6;
    /** Toggles the race ghost's visibility for this connection (/hide race). */
    public static final byte CONTROL_HIDE_RACE = 7;

    public static final int RACE_OFF = 0;
    public static final int RACE_ON = 1;
    public static final int RACE_TOGGLE = 2;
    public static final int RACE_PB = 3;
    public static final int RACE_WR = 4;

    private ReplayProtocol() {
    }
}
