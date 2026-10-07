package net.nerdorg.minehop.replays;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.data.DataManager;
import net.nerdorg.minehop.entity.ModEntities;
import net.nerdorg.minehop.entity.custom.ReplayEntity;
import net.nerdorg.minehop.platform.Services;
import net.nerdorg.minehop.spectate.SpectateSessions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Owns every replay ghost ({@link ReplayEntity}): one world-record ghost per map that has a WR replay, plus one
 * private ghost per player watching a personal best. Ghost entities are never saved with their chunk, and a
 * ReplayEntity this registry did not spawn (e.g. one an older version saved to disk) removes itself on its first
 * tick. Before, a ghost that walked into an unloading chunk was saved there, the lookups (which only see loaded
 * entities) missed it and every new record or restart spawned another: the test world had 63 ghosts for 43 maps.
 *
 * <p>Playback runs on wall time, one recorded frame per 50 ms, and is independent of the entity: a ghost is only
 * spawned where its current frame is in an entity-ticking chunk whose entities are loaded, and a WR ghost nobody
 * watches is removed when its route leaves that area. Its clock keeps running, so it comes back at the right point
 * of the run when players are near the route again. A new record restarts the ghost at frame 0; after the last
 * frame it holds for {@link #LOOP_HOLD_FRAMES} frames and loops.
 *
 * <p>Server thread only.
 */
public final class ReplayGhosts {
    /** Recorded frames are one server tick (at 20 TPS) apart. */
    public static final long FRAME_NANOS = 50_000_000L;
    /** The last frame is held this many frames (2 s) before the loop restarts at frame 0. */
    public static final int LOOP_HOLD_FRAMES = 40;
    /** How often the WR ghosts are re-checked against the records (they are also refreshed on every change). */
    private static final int REVALIDATE_TICKS = 100;

    private static final Map<String, Ghost> WORLD_RECORDS = new LinkedHashMap<>();
    private static final Map<UUID, Ghost> VIEWER_GHOSTS = new LinkedHashMap<>();
    private static final Map<UUID, Ghost> BY_ENTITY = new HashMap<>();
    private static final List<ReplayEntity> UNOWNED_LOADED = new ArrayList<>();
    private static int legacyGhostsRemoved;

    private ReplayGhosts() {
    }

    /** One ghost: a replay, its playback clock and (while spawned) its entity. */
    public static final class Ghost {
        private final String mapName;
        private final UUID viewer;
        private ReplayManager.Replay replay;
        private long startNanos;
        private long pausedSince = -1L;
        private ReplayEntity entity;
        private int frame;

        private Ghost(String mapName, UUID viewer, ReplayManager.Replay replay, long now) {
            this.mapName = mapName;
            this.viewer = viewer;
            this.replay = replay;
            this.startNanos = now;
        }

        public String mapName() {
            return this.mapName;
        }

        /** The watching player of a private PB ghost; null for a map's world-record ghost. */
        public UUID viewer() {
            return this.viewer;
        }

        public boolean isWorldRecord() {
            return this.viewer == null;
        }

        public ReplayManager.Replay replay() {
            return this.replay;
        }

        /** The ghost's entity, or null while it isn't spawned (its frame is outside the ticking area). */
        public ReplayEntity entity() {
            return this.entity;
        }

        /** Index of the frame currently shown. */
        public int frame() {
            return this.frame;
        }

        public ReplayManager.ReplayEntry currentEntry() {
            List<ReplayManager.ReplayEntry> frames = this.replay.replayEntries;
            return frames.get(Math.min(this.frame, frames.size() - 1));
        }

        /** Seconds into the run of the frame currently shown. */
        public double elapsedSeconds() {
            return this.frame * (FRAME_NANOS / 1_000_000_000.0D);
        }

        private int frameAt(long now) {
            List<ReplayManager.ReplayEntry> frames = this.replay.replayEntries;
            long elapsed = Math.max(0L, (this.pausedSince >= 0L ? this.pausedSince : now) - this.startNanos);
            long cycle = frames.size() + (long) LOOP_HOLD_FRAMES;
            return (int) Math.min((elapsed / FRAME_NANOS) % cycle, frames.size() - 1L);
        }

        private void restart(long now) {
            this.startNanos = now;
            if (this.pausedSince >= 0L) {
                this.pausedSince = now;
            }
            this.frame = 0;
        }

        private void pause(long now) {
            if (this.pausedSince < 0L) {
                this.pausedSince = now;
            }
        }

        private void resume(long now) {
            if (this.pausedSince >= 0L) {
                this.startNanos += now - this.pausedSince;
                this.pausedSince = -1L;
            }
        }
    }

    public static void register() {
        Services.EVENTS.onServerStarted(ReplayGhosts::refreshAllWorldRecords);
        Services.EVENTS.onServerTickEnd(ReplayGhosts::tick);
        // A ghost an older version saved with its chunk: remove it as soon as it is loaded, even where it would never
        // tick (it was still sent to nearby clients there, frozen in place). Queued: the load callback is no place to
        // remove an entity.
        Services.EVENTS.onEntityLoad((entity, level) -> {
            if (entity instanceof ReplayEntity replayEntity && !owns(replayEntity)) {
                UNOWNED_LOADED.add(replayEntity);
            }
        });
        Services.EVENTS.onServerStopped(server -> {
            WORLD_RECORDS.clear();
            VIEWER_GHOSTS.clear();
            BY_ENTITY.clear();
            UNOWNED_LOADED.clear();
        });
    }

    /** True if this entity is a ghost the registry spawned and still drives; any other ReplayEntity removes itself. */
    public static boolean owns(ReplayEntity entity) {
        Ghost ghost = entity == null ? null : BY_ENTITY.get(entity.getUUID());
        return ghost != null && ghost.entity == entity;
    }

    public static Ghost worldRecordGhost(String mapName) {
        return mapName == null ? null : WORLD_RECORDS.get(mapName);
    }

    public static Ghost viewerGhost(UUID viewer) {
        return viewer == null ? null : VIEWER_GHOSTS.get(viewer);
    }

    /** Maps that currently have a world-record ghost (spawned or not). */
    public static List<String> worldRecordMaps() {
        return new ArrayList<>(WORLD_RECORDS.keySet());
    }

    /**
     * Points the map's world-record ghost at the current WR replay: creates it, restarts it at frame 0 when the
     * record changed, or removes it (and returns false) when the map has no WR replay or its ghost is switched off.
     */
    public static boolean refreshWorldRecord(MinecraftServer server, String mapName) {
        if (mapName == null || mapName.isBlank()) {
            return false;
        }
        DataManager.MapData mapData = DataManager.getMap(mapName);
        ReplayManager.Replay replay = mapData == null || mapData.replay_ghost_disabled ? null : ReplayManager.getReplay(mapName);
        return applyWorldRecord(mapName, replay, System.nanoTime());
    }

    /** {@link #refreshWorldRecord} for every map (one pass over the replays), dropping ghosts of deleted maps. */
    public static void refreshAllWorldRecords(MinecraftServer server) {
        Map<String, ReplayManager.Replay> replays = ReplayManager.worldRecordReplays();
        long now = System.nanoTime();
        List<String> maps = new ArrayList<>(WORLD_RECORDS.keySet());
        if (Minehop.mapList != null) {
            for (DataManager.MapData mapData : Minehop.mapList) {
                if (mapData != null && mapData.name != null && !maps.contains(mapData.name)) {
                    maps.add(mapData.name);
                }
            }
        }
        for (String mapName : maps) {
            DataManager.MapData mapData = DataManager.getMap(mapName);
            applyWorldRecord(mapName, mapData == null || mapData.replay_ghost_disabled ? null : replays.get(mapName), now);
        }
    }

    private static boolean applyWorldRecord(String mapName, ReplayManager.Replay replay, long now) {
        Ghost ghost = WORLD_RECORDS.get(mapName);
        if (replay == null) {
            if (ghost != null) {
                WORLD_RECORDS.remove(mapName);
                despawn(ghost);
            }
            return false;
        }
        if (ghost == null) {
            WORLD_RECORDS.put(mapName, new Ghost(mapName, null, replay, now));
        } else if (ghost.replay != replay) {
            // A new record (or the old one invalidated): show the new run from its start.
            ghost.replay = replay;
            ghost.restart(now);
        }
        return true;
    }

    /** Removes the map's world-record ghost. */
    public static void removeWorldRecord(String mapName) {
        Ghost ghost = mapName == null ? null : WORLD_RECORDS.remove(mapName);
        if (ghost != null) {
            despawn(ghost);
        }
    }

    /**
     * Gives the viewer a private ghost of the replay (replacing an earlier one). It starts paused at frame 0 and
     * begins playing once it could be spawned, i.e. once the viewer's client has the area loaded.
     */
    public static Ghost startViewerGhost(UUID viewer, String mapName, ReplayManager.Replay replay) {
        stopViewerGhost(viewer);
        long now = System.nanoTime();
        Ghost ghost = new Ghost(mapName, viewer, replay, now);
        ghost.pause(now);
        VIEWER_GHOSTS.put(viewer, ghost);
        return ghost;
    }

    public static void stopViewerGhost(UUID viewer) {
        Ghost ghost = viewer == null ? null : VIEWER_GHOSTS.remove(viewer);
        if (ghost != null) {
            despawn(ghost);
        }
    }

    /** Removes every ghost (WR and private) of a map, e.g. when the map or plot is deleted. */
    public static void forgetMap(String mapName) {
        removeWorldRecord(mapName);
        List<UUID> viewers = new ArrayList<>();
        for (Map.Entry<UUID, Ghost> entry : VIEWER_GHOSTS.entrySet()) {
            if (entry.getValue().mapName.equals(mapName)) {
                viewers.add(entry.getKey());
            }
        }
        viewers.forEach(ReplayGhosts::stopViewerGhost);
    }

    /** Where the ghost is (or would be, if it isn't spawned): its current frame's position. */
    public static Vec3 position(Ghost ghost) {
        ReplayManager.ReplayEntry entry = ghost.currentEntry();
        return new Vec3(entry.x, entry.y, entry.z);
    }

    /** The level the ghost's map is in. */
    public static ServerLevel level(MinecraftServer server, Ghost ghost) {
        DataManager.MapData mapData = DataManager.getMap(ghost.mapName);
        if (server == null || mapData == null) {
            return null;
        }
        for (ServerLevel level : server.getAllLevels()) {
            if (level.dimension().toString().equals(mapData.worldKey)) {
                return level;
            }
        }
        return server.overworld();
    }

    /** Number of ghosts loaded from disk (saved by an older version) and removed since the server started. */
    public static int legacyGhostsRemoved() {
        return legacyGhostsRemoved;
    }

    /** Removes a ReplayEntity the registry doesn't own (called from its tick). */
    public static void removeUnowned(ReplayEntity entity) {
        if (!entity.isRemoved()) {
            entity.discard();
            legacyGhostsRemoved++;
        }
    }

    private static void tick(MinecraftServer server) {
        if (!UNOWNED_LOADED.isEmpty()) {
            for (ReplayEntity entity : UNOWNED_LOADED) {
                if (!entity.isRemoved() && !owns(entity)) {
                    entity.discard();
                    legacyGhostsRemoved++;
                }
            }
            UNOWNED_LOADED.clear();
        }
        if (server.getTickCount() % REVALIDATE_TICKS == 0) {
            refreshAllWorldRecords(server);
            List<UUID> stale = new ArrayList<>();
            for (Map.Entry<UUID, Ghost> entry : VIEWER_GHOSTS.entrySet()) {
                // Invalidated or deleted meanwhile (its viewer's session then ends), or nobody watching it any more.
                if (!ReplayManager.isPlayable(entry.getValue().replay) || !Minehop.replayList.contains(entry.getValue().replay)
                        || !SpectateSessions.isWatched(entry.getValue())) {
                    stale.add(entry.getKey());
                }
            }
            stale.forEach(ReplayGhosts::stopViewerGhost);
        }
        long now = System.nanoTime();
        for (Ghost ghost : new ArrayList<>(WORLD_RECORDS.values())) {
            drive(server, ghost, now);
        }
        for (Ghost ghost : new ArrayList<>(VIEWER_GHOSTS.values())) {
            drive(server, ghost, now);
        }
    }

    private static void drive(MinecraftServer server, Ghost ghost, long now) {
        ServerLevel level = level(server, ghost);
        if (ghost.entity != null && (ghost.entity.isRemoved() || !ghost.entity.isAlive() || ghost.entity.level() != level)) {
            despawn(ghost);
        }
        if (level == null) {
            return;
        }
        ghost.frame = ghost.frameAt(now);
        ReplayManager.ReplayEntry entry = ghost.currentEntry();
        BlockPos pos = BlockPos.containing(entry.x, entry.y, entry.z);
        boolean ready = level.isPositionEntityTicking(pos) && level.areEntitiesLoaded(ChunkPos.asLong(pos));
        if (ghost.entity == null) {
            if (!ready) {
                if (!ghost.isWorldRecord()) {
                    // A private ghost waits (its clock stopped) until the area around its viewer is loaded.
                    ghost.pause(now);
                }
                return;
            }
            ghost.resume(now);
            ghost.frame = ghost.frameAt(now);
            entry = ghost.currentEntry();
            spawn(level, ghost, entry);
            return;
        }
        if (!ready && ghost.isWorldRecord() && !SpectateSessions.isWatched(ghost)) {
            // Nobody near the route ahead: don't let it walk into chunks that unload. It returns when they tick again.
            // A watched ghost keeps moving: its spectators load the chunks ahead of it.
            despawn(ghost);
            return;
        }
        apply(ghost.entity, entry);
    }

    private static void spawn(ServerLevel level, Ghost ghost, ReplayManager.ReplayEntry entry) {
        ReplayEntity entity = ModEntities.REPLAY_ENTITY.get().create(level, EntitySpawnReason.COMMAND);
        if (entity == null) {
            return;
        }
        boolean worldRecord = ghost.isWorldRecord();
        entity.setReplay(ghost.mapName, worldRecord ? "" : ghost.replay.player_name, !worldRecord, !worldRecord);
        entity.setViewer(ghost.viewer);
        entity.setNoAi(true);
        apply(entity, entry);
        ghost.entity = entity;
        BY_ENTITY.put(entity.getUUID(), ghost);
        if (!level.addFreshEntity(entity)) {
            BY_ENTITY.remove(entity.getUUID());
            ghost.entity = null;
        }
    }

    private static void apply(ReplayEntity entity, ReplayManager.ReplayEntry entry) {
        float yaw = (float) entry.yrot;
        float pitch = (float) entry.xrot;
        entity.snapTo(entry.x, entry.y, entry.z, yaw, pitch);
        entity.setYHeadRot(yaw);
        entity.setYBodyRot(yaw);
    }

    private static void despawn(Ghost ghost) {
        ReplayEntity entity = ghost.entity;
        ghost.entity = null;
        if (entity != null) {
            BY_ENTITY.remove(entity.getUUID());
            if (!entity.isRemoved()) {
                entity.discard();
            }
        }
    }
}
