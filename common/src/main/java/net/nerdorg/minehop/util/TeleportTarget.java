package net.nerdorg.minehop.util;

import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.phys.Vec3;

/**
 * 1.20.1 backport of the 1.21.2+ {@code net.minecraft.world.TeleportTarget} the mod teleports with
 * (target world, position, velocity, rotation, relative flags and a post-teleport callback), plus
 * {@link #teleport(Entity)}, the equivalent of 1.21.4's {@code Entity#teleportTo(TeleportTarget)}.
 */
public record TeleportTarget(ServerLevel world, Vec3 position, Vec3 velocity, float yaw, float pitch,
                             Set<RelativeMovement> relatives, PostTeleport postTeleport) {
    @FunctionalInterface
    public interface PostTeleport {
        void onTransition(Entity entity);
    }

    public TeleportTarget(ServerLevel world, Vec3 position, Vec3 velocity, float yaw, float pitch, PostTeleport postTeleport) {
        this(world, position, velocity, yaw, pitch, Set.of(), postTeleport);
    }

    /**
     * Same-world player teleport like 1.21.4: stop riding, move server-side + send the position packet
     * (the client zeroes its velocity on absolute axes), set the server velocity to the target's (1.21.4
     * {@code Entity#setPosition(PlayerPosition, Set)}), resync the move baseline, then run the callback.
     * 1.20.1 position packets can't carry velocity: callers that keep the player's speed send it
     * separately (velocity packet), as {@code ResetEntity} does. Other worlds use vanilla's
     * dimension change.
     */
    public Entity teleport(Entity entity) {
        if (entity == null || entity.isRemoved()) {
            return null;
        }
        Set<RelativeMovement> flags = this.relatives == null ? Set.of() : this.relatives;
        Vec3 targetVelocity = this.velocity == null ? Vec3.ZERO : this.velocity;
        if (entity instanceof ServerPlayer player) {
            player.stopRiding();
            if (this.world == player.serverLevel()) {
                player.connection.teleport(this.position.x, this.position.y, this.position.z, this.yaw, this.pitch, flags);
                player.setYHeadRot(this.yaw);
                player.setDeltaMovement(targetVelocity);
                player.connection.resetPosition();
            } else {
                player.teleportTo(this.world, this.position.x, this.position.y, this.position.z, this.yaw, this.pitch);
                player.setDeltaMovement(targetVelocity);
            }
            if (this.postTeleport != null) {
                this.postTeleport.onTransition(player);
            }
            return player;
        }
        if (!(entity.level() instanceof ServerLevel)) {
            return null;
        }
        entity.teleportTo(this.world, this.position.x, this.position.y, this.position.z, flags, this.yaw, this.pitch);
        Entity moved = entity;
        if (moved.isRemoved() && this.world != entity.level()) {
            // Cross-dimension teleports of non-players recreate the entity in the target world.
            moved = this.world.getEntity(entity.getUUID());
        }
        if (moved != null) {
            moved.setDeltaMovement(targetVelocity);
            if (this.postTeleport != null) {
                this.postTeleport.onTransition(moved);
            }
        }
        return moved;
    }
}
