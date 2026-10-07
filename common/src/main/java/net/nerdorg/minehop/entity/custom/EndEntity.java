package net.nerdorg.minehop.entity.custom;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.data.DataManager;
import net.nerdorg.minehop.networking.PacketHandler;
import net.nerdorg.minehop.replays.RunClock;
import net.nerdorg.minehop.util.Logger;

import java.util.HashMap;
import java.util.List;

public class EndEntity extends Zone {
    private BlockPos corner1;
    private BlockPos corner2;

    public EndEntity(EntityType<? extends Mob> entityType, Level world) {
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

    public static AttributeSupplier.Builder createResetEntityAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 1000000);
    }

    @Override
    public void tick() {
        this.updateInteractionBounds(this.corner1, this.corner2);
        Level world = this.level();
        if (world instanceof ServerLevel serverWorld) {
            if (this.corner1 != null && this.corner2 != null) {
                RunZones.register(this);
            }
            if (serverWorld.getGameTime() % 2 == 0) {
                if (this.corner1 != null && this.corner2 != null) {
                    Vec3 center = this.getBoundsCenter(this.corner1, this.corner2);
                    this.teleportTo(center.x, center.y, center.z);
                }
                for (ServerPlayer worldPlayer : serverWorld.players()) {
                    PacketHandler.updateZone(worldPlayer, this.getId(), this.corner1, this.corner2, this.getPairedMap(), 0);
                }
            }
            if (this.corner1 != null && this.corner2 != null) {
                DataManager.MapData pairedMap = DataManager.getMap(this.getPairedMap());
                if (pairedMap == null) {
                    this.kill();
                } else {
                    AABB endBox = this.getBoundsBox();
                    String mapName = this.getPairedMap();
                    for (ServerPlayer player : serverWorld.players()) {
                        if (player.isCreative() || player.isSpectator()) {
                            continue;
                        }
                        // A player with a packet stream is timed per client tick (RunClock, also for a pass
                        // through the zone between two server ticks); here only players without one.
                        if (RunClock.usesStream(player)) {
                            continue;
                        }
                        String playerName = player.getScoreboardName();
                        HashMap<String, Long> timerMap = Minehop.timerManager.get(playerName);
                        // Only stamp while a run for this map is actually active.
                        if (timerMap == null || !timerMap.containsKey(mapName)) {
                            continue;
                        }
                        if (!endBox.contains(player.position())) {
                            continue;
                        }
                        HashMap<String, Long> finishMap = Minehop.finishTimeManager.computeIfAbsent(playerName, k -> new HashMap<>());
                        // first crossing only — keep the earliest entry time for this run
                        if (finishMap.putIfAbsent(mapName, System.nanoTime()) == null) {
                            Minehop.runFinishClientTicks.put(playerName,
                                    net.nerdorg.minehop.anticheat.stream.MovementValidator.clientTicks(player));
                        }
                    }
                }
            }
        }
        super.tick();
    }

    /** The zone's box (what a finishing player must enter), or null while its corners aren't set. */
    public AABB runBounds() {
        return this.corner1 == null || this.corner2 == null ? null : this.getBoundsBox();
    }

    private AABB getBoundsBox() {
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
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }
}
