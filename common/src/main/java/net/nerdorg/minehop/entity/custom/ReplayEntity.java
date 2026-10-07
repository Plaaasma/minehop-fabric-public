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
        // 1.21.5+: client position updates go through the entity's InterpolationHandler (moveOrInterpolateTo is
        // final), so the snap below lives in the handler.
        this.interpolation = new SnappingInterpolation(this);
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
     * when the run teleported (reset zone, checkpoint) or the replay looped. An update this far from the previous one
     * can't be movement (the server sends one every tick), so jump there instead.
     */
    private static final class SnappingInterpolation extends InterpolationHandler {
        private final ReplayEntity ghost;

        SnappingInterpolation(ReplayEntity ghost) {
            super(ghost);
            this.ghost = ghost;
        }

        @Override
        public void interpolateTo(Vec3 target, float yRot, float xRot) {
            if (this.ghost.level().isClientSide()) {
                // position() is the current interpolation target, or the entity's position when there is none
                // (1.21.4's lerpTargetX/Y/Z).
                Vec3 previous = this.position();
                double dx = target.x - previous.x;
                double dy = target.y - previous.y;
                double dz = target.z - previous.z;
                if (dx * dx + dy * dy + dz * dz > CLIENT_SNAP_DISTANCE * CLIENT_SNAP_DISTANCE) {
                    this.cancel();
                    this.ghost.snapTo(target.x, target.y, target.z, yRot, xRot);
                    this.ghost.setYHeadRot(yRot);
                    this.ghost.yHeadRotO = yRot;
                    this.ghost.setYBodyRot(yRot);
                    this.ghost.yBodyRotO = yRot;
                    return;
                }
            }
            super.interpolateTo(target, yRot, xRot);
        }
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
