package net.nerdorg.minehop.entity.custom;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.data.DataManager;
import net.nerdorg.minehop.networking.PacketHandler;
import net.nerdorg.minehop.util.Logger;

import java.util.HashMap;
import java.util.List;

public class EndEntity extends Zone {
    private BlockPos corner1;
    private BlockPos corner2;

    public EndEntity(EntityType<? extends MobEntity> entityType, World world) {
        super(entityType, world);
    }

    @Override
    public void writeCustomData(WriteView nbt) {
        super.writeCustomData(nbt);
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
    public void readCustomData(ReadView nbt) {
        super.readCustomData(nbt);
        int x1 = nbt.getInt("Corner1X", 0);
        int y1 = nbt.getInt("Corner1Y", 0);
        int z1 = nbt.getInt("Corner1Z", 0);
        corner1 = new BlockPos(x1, y1, z1);

        int x2 = nbt.getInt("Corner2X", 0);
        int y2 = nbt.getInt("Corner2Y", 0);
        int z2 = nbt.getInt("Corner2Z", 0);
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

    public static DefaultAttributeContainer.Builder createResetEntityAttributes() {
        return MobEntity.createMobAttributes()
                .add(EntityAttributes.MAX_HEALTH, 1000000);
    }

    @Override
    public void tick() {
        this.updateInteractionBounds(this.corner1, this.corner2);
        World world = this.getEntityWorld();
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
                if (pairedMap == null) {
                    this.kill(serverWorld);
                } else {
                    Box endBox = this.getBoundsBox();
                    String mapName = this.getPairedMap();
                    for (ServerPlayerEntity player : serverWorld.getPlayers()) {
                        if (player.isCreative() || player.isSpectator()) {
                            continue;
                        }
                        String playerName = player.getNameForScoreboard();
                        HashMap<String, Long> timerMap = Minehop.timerManager.get(playerName);
                        // Only stamp while a run for this map is actually active.
                        if (timerMap == null || !timerMap.containsKey(mapName)) {
                            continue;
                        }
                        if (!endBox.contains(player.getEntityPos())) {
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
