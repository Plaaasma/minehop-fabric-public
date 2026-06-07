package net.nerdorg.minehop.entity.custom;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.config.ConfigWrapper;
import net.nerdorg.minehop.config.MinehopConfig;
import net.nerdorg.minehop.data.DataManager;
import net.nerdorg.minehop.networking.PacketHandler;
import net.nerdorg.minehop.replays.ReplayEvents;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class StartEntity extends Zone {
    private static final double SOURCE_UNIT_TO_BLOCKS_PER_TICK = 1.0D / 800.0D;
    // ~walking speed in blocks/tick (MC walk ≈ 4.3 b/s ≈ 0.215 bpt). The run timer only (re)starts
    // while the player is below this in the start zone, so circle-bhopping (always faster) can't
    // keep resetting the timer for a free running start. See [[run-timer-validation]].
    public static final double WALK_SPEED_BPT = 0.25D;
    private BlockPos corner1;
    private BlockPos corner2;
    // Players (by name) that were inside this start zone last tick — used to detect a fresh airborne
    // RE-ENTRY (the timer-reset-while-keeping-speed exploit) vs jumping out from the ground.
    private final Set<String> insideLastTick = new HashSet<>();
    // Players who slowed below walk on the ground in this zone and haven't launched yet. While armed
    // the timer is held at 0 (so ground prestrafe doesn't count); it STARTS when they go airborne.
    // Circle-bhopping never goes below walk, so it never (re)arms -> no free reset.
    private final Set<String> startArmed = new HashSet<>();

    public StartEntity(EntityType<? extends MobEntity> entityType, World world) {
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
    }

    public static DefaultAttributeContainer.Builder createResetEntityAttributes() {
        return MobEntity.createMobAttributes()
                .add(EntityAttributes.MAX_HEALTH, 1000000);
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
    }

    public void setCorner1(BlockPos corner1) {
        this.corner1 = corner1;
    }

    public void setCorner2(BlockPos corner2) {
        this.corner2 = corner2;
    }

    public BlockPos getCorner1() {
        return corner1;
    }

    public BlockPos getCorner2() {
        return corner2;
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
                    PacketHandler.updateZone(worldPlayer, this.getId(), this.corner1, this.corner2, this.getPairedMap(), 0);
                }
            }
            if (this.corner1 != null && this.corner2 != null) {
                DataManager.MapData pairedMap = DataManager.getMap(this.getPairedMap());
                if (pairedMap != null) {
                    Box colliderBox = this.getBoundsBox();
                    List<ServerPlayerEntity> players = serverWorld.getPlayers();
                    for (ServerPlayerEntity player : players) {
                        String playerName = player.getNameForScoreboard();
                        boolean insideStartZone = colliderBox.contains(player.getPos());
                        boolean runner = !player.isCreative() && !player.isSpectator();
                        if (runner && insideStartZone) {
                            Minehop.playerMapLocation.put(player.getUuidAsString(), this);
                            boolean grounded = Minehop.groundedList.contains(playerName);
                            // Anti-exploit: entering the start zone while AIRBORNE (and not already
                            // inside last tick) is a re-entry to reset the timer without losing
                            // speed -> fully stop the player. Jumping out from the ground is fine
                            // because the player was inside (grounded) the previous tick.
                            if (!grounded && !this.insideLastTick.contains(playerName)) {
                                fullStopPlayer(player);
                            }
                            clampPlayerToStartZoneSpeed(player);
                            Vec3d vel = player.getVelocity();
                            double horizontalSpeed = Math.sqrt(vel.x * vel.x + vel.z * vel.z);
                            if (grounded) {
                                // Arm a fresh start by slowing below walk on the ground (so circling,
                                // which never goes below walk, can't re-arm and reset the timer).
                                if (horizontalSpeed < WALK_SPEED_BPT) {
                                    this.startArmed.add(playerName);
                                }
                                // While armed + grounded, hold the timer at 0 (ground prestrafe is
                                // free); it begins the moment they leave the ground (below).
                                if (this.startArmed.contains(playerName)) {
                                    Minehop.playerMapLocation.put(player.getUuidAsString(), this);
                                    HashMap<String, Long> informationMap = new HashMap<>();
                                    informationMap.put(this.getPairedMap(), System.nanoTime());
                                    if (ReplayEvents.replayEntryMap.containsKey(playerName)) {
                                        ReplayEvents.replayEntryMap.remove(playerName);
                                    }
                                    Minehop.timerManager.put(playerName, informationMap);
                                    Minehop.finishTimeManager.remove(playerName);
                                }
                            } else {
                                // Airborne in the start zone -> the run has begun; stop re-stamping
                                // (timer frozen at the last grounded tick = the launch) until they
                                // slow on the ground again.
                                this.startArmed.remove(playerName);
                            }
                        }
                        else {
                            if (player.isCreative() || player.isSpectator()) {
                                if (Minehop.timerManager.containsKey(playerName)) {
                                    Minehop.timerManager.remove(playerName);
                                }
                            }
                        }
                        if (runner && insideStartZone) {
                            this.insideLastTick.add(playerName);
                        } else {
                            this.insideLastTick.remove(playerName);
                            this.startArmed.remove(playerName);
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

    public static boolean isPlayerInsideAnyStartZone(ServerPlayerEntity player) {
        return getStartZoneForPlayer(player) != null;
    }

    public static StartEntity getStartZoneForPlayer(ServerPlayerEntity player) {
        if (player == null || !(player.getWorld() instanceof ServerWorld serverWorld)) {
            return null;
        }
        for (Entity entity : serverWorld.iterateEntities()) {
            if (!(entity instanceof StartEntity startEntity)) {
                continue;
            }
            if (startEntity.corner1 == null || startEntity.corner2 == null) {
                continue;
            }
            if (startEntity.getBoundsBox().contains(player.getPos())) {
                return startEntity;
            }
        }
        return null;
    }

    public static void fullStopPlayer(ServerPlayerEntity player) {
        if (player == null) {
            return;
        }
        player.setVelocity(0.0D, 0.0D, 0.0D);
        player.velocityDirty = true;
        player.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(player));
    }

    public static void clampPlayerToStartZoneSpeed(ServerPlayerEntity player) {
        if (player == null) {
            return;
        }
        Vec3d velocity = player.getVelocity();
        Vec3d clamped = clampVelocityToStartZoneSpeed(player, velocity);
        if (clamped == velocity || clamped.equals(velocity)) {
            return;
        }
        player.setVelocity(clamped.x, clamped.y, clamped.z);
        player.velocityDirty = true;
        player.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(player));
    }

    public static Vec3d clampVelocityToStartZoneSpeed(ServerPlayerEntity player, Vec3d velocity) {
        if (player == null || velocity == null) {
            return velocity;
        }
        double horizontalSpeed = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
        double maxHorizontalSpeed = getStartZoneSpeedLimit(player);
        if (!Double.isFinite(horizontalSpeed) || horizontalSpeed <= maxHorizontalSpeed || horizontalSpeed <= 1.0E-8D) {
            return velocity;
        }
        double scale = maxHorizontalSpeed / horizontalSpeed;
        return new Vec3d(velocity.x * scale, velocity.y, velocity.z * scale);
    }

    public static double getStartZoneSpeedLimit(ServerPlayerEntity player) {
        MinehopConfig config = ConfigWrapper.getEffectiveConfig(player);
        double maxAirSpeed = config == null ? 30.0D : config.movement.sv_maxairspeed;
        double speedCoefficient = config == null ? 1.0D : config.movement.speed_coefficient;
        if (!Double.isFinite(maxAirSpeed)) {
            maxAirSpeed = 30.0D;
        }
        if (!Double.isFinite(speedCoefficient)) {
            speedCoefficient = 1.0D;
        }
        return Math.max(maxAirSpeed * SOURCE_UNIT_TO_BLOCKS_PER_TICK * Math.max(speedCoefficient, 0.0D), 0.0D);
    }

    private Box getBoundsBox() {
        double minX = Math.min(this.corner1.getX(), this.corner2.getX());
        double minY = Math.min(this.corner1.getY(), this.corner2.getY());
        double minZ = Math.min(this.corner1.getZ(), this.corner2.getZ());
        double maxX = Math.max(this.corner1.getX(), this.corner2.getX());
        double maxY = Math.max(this.corner1.getY(), this.corner2.getY());
        double maxZ = Math.max(this.corner1.getZ(), this.corner2.getZ());
        if (maxX <= minX) {
            maxX = minX + 1.0D;
        }
        if (maxY <= minY) {
            maxY = minY + 1.0D;
        }
        if (maxZ <= minZ) {
            maxZ = minZ + 1.0D;
        }
        return new Box(minX, minY, minZ, maxX, maxY, maxZ);
    }
}
