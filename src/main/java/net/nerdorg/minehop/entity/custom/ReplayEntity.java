package net.nerdorg.minehop.entity.custom;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.EntitySetHeadYawS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.nerdorg.minehop.commands.SpectateCommands;
import net.nerdorg.minehop.data.DataManager;
import net.nerdorg.minehop.networking.PacketHandler;
import net.nerdorg.minehop.replays.ReplayManager;
import net.nerdorg.minehop.util.Logger;
import org.joml.Math;

import java.util.List;

public class ReplayEntity extends MobEntity {
    private String map_name;
    private int replayIndex = 0;
    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putString("map", map_name);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        map_name = nbt.getString("map");
    }

    public ReplayEntity(EntityType<? extends MobEntity> entityType, World world) {
        super(entityType, world);
    }

    public void setMapName(String map_name) {
        this.map_name = map_name;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean cannotDespawn() {
        return true;
    }

    @Override
    public boolean hasNoGravity() {
        return true;
    }

    @Override
    public boolean isPersistent() {
        return true;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean collidesWith(Entity other) {
        return false;
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (source.isOf(DamageTypes.GENERIC_KILL)) {
            return super.damage(source, amount);
        }
        else {
            return false;
        }
    }

    @Override
    public boolean isPushedByFluids() {
        return false;
    }

    @Override
    protected void pushAway(Entity entity) {
    }

    @Override
    public boolean doesNotCollide(double offsetX, double offsetY, double offsetZ) {
        return true;
    }

    @Override
    public void onPlayerCollision(PlayerEntity player) { }

    @Override
    public void setPitch(float pitch) {
        if (pitch != 0f) {
            super.setPitch(pitch);
        }
    }

    @Override
    public String getNameForScoreboard() {
        return map_name + "_replay";
    }

    @Override
    public Text getName() {
        return Text.literal(map_name + "_replay");
    }

    @Override
    public void tick() {
        if (this.getWorld() instanceof ServerWorld serverWorld) {
            ReplayManager.Replay replay = ReplayManager.getReplay(this.map_name);
            if (replay != null) {
                if (this.replayIndex < replay.replayEntries.size()) {
                    ReplayManager.ReplayEntry replayEntry = replay.replayEntries.get(this.replayIndex);
                    double x = replayEntry.x;
                    double y = replayEntry.y;
                    double z = replayEntry.z;
                    double xrot = replayEntry.xrot;
                    double yrot = replayEntry.yrot;
                    double jump_count = replayEntry.jump_count;
                    double last_jump_speed = replayEntry.last_jump_speed;
                    double efficiency = replayEntry.efficiency;

                    if (xrot == 0) {
                        xrot = 0.01;
                    }

                    this.teleport(x, y, z, false);
                    this.setYaw((float) yrot);
                    this.setHeadYaw((float) yrot);
                    this.setPitch((float) xrot);

                    if (SpectateCommands.spectatorList.containsKey(this.getNameForScoreboard())) {
                        List<String> spectators = SpectateCommands.spectatorList.get(this.getNameForScoreboard());
                        for (String spectatorName : spectators) {
                            if (!spectatorName.equals(this.getNameForScoreboard())) {
                                ServerPlayerEntity spectatorPlayer = this.getServer().getPlayerManager().getPlayer(spectatorName);
                                if (spectatorPlayer != null) {
                                    if (!spectatorPlayer.isCreative()) {
                                        spectatorPlayer.getInventory().clear();
                                    }
                                    spectatorPlayer.teleport(serverWorld, this.getX(), this.getY(), this.getZ(), this.headYaw, this.getPitch());
                                    spectatorPlayer.setCameraEntity(this);
                                    PacketHandler.sendSpecEfficiency(spectatorPlayer, last_jump_speed, (int) jump_count, efficiency);
                                    Logger.logActionBar(spectatorPlayer, "End Time: " + String.format("%.5f", replay.time));
                                }
                            }
                        }
                    }

                    this.replayIndex += 1;
                }
                else {
                    this.replayIndex = 0;
                }
            }
        }
        super.tick();
    }
}
