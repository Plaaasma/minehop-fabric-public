package net.nerdorg.minehop.entity.custom;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.data.DataManager;
import net.nerdorg.minehop.networking.PacketHandler;

import java.util.HashMap;
import java.util.List;

public class StartEntity extends Zone {
    private BlockPos corner1;
    private BlockPos corner2;
    private String paired_map = "";

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
        nbt.putString("map", paired_map);
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

        paired_map = nbt.getString("map");
    }

    public void setPairedMap(String paired_map) {
        this.paired_map = paired_map;
    }

    public void setCorner1(BlockPos corner1) {
        this.corner1 = corner1;
    }

    public void setCorner2(BlockPos corner2) {
        this.corner2 = corner2;
    }

    public String getPairedMap() {
        return paired_map;
    }

    public BlockPos getCorner1() {
        return corner1;
    }

    public BlockPos getCorner2() {
        return corner2;
    }

    public static DefaultAttributeContainer.Builder createResetEntityAttributes() {
        return MobEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 1000000);
    }

    @Override
    public void tick() {
        World world = this.getWorld();
        if (world instanceof ServerWorld serverWorld) {
            if (serverWorld.getTime() % 100 == 0) {
                if (this.corner1 != null && this.corner2 != null) {
                    int avgX = (this.corner1.getX() + this.corner2.getX()) / 2;
                    int avgY = (this.corner1.getY() + this.corner2.getY()) / 2;
                    int avgZ = (this.corner1.getZ() + this.corner2.getZ()) / 2;

                    this.teleport(avgX, avgY, avgZ);
                }
                for (ServerPlayerEntity worldPlayer : serverWorld.getPlayers()) {
                    PacketHandler.updateZone(worldPlayer, this.getId(), this.corner1, this.corner2, this.paired_map, 0);
                }
            }
            if (this.corner1 != null && this.corner2 != null) {
                DataManager.MapData pairedMap = DataManager.getMap(this.paired_map);
                if (pairedMap != null) {
                    Box colliderBox = new Box(new Vec3d(this.corner1.getX(), this.corner1.getY(), this.corner1.getZ()), new Vec3d(this.corner2.getX(), this.corner2.getY(), this.corner2.getZ()));
                    List<ServerPlayerEntity> players = serverWorld.getPlayers();
                    for (ServerPlayerEntity player : players) {
                        if (!player.isCreative() && !player.isSpectator() && (Minehop.groundedList.contains(player.getNameForScoreboard()))) {
                            if (colliderBox.contains(player.getPos())) {
                                HashMap<String, Long> informationMap = new HashMap<>();
                                informationMap.put(this.paired_map, System.nanoTime());
                                Minehop.timerManager.put(player.getNameForScoreboard(), informationMap);
                            }
                        }
                    }
                }
                else {
                    this.kill();
                }
            }
        }
        super.tick();
    }
}
