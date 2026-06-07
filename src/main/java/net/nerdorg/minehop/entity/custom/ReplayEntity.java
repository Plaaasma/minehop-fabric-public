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
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.world.World;
import net.nerdorg.minehop.commands.SpectateCommands;
import net.nerdorg.minehop.networking.PacketHandler;
import net.nerdorg.minehop.replays.ReplayManager;
import net.nerdorg.minehop.util.Logger;
import net.nerdorg.minehop.util.ZoneUtil;

import java.util.List;

public class ReplayEntity extends MobEntity {
    private String map_name = "";
    private String replay_player_name = "";
    private boolean hide_head = false;
    private boolean temporary = false;
    private int replayIndex = 0;

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putString("map", map_name);
        nbt.putString("replay_player", replay_player_name);
        nbt.putBoolean("hide_head", hide_head);
        nbt.putBoolean("temporary", temporary);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        map_name = nbt.getString("map");
        replay_player_name = nbt.getString("replay_player");
        hide_head = nbt.getBoolean("hide_head");
        temporary = nbt.getBoolean("temporary");
    }

    public static DefaultAttributeContainer.Builder createResetEntityAttributes() {
        return MobEntity.createMobAttributes()
                .add(EntityAttributes.MAX_HEALTH, 1000000);
    }

    public ReplayEntity(EntityType<? extends MobEntity> entityType, World world) {
        super(entityType, world);
    }

    public void setMapName(String map_name) {
        this.map_name = map_name == null ? "" : map_name;
        this.replay_player_name = "";
        this.hide_head = false;
        this.temporary = false;
        this.replayIndex = 0;
    }

    public void setReplay(String mapName, String replayPlayerName, boolean hideHead, boolean temporaryReplay) {
        this.map_name = mapName == null ? "" : mapName;
        this.replay_player_name = replayPlayerName == null ? "" : replayPlayerName;
        this.hide_head = hideHead;
        this.temporary = temporaryReplay;
        this.replayIndex = 0;
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
    public boolean damage(ServerWorld world, DamageSource source, float amount) {
        if (source.isOf(DamageTypes.GENERIC_KILL)) {
            return super.damage(world, source, amount);
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
        if (map_name == null || map_name.isBlank()) {
            return "replay";
        }
        if (replay_player_name == null || replay_player_name.isBlank()) {
            return map_name + "_replay";
        }
        return map_name + "_replay_" + sanitizeForScoreboard(replay_player_name);
    }

    @Override
    public Text getName() {
        String mapNameValue = map_name == null ? "" : map_name;
        String replayPlayerNameValue = replay_player_name == null ? "" : replay_player_name;
        if (replayPlayerNameValue.isBlank()) {
            return Text.literal(mapNameValue + "_replay");
        }
        return Text.literal(mapNameValue + "_replay_" + replayPlayerNameValue);
    }

    @Override
    public void tick() {
        if (this.getWorld() instanceof ServerWorld) {
            if (temporary && !SpectateCommands.spectatorList.containsKey(this.getNameForScoreboard())) {
                this.kill((ServerWorld) this.getWorld());
                super.tick();
                return;
            }

            ReplayManager.Replay replay = resolveReplay();
            if (replay != null && replay.replayEntries != null && !replay.replayEntries.isEmpty()) {
                this.setInvisible(false);
                if (this.replayIndex >= replay.replayEntries.size()) {
                    this.replayIndex = 0;
                }

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

                this.requestTeleport(x, y, z);
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
                                spectatorPlayer.teleportTo(ZoneUtil.makeTeleportTarget((ServerWorld) this.getWorld(), this.getPos(), this.getYaw(), this.getPitch()));
                                spectatorPlayer.setCameraEntity(this);
                                PacketHandler.sendSpecEfficiency(spectatorPlayer, last_jump_speed, (int) jump_count, efficiency);
                                Logger.logActionBar(spectatorPlayer, "End Time: " + String.format("%.5f", replay.time));
                            }
                        }
                    }
                }

                this.replayIndex += 1;
            } else {
                this.replayIndex = 0;
                if (replay_player_name == null || replay_player_name.isBlank()) {
                    this.setInvisible(true);
                }
            }

        }
        super.tick();
    }

    private ReplayManager.Replay resolveReplay() {
        String replayPlayerNameValue = replay_player_name == null ? "" : replay_player_name;
        if (!replayPlayerNameValue.isBlank()) {
            return ReplayManager.getReplay(this.map_name, replayPlayerNameValue);
        }
        return ReplayManager.getReplay(this.map_name);
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
