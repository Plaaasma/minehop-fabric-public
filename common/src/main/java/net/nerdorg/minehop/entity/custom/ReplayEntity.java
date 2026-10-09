package net.nerdorg.minehop.entity.custom;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.nerdorg.minehop.replays.ReplayGhosts;

import java.util.UUID;

/**
 * A replay ghost. It holds no playback state: {@link ReplayGhosts} spawns it, moves it to the current frame every
 * tick and removes it. It is never saved with its chunk, and one the registry doesn't own (e.g. loaded from a world
 * saved by an older version, which did save ghosts) removes itself.
 */
public class ReplayEntity extends Mob {
    /** Blocks between two position updates beyond which the client snaps instead of interpolating. */
    private static final double CLIENT_SNAP_DISTANCE = 10.0D;
    /**
     * Entity event the server sends right before moving a ghost across a teleport of its recording (or back to the
     * start when it loops): the client then jumps to the next position instead of interpolating to it. Not a vanilla
     * event id; clients without this handler ignore it (and interpolate, or snap beyond {@link #CLIENT_SNAP_DISTANCE}).
     */
    public static final byte SNAP_EVENT = 117;
    // Client: the next position update is a teleport (SNAP_EVENT).
    private boolean snapNextPosition = false;
    private String map_name = "";
    private String replay_player_name = "";
    private boolean hide_head = false;
    private boolean temporary = false;
    // Private ghost of one viewer (/replay watch): only that player is sent the entity. Null = world-record ghost.
    private UUID viewer = null;

    @Override
    public void addAdditionalSaveData(ValueOutput nbt) {
        super.addAdditionalSaveData(nbt);
        nbt.putString("map", map_name);
        nbt.putString("replay_player", replay_player_name);
        nbt.putBoolean("hide_head", hide_head);
        nbt.putBoolean("temporary", temporary);
    }

    @Override
    public void readAdditionalSaveData(ValueInput nbt) {
        super.readAdditionalSaveData(nbt);
        map_name = nbt.getStringOr("map", "");
        replay_player_name = nbt.getStringOr("replay_player", "");
        hide_head = nbt.getBooleanOr("hide_head", false);
        temporary = nbt.getBooleanOr("temporary", false);
    }

    public static AttributeSupplier.Builder createResetEntityAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 1000000);
    }

    public ReplayEntity(EntityType<? extends Mob> entityType, Level world) {
        super(entityType, world);
    }

    public void setMapName(String map_name) {
        this.map_name = map_name == null ? "" : map_name;
        this.replay_player_name = "";
        this.hide_head = false;
        this.temporary = false;
    }

    public void setReplay(String mapName, String replayPlayerName, boolean hideHead, boolean temporaryReplay) {
        this.map_name = mapName == null ? "" : mapName;
        this.replay_player_name = replayPlayerName == null ? "" : replayPlayerName;
        this.hide_head = hideHead;
        this.temporary = temporaryReplay;
    }

    public void setViewer(UUID viewer) {
        this.viewer = viewer;
    }

    public UUID getViewer() {
        return this.viewer;
    }

    public String getMapName() {
        return map_name;
    }

    public String getReplayPlayerName() {
        return replay_player_name;
    }

    public boolean shouldRenderHead() {
        return !hide_head;
    }

    @Override
    public boolean shouldBeSaved() {
        // Ghosts live only while the server runs; the registry recreates them. A saved ghost was the source of the
        // duplicates (it came back from disk next to the one the server spawned).
        return false;
    }

    @Override
    public boolean broadcastToPlayer(ServerPlayer player) {
        return this.viewer == null || this.viewer.equals(player.getUUID());
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean requiresCustomPersistence() {
        return true;
    }

    @Override
    public boolean isNoGravity() {
        return true;
    }

    @Override
    public boolean isPersistenceRequired() {
        return true;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canCollideWith(Entity other) {
        return false;
    }

    @Override
    public boolean hurtServer(ServerLevel world, DamageSource source, float amount) {
        if (source.is(DamageTypes.GENERIC_KILL)) {
            return super.hurtServer(world, source, amount);
        }
        else {
            return false;
        }
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    protected void doPush(Entity entity) {
    }

    @Override
    public boolean isFree(double offsetX, double offsetY, double offsetZ) {
        return true;
    }

    @Override
    public void playerTouch(Player player) { }

    @Override
    public String getScoreboardName() {
        if (map_name == null || map_name.isBlank()) {
            return "replay";
        }
        if (replay_player_name == null || replay_player_name.isBlank()) {
            return map_name + "_replay";
        }
        return map_name + "_replay_" + sanitizeForScoreboard(replay_player_name);
    }

    @Override
    public Component getName() {
        String mapNameValue = map_name == null ? "" : map_name;
        String replayPlayerNameValue = replay_player_name == null ? "" : replay_player_name;
        if (replayPlayerNameValue.isBlank()) {
            return Component.literal(mapNameValue + "_replay");
        }
        return Component.literal(mapNameValue + "_replay_" + replayPlayerNameValue);
    }

    /**
     * Client: position updates are normally interpolated over 3 ticks, which made the ghost slide across the map
     * when the run teleported (reset zone, checkpoint) or the replay looped. Jump instead when the server announced a
     * teleport ({@link #SNAP_EVENT}: recordings that mark their teleports), or when an update is this far from the
     * previous one, which can't be movement (the server sends one every tick; older recordings have no marks).
     * (26.1: updates go through Entity#moveOrInterpolateTo, which is final, into getInterpolation(); this entity's
     * handler snaps instead of interpolating.)
     */
    private final InterpolationHandler snappingInterpolation = new InterpolationHandler(this) {
        @Override
        public void interpolateTo(Vec3 position, float yRot, float xRot) {
            if (ReplayEntity.this.level().isClientSide()) {
                boolean snap = ReplayEntity.this.snapNextPosition;
                ReplayEntity.this.snapNextPosition = false;
                if (snap || this.position().distanceToSqr(position) > CLIENT_SNAP_DISTANCE * CLIENT_SNAP_DISTANCE) {
                    this.cancel();
                    ReplayEntity.this.snapToFrame(position, yRot, xRot);
                    return;
                }
            }
            super.interpolateTo(position, yRot, xRot);
        }
    };

    @Override
    public InterpolationHandler getInterpolation() {
        return this.snappingInterpolation;
    }

    private void snapToFrame(Vec3 position, float yRot, float xRot) {
        this.snapTo(position.x, position.y, position.z, yRot, xRot);
        this.setYHeadRot(yRot);
        this.yHeadRotO = yRot;
        this.setYBodyRot(yRot);
        this.yBodyRotO = yRot;
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == SNAP_EVENT) {
            this.snapNextPosition = true;
            return;
        }
        super.handleEntityEvent(id);
    }

    @Override
    public void tick() {
        if (this.level() instanceof ServerLevel && !ReplayGhosts.owns(this)) {
            // Not a ghost the registry spawned (a legacy saved ghost, a duplicate, /summon): remove it.
            ReplayGhosts.removeUnowned(this);
            return;
        }
        super.tick();
    }

    private static String sanitizeForScoreboard(String raw) {
        if (raw == null || raw.isBlank()) {
            return "player";
        }
        StringBuilder sanitized = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if ((c >= 'A' && c <= 'Z')
                    || (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9')
                    || c == '_') {
                sanitized.append(c);
            } else {
                sanitized.append('_');
            }
        }
        return sanitized.toString();
    }
}
