package net.nerdorg.minehop.entity.custom;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.fabricmc.loader.impl.lib.sat4j.core.Vec;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.TargetPredicate;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageSources;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.TeleportTarget;
import net.minecraft.world.World;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.data.DataManager;
import net.nerdorg.minehop.networking.PacketHandler;
import net.nerdorg.minehop.util.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class ResetEntity extends Zone {
    private static final int PRESERVE_SPEED_CARRY_TICKS = 3;
    private static final Map<UUID, PendingVelocityCarry> PENDING_VELOCITY_CARRIES = new HashMap<>();
    private static final Map<UUID, ObservedVelocitySample> LAST_OBSERVED_VELOCITY = new HashMap<>();
    private static final Map<UUID, ObservedPositionSample> LAST_OBSERVED_POSITION = new HashMap<>();

    private BlockPos corner1;
    private BlockPos corner2;
    private int check_index;
    private boolean preserveSpeed = true;

    public ResetEntity(EntityType<? extends MobEntity> entityType, World world) {
        super(entityType, world);
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        if (corner1 != null) {
            nbt.putInt("Corner1X", corner1.getX());
            nbt.putInt("Corner1Y", corner1.getY());
            nbt.putInt("Corner1Z", corner1.getZ());
        }
        if (corner2 != null) {
            nbt.putInt("Corner2X", corner2.getX());
            nbt.putInt("Corner2Y", corner2.getY());
            nbt.putInt("Corner2Z", corner2.getZ());
        }
        nbt.putInt("check_index", check_index);
        nbt.putBoolean("preserve_speed", this.preserveSpeed);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        int x1 = nbt.getInt("Corner1X");
        int y1 = nbt.getInt("Corner1Y");
        int z1 = nbt.getInt("Corner1Z");
        corner1 = new BlockPos(x1, y1, z1);

        int x2 = nbt.getInt("Corner2X");
        int y2 = nbt.getInt("Corner2Y");
        int z2 = nbt.getInt("Corner2Z");
        corner2 = new BlockPos(x2, y2, z2);

        check_index = nbt.getInt("check_index");
        preserveSpeed = !nbt.contains("preserve_speed") || nbt.getBoolean("preserve_speed");
    }

    public void setCheckIndex(int check_index) {
        this.check_index = check_index;
    }

    public void setCorner1(BlockPos corner1) {
        this.corner1 = corner1;
    }

    public void setCorner2(BlockPos corner2) {
        this.corner2 = corner2;
    }

    public int getCheckIndex() {
        return check_index;
    }

    public boolean isPreserveSpeed() {
        return this.preserveSpeed;
    }

    public void setPreserveSpeed(boolean preserveSpeed) {
        this.preserveSpeed = preserveSpeed;
    }

    public BlockPos getCorner1() {
        return corner1;
    }

    public BlockPos getCorner2() {
        return corner2;
    }

    public static DefaultAttributeContainer.Builder createResetEntityAttributes() {
        return MobEntity.createMobAttributes()
                .add(EntityAttributes.MAX_HEALTH, 1000000);
    }

    // Keep the preserved SPEED but point it along the spawn/checkpoint facing (what a bhop/surf
    // reset expects), instead of the stale world-space direction the player entered the zone with.
    // Vertical keeps only downward momentum (no upward launch).
    private static Vec3d redirectToYaw(Vec3d preserved, float yawDeg) {
        Vec3d safe = sanitizeVelocity(preserved);
        double horizontalSpeed = Math.sqrt(safe.x * safe.x + safe.z * safe.z);
        double yaw = Math.toRadians(yawDeg);
        double forwardX = -Math.sin(yaw);
        double forwardZ = Math.cos(yaw);
        return new Vec3d(forwardX * horizontalSpeed, Math.min(safe.y, 0.0D), forwardZ * horizontalSpeed);
    }

    private static Vec3d sanitizeVelocity(Vec3d velocity) {
        if (velocity == null) {
            return Vec3d.ZERO;
        }
        if (!Double.isFinite(velocity.x) || !Double.isFinite(velocity.y) || !Double.isFinite(velocity.z)) {
            return Vec3d.ZERO;
        }
        return velocity;
    }

    private Vec3d resolvePreservedVelocity(ServerPlayerEntity player) {
        if (player == null) {
            return Vec3d.ZERO;
        }
        // Use the player's REALIZED movement this tick (how far they actually went), not
        // player.getVelocity() — on surf the solver stores a high pre-collision-clip velocity that
        // overshoots the true speed and made resets feel way too fast. Fall back to the recent
        // observed delta, then the reported velocity, only when the realized delta is missing
        // (e.g. stale around a teleport).
        Vec3d tickDeltaVelocity = sanitizeVelocity(new Vec3d(
                player.getX() - player.prevX,
                player.getY() - player.prevY,
                player.getZ() - player.prevZ
        ));
        double tickDeltaHorizontalSq = (tickDeltaVelocity.x * tickDeltaVelocity.x) + (tickDeltaVelocity.z * tickDeltaVelocity.z);

        // The realized movement this tick is the truth when present. If the reset zone ticks before
        // player movement, fall back to the very fresh observed movement sample, then current velocity.
        Vec3d currentVelocity = sanitizeVelocity(player.getVelocity());
        Vec3d observedVelocity = this.resolveObservedVelocity(player, 2L);
        Vec3d preferred = tickDeltaHorizontalSq > 1.0E-6D
                ? tickDeltaVelocity
                : chooseStrongerHorizontal(observedVelocity, currentVelocity);
        // Keep horizontal momentum and only preserve downward vertical velocity to avoid upward launch spikes.
        return new Vec3d(preferred.x, Math.min(preferred.y, 0.0D), preferred.z);
    }

    private static Vec3d chooseStrongerHorizontal(Vec3d first, Vec3d second) {
        Vec3d safeFirst = sanitizeVelocity(first);
        Vec3d safeSecond = sanitizeVelocity(second);
        double firstHorizontalSq = horizontalLengthSquared(safeFirst);
        double secondHorizontalSq = horizontalLengthSquared(safeSecond);
        if (firstHorizontalSq <= 1.0E-8D) {
            return safeSecond;
        }
        if (secondHorizontalSq <= 1.0E-8D) {
            return safeFirst;
        }
        return firstHorizontalSq >= secondHorizontalSq ? safeFirst : safeSecond;
    }

    private static double horizontalLengthSquared(Vec3d velocity) {
        if (velocity == null) {
            return 0.0D;
        }
        return (velocity.x * velocity.x) + (velocity.z * velocity.z);
    }

    private Vec3d resolveObservedVelocity(ServerPlayerEntity player, long maxAgeTicks) {
        if (player == null) {
            return Vec3d.ZERO;
        }
        ObservedVelocitySample sample = LAST_OBSERVED_VELOCITY.get(player.getUuid());
        if (sample == null) {
            return Vec3d.ZERO;
        }
        String worldKey = player.getServerWorld().getRegistryKey().getValue().toString();
        if (!worldKey.equals(sample.worldKey)) {
            LAST_OBSERVED_VELOCITY.remove(player.getUuid());
            return Vec3d.ZERO;
        }
        long currentTick = player.getServerWorld().getTime();
        if (currentTick - sample.worldTick > Math.max(0L, maxAgeTicks)) {
            LAST_OBSERVED_VELOCITY.remove(player.getUuid());
            return Vec3d.ZERO;
        }
        return sanitizeVelocity(sample.velocity);
    }

    public static void trackPlayerMotion(ServerPlayerEntity player) {
        if (player == null || player.isRemoved() || !player.isAlive()) {
            return;
        }
        ServerWorld world = player.getServerWorld();
        String worldKey = world.getRegistryKey().getValue().toString();
        long worldTick = world.getTime();
        Vec3d currentPos = player.getPos();
        UUID uuid = player.getUuid();

        ObservedPositionSample previous = LAST_OBSERVED_POSITION.get(uuid);
        if (previous != null
                && worldTick > previous.worldTick
                && worldKey.equals(previous.worldKey)) {
            Vec3d delta = sanitizeVelocity(currentPos.subtract(previous.position));
            double lengthSq = delta.lengthSquared();
            // Ignore teleports/chunk corrections; keep real movement-scale samples.
            if (lengthSq > 1.0E-8D && lengthSq <= 64.0D) {
                LAST_OBSERVED_VELOCITY.put(
                        uuid,
                        new ObservedVelocitySample(worldKey, delta, worldTick)
                );
            }
        }
        LAST_OBSERVED_POSITION.put(uuid, new ObservedPositionSample(worldKey, currentPos, worldTick));
    }

    public static void recordObservedVelocity(ServerPlayerEntity player, Vec3d velocity) {
        if (player == null || velocity == null) {
            return;
        }
        Vec3d sanitized = sanitizeVelocity(velocity);
        double horizontalSq = (sanitized.x * sanitized.x) + (sanitized.z * sanitized.z);
        if (horizontalSq <= 1.0E-8D) {
            return;
        }
        String worldKey = player.getServerWorld().getRegistryKey().getValue().toString();
        LAST_OBSERVED_VELOCITY.put(
                player.getUuid(),
                new ObservedVelocitySample(worldKey, sanitized, player.getServerWorld().getTime())
        );
    }

    private void applyPreservedVelocity(ServerPlayerEntity player, Vec3d velocity) {
        if (player == null) {
            return;
        }
        Vec3d sanitized = sanitizeVelocity(velocity);
        player.setVelocity(sanitized.x, sanitized.y, sanitized.z);
        player.setOnGround(false);
        player.fallDistance = 0.0F;
        player.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(player));
    }

    private static void scheduleVelocityCarry(ServerPlayerEntity player, Vec3d velocity) {
        if (player == null) {
            return;
        }
        Vec3d sanitized = sanitizeVelocity(velocity);
        if (sanitized.lengthSquared() <= 1.0E-8D) {
            return;
        }
        String worldKey = player.getServerWorld().getRegistryKey().getValue().toString();
        long createdWorldTick = player.getServerWorld().getTime();
        PENDING_VELOCITY_CARRIES.put(
                player.getUuid(),
                new PendingVelocityCarry(worldKey, sanitized, PRESERVE_SPEED_CARRY_TICKS, createdWorldTick)
        );
    }

    public static Vec3d applyScheduledVelocityCarry(ServerPlayerEntity player) {
        if (player == null || PENDING_VELOCITY_CARRIES.isEmpty()) {
            return null;
        }
        PendingVelocityCarry carry = PENDING_VELOCITY_CARRIES.get(player.getUuid());
        if (carry == null) {
            return null;
        }
        String worldKey = player.getServerWorld().getRegistryKey().getValue().toString();
        long worldTick = player.getServerWorld().getTime();
        if (!worldKey.equals(carry.worldKey)) {
            PENDING_VELOCITY_CARRIES.remove(player.getUuid());
            return null;
        }
        if (carry.ticksRemaining <= 0 || worldTick - carry.createdWorldTick > 40L) {
            PENDING_VELOCITY_CARRIES.remove(player.getUuid());
            return null;
        }

        Vec3d velocity = sanitizeVelocity(carry.velocity);
        if (velocity.lengthSquared() <= 1.0E-8D) {
            PENDING_VELOCITY_CARRIES.remove(player.getUuid());
            return null;
        }
        if (carry.lastAppliedWorldTick != worldTick) {
            player.setVelocity(velocity.x, velocity.y, velocity.z);
            player.setOnGround(false);
            player.fallDistance = 0.0F;
            player.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(player));
            carry.lastAppliedWorldTick = worldTick;
            carry.ticksRemaining--;
            if (carry.ticksRemaining <= 0) {
                PENDING_VELOCITY_CARRIES.remove(player.getUuid());
            }
        }
        return velocity;
    }

    @Override
    public void tick() {
        this.updateInteractionBounds(this.corner1, this.corner2);
        World world = this.getWorld();
        if (world instanceof ServerWorld serverWorld) {
            if (serverWorld.getTime() % 2 == 0) {
                if (this.corner1 != null && this.corner2 != null) {
                    Vec3d center = this.getBoundsCenter(this.corner1, this.corner2);
                    this.requestTeleport(center.x, center.y, center.z);
                }
                for (ServerPlayerEntity worldPlayer : serverWorld.getPlayers()) {
                    PacketHandler.updateZone(worldPlayer, this.getId(), this.corner1, this.corner2, this.getPairedMap(), this.check_index);
                }
            }
            if (this.corner1 != null && this.corner2 != null) {
                DataManager.MapData pairedMap = DataManager.getMap(this.getPairedMap());
                if (pairedMap != null) {
                    Box colliderBox = new Box(new Vec3d(this.corner1.getX(), this.corner1.getY(), this.corner1.getZ()), new Vec3d(this.corner2.getX(), this.corner2.getY(), this.corner2.getZ()));
                    List<ServerPlayerEntity> players = serverWorld.getPlayers();
                    for (ServerPlayerEntity player : players) {
                        if (!player.isCreative() && !player.isSpectator()) {
                            if (colliderBox.contains(player.getPos())) {
                                Vec3d targetLocation = new Vec3d(pairedMap.x, pairedMap.y, pairedMap.z);
                                Vec2f targetRot = new Vec2f((float) pairedMap.xrot, (float) pairedMap.yrot);
                                if (pairedMap.checkpointPositions != null) {
                                    if (this.check_index > 0 && pairedMap.checkpointPositions.size() > this.check_index - 1) {
                                        targetLocation = pairedMap.checkpointPositions.get(this.check_index - 1).get(0);
                                        Vec3d rotVec3d = pairedMap.checkpointPositions.get(this.check_index - 1).get(1);
                                        targetRot = new Vec2f((float) rotVec3d.getX(), (float) rotVec3d.getY());
                                    }
                                } else {
                                    Minehop.timerManager.remove(player.getNameForScoreboard());
                                }
                                if (!player.isCreative()) {
                                    player.getInventory().clear();
                                }

                                Zone startZone = null;
                                for (Entity entity : serverWorld.iterateEntities()) {
                                    if (entity instanceof StartEntity startEntity) {
                                        if (startEntity.getPairedMap().equals(this.getPairedMap())) {
                                            startZone = startEntity;
                                        }
                                    }
                                }

                                if (startZone != null){
                                    Minehop.playerMapLocation.put(player.getUuidAsString(), startZone);
                                }
                                // Preserve speed is a PER-MAP setting (default off; meant mainly for
                                // surf). Preserve the magnitude of the player's speed but redirect it
                                // to the spawn/checkpoint facing. ABSOLUTE velocity (empty flag set) —
                                // the old DELTA_X/Y/Z flags made the passed velocity RELATIVE (added to
                                // current), which roughly doubled the speed on reset.
                                boolean preserve = this.preserveSpeed || pairedMap.preserve_speed;
                                Vec3d preservedVelocity = preserve
                                        ? redirectToYaw(this.resolvePreservedVelocity(player), targetRot.y)
                                        : Vec3d.ZERO;
                                player.teleportTo(new TeleportTarget(
                                        serverWorld,
                                        new Vec3d(targetLocation.getX(), targetLocation.getY(), targetLocation.getZ()),
                                        preservedVelocity,
                                        targetRot.y,
                                        targetRot.x,
                                        Set.of(),
                                        (playerEntity) -> {
                                            if (preserve && playerEntity instanceof ServerPlayerEntity serverPlayerEntity) {
                                                this.applyPreservedVelocity(serverPlayerEntity, preservedVelocity);
                                            }
                                        }
                                ));
                                if (preserve) {
                                    // Explicitly restore and sync movement on the same tick as a fallback.
                                    // Some teleport paths still zero momentum after target application.
                                    this.applyPreservedVelocity(player, preservedVelocity);
                                    scheduleVelocityCarry(player, preservedVelocity);
                                    PacketHandler.sendResetVelocityCarry(player, preservedVelocity, PRESERVE_SPEED_CARRY_TICKS);
                                }
                            }
                        }
                    }
                }
                else {
                    this.kill(serverWorld);
                }
            }
        }
        super.tick();
    }

    private static final class PendingVelocityCarry {
        private final String worldKey;
        private final Vec3d velocity;
        private final long createdWorldTick;
        private int ticksRemaining;
        private long lastAppliedWorldTick;

        private PendingVelocityCarry(String worldKey, Vec3d velocity, int ticksRemaining, long createdWorldTick) {
            this.worldKey = worldKey == null ? "" : worldKey;
            this.velocity = velocity == null ? Vec3d.ZERO : velocity;
            this.ticksRemaining = Math.max(0, ticksRemaining);
            this.createdWorldTick = Math.max(0L, createdWorldTick);
            this.lastAppliedWorldTick = Long.MIN_VALUE;
        }
    }

    private static final class ObservedVelocitySample {
        private final String worldKey;
        private final Vec3d velocity;
        private final long worldTick;

        private ObservedVelocitySample(String worldKey, Vec3d velocity, long worldTick) {
            this.worldKey = worldKey == null ? "" : worldKey;
            this.velocity = velocity == null ? Vec3d.ZERO : velocity;
            this.worldTick = Math.max(0L, worldTick);
        }
    }

    private static final class ObservedPositionSample {
        private final String worldKey;
        private final Vec3d position;
        private final long worldTick;

        private ObservedPositionSample(String worldKey, Vec3d position, long worldTick) {
            this.worldKey = worldKey == null ? "" : worldKey;
            this.position = position == null ? Vec3d.ZERO : position;
            this.worldTick = Math.max(0L, worldTick);
        }
    }
}
