package net.nerdorg.minehop.util;

import net.minecraft.entity.Entity;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

import java.util.Set;

/**
 * 1.20.1 backport of the 1.21.2+ {@code net.minecraft.world.TeleportTarget} the mod teleports with
 * (target world, position, velocity, rotation, relative flags and a post-teleport callback), plus
 * {@link #teleport(Entity)}, the equivalent of 1.21.4's {@code Entity#teleportTo(TeleportTarget)}.
 */
public record TeleportTarget(ServerWorld world, Vec3d position, Vec3d velocity, float yaw, float pitch,
                             Set<PositionFlag> relatives, PostTeleport postTeleport) {
    @FunctionalInterface
    public interface PostTeleport {
        void onTransition(Entity entity);
    }

    public TeleportTarget(ServerWorld world, Vec3d position, Vec3d velocity, float yaw, float pitch, PostTeleport postTeleport) {
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
        Set<PositionFlag> flags = this.relatives == null ? Set.of() : this.relatives;
        Vec3d targetVelocity = this.velocity == null ? Vec3d.ZERO : this.velocity;
        if (entity instanceof ServerPlayerEntity player) {
            player.stopRiding();
            if (this.world == player.getServerWorld()) {
                player.networkHandler.requestTeleport(this.position.x, this.position.y, this.position.z, this.yaw, this.pitch, flags);
                player.setHeadYaw(this.yaw);
                player.setVelocity(targetVelocity);
                player.networkHandler.syncWithPlayerPosition();
            } else {
                player.teleport(this.world, this.position.x, this.position.y, this.position.z, this.yaw, this.pitch);
                player.setVelocity(targetVelocity);
            }
            if (this.postTeleport != null) {
                this.postTeleport.onTransition(player);
            }
            return player;
        }
        if (!(entity.getWorld() instanceof ServerWorld)) {
            return null;
        }
        entity.teleport(this.world, this.position.x, this.position.y, this.position.z, flags, this.yaw, this.pitch);
        Entity moved = entity;
        if (moved.isRemoved() && this.world != entity.getWorld()) {
            // Cross-dimension teleports of non-players recreate the entity in the target world.
            moved = this.world.getEntity(entity.getUuid());
        }
        if (moved != null) {
            moved.setVelocity(targetVelocity);
            if (this.postTeleport != null) {
                this.postTeleport.onTransition(moved);
            }
        }
        return moved;
    }
}
