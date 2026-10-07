package net.nerdorg.minehop.spectate;

import net.minecraft.network.protocol.game.ClientboundSetCameraPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerPlayerConnection;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.data.DataManager;
import net.nerdorg.minehop.entity.custom.Zone;
import net.nerdorg.minehop.mixin.ChunkMapAccessor;
import net.nerdorg.minehop.mixin.TrackedEntityAccessor;
import net.nerdorg.minehop.networking.PacketHandler;
import net.nerdorg.minehop.platform.Services;
import net.nerdorg.minehop.replays.ReplayGhosts;
import net.nerdorg.minehop.replays.ReplayManager;
import net.nerdorg.minehop.replays.RunStats;
import net.nerdorg.minehop.util.Logger;
import net.nerdorg.minehop.util.ZoneUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Server-side spectate sessions (/spec, /spectate, /replay watch), keyed by the viewer's UUID.
 *
 * <p>A session puts the viewer in spectator mode and remembers where they were: game mode, dimension, position,
 * rotation, fall distance and the map they were last on. Ending it (/unspec, sneaking, the target leaving or no
 * longer being spectatable, disconnecting, the server stopping) puts all of that back. While it lasts the server
 * owns the camera: the viewer can't release it, switch it to another entity, use the spectator teleport menu or
 * move on their own (see ServerPlayerSpectateMixin / ServerPlayNetworkHandlerSpectateMixin). The camera follows the
 * target across dimensions and is (re)sent only once the viewer's client has been sent the target entity, so it
 * also attaches after a dimension change, a respawn or a replay ghost being respawned.
 *
 * <p>A session can't affect runs: starting one ends the viewer's run (timer, finish stamp, recording), and spectators
 * are ignored by the zones, the finish handler and the anticheat. Server thread only.
 */
public final class SpectateSessions {
    /** Minimum time between starting (or switching) sessions, per player: they spawn ghosts and move players. */
    public static final int START_COOLDOWN_TICKS = 40;
    private static final int ORPHAN_CHECK_TICKS = 20;
    private static final int HUD_INTERVAL_TICKS = 5;
    /** A viewer waiting for a replay ghost to spawn is kept within this distance of where it will appear. */
    private static final double PENDING_FOLLOW_DISTANCE = 16.0D;

    public enum Kind {
        /** A live player. */
        PLAYER,
        /** A map's world-record ghost. */
        WORLD_RECORD,
        /** The viewer's own ghost of someone's personal best (/replay watch). */
        PERSONAL_BEST
    }

    private record ReturnState(GameType gameMode, ResourceKey<Level> dimension, Vec3 position, float yRot, float xRot,
                               float fallDistance, Zone mapLocation) {
    }

    public static final class Session {
        private final UUID viewer;
        private final Kind kind;
        private final UUID targetPlayer;
        private final String targetName;
        private final String mapName;
        private final ReturnState back;
        private boolean clientAttached;
        private boolean shiftReleased;
        private long lastRefusalMessageTick = Long.MIN_VALUE;
        private RunStats.Snapshot lastStats;
        private long lastStatsTick = Long.MIN_VALUE / 2;

        private Session(UUID viewer, Kind kind, UUID targetPlayer, String targetName, String mapName, ReturnState back) {
            this.viewer = viewer;
            this.kind = kind;
            this.targetPlayer = targetPlayer;
            this.targetName = targetName;
            this.mapName = mapName;
            this.back = back;
        }

        public Kind kind() {
            return this.kind;
        }

        /** The spectated player's UUID (PLAYER sessions), else null. */
        public UUID targetPlayer() {
            return this.targetPlayer;
        }

        /** The spectated player's name, or the replay's player name. */
        public String targetName() {
            return this.targetName;
        }

        /** The replay's map (replay sessions), else null. */
        public String mapName() {
            return this.mapName;
        }

        /** Whether the camera packet was sent after the client had the target (i.e. the viewer sees through it). */
        public boolean clientAttached() {
            return this.clientAttached;
        }
    }

    private static final Map<UUID, Session> SESSIONS = new LinkedHashMap<>();
    private static final Map<UUID, Long> LAST_START = new HashMap<>();
    /** The spectator list last sent to each spectated player (sent again only when it changes). */
    private static final Map<UUID, List<String>> SENT_LISTS = new HashMap<>();
    /** Set while this class itself changes a viewer's camera; every other change is refused during a session. */
    private static boolean movingCamera;

    private SpectateSessions() {
    }

    public static void register() {
        Services.EVENTS.onServerTickEnd(SpectateSessions::tick);
        // Give everyone their game mode and position back before the players are saved.
        Services.EVENTS.onServerStopping(server -> {
            for (Session session : new ArrayList<>(SESSIONS.values())) {
                ServerPlayer viewer = server.getPlayerList().getPlayer(session.viewer);
                if (viewer != null) {
                    end(viewer, session, null, true);
                }
            }
        });
        Services.EVENTS.onServerStopped(server -> {
            SESSIONS.clear();
            LAST_START.clear();
            SENT_LISTS.clear();
        });
    }

    // ------------------------------------------------------------------------------------------
    // Queries
    // ------------------------------------------------------------------------------------------

    public static boolean isSpectating(ServerPlayer player) {
        return player != null && SESSIONS.containsKey(player.getUUID());
    }

    public static Session session(ServerPlayer player) {
        return player == null ? null : SESSIONS.get(player.getUUID());
    }

    /** The online players spectating this player. */
    public static List<ServerPlayer> spectatorsOf(ServerPlayer target) {
        List<ServerPlayer> viewers = new ArrayList<>();
        if (target == null || SESSIONS.isEmpty()) {
            return viewers;
        }
        MinecraftServer server = target.getServer();
        for (Session session : SESSIONS.values()) {
            if (session.kind == Kind.PLAYER && target.getUUID().equals(session.targetPlayer)) {
                ServerPlayer viewer = server == null ? null : server.getPlayerList().getPlayer(session.viewer);
                if (viewer != null) {
                    viewers.add(viewer);
                }
            }
        }
        return viewers;
    }

    /** Whether anyone is watching this ghost. */
    public static boolean isWatched(ReplayGhosts.Ghost ghost) {
        for (Session session : SESSIONS.values()) {
            if (ghost.isWorldRecord()
                    ? session.kind == Kind.WORLD_RECORD && ghost.mapName().equals(session.mapName)
                    : session.kind == Kind.PERSONAL_BEST && session.viewer.equals(ghost.viewer())) {
                return true;
            }
        }
        return false;
    }

    /** Null if the player may start a session now, else why not. */
    public static String cooldownMessage(ServerPlayer viewer) {
        MinecraftServer server = viewer.getServer();
        Long last = LAST_START.get(viewer.getUUID());
        if (server != null && last != null && server.getTickCount() - last < START_COOLDOWN_TICKS) {
            return "Please wait a moment before spectating again.";
        }
        return null;
    }

    // ------------------------------------------------------------------------------------------
    // Starting and ending
    // ------------------------------------------------------------------------------------------

    public static void startPlayer(ServerPlayer viewer, ServerPlayer target) {
        Session session = begin(viewer, Kind.PLAYER, target.getUUID(), target.getScoreboardName(), null);
        follow(viewer.getServer(), session, viewer);
    }

    /** Watches the map's world-record ghost; false if the map has none. */
    public static boolean startWorldRecord(ServerPlayer viewer, String mapName) {
        ReplayGhosts.Ghost ghost = ReplayGhosts.worldRecordGhost(mapName);
        if (ghost == null) {
            return false;
        }
        Session session = begin(viewer, Kind.WORLD_RECORD, null, ghost.replay().player_name, mapName);
        follow(viewer.getServer(), session, viewer);
        return true;
    }

    /** Watches a personal-best replay from its start on a ghost of the viewer's own. */
    public static void startPersonalBest(ServerPlayer viewer, String mapName, ReplayManager.Replay replay) {
        Session session = begin(viewer, Kind.PERSONAL_BEST, null, replay.player_name, mapName);
        ReplayGhosts.startViewerGhost(viewer.getUUID(), mapName, replay);
        follow(viewer.getServer(), session, viewer);
    }

    /** Ends the viewer's session and restores them. False if they weren't spectating. */
    public static boolean stop(ServerPlayer viewer, String message) {
        Session session = session(viewer);
        if (session == null) {
            return false;
        }
        end(viewer, session, message, true);
        return true;
    }

    private static Session begin(ServerPlayer viewer, Kind kind, UUID targetPlayer, String targetName, String mapName) {
        MinecraftServer server = viewer.getServer();
        Session previous = SESSIONS.remove(viewer.getUUID());
        // Switching targets keeps the place the player first came from.
        ReturnState back = previous != null ? previous.back : capture(viewer);
        if (previous != null && previous.kind == Kind.PERSONAL_BEST) {
            ReplayGhosts.stopViewerGhost(viewer.getUUID());
        }
        // Watching ends the viewer's own run: no timer, finish stamp or recording carries through a session.
        PacketHandler.clearRunState(viewer, server);
        PacketHandler.clearReplayPath(viewer);
        Session session = new Session(viewer.getUUID(), kind, targetPlayer, targetName, mapName, back);
        session.shiftReleased = !viewer.isShiftKeyDown();
        SESSIONS.put(viewer.getUUID(), session);
        if (server != null) {
            LAST_START.put(viewer.getUUID(), (long) server.getTickCount());
        }
        if (!viewer.isSpectator()) {
            viewer.setGameMode(GameType.SPECTATOR);
        }
        moveCamera(viewer, viewer);
        return session;
    }

    private static ReturnState capture(ServerPlayer viewer) {
        return new ReturnState(viewer.gameMode.getGameModeForPlayer(), viewer.level().dimension(), viewer.position(),
                viewer.getYRot(), viewer.getXRot(), viewer.fallDistance, Minehop.playerMapLocation.get(viewer.getStringUUID()));
    }

    /**
     * Ends a session and puts the viewer back where they started it. The game mode is not touched when something
     * else already changed it (e.g. an operator's /gamemode).
     */
    private static void end(ServerPlayer viewer, Session session, String message, boolean restoreGameMode) {
        SESSIONS.remove(session.viewer);
        if (session.kind == Kind.PERSONAL_BEST) {
            ReplayGhosts.stopViewerGhost(session.viewer);
        }
        moveCamera(viewer, viewer);
        ReturnState back = session.back;
        MinecraftServer server = viewer.getServer();
        try {
            ServerLevel level = server == null ? null : server.getLevel(back.dimension());
            if (level != null) {
                viewer.changeDimension(ZoneUtil.makeTeleportTarget(level, back.position(), back.yRot(), back.xRot()));
            } else {
                sendToSpawn(viewer);
            }
        } finally {
            if (restoreGameMode && viewer.isSpectator() && back.gameMode() != GameType.SPECTATOR) {
                viewer.setGameMode(back.gameMode());
            }
            // Same fall as before the pause: watching can't be used to cancel fall damage.
            viewer.fallDistance = back.fallDistance();
            if (back.mapLocation() != null) {
                Minehop.playerMapLocation.put(viewer.getStringUUID(), back.mapLocation());
            } else {
                Minehop.playerMapLocation.remove(viewer.getStringUUID());
            }
        }
        PacketHandler.clearRunTimerHud(viewer);
        PacketHandler.sendSpecEfficiency(viewer, 0.0D, 0, 0.0D);
        if (message != null) {
            Logger.logSuccess(viewer, message);
        }
    }

    // ------------------------------------------------------------------------------------------
    // Enforcement hooks (mixins)
    // ------------------------------------------------------------------------------------------

    /** Only this class may change the camera of a player in a session (setting it to what it already is is fine). */
    public static boolean allowCameraChange(ServerPlayer player, Entity camera) {
        if (movingCamera || SESSIONS.isEmpty() || !SESSIONS.containsKey(player.getUUID())) {
            return true;
        }
        Entity effective = camera == null ? player : camera;
        return effective == player.getCamera();
    }

    /** The spectator teleport menu was used during a session (the packet is dropped). */
    public static void onTeleportRequestRefused(ServerPlayer player) {
        Session session = session(player);
        MinecraftServer server = player.getServer();
        if (session != null && server != null && server.getTickCount() - session.lastRefusalMessageTick > 40) {
            session.lastRefusalMessageTick = server.getTickCount();
            Logger.logFailure(player, "You can't teleport while spectating. Use /unspec to stop spectating.");
        }
    }

    private static void moveCamera(ServerPlayer viewer, Entity camera) {
        movingCamera = true;
        try {
            viewer.setCamera(camera);
        } finally {
            movingCamera = false;
        }
    }

    // ------------------------------------------------------------------------------------------
    // Connection events
    // ------------------------------------------------------------------------------------------

    /** The viewer is disconnecting: restore them before they are saved. Sessions on them end next tick. */
    public static void onDisconnect(ServerPlayer player) {
        if (player == null) {
            return;
        }
        Session session = session(player);
        if (session != null) {
            end(player, session, null, true);
        }
        LAST_START.remove(player.getUUID());
        SENT_LISTS.remove(player.getUUID());
    }

    public static void onJoin(ServerPlayer player) {
        enforceNoStraySpectator(player);
    }

    /**
     * Only a session (or an operator) puts a player in spectator mode. A player left in it some other way - e.g. one
     * who disconnected mid-spectate under an older version - would have free no-clip flight and the vanilla teleport
     * menu, so they are put back in the default game mode at spawn.
     */
    private static void enforceNoStraySpectator(ServerPlayer player) {
        if (player == null || !player.isSpectator() || SESSIONS.containsKey(player.getUUID()) || player.hasPermissions(2)
                || Services.PLATFORM.isFakePlayer(player)) {
            return;
        }
        MinecraftServer server = player.getServer();
        GameType mode = server == null ? GameType.ADVENTURE : server.getDefaultGameType();
        if (mode == GameType.SPECTATOR) {
            mode = GameType.ADVENTURE;
        }
        player.setGameMode(mode);
        sendToSpawn(player);
        Minehop.LOGGER.info("{} was in spectator mode outside a spectate session; reset to {} at spawn", player.getScoreboardName(), mode.getName());
        Logger.logFailure(player, "You were in spectator mode without spectating anyone, so you were sent back to spawn.");
    }

    private static void sendToSpawn(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        DataManager.MapData spawn = DataManager.getMap("spawn");
        ServerLevel level = server.overworld();
        if (spawn != null && spawn.worldKey != null) {
            for (ServerLevel candidate : server.getAllLevels()) {
                if (candidate.dimension().toString().equals(spawn.worldKey)) {
                    level = candidate;
                    break;
                }
            }
        }
        Vec3 position = spawn != null ? new Vec3(spawn.x, spawn.y, spawn.z) : Vec3.atBottomCenterOf(level.getSharedSpawnPos());
        float yRot = spawn != null ? (float) spawn.yrot : 0.0F;
        float xRot = spawn != null ? (float) spawn.xrot : 0.0F;
        player.changeDimension(ZoneUtil.makeTeleportTarget(level, position, yRot, xRot));
    }

    // ------------------------------------------------------------------------------------------
    // Per tick
    // ------------------------------------------------------------------------------------------

    private static void tick(MinecraftServer server) {
        if (!SESSIONS.isEmpty()) {
            for (Session session : new ArrayList<>(SESSIONS.values())) {
                ServerPlayer viewer = server.getPlayerList().getPlayer(session.viewer);
                if (viewer == null) {
                    SESSIONS.remove(session.viewer);
                    ReplayGhosts.stopViewerGhost(session.viewer);
                    continue;
                }
                if (!viewer.isSpectator()) {
                    // Something else changed their game mode: the session is over, keep that mode.
                    end(viewer, session, "No longer spectating.", false);
                    continue;
                }
                if (!viewer.isShiftKeyDown()) {
                    session.shiftReleased = true;
                } else if (session.shiftReleased) {
                    // Sneaking leaves spectating, like in vanilla - but through the session, so the player is restored.
                    end(viewer, session, "No longer spectating.", true);
                    continue;
                }
                follow(server, session, viewer);
                if (SESSIONS.get(session.viewer) == session) {
                    sendHud(server, session, viewer);
                }
            }
        }
        updateSpectatorLists(server);
        if (server.getTickCount() % ORPHAN_CHECK_TICKS == 0) {
            for (ServerPlayer player : new ArrayList<>(server.getPlayerList().getPlayers())) {
                enforceNoStraySpectator(player);
            }
        }
    }

    /** Keeps the viewer on the target: same dimension, camera set, and the camera sent once the client has it. */
    private static void follow(MinecraftServer server, Session session, ServerPlayer viewer) {
        if (server == null) {
            return;
        }
        Entity target;
        ReplayGhosts.Ghost ghost = null;
        switch (session.kind) {
            case PLAYER -> {
                ServerPlayer player = server.getPlayerList().getPlayer(session.targetPlayer);
                if (player == null) {
                    end(viewer, session, session.targetName + " left the server; no longer spectating.", true);
                    return;
                }
                if (player.isSpectator() || player.isCreative()) {
                    end(viewer, session, session.targetName + " can't be spectated right now; no longer spectating.", true);
                    return;
                }
                target = player;
            }
            case WORLD_RECORD -> {
                ghost = ReplayGhosts.worldRecordGhost(session.mapName);
                if (ghost == null) {
                    end(viewer, session, "The world record replay of " + session.mapName + " is no longer available.", true);
                    return;
                }
                target = ghost.entity();
            }
            default -> {
                ghost = ReplayGhosts.viewerGhost(session.viewer);
                if (ghost == null) {
                    end(viewer, session, "That replay is no longer available.", true);
                    return;
                }
                target = ghost.entity();
            }
        }
        if (target == null) {
            // The ghost isn't spawned yet: keep the viewer where it will appear, so that area loads and it can spawn.
            session.clientAttached = false;
            if (viewer.getCamera() != viewer) {
                moveCamera(viewer, viewer);
            }
            ServerLevel level = ReplayGhosts.level(server, ghost);
            Vec3 position = ReplayGhosts.position(ghost);
            if (level != null && (viewer.level() != level
                    || viewer.position().distanceToSqr(position) > PENDING_FOLLOW_DISTANCE * PENDING_FOLLOW_DISTANCE)) {
                ReplayManager.ReplayEntry entry = ghost.currentEntry();
                viewer.changeDimension(ZoneUtil.makeTeleportTarget(level, position, (float) entry.yrot, (float) entry.xrot));
                // Vanilla moves a player's chunk tickets when the client's move packet confirms a teleport, but the
                // viewer's moves are ignored during a session: move them here, or the area would never load.
                viewer.serverLevel().getChunkSource().move(viewer);
            }
            return;
        }
        if (viewer.level() != target.level() && target.level() instanceof ServerLevel targetLevel) {
            // Follow across dimensions. The client starts the new dimension with its own camera, so it is sent again below.
            viewer.changeDimension(ZoneUtil.makeTeleportTarget(targetLevel, target.position(), target.getYRot(), target.getXRot()));
            viewer.serverLevel().getChunkSource().move(viewer);
            session.clientAttached = false;
        }
        if (viewer.getCamera() != target) {
            moveCamera(viewer, target);
            session.clientAttached = false;
        }
        if (!isSentTo(target, viewer)) {
            // The client doesn't have the entity (yet, or any more): a camera packet now would be ignored.
            session.clientAttached = false;
        } else if (!session.clientAttached) {
            viewer.connection.send(new ClientboundSetCameraPacket(target));
            session.clientAttached = true;
        }
    }

    /** Diagnostics for /spectest: whether the viewer's camera target is tracked for (sent to) the viewer. */
    public static String trackingDebug(ServerPlayer viewer) {
        Entity camera = viewer.getCamera();
        if (camera == viewer || !(camera.level() instanceof ServerLevel level)) {
            return "camera=self";
        }
        ChunkMap chunkMap = level.getChunkSource().chunkMap;
        Object tracked = ((ChunkMapAccessor) chunkMap).minehop$getEntityMap().get(camera.getId());
        Set<ServerPlayerConnection> seenBy = tracked == null ? Set.of() : ((TrackedEntityAccessor) tracked).minehop$getSeenBy();
        boolean chunkTracked = chunkMap.getPlayers(camera.chunkPosition(), false).contains(viewer);
        return "tracker=" + (tracked != null) + " seenBy=" + seenBy.size() + " containsViewer=" + seenBy.contains(viewer.connection)
                + " chunkTrackedForViewer=" + chunkTracked + " sameLevel=" + (viewer.level() == camera.level());
    }

    /** True once the server has sent this entity to the player's client (vanilla records it after the spawn packet). */
    private static boolean isSentTo(Entity entity, ServerPlayer player) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return false;
        }
        ChunkMap chunkMap = level.getChunkSource().chunkMap;
        Object tracked = ((ChunkMapAccessor) chunkMap).minehop$getEntityMap().get(entity.getId());
        if (tracked == null) {
            return false;
        }
        Set<ServerPlayerConnection> seenBy = ((TrackedEntityAccessor) tracked).minehop$getSeenBy();
        return seenBy.contains(player.connection);
    }

    /**
     * The viewer's HUD: the target's jump stats (server-derived, see RunStats) whenever they change and at least
     * every second; replay viewers also get the run time on the timer HUD (for a live player the runner's own client
     * reports it, see PacketHandler).
     */
    private static void sendHud(MinecraftServer server, Session session, ServerPlayer viewer) {
        long now = server.getTickCount();
        RunStats.Snapshot stats;
        if (session.kind == Kind.PLAYER) {
            ServerPlayer target = server.getPlayerList().getPlayer(session.targetPlayer);
            if (target == null) {
                return;
            }
            stats = RunStats.of(target);
        } else {
            ReplayGhosts.Ghost ghost = session.kind == Kind.WORLD_RECORD
                    ? ReplayGhosts.worldRecordGhost(session.mapName)
                    : ReplayGhosts.viewerGhost(session.viewer);
            if (ghost == null || ghost.entity() == null) {
                return;
            }
            if (now % HUD_INTERVAL_TICKS == 0) {
                PacketHandler.sendRunTimerHud(viewer, (float) ghost.elapsedSeconds(), (float) ghost.replay().time);
            }
            ReplayManager.ReplayEntry entry = ghost.currentEntry();
            stats = new RunStats.Snapshot((int) entry.jump_count, entry.last_jump_speed, entry.efficiency);
        }
        if (!stats.equals(session.lastStats) || now - session.lastStatsTick >= 20L) {
            PacketHandler.sendSpecEfficiency(viewer, stats.lastJumpSpeed(), stats.jumpCount(), stats.efficiency());
            session.lastStats = stats;
            session.lastStatsTick = now;
        }
    }

    /** Sends each spectated player who is watching them, whenever that changes (an empty list clears their HUD). */
    private static void updateSpectatorLists(MinecraftServer server) {
        if (SESSIONS.isEmpty() && SENT_LISTS.isEmpty()) {
            return;
        }
        Map<UUID, List<String>> current = new LinkedHashMap<>();
        for (Session session : SESSIONS.values()) {
            if (session.kind != Kind.PLAYER) {
                continue;
            }
            ServerPlayer viewer = server.getPlayerList().getPlayer(session.viewer);
            if (viewer != null) {
                current.computeIfAbsent(session.targetPlayer, uuid -> new ArrayList<>()).add(viewer.getScoreboardName());
            }
        }
        List<UUID> targets = new ArrayList<>(SENT_LISTS.keySet());
        for (UUID target : current.keySet()) {
            if (!targets.contains(target)) {
                targets.add(target);
            }
        }
        for (UUID target : targets) {
            List<String> now = current.getOrDefault(target, List.of());
            List<String> before = SENT_LISTS.getOrDefault(target, List.of());
            if (now.equals(before)) {
                continue;
            }
            ServerPlayer targetPlayer = server.getPlayerList().getPlayer(target);
            if (targetPlayer != null) {
                PacketHandler.sendSpectatorList(targetPlayer, now);
            }
            if (now.isEmpty() || targetPlayer == null) {
                SENT_LISTS.remove(target);
            } else {
                SENT_LISTS.put(target, List.copyOf(now));
            }
        }
    }
}
