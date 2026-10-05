package net.nerdorg.minehop.entity.custom;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.nerdorg.minehop.commands.SpectateCommands;
import net.nerdorg.minehop.networking.PacketHandler;
import net.nerdorg.minehop.replays.ReplayManager;
import net.nerdorg.minehop.util.Logger;
import net.nerdorg.minehop.util.ZoneUtil;

import java.util.List;

public class ReplayEntity extends Mob {
    private String map_name = "";
    private String replay_player_name = "";
    private boolean hide_head = false;
    private boolean temporary = false;
    private int replayIndex = 0;

    @Override
    public void addAdditionalSaveData(CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);
        nbt.putString("map", map_name);
        nbt.putString("replay_player", replay_player_name);
        nbt.putBoolean("hide_head", hide_head);
        nbt.putBoolean("temporary", temporary);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);
        map_name = nbt.getString("map");
        replay_player_name = nbt.getString("replay_player");
        hide_head = nbt.getBoolean("hide_head");
        temporary = nbt.getBoolean("temporary");
    }

    public static AttributeSupplier.Builder createResetEntityAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 1000000);
    }

    public ReplayEntity(EntityType<? extends Mob> entityType, Level world) {
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
    public boolean requiresCustomPersistence() {
        return true;
    }

    @Override
    public boolean isNoGravity() {
        return true;
    }

    @Override
    public boolean isPersistenceRequired() {
        return true;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canCollideWith(Entity other) {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(DamageTypes.GENERIC_KILL)) {
            return super.hurt(source, amount);
        }
        else {
            return false;
        }
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    protected void doPush(Entity entity) {
    }

    @Override
    public boolean isFree(double offsetX, double offsetY, double offsetZ) {
        return true;
    }

    @Override
    public void playerTouch(Player player) { }

    @Override
    public void setXRot(float pitch) {
        if (pitch != 0f) {
            super.setXRot(pitch);
        }
    }

    @Override
    public String getScoreboardName() {
        if (map_name == null || map_name.isBlank()) {
            return "replay";
        }
        if (replay_player_name == null || replay_player_name.isBlank()) {
            return map_name + "_replay";
        }
        return map_name + "_replay_" + sanitizeForScoreboard(replay_player_name);
    }

    @Override
    public Component getName() {
        String mapNameValue = map_name == null ? "" : map_name;
        String replayPlayerNameValue = replay_player_name == null ? "" : replay_player_name;
        if (replayPlayerNameValue.isBlank()) {
            return Component.literal(mapNameValue + "_replay");
        }
        return Component.literal(mapNameValue + "_replay_" + replayPlayerNameValue);
    }

    @Override
    public void tick() {
        if (this.level() instanceof ServerLevel) {
            if (temporary && !SpectateCommands.spectatorList.containsKey(this.getScoreboardName())) {
                this.kill();
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

                this.teleportTo(x, y, z);
                this.setYRot((float) yrot);
                this.setYHeadRot((float) yrot);
                this.setXRot((float) xrot);

                if (SpectateCommands.spectatorList.containsKey(this.getScoreboardName())) {
                    List<String> spectators = SpectateCommands.spectatorList.get(this.getScoreboardName());
                    for (String spectatorName : spectators) {
                        if (!spectatorName.equals(this.getScoreboardName())) {
                            ServerPlayer spectatorPlayer = this.getServer().getPlayerList().getPlayerByName(spectatorName);
                            if (spectatorPlayer != null) {
                                if (!spectatorPlayer.isCreative()) {
                                    spectatorPlayer.getInventory().clearContent();
                                }
                                ZoneUtil.teleportTo(spectatorPlayer, ZoneUtil.makeTeleportTarget((ServerLevel) this.level(), this.position(), this.getYRot(), this.getXRot()));
                                spectatorPlayer.setCamera(this);
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
