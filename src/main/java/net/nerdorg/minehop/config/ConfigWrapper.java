package net.nerdorg.minehop.config;

import me.shedaniel.autoconfig.AutoConfig;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.commands.SpectateCommands;
import net.nerdorg.minehop.data.DataManager;
import net.nerdorg.minehop.entity.custom.ResetEntity;
import net.nerdorg.minehop.item.ModItems;
import net.nerdorg.minehop.networking.PacketHandler;
import net.nerdorg.minehop.util.ZoneUtil;

import java.util.*;

public class ConfigWrapper {
    public static MinehopConfig config;

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register((server) -> {
            HashMap<String, List<String>> newSpectatorList = new HashMap<>();
            for (ServerPlayerEntity playerEntity : server.getPlayerManager().getPlayerList()) {
                ResetEntity.trackPlayerMotion(playerEntity);
                DataManager.MapData currentMap = resolveEffectiveMap(playerEntity);

                if (playerEntity.isSpectator() || playerEntity.isCreative()) {
                    if (Minehop.timerManager.containsKey(playerEntity.getNameForScoreboard())) {
                        Minehop.timerManager.remove(playerEntity.getNameForScoreboard());
                    }
                }

                if (newSpectatorList.containsKey(playerEntity.getCameraEntity().getNameForScoreboard())) {
                    List<String> newList = newSpectatorList.get(playerEntity.getCameraEntity().getNameForScoreboard());
                    newList.add(playerEntity.getNameForScoreboard());
                    newSpectatorList.put(playerEntity.getCameraEntity().getNameForScoreboard(), newList);
                }
                else {
                    newSpectatorList.put(playerEntity.getCameraEntity().getNameForScoreboard(), new ArrayList<>(Arrays.asList(playerEntity.getNameForScoreboard())));
                }

                if (currentMap != null) {
                    if (currentMap.hns) {
                        Minehop.speedCapMap.put(playerEntity.getNameForScoreboard(), 0.6);
                    } else {
                        playerEntity.setGlowing(false);
                        Minehop.speedCapMap.remove(playerEntity.getNameForScoreboard());
                    }
                }
                PacketHandler.sendConfigToClient(playerEntity, ConfigWrapper.config);
            }
            SpectateCommands.spectatorList = newSpectatorList;
            if (server.getTicks() % 100 == 0) {
                refreshPlayerCounts(server);
                for (ServerPlayerEntity playerEntity : server.getPlayerManager().getPlayerList()) {
                    DataManager.MapData mapData = ZoneUtil.getCurrentMapForPlayerCount(playerEntity);
                    if (mapData != null && mapData.arena && !playerEntity.isSpectator()) {
                        for (int slotNum = 1; slotNum < playerEntity.getInventory().size(); slotNum++) {
                            playerEntity.getInventory().setStack(slotNum, new ItemStack(Items.AIR));
                        }
                        playerEntity.getInventory().setStack(0, new ItemStack(ModItems.INSTAGIB_GUN));
                    }
                    PacketHandler.sendSpectators(playerEntity);
                    PacketHandler.sendRecords(playerEntity);
                    PacketHandler.sendMaps(playerEntity);
                    PacketHandler.sendPersonalRecords(playerEntity);
                }
            }
        });
    }

    public static void loadConfig() {
        config = AutoConfig.getConfigHolder(MinehopConfig.class).getConfig();
        if (migrateLegacyMovementConfig(config)) {
            saveConfig(config);
        }
    }

    public static void saveConfig(MinehopConfig minehopConfig) {
        AutoConfig.getConfigHolder(MinehopConfig.class).setConfig(minehopConfig);
        AutoConfig.getConfigHolder(MinehopConfig.class).save();
    }

    public static MinehopConfig getEffectiveConfig(Entity entity) {
        MinehopConfig effective = copyConfig(config);
        DataManager.MapData mapData = resolveEffectiveMap(entity);
        if (mapData == null || !mapData.movement_override) {
            return effective;
        }

        DataManager.sanitizeMapMovementFields(mapData);
        effective.movement.sv_friction = mapData.movement_sv_friction;
        effective.movement.sv_accelerate = mapData.movement_sv_accelerate;
        effective.movement.sv_airaccelerate = mapData.movement_sv_airaccelerate;
        effective.movement.sv_maxairspeed = mapData.movement_sv_maxairspeed;
        effective.movement.sv_jump_impulse = mapData.movement_sv_jump_impulse;
        effective.movement.speed_mul = mapData.movement_speed_mul;
        effective.movement.sv_gravity = mapData.movement_sv_gravity;
        effective.movement.sv_stopspeed = mapData.movement_sv_stopspeed;
        effective.movement.speed_coefficient = mapData.movement_speed_coefficient;
        effective.movement.auto_step_up = mapData.movement_auto_step_up;
        effective.movement.css_crouch_jump = mapData.movement_css_crouch_jump;
        effective.movement.disable_sprint = mapData.movement_disable_sprint;
        effective.fall_damage = mapData.movement_fall_damage;
        return effective;
    }

    public static DataManager.MapData resolveEffectiveMap(Entity entity) {
        if (entity == null) {
            return null;
        }
        if (entity instanceof ServerPlayerEntity serverPlayer) {
            DataManager.MapData byLocation = ZoneUtil.getCurrentMapForPlayerCount(serverPlayer);
            if (byLocation != null) {
                return byLocation;
            }
        }
        return ZoneUtil.getCurrentMap(entity);
    }

    private static MinehopConfig copyConfig(MinehopConfig source) {
        MinehopConfig fallback = new MinehopConfig();
        MinehopConfig input = source == null ? fallback : source;
        MinehopConfig copy = new MinehopConfig();
        copy.enabled = input.enabled;
        copy.fall_damage = input.fall_damage;
        copy.hideSelf = input.hideSelf;
        copy.hideOthers = input.hideOthers;
        copy.hideReplay = input.hideReplay;
        copy.nulls = input.nulls;
        copy.censor_user_map_descriptions = input.censor_user_map_descriptions;
        copy.help_command = input.help_command;
        copy.minehop_motd = input.minehop_motd;
        copy.client_validation = input.client_validation;
        copy.bot_token = input.bot_token;
        copy.record_channel = input.record_channel;

        copy.movement.sv_friction = input.movement.sv_friction;
        copy.movement.sv_accelerate = input.movement.sv_accelerate;
        copy.movement.sv_airaccelerate = input.movement.sv_airaccelerate;
        copy.movement.sv_maxairspeed = input.movement.sv_maxairspeed;
        copy.movement.sv_jump_impulse = input.movement.sv_jump_impulse;
        copy.movement.speed_mul = input.movement.speed_mul;
        copy.movement.sv_gravity = input.movement.sv_gravity;
        copy.movement.sv_stopspeed = input.movement.sv_stopspeed;
        copy.movement.speed_coefficient = input.movement.speed_coefficient;
        copy.movement.auto_step_up = input.movement.auto_step_up;
        copy.movement.css_crouch_jump = input.movement.css_crouch_jump;
        copy.movement.disable_sprint = input.movement.disable_sprint;
        copy.movement.source_units_migrated = input.movement.source_units_migrated;
        copy.movement.source_movement_version = input.movement.source_movement_version;
        return copy;
    }

    public static void refreshPlayerCounts(MinecraftServer server) {
        if (server == null) {
            return;
        }
        DataManager.resetPlayerCounts();
        for (ServerPlayerEntity playerEntity : server.getPlayerManager().getPlayerList()) {
            if (playerEntity == null || playerEntity.isSpectator()) {
                continue;
            }
            DataManager.MapData mapData = ZoneUtil.getCurrentMapForPlayerCount(playerEntity);
            if (mapData != null) {
                mapData.player_count += 1;
            }
        }
    }

    private static boolean migrateLegacyMovementConfig(MinehopConfig minehopConfig) {
        MinehopConfig.MovementSettings movement = minehopConfig.movement;
        if (movement.source_units_migrated && movement.source_movement_version >= 12) {
            return false;
        }

        boolean looksLegacy =
                movement.sv_friction <= 1.0D
                || movement.sv_accelerate <= 1.0D
                || movement.sv_airaccelerate > 10000.0D
                || movement.sv_maxairspeed < 1.0D
                || movement.sv_gravity < 10.0D;

        boolean looksFirstSourcePreset =
                Math.abs(movement.sv_friction - 4.0D) < 1.0E-9D
                        && Math.abs(movement.sv_accelerate - 10.0D) < 1.0E-9D
                        && Math.abs(movement.sv_airaccelerate - 100.0D) < 1.0E-9D
                        && Math.abs(movement.sv_maxairspeed - 30.0D) < 1.0E-9D
                        && Math.abs(movement.sv_jump_impulse - 289.0D) < 1.0E-9D
                        && Math.abs(movement.speed_mul - 2.5D) < 1.0E-9D
                        && Math.abs(movement.sv_gravity - 800.0D) < 1.0E-9D;

        boolean looksSecondSourcePreset =
                Math.abs(movement.sv_friction - 4.0D) < 1.0E-9D
                        && Math.abs(movement.sv_accelerate - 10.0D) < 1.0E-9D
                        && Math.abs(movement.sv_airaccelerate - 150.0D) < 1.0E-9D
                        && Math.abs(movement.sv_maxairspeed - 100.0D) < 1.0E-9D
                        && Math.abs(movement.sv_jump_impulse - 289.0D) < 1.0E-9D
                        && Math.abs(movement.speed_mul - 3.2D) < 1.0E-9D
                        && Math.abs(movement.sv_gravity - 800.0D) < 1.0E-9D;

        boolean looksThirdSourcePreset =
                Math.abs(movement.sv_friction - 4.0D) < 1.0E-9D
                        && Math.abs(movement.sv_accelerate - 10.0D) < 1.0E-9D
                        && Math.abs(movement.sv_airaccelerate - 100.0D) < 1.0E-9D
                        && Math.abs(movement.sv_maxairspeed - 40.0D) < 1.0E-9D
                        && Math.abs(movement.sv_jump_impulse - 289.0D) < 1.0E-9D
                        && Math.abs(movement.speed_mul - 3.0D) < 1.0E-9D
                        && Math.abs(movement.sv_gravity - 800.0D) < 1.0E-9D;

        boolean looksFourthSourcePreset =
                Math.abs(movement.sv_friction - 4.0D) < 1.0E-9D
                        && Math.abs(movement.sv_accelerate - 10.0D) < 1.0E-9D
                        && Math.abs(movement.sv_airaccelerate - 125.0D) < 1.0E-9D
                        && Math.abs(movement.sv_maxairspeed - 70.0D) < 1.0E-9D
                        && Math.abs(movement.sv_jump_impulse - 289.0D) < 1.0E-9D
                        && Math.abs(movement.speed_mul - 3.1D) < 1.0E-9D
                        && Math.abs(movement.sv_gravity - 800.0D) < 1.0E-9D;

        boolean looksFifthSourcePreset =
                Math.abs(movement.sv_friction - 4.0D) < 1.0E-9D
                        && Math.abs(movement.sv_accelerate - 10.0D) < 1.0E-9D
                        && Math.abs(movement.sv_airaccelerate - 100.0D) < 1.0E-9D
                        && Math.abs(movement.sv_maxairspeed - 57.0D) < 1.0E-9D
                        && Math.abs(movement.sv_jump_impulse - 300.0D) < 1.0E-9D
                        && Math.abs(movement.speed_mul - 3.25D) < 1.0E-9D
                        && Math.abs(movement.sv_gravity - 800.0D) < 1.0E-9D;

        boolean looksSixthSourcePreset =
                Math.abs(movement.sv_friction - 4.0D) < 1.0E-9D
                        && Math.abs(movement.sv_accelerate - 10.0D) < 1.0E-9D
                        && Math.abs(movement.sv_airaccelerate - 100.0D) < 1.0E-9D
                        && Math.abs(movement.sv_maxairspeed - 33.0D) < 1.0E-9D
                        && Math.abs(movement.sv_jump_impulse - 300.0D) < 1.0E-9D
                        && Math.abs(movement.speed_mul - 3.25D) < 1.0E-9D
                        && Math.abs(movement.sv_gravity - 800.0D) < 1.0E-9D;

        boolean looksSeventhSourcePreset =
                Math.abs(movement.sv_friction - 4.0D) < 1.0E-9D
                        && Math.abs(movement.sv_accelerate - 10.0D) < 1.0E-9D
                        && Math.abs(movement.sv_airaccelerate - 31.25D) < 1.0E-9D
                        && Math.abs(movement.sv_maxairspeed - 57.0D) < 1.0E-9D
                        && Math.abs(movement.sv_jump_impulse - 300.0D) < 1.0E-9D
                        && Math.abs(movement.speed_mul - 3.25D) < 1.0E-9D
                        && Math.abs(movement.sv_gravity - 800.0D) < 1.0E-9D;

        boolean looksEighthSourcePreset =
                Math.abs(movement.sv_friction - 4.0D) < 1.0E-9D
                        && Math.abs(movement.sv_accelerate - 10.0D) < 1.0E-9D
                        && Math.abs(movement.sv_airaccelerate - 100.0D) < 1.0E-9D
                        && Math.abs(movement.sv_maxairspeed - 30.0D) < 1.0E-9D
                        && Math.abs(movement.sv_jump_impulse - 340.0D) < 1.0E-9D
                        && Math.abs(movement.speed_mul - 3.25D) < 1.0E-9D
                        && Math.abs(movement.sv_gravity - 800.0D) < 1.0E-9D;

        boolean looksNinthSourcePreset =
                Math.abs(movement.sv_friction - 4.0D) < 1.0E-9D
                        && Math.abs(movement.sv_accelerate - 10.0D) < 1.0E-9D
                        && Math.abs(movement.sv_airaccelerate - 100.0D) < 1.0E-9D
                        && Math.abs(movement.sv_maxairspeed - 27.5D) < 1.0E-9D
                        && Math.abs(movement.sv_jump_impulse - 300.0D) < 1.0E-9D
                        && Math.abs(movement.speed_mul - 3.25D) < 1.0E-9D
                        && Math.abs(movement.sv_gravity - 800.0D) < 1.0E-9D;

        boolean looksTenthSourcePreset =
                Math.abs(movement.sv_friction - 4.0D) < 1.0E-9D
                        && Math.abs(movement.sv_accelerate - 10.0D) < 1.0E-9D
                        && Math.abs(movement.sv_airaccelerate - 100.0D) < 1.0E-9D
                        && Math.abs(movement.sv_maxairspeed - 57.0D) < 1.0E-9D
                        && Math.abs(movement.sv_jump_impulse - 300.0D) < 1.0E-9D
                        && Math.abs(movement.speed_mul - 3.25D) < 1.0E-9D
                        && Math.abs(movement.sv_gravity - 800.0D) < 1.0E-9D;

        if (looksLegacy || looksFirstSourcePreset || looksSecondSourcePreset || looksThirdSourcePreset || looksFourthSourcePreset || looksFifthSourcePreset || looksSixthSourcePreset || looksSeventhSourcePreset || looksEighthSourcePreset || looksNinthSourcePreset || looksTenthSourcePreset) {
            movement.sv_friction = 4.0D;
            movement.sv_accelerate = 10.0D;
            movement.sv_airaccelerate = 100.0D;
            movement.sv_maxairspeed = 30.0D;
            movement.sv_jump_impulse = 300.0D;
            movement.speed_mul = 3.25D;
            movement.sv_gravity = 800.0D;
            movement.speed_coefficient = 1.0D;
        }

        movement.source_units_migrated = true;
        movement.source_movement_version = 12;
        return true;
    }
}
