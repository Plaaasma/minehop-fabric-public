package net.nerdorg.minehop.entity.custom;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.anticheat.stream.MovementValidator;
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
    private static final int PRESERVE_SPEED_SYNC_TICKS = 1;
    private static final Map<UUID, ObservedVelocitySample> LAST_OBSERVED_VELOCITY = new HashMap<>();
    private static final Map<UUID, ObservedPositionSample> LAST_OBSERVED_POSITION = new HashMap<>();

    private BlockPos corner1;
    private BlockPos corner2;
    private int check_index;
    private boolean preserveSpeed = true;

    public ResetEntity(EntityType<? extends Mob> entityType, Level world) {
        super(entityType, world);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);
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
    public void readAdditionalSaveData(CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);
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

    public static AttributeSupplier.Builder createResetEntityAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 1000000);
    }

    // Keep the preserved SPEED but point it along the spawn/checkpoint facing (what a bhop/surf
    // reset expects), instead of the stale world-space direction the player entered the zone with.
    // Vertical keeps only downward momentum (no upward launch).
    private static Vec3 redirectToYaw(Vec3 preserved, float yawDeg) {
        Vec3 safe = sanitizeVelocity(preserved);
        double horizontalSpeed = Math.sqrt(safe.x * safe.x + safe.z * safe.z);
        double yaw = Math.toRadians(yawDeg);
        double forwardX = -Math.sin(yaw);
        double forwardZ = Math.cos(yaw);
        return new Vec3(forwardX * horizontalSpeed, Math.min(safe.y, 0.0D), forwardZ * horizontalSpeed);
    }

    private static Vec3 sanitizeVelocity(Vec3 velocity) {
        if (velocity == null) {
            return Vec3.ZERO;
        }
        if (!Double.isFinite(velocity.x) || !Double.isFinite(velocity.y) || !Double.isFinite(velocity.z)) {
            return Vec3.ZERO;
        }
        return velocity;
    }

    private Vec3 resolvePreservedVelocity(ServerPlayer player) {
        if (player == null) {
            return Vec3.ZERO;
        }
        // Use the player's REALIZED movement this tick (how far they actually went), not
        // player.getVelocity() — on surf the solver stores a high pre-collision-clip velocity that
        // overshoots the true speed and made resets feel way too fast. Fall back to the recent
        // observed delta, then the reported velocity, only when the realized delta is missing
        // (e.g. stale around a teleport).
        Vec3 tickDeltaVelocity = sanitizeVelocity(new Vec3(
                player.getX() - player.xo,
                player.getY() - player.yo,
                player.getZ() - player.zo
        ));
        double tickDeltaHorizontalSq = (tickDeltaVelocity.x * tickDeltaVelocity.x) + (tickDeltaVelocity.z * tickDeltaVelocity.z);

        // The realized movement this tick is the truth when present. A client-driven player's position
        // only changes when its move packets are handled (which also resets xo), so for it take the
        // movement of its last client tick from the packet stream. The observed sample is a delta
        // between server ticks: it covers two client ticks whenever two move packets land in one server
        // tick, which doubled the preserved speed. It, then the current velocity, are the fallbacks.
        Vec3 currentVelocity = sanitizeVelocity(player.getDeltaMovement());
        Vec3 observedVelocity = this.resolveObservedVelocity(player, 2L);
        Vec3 clientStep = MovementValidator.lastClientStep(player);
        Vec3 preferred = tickDeltaHorizontalSq > 1.0E-6D
                ? tickDeltaVelocity
                : clientStep != null
                ? sanitizeVelocity(clientStep)
                : chooseStrongerHorizontal(observedVelocity, currentVelocity);
        // Keep horizontal momentum and only preserve downward vertical velocity to avoid upward launch spikes.
        return new Vec3(preferred.x, Math.min(preferred.y, 0.0D), preferred.z);
    }

    private static Vec3 chooseStrongerHorizontal(Vec3 first, Vec3 second) {
        Vec3 safeFirst = sanitizeVelocity(first);
        Vec3 safeSecond = sanitizeVelocity(second);
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

    private static double horizontalLengthSquared(Vec3 velocity) {
        if (velocity == null) {
            return 0.0D;
        }
        return (velocity.x * velocity.x) + (velocity.z * velocity.z);
    }

    private Vec3 resolveObservedVelocity(ServerPlayer player, long maxAgeTicks) {
        if (player == null) {
            return Vec3.ZERO;
        }
        ObservedVelocitySample sample = LAST_OBSERVED_VELOCITY.get(player.getUUID());
        if (sample == null) {
            return Vec3.ZERO;
        }
        String worldKey = player.serverLevel().dimension().location().toString();
        if (!worldKey.equals(sample.worldKey)) {
            LAST_OBSERVED_VELOCITY.remove(player.getUUID());
            return Vec3.ZERO;
        }
        long currentTick = player.serverLevel().getGameTime();
        if (currentTick - sample.worldTick > Math.max(0L, maxAgeTicks)) {
            LAST_OBSERVED_VELOCITY.remove(player.getUUID());
            return Vec3.ZERO;
        }
        return sanitizeVelocity(sample.velocity);
    }

    public static void trackPlayerMotion(ServerPlayer player) {
        if (player == null || player.isRemoved() || !player.isAlive()) {
            return;
        }
        ServerLevel world = player.serverLevel();
        String worldKey = world.dimension().location().toString();
        long worldTick = world.getGameTime();
        Vec3 currentPos = player.position();
        UUID uuid = player.getUUID();

        ObservedPositionSample previous = LAST_OBSERVED_POSITION.get(uuid);
        if (previous != null
                && worldTick > previous.worldTick
                && worldKey.equals(previous.worldKey)) {
            Vec3 delta = sanitizeVelocity(currentPos.subtract(previous.position));
            double lengthSq = delta.lengthSqr();
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

    public static void recordObservedVelocity(ServerPlayer player, Vec3 velocity) {
        if (player == null || velocity == null) {
            return;
        }
        Vec3 sanitized = sanitizeVelocity(velocity);
        double horizontalSq = (sanitized.x * sanitized.x) + (sanitized.z * sanitized.z);
        if (horizontalSq <= 1.0E-8D) {
            return;
        }
        String worldKey = player.serverLevel().dimension().location().toString();
        LAST_OBSERVED_VELOCITY.put(
                player.getUUID(),
                new ObservedVelocitySample(worldKey, sanitized, player.serverLevel().getGameTime())
        );
    }

    private void applyPreservedVelocity(ServerPlayer player, Vec3 velocity) {
        if (player == null) {
            return;
        }
        Vec3 sanitized = sanitizeVelocity(velocity);
        player.setDeltaMovement(sanitized.x, sanitized.y, sanitized.z);
        player.setOnGround(false);
        player.fallDistance = 0.0F;
        player.connection.send(new ClientboundSetEntityMotionPacket(player));
    }

    @Override
    public void tick() {
        this.updateInteractionBounds(this.corner1, this.corner2);
        Level world = this.level();
        if (world instanceof ServerLevel serverWorld) {
            if (serverWorld.getGameTime() % 2 == 0) {
                if (this.corner1 != null && this.corner2 != null) {
                    Vec3 center = this.getBoundsCenter(this.corner1, this.corner2);
                    this.teleportTo(center.x, center.y, center.z);
                }
                for (ServerPlayer worldPlayer : serverWorld.players()) {
                    PacketHandler.updateZone(worldPlayer, this.getId(), this.corner1, this.corner2, this.getPairedMap(), this.check_index);
                }
            }
            if (this.corner1 != null && this.corner2 != null) {
                DataManager.MapData pairedMap = DataManager.getMap(this.getPairedMap());
                if (pairedMap != null) {
                    AABB colliderBox = new AABB(new Vec3(this.corner1.getX(), this.corner1.getY(), this.corner1.getZ()), new Vec3(this.corner2.getX(), this.corner2.getY(), this.corner2.getZ()));
                    List<ServerPlayer> players = serverWorld.players();
                    for (ServerPlayer player : players) {
                        if (!player.isCreative() && !player.isSpectator()) {
                            if (colliderBox.contains(player.position())) {
                                Vec3 targetLocation = new Vec3(pairedMap.x, pairedMap.y, pairedMap.z);
                                Vec2 targetRot = new Vec2((float) pairedMap.xrot, (float) pairedMap.yrot);
                                if (pairedMap.checkpointPositions != null) {
                                    if (this.check_index > 0 && pairedMap.checkpointPositions.size() > this.check_index - 1) {
                                        targetLocation = pairedMap.checkpointPositions.get(this.check_index - 1).get(0);
                                        Vec3 rotVec3d = pairedMap.checkpointPositions.get(this.check_index - 1).get(1);
                                        targetRot = new Vec2((float) rotVec3d.x(), (float) rotVec3d.y());
                                    }
                                } else {
                                    Minehop.timerManager.remove(player.getScoreboardName());
                                }
                                if (!player.isCreative()) {
                                    player.getInventory().clearContent();
                                }

                                Zone startZone = null;
                                for (Entity entity : serverWorld.getAllEntities()) {
                                    if (entity instanceof StartEntity startEntity) {
                                        if (startEntity.getPairedMap().equals(this.getPairedMap())) {
                                            startZone = startEntity;
                                        }
                                    }
                                }

                                if (startZone != null){
                                    Minehop.playerMapLocation.put(player.getStringUUID(), startZone);
                                }
                                // Preserve speed is a PER-MAP setting (default off; meant mainly for
                                // surf). Preserve the magnitude of the player's speed but redirect it
                                // to the spawn/checkpoint facing. ABSOLUTE velocity (empty flag set) —
                                // the old DELTA_X/Y/Z flags made the passed velocity RELATIVE (added to
                                // current), which roughly doubled the speed on reset.
                                boolean preserve = this.preserveSpeed || pairedMap.preserve_speed;
                                Vec3 preservedVelocity = preserve
                                        ? redirectToYaw(this.resolvePreservedVelocity(player), targetRot.y)
                                        : Vec3.ZERO;
                                player.teleport(new TeleportTransition(
                                        serverWorld,
                                        new Vec3(targetLocation.x(), targetLocation.y(), targetLocation.z()),
                                        preservedVelocity,
                                        targetRot.y,
                                        targetRot.x,
                                        Set.of(),
                                        (playerEntity) -> {
                                            if (playerEntity instanceof ServerPlayer serverPlayerEntity) {
                                                // Authorized server teleport: grants the anticheat
                                                // teleport/reconciliation grace (like ZoneUtil targets).
                                                net.nerdorg.minehop.anticheat.AntiCheatManager.markAuthorizedTeleport(serverPlayerEntity);
                                                if (preserve) {
                                                    this.applyPreservedVelocity(serverPlayerEntity, preservedVelocity);
                                                }
                                            }
                                        }
                                ));
                                if (preserve) {
                                    PacketHandler.sendResetVelocityCarry(player, preservedVelocity, PRESERVE_SPEED_SYNC_TICKS);
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

    private static final class ObservedVelocitySample {
        private final String worldKey;
        private final Vec3 velocity;
        private final long worldTick;

        private ObservedVelocitySample(String worldKey, Vec3 velocity, long worldTick) {
            this.worldKey = worldKey == null ? "" : worldKey;
            this.velocity = velocity == null ? Vec3.ZERO : velocity;
            this.worldTick = Math.max(0L, worldTick);
        }
    }

    private static final class ObservedPositionSample {
        private final String worldKey;
        private final Vec3 position;
        private final long worldTick;

        private ObservedPositionSample(String worldKey, Vec3 position, long worldTick) {
            this.worldKey = worldKey == null ? "" : worldKey;
            this.position = position == null ? Vec3.ZERO : position;
            this.worldTick = Math.max(0L, worldTick);
        }
    }
}
