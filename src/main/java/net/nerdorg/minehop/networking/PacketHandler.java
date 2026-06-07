package net.nerdorg.minehop.networking;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.commands.ReplayCommands;
import net.nerdorg.minehop.commands.SpectateCommands;
import net.nerdorg.minehop.config.ConfigWrapper;
import net.nerdorg.minehop.config.MinehopConfig;
import net.nerdorg.minehop.data.DataManager;
import net.nerdorg.minehop.discord.DiscordIntegration;
import net.nerdorg.minehop.entity.custom.EndEntity;
import net.nerdorg.minehop.networking.payloads.*;
import net.nerdorg.minehop.replays.ReplayEvents;
import net.nerdorg.minehop.replays.ReplayManager;
import net.nerdorg.minehop.util.Logger;
import net.nerdorg.minehop.util.MapCreationManager;
import net.nerdorg.minehop.util.SurfRampPlacementManager;
import net.nerdorg.minehop.util.ZoneUtil;
import net.nerdorg.minehop.util.ZonePlacementManager;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class PacketHandler {
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private static boolean registered = false;
    public static final int MAX_BUFF_CHARS = 24_000;

    public static void register() {
        registerC2S();
        registerS2C();
    }

    private static void registerS2C() {
        // server to client
        PayloadTypeRegistry.playS2C().register(AntiCheatPayload.ID, AntiCheatPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(BoundsStickSelectionPayload.ID, BoundsStickSelectionPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ConfigSyncPayload.ID, ConfigSyncPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(CSpecEfficiencyPayload.ID, CSpecEfficiencyPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(HandshakeIDPayload.ID, HandshakeIDPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(MapFinishPayload.ID, MapFinishPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(OpenMapCreatorScreenPayload.ID, OpenMapCreatorScreenPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(OpenMapScreenPayload.ID, OpenMapScreenPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(OtherVTogglePayload.ID, OtherVTogglePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ResetVelocityCarryPayload.ID, ResetVelocityCarryPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ReplayVTogglePayload.ID, ReplayVTogglePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(RunTimerHudPayload.ID, RunTimerHudPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SelfVTogglePayload.ID, SelfVTogglePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SendEfficiencyPayload.ID, SendEfficiencyPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SendMapPayload.ID, SendMapPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SendPersonalRecordPayload.ID, SendPersonalRecordPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SendRecordPayload.ID, SendRecordPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SendSpectatorsPayload.ID, SendSpectatorsPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SendTimePayload.ID, SendTimePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SetCheaterPayload.ID, SetCheaterPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SSpecEfficiencyPayload.ID, SSpecEfficiencyPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(OpenSurfStickSettingsPayload.ID, OpenSurfStickSettingsPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(OpenZoneStickSettingsPayload.ID, OpenZoneStickSettingsPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SurfStickPreviewPayload.ID, SurfStickPreviewPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(UpdatePowerPayload.ID, UpdatePowerPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ZoneSyncIDPayload.ID, ZoneSyncIDPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(OpenAntiCheatScreenPayload.ID, OpenAntiCheatScreenPayload.CODEC);
    }

    private static void registerC2S() {
        // client to server
        PayloadTypeRegistry.playC2S().register(AntiCheatPayload.ID, AntiCheatPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ConfigSyncPayload.ID, ConfigSyncPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(CSpecEfficiencyPayload.ID, CSpecEfficiencyPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(HandshakeIDPayload.ID, HandshakeIDPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(MapCreatorActionPayload.ID, MapCreatorActionPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(MapFinishPayload.ID, MapFinishPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(OpenMapScreenPayload.ID, OpenMapScreenPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(OtherVTogglePayload.ID, OtherVTogglePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ReplayVTogglePayload.ID, ReplayVTogglePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(SelfVTogglePayload.ID, SelfVTogglePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(SendEfficiencyPayload.ID, SendEfficiencyPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(SendMapPayload.ID, SendMapPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(SendPersonalRecordPayload.ID, SendPersonalRecordPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(SendRecordPayload.ID, SendRecordPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(SendSpectatorsPayload.ID, SendSpectatorsPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(SendTimePayload.ID, SendTimePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(SetCheaterPayload.ID, SetCheaterPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(SSpecEfficiencyPayload.ID, SSpecEfficiencyPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(SurfStickCancelPayload.ID, SurfStickCancelPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(SurfStickDeletePayload.ID, SurfStickDeletePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(SurfStickSettingsPayload.ID, SurfStickSettingsPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ZoneStickCancelPayload.ID, ZoneStickCancelPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ZoneStickDeletePayload.ID, ZoneStickDeletePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ZoneStickSettingsPayload.ID, ZoneStickSettingsPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(SurfStickPreviewPayload.ID, SurfStickPreviewPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(UpdatePowerPayload.ID, UpdatePowerPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ZoneSyncIDPayload.ID, ZoneSyncIDPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(AntiCheatActionPayload.ID, AntiCheatActionPayload.CODEC);
    }

    public static void sendConfigToClient(ServerPlayerEntity player, MinehopConfig config) {
        MinehopConfig effectiveConfig = ConfigWrapper.getEffectiveConfig(player);
        DataManager.MapData currentMap = ConfigWrapper.resolveEffectiveMap(player);
        ServerPlayNetworking.send(player,  new ConfigSyncPayload(
            effectiveConfig.movement.sv_friction,
            effectiveConfig.movement.sv_accelerate,
            effectiveConfig.movement.sv_airaccelerate,
            effectiveConfig.movement.sv_maxairspeed,
            effectiveConfig.movement.sv_jump_impulse,
            effectiveConfig.movement.speed_mul,
            effectiveConfig.movement.sv_gravity,
            effectiveConfig.movement.speed_coefficient,
            Minehop.speedCapMap.containsKey(player.getNameForScoreboard()) ? Minehop.speedCapMap.get(player.getNameForScoreboard()) : 1000000,
            effectiveConfig.movement.auto_step_up,
            effectiveConfig.movement.css_crouch_jump,
            currentMap != null && currentMap.hns,
            effectiveConfig.enabled,
            effectiveConfig.fall_damage,
            effectiveConfig.movement.sv_stopspeed,
            effectiveConfig.movement.disable_sprint
        ));
    }
    public static void updateZone(ServerPlayerEntity player, int entityId, BlockPos pos1, BlockPos pos2, String name, int check_index) {
        ServerPlayNetworking.send(player,  new ZoneSyncIDPayload(
                entityId,
                new Vector3f(pos1.getX(), pos1.getY(), pos1.getZ()),
                new Vector3f(pos2.getX(), pos2.getY(), pos2.getZ()),
                name,
                check_index
        ));
    }

    public static void sendSelfVToggle(ServerPlayerEntity player) {
        ServerPlayNetworking.send(player, new SelfVTogglePayload(true));
    }

    public static void sendBoundsStickSelection(ServerPlayerEntity player, BlockPos first, BlockPos second) {
        if (player == null) {
            return;
        }
        ServerPlayNetworking.send(
                player,
                new BoundsStickSelectionPayload(
                        first != null,
                        first == null ? BlockPos.ORIGIN : first,
                        second != null,
                        second == null ? BlockPos.ORIGIN : second
                )
        );
    }

    public static void sendOtherVToggle(ServerPlayerEntity player) {
        ServerPlayNetworking.send(player,  new OtherVTogglePayload(true));
    }

    public static void sendReplayVToggle(ServerPlayerEntity player) {
        ServerPlayNetworking.send(player,  new ReplayVTogglePayload(true));
    }

    public static void sendEfficiency(ServerPlayerEntity player, double efficiency) {
        ServerPlayNetworking.send(player,  new SendEfficiencyPayload(efficiency));
    }

    public static void sendSpectators(ServerPlayerEntity player) {
        if (SpectateCommands.spectatorList.containsKey(player.getNameForScoreboard())) {
            List<String> spectators = SpectateCommands.spectatorList.get(player.getNameForScoreboard());
            if (spectators.size() > 1) {
                String buff = "";
                buff += (spectators.size() - 1);
                for (String spectator : spectators) {
                    if (!spectator.equals(player.getNameForScoreboard())) {
                        buff += ("~" + spectator);
                    }
                }

                ServerPlayNetworking.send(player,  new SendSpectatorsPayload(buff));
            }
        }
    }

    private static String resolveActiveMapName(HashMap<String, Long> timerMap) {
        if (timerMap == null || timerMap.isEmpty()) {
            return null;
        }
        for (String mapName : timerMap.keySet()) {
            if (mapName != null && !mapName.isBlank()) {
                return mapName;
            }
        }
        return null;
    }

    private static boolean isInsideZoneBounds(Vec3d playerPos, BlockPos corner1, BlockPos corner2) {
        if (playerPos == null || corner1 == null || corner2 == null) {
            return false;
        }
        double minX = Math.min(corner1.getX(), corner2.getX());
        double minY = Math.min(corner1.getY(), corner2.getY());
        double minZ = Math.min(corner1.getZ(), corner2.getZ());
        double maxX = Math.max(corner1.getX(), corner2.getX());
        double maxY = Math.max(corner1.getY(), corner2.getY());
        double maxZ = Math.max(corner1.getZ(), corner2.getZ());
        if (maxX <= minX) {
            maxX = minX + 1.0D;
        }
        if (maxY <= minY) {
            maxY = minY + 1.0D;
        }
        if (maxZ <= minZ) {
            maxZ = minZ + 1.0D;
        }
        return playerPos.x >= minX
                && playerPos.x < maxX
                && playerPos.y >= minY
                && playerPos.y < maxY
                && playerPos.z >= minZ
                && playerPos.z < maxZ;
    }

    private static boolean isPlayerInsideMatchingEndZone(ServerPlayerEntity player, String mapName) {
        if (player == null || mapName == null || mapName.isBlank()) {
            return false;
        }
        Vec3d playerPos = player.getPos();
        for (net.minecraft.entity.Entity entity : player.getServerWorld().iterateEntities()) {
            if (!(entity instanceof EndEntity endEntity)) {
                continue;
            }
            if (!mapName.equals(endEntity.getPairedMap())) {
                continue;
            }
            if (isInsideZoneBounds(playerPos, endEntity.getCorner1(), endEntity.getCorner2())) {
                return true;
            }
        }
        return false;
    }

    private static void clearRunTimerHudForRunnerAndSpectators(ServerPlayerEntity runner, MinecraftServer server) {
        if (runner == null) {
            return;
        }
        clearRunTimerHud(runner);
        if (server == null) {
            return;
        }
        List<String> spectators = SpectateCommands.spectatorList.get(runner.getNameForScoreboard());
        if (spectators == null || spectators.isEmpty()) {
            return;
        }
        for (String spectatorName : spectators) {
            if (spectatorName == null || spectatorName.equals(runner.getNameForScoreboard())) {
                continue;
            }
            ServerPlayerEntity spectatorPlayer = server.getPlayerManager().getPlayer(spectatorName);
            clearRunTimerHud(spectatorPlayer);
        }
    }

    private static void clearFinishedRunState(ServerPlayerEntity player, MinecraftServer server) {
        if (player == null) {
            return;
        }
        String playerName = player.getNameForScoreboard();
        Minehop.timerManager.remove(playerName);
        Minehop.finishTimeManager.remove(playerName);
        ReplayEvents.replayEntryMap.remove(playerName);
        clearRunTimerHudForRunnerAndSpectators(player, server);
    }

    private static void handleMapCompletion(ServerPlayerEntity player, MinecraftServer server, String mapName, float time) {
        float ping_limit = 300; // ping limit in ms
        if (player == null || server == null) {
            return;
        }
        if (player.isCreative() || player.isSpectator() || Minehop.currentCheaters.contains(player)) {
            clearFinishedRunState(player, server);
            return;
        }
        HashMap<String, Long> timerMap = Minehop.timerManager.get(player.getNameForScoreboard());
        if (timerMap == null || timerMap.isEmpty()) {
            clearFinishedRunState(player, server);
            return;
        }

        String activeMapName = resolveActiveMapName(timerMap);
        if (activeMapName == null) {
            clearFinishedRunState(player, server);
            return;
        }
        if (mapName != null && !mapName.isBlank() && !activeMapName.equals(mapName)) {
            Logger.logServer(server, "Rejected map finish from " + player.getNameForScoreboard() + " due to map mismatch (" + mapName + " vs " + activeMapName + ").");
            clearFinishedRunState(player, server);
            return;
        }
        if (DataManager.getMap(activeMapName) == null) {
            Logger.logServer(server, "Rejected map finish from " + player.getNameForScoreboard() + " because active map " + activeMapName + " no longer exists.");
            clearFinishedRunState(player, server);
            return;
        }

        Long timerStart = timerMap.get(activeMapName);
        if (timerStart == null) {
            clearFinishedRunState(player, server);
            return;
        }
        if (!Float.isFinite(time) || time <= 0.0F) {
            Logger.logServer(server, "Rejected map finish from " + player.getNameForScoreboard() + " due to invalid time value.");
            clearFinishedRunState(player, server);
            return;
        }
        if (!isPlayerInsideMatchingEndZone(player, activeMapName)) {
            Logger.logServer(server, "Rejected map finish from " + player.getNameForScoreboard() + " because they were not inside an end zone for " + activeMapName + ".");
            clearFinishedRunState(player, server);
            return;
        }

        // Prefer the server-stamped end-zone entry time (measured at the real crossing) over `now`
        // (packet-processing time), so a server-thread stall can't inflate rawTime and reject a
        // legit run.
        HashMap<String, Long> finishMap = Minehop.finishTimeManager.get(player.getNameForScoreboard());
        Long finishStamp = finishMap == null ? null : finishMap.get(activeMapName);
        long finishNanos = finishStamp != null ? finishStamp : System.nanoTime();
        double rawTime = (double) (finishNanos - timerStart) / 1000000000;
        if (time < rawTime + (ping_limit / 1000f) && time > rawTime - (ping_limit / 1000f)) {
            String formattedNumber = String.format("%.5f", time);
            String playerName = player.getNameForScoreboard();
            List<ReplayManager.ReplayEntry> replayEntries = ReplayEvents.replayEntryMap.get(playerName);
            if (replayEntries != null && !replayEntries.isEmpty()) {
                ReplayManager.saveReplay(
                        player.getServerWorld(),
                        new ReplayManager.Replay(
                                activeMapName,
                                playerName,
                                time,
                                ReplayManager.copyReplayEntries(replayEntries)
                        )
                );
            }

            DataManager.RecordData existingPersonalRecord = DataManager.getPersonalRecord(playerName, activeMapName);
            boolean isNewPersonalRecord = existingPersonalRecord == null || time < existingPersonalRecord.time;
            if (isNewPersonalRecord) {
                if (existingPersonalRecord != null) {
                    Logger.logSuccess(player, "You just beat your time (" + String.format("%.5f", existingPersonalRecord.time) + ") on " + existingPersonalRecord.map_name + ", your new record is " + formattedNumber + "!");
                } else {
                    Logger.logSuccess(player, "You just claimed a personal record of " + formattedNumber + "!");
                }

                DataManager.upsertPersonalRecord(playerName, activeMapName, time);
                DataManager.saveData(player.getServerWorld(), DataManager.pbListLocation, Minehop.personalRecordList);
            }

            DataManager.RecordData existingRecord = DataManager.getRecord(activeMapName);
            boolean newWorldRecord = existingRecord == null || time < existingRecord.time;
            if (newWorldRecord) {
                String previousHolder = existingRecord == null ? "" : existingRecord.name;
                double previousTime = existingRecord == null ? 0.0D : existingRecord.time;
                boolean firstWorldRecord = existingRecord == null;

                DataManager.upsertRecord(playerName, activeMapName, time);
                DataManager.saveData(player.getServerWorld(), DataManager.recordsListLocation, Minehop.recordList);

                if (!previousHolder.isBlank() && !previousHolder.equals(playerName) && DataManager.getAnyRecordFromName(previousHolder) == null && isSafeMinecraftPlayerName(previousHolder)) {
                    server.getCommandManager().execute(
                            server.getCommandManager().getDispatcher().parse("lp user " + previousHolder + " parent remove record_holder", server.getCommandSource()),
                            "lp user " + previousHolder + " parent remove record_holder"
                    );
                }
                if (isSafeMinecraftPlayerName(playerName)) {
                    server.getCommandManager().execute(
                            server.getCommandManager().getDispatcher().parse("lp user " + playerName + " parent add record_holder", server.getCommandSource()),
                            "lp user " + playerName + " parent add record_holder"
                    );
                }

                String recordMessage;
                if (!previousHolder.isBlank()) {
                    recordMessage = playerName + " just beat " + previousHolder + "'s time (" + String.format("%.5f", previousTime) + ") on " + activeMapName + " and now hold the world record with a time of " + formattedNumber + "!";
                } else {
                    recordMessage = playerName + " just claimed the world record on " + activeMapName + " with a time of " + formattedNumber + "!";
                }
                Logger.logGlobal(server, recordMessage);
                DiscordIntegration.sendRecordToDiscord(recordMessage);
                ReplayCommands.ensureWorldRecordReplayEntity(server, activeMapName);
            }
            Logger.logSuccess(player, "Completed " + activeMapName + " in " + formattedNumber + " seconds.");
        } else {
            Logger.logServer(server, "Invalid time for " + player.getNameForScoreboard() + ".");
        }
        clearFinishedRunState(player, server);
    }

    private static boolean isSafeMinecraftPlayerName(String playerName) {
        return playerName != null && playerName.matches("[A-Za-z0-9_]{1,16}");
    }

    public static void sendSpecEfficiency(ServerPlayerEntity player, double last_jump_speed, int jump_count, double last_efficiency) {
        ServerPlayNetworking.send(player,  new CSpecEfficiencyPayload(last_jump_speed, jump_count, last_efficiency));
    }

    public static void sendRunTimerHud(ServerPlayerEntity player, float time, float personalBest) {
        if (player == null) {
            return;
        }
        ServerPlayNetworking.send(player, new RunTimerHudPayload(true, time, personalBest));
    }

    public static void clearRunTimerHud(ServerPlayerEntity player) {
        if (player == null) {
            return;
        }
        ServerPlayNetworking.send(player, new RunTimerHudPayload(false, 0.0F, 0.0F));
    }

    public static void sendResetVelocityCarry(ServerPlayerEntity player, Vec3d velocity, int ticks) {
        if (player == null || velocity == null || ticks <= 0) {
            return;
        }
        if (!Double.isFinite(velocity.x) || !Double.isFinite(velocity.y) || !Double.isFinite(velocity.z)) {
            return;
        }
        ServerPlayNetworking.send(
                player,
                new ResetVelocityCarryPayload(
                        (float) velocity.x,
                        (float) velocity.y,
                        (float) velocity.z,
                        Math.max(1, ticks)
                )
        );
    }

    public static void sendOpenMapScreen(ServerPlayerEntity player, String title) {
        ServerPlayNetworking.send(player,  new OpenMapScreenPayload(title));
    }

    public static void sendOpenMapCreatorScreen(
            ServerPlayerEntity player,
            String mapName,
            int difficulty,
            boolean arena,
            boolean hns,
            boolean surf,
            boolean kz,
            boolean movementOverride,
            double movementSvFriction,
            double movementSvAccelerate,
            double movementSvAiraccelerate,
            double movementSvMaxairspeed,
            double movementSvJumpImpulse,
            double movementSpeedMul,
            double movementSvGravity,
            double movementSvStopspeed,
            double movementSpeedCoefficient,
            boolean movementAutoStepUp,
            boolean movementCssCrouchJump,
            boolean movementDisableSprint,
            boolean movementFallDamage,
            int checkpointIndex
    ) {
        ServerPlayNetworking.send(
                player,
                new OpenMapCreatorScreenPayload(
                        mapName,
                        difficulty,
                        arena,
                        hns,
                        surf,
                        kz,
                        movementOverride,
                        movementSvFriction,
                        movementSvAccelerate,
                        movementSvAiraccelerate,
                        movementSvMaxairspeed,
                        movementSvJumpImpulse,
                        movementSpeedMul,
                        movementSvGravity,
                        movementSvStopspeed,
                        movementSpeedCoefficient,
                        movementAutoStepUp,
                        movementCssCrouchJump,
                        movementDisableSprint,
                        movementFallDamage,
                        checkpointIndex
                )
        );
    }

    public static void sendMaps(ServerPlayerEntity player) {
        ServerPlayNetworking.send(player, new SendMapPayload("#RESET"));

        for (DataManager.MapData mapData : Minehop.mapList) {
            if (mapData == null) {
                continue;
            }
            String buff = "";

            buff += sanitizeMapField(mapData.name);buff += "~";
            buff += mapData.x;buff += "~";
            buff += mapData.y;buff += "~";
            buff += mapData.z;buff += "~";
            buff += mapData.xrot;buff += "~";
            buff += mapData.yrot;buff += "~";
            buff += sanitizeMapField(mapData.worldKey);buff += "~";
            buff += mapData.arena;buff += "~";
            buff += mapData.hns;buff += "~";
            buff += mapData.surf;buff += "~";
            buff += mapData.difficulty;buff += "~";
            buff += mapData.player_count;buff += "~";
            buff += mapData.userMap;buff += "~";
            buff += sanitizeMapField(mapData.ownerUuid);buff += "~";
            buff += sanitizeMapField(mapData.ownerName);buff += "~";
            buff += sanitizeMapField(mapData.description);buff += "~";
            buff += mapData.plotMinX;buff += "~";
            buff += mapData.plotMinY;buff += "~";
            buff += mapData.plotMinZ;buff += "~";
            buff += mapData.plotMaxX;buff += "~";
            buff += mapData.plotMaxY;buff += "~";
            buff += mapData.plotMaxZ;buff += "~";
            buff += sanitizeMapField(mapData.plotGroundBlockId);buff += "~";
            buff += mapData.play_count;buff += "~";
            buff += mapData.rating_count;buff += "~";
            buff += mapData.rating_quality_total;buff += "~";
            buff += mapData.rating_difficulty_total;buff += "~";
            buff += mapData.kz;buff += "~";
            buff += mapData.movement_override;buff += "~";
            buff += mapData.movement_sv_friction;buff += "~";
            buff += mapData.movement_sv_accelerate;buff += "~";
            buff += mapData.movement_sv_airaccelerate;buff += "~";
            buff += mapData.movement_sv_maxairspeed;buff += "~";
            buff += mapData.movement_sv_jump_impulse;buff += "~";
            buff += mapData.movement_speed_mul;buff += "~";
            buff += mapData.movement_sv_gravity;buff += "~";
            buff += mapData.movement_sv_stopspeed;buff += "~";
            buff += mapData.movement_speed_coefficient;buff += "~";
            buff += mapData.movement_auto_step_up;buff += "~";
            buff += mapData.movement_css_crouch_jump;buff += "~";
            buff += mapData.movement_fall_damage;buff += "~";
            buff += mapData.movement_disable_sprint;

            ServerPlayNetworking.send(player,  new SendMapPayload(buff));
        }
    }

    private static String sanitizeMapField(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace('~', ' ')
                .replace('\r', ' ')
                .replace('\n', ' ')
                .replace('\t', ' ');
    }

    public static void sendRecords(ServerPlayerEntity player) {
        ServerPlayNetworking.send(player, new SendRecordPayload("#RESET"));

        StringBuilder sb = new StringBuilder(8192);

        for (DataManager.MapData mapData : Minehop.mapList) {
            DataManager.RecordData rd = DataManager.getRecord(mapData.name);

            String mapName, name;
            double time;
            if (rd != null) {
                mapName = rd.map_name;
                name    = rd.name;
                time    = rd.time;
            } else {
                mapName = mapData.name;
                name    = "None";
                time    = 1_000_000;
            }

            String line = mapName + "~" + name + "~" + time + "\n";

            if (sb.length() + line.length() > MAX_BUFF_CHARS) {
                ServerPlayNetworking.send(player, new SendRecordPayload(sb.toString()));
                sb.setLength(0);
            }
            sb.append(line);
        }

        if (sb.length() > 0) {
            ServerPlayNetworking.send(player, new SendRecordPayload(sb.toString()));
        }
    }

    public static void sendPersonalRecords(ServerPlayerEntity player) {
        ServerPlayNetworking.send(player, new SendPersonalRecordPayload("#RESET"));

        StringBuilder sb = new StringBuilder(8192);

        for (DataManager.RecordData rd : Minehop.personalRecordList) {
            String line = rd.map_name + "~" + rd.name + "~" + rd.time + "\n";

            if (sb.length() + line.length() > MAX_BUFF_CHARS) {
                ServerPlayNetworking.send(player, new SendPersonalRecordPayload(sb.toString()));
                sb.setLength(0);
            }
            sb.append(line);
        }

        if (!sb.isEmpty()) {
            ServerPlayNetworking.send(player, new SendPersonalRecordPayload(sb.toString()));
        }
    }

    public static void sendPower(ServerPlayerEntity player, double x_power, double y_power, double z_power, BlockPos boosterPos) {
        ServerPlayNetworking.send(player,  new UpdatePowerPayload(x_power, y_power, z_power, boosterPos.getX(), boosterPos.getY(), boosterPos.getZ()));
    }

    public static void sendSurfStickPreview(
            ServerPlayerEntity player,
            List<BlockPos> points,
            double width,
            double drop,
            boolean oneSided,
            boolean outsideCurve
    ) {
        if (player == null) {
            return;
        }
        List<BlockPos> immutablePoints = new ArrayList<>();
        if (points != null) {
            for (BlockPos point : points) {
                if (point != null) {
                    immutablePoints.add(point.toImmutable());
                }
            }
        }
        ServerPlayNetworking.send(
                player,
                new SurfStickPreviewPayload(false, (float) width, (float) drop, oneSided, outsideCurve, immutablePoints)
        );
    }

    public static void clearSurfStickPreview(ServerPlayerEntity player) {
        if (player == null) {
            return;
        }
        ServerPlayNetworking.send(player, new SurfStickPreviewPayload(true, 0.0F, 0.0F, false, false, List.of()));
    }

    public static void openSurfStickSettings(
            ServerPlayerEntity player,
            double width,
            double drop,
            String textureBlockId,
            boolean oneSided,
            boolean outsideCurve,
            String renderMode,
            int wireframeColor,
            boolean wireframeFill,
            int wireframeFillColor,
            int wireframeFillAlpha,
            boolean editingExisting
    ) {
        if (player == null) {
            return;
        }
        ServerPlayNetworking.send(
                player,
                new OpenSurfStickSettingsPayload(
                        (float) width,
                        (float) drop,
                        textureBlockId,
                        oneSided,
                        outsideCurve,
                        renderMode,
                        wireframeColor,
                        wireframeFill,
                        wireframeFillColor,
                        wireframeFillAlpha,
                        editingExisting
                )
        );
    }

    public static void openZoneStickSettings(
            ServerPlayerEntity player,
            String zoneType,
            String mapName,
            int checkpointIndex,
            boolean checkpointEditable,
            boolean preserveSpeed,
            boolean preserveSpeedEditable
    ) {
        if (player == null) {
            return;
        }
        ServerPlayNetworking.send(
                player,
                new OpenZoneStickSettingsPayload(
                        zoneType,
                        mapName,
                        checkpointIndex,
                        checkpointEditable,
                        preserveSpeed,
                        preserveSpeedEditable
                )
        );
    }


    public static void registerReceivers() {
        if (registered) {return;}
        registered = true;

        net.nerdorg.minehop.commands.AntiCheatCommands.wireServerHandlers();

        ServerPlayNetworking.registerGlobalReceiver(SendTimePayload.ID, (payload, ctx) -> {
            ServerPlayerEntity player = ctx.player();
            MinecraftServer server = ctx.server();
            float time = payload.time();
            ctx.server().execute(() -> {
                if (player == null || player.isSpectator() || !Float.isFinite(time)) {
                    return;
                }
                HashMap<String, Long> timerMap = Minehop.timerManager.get(player.getNameForScoreboard());
                String mapName = resolveActiveMapName(timerMap);
                if (mapName == null) {
                    clearRunTimerHudForRunnerAndSpectators(player, server);
                    return;
                }
                DataManager.RecordData personalRecordData = DataManager.getPersonalRecord(player.getNameForScoreboard(), mapName);
                double personalRecord = 0;
                if (personalRecordData != null) {
                    personalRecord = personalRecordData.time;
                }
                float safeTime = Math.max(0.0F, time);
                float safePb = (float) Math.max(0.0D, personalRecord);
                if (SpectateCommands.spectatorList.containsKey(player.getNameForScoreboard())) {
                    List<String> spectators = SpectateCommands.spectatorList.get(player.getNameForScoreboard());
                    for (String spectatorName : spectators) {
                        if (!spectatorName.equals(player.getNameForScoreboard())) {
                            ServerPlayerEntity spectatorPlayer = server.getPlayerManager().getPlayer(spectatorName);
                            if (spectatorPlayer == null) {
                                continue;
                            }
                            if (!spectatorPlayer.isCreative()) {
                                spectatorPlayer.getInventory().clear();
                            }
                            spectatorPlayer.teleportTo(ZoneUtil.makeTeleportTarget(player.getServerWorld(), new Vec3d(player.getX(), player.getY(), player.getZ()), player.getYaw(), player.getPitch()));
                            spectatorPlayer.setCameraEntity(player);
                            sendRunTimerHud(spectatorPlayer, safeTime, safePb);
                        }
                    }
                }
                sendRunTimerHud(player, safeTime, safePb);
            });
        });
        ServerPlayNetworking.registerGlobalReceiver(MapFinishPayload.ID, (payload, ctx) -> {
            ServerPlayerEntity player = ctx.player();
            MinecraftServer server = ctx.server();
            String mapName = payload.map_name();
            float time = payload.time();
            ctx.server().execute(() -> handleMapCompletion(player, server, mapName, time));
        });
        ServerPlayNetworking.registerGlobalReceiver(MapCreatorActionPayload.ID, (payload, ctx) -> {
            ServerPlayerEntity player = ctx.player();
            ctx.server().execute(() -> MapCreationManager.handleAction(
                    player,
                    payload.action(),
                    payload.mapName(),
                    payload.difficulty(),
                    payload.arena(),
                    payload.hns(),
                    payload.surf(),
                    payload.kz(),
                    payload.movementOverride(),
                    payload.movementSvFriction(),
                    payload.movementSvAccelerate(),
                    payload.movementSvAiraccelerate(),
                    payload.movementSvMaxairspeed(),
                    payload.movementSvJumpImpulse(),
                    payload.movementSpeedMul(),
                    payload.movementSvGravity(),
                    payload.movementSvStopspeed(),
                    payload.movementSpeedCoefficient(),
                    payload.movementAutoStepUp(),
                    payload.movementCssCrouchJump(),
                    payload.movementDisableSprint(),
                    payload.movementFallDamage(),
                    payload.checkpointIndex()
            ));
        });
        ServerPlayNetworking.registerGlobalReceiver(SurfStickSettingsPayload.ID, (payload, ctx) -> {
            ServerPlayerEntity player = ctx.player();
            ctx.server().execute(() -> SurfRampPlacementManager.applyOptionsFromGui(
                    player,
                    payload.width(),
                    payload.drop(),
                    payload.textureBlockId(),
                    payload.oneSided(),
                    payload.outsideCurve(),
                    payload.renderMode(),
                    payload.wireframeColor(),
                    payload.wireframeFill(),
                    payload.wireframeFillColor(),
                    payload.wireframeFillAlpha()
            ));
        });
        ServerPlayNetworking.registerGlobalReceiver(ZoneStickSettingsPayload.ID, (payload, ctx) -> {
            ServerPlayerEntity player = ctx.player();
            ctx.server().execute(() -> ZonePlacementManager.applyOptionsFromGui(
                    player,
                    payload.mapName(),
                    payload.checkpointIndex(),
                    payload.applyBounds(),
                    payload.preserveSpeed()
            ));
        });
        ServerPlayNetworking.registerGlobalReceiver(SurfStickCancelPayload.ID, (payload, ctx) -> {
            ServerPlayerEntity player = ctx.player();
            if (!payload.cancel()) {
                return;
            }
            ctx.server().execute(() -> SurfRampPlacementManager.cancelSelectionFromGui(player));
        });
        ServerPlayNetworking.registerGlobalReceiver(ZoneStickCancelPayload.ID, (payload, ctx) -> {
            ServerPlayerEntity player = ctx.player();
            if (!payload.cancel()) {
                return;
            }
            ctx.server().execute(() -> ZonePlacementManager.cancelEditing(player));
        });
        ServerPlayNetworking.registerGlobalReceiver(SurfStickDeletePayload.ID, (payload, ctx) -> {
            ServerPlayerEntity player = ctx.player();
            if (!payload.delete()) {
                return;
            }
            ctx.server().execute(() -> SurfRampPlacementManager.deleteEditedRamp(player));
        });
        ServerPlayNetworking.registerGlobalReceiver(ZoneStickDeletePayload.ID, (payload, ctx) -> {
            ServerPlayerEntity player = ctx.player();
            if (!payload.delete()) {
                return;
            }
            ctx.server().execute(() -> ZonePlacementManager.deleteEditedZone(player));
        });
        ServerPlayNetworking.registerGlobalReceiver(SSpecEfficiencyPayload.ID, (payload, ctx) -> {
            ServerPlayerEntity player = ctx.player();
            MinecraftServer server = ctx.server();
            double last_jump_speed =  payload.last_jump_speed();
            int jump_count = (int) payload.jump_count();
            double last_efficiency = payload.last_efficiency();
            ctx.server().execute(() -> {
                if (player == null || !Double.isFinite(last_jump_speed) || !Double.isFinite(last_efficiency)) {
                    return;
                }
                Minehop.lastEfficiencyMap.put(player.getNameForScoreboard(), new ReplayManager.SSJEntry(jump_count, last_jump_speed, last_efficiency));

                if (SpectateCommands.spectatorList.containsKey(player.getNameForScoreboard())) {
                    List<String> spectators = SpectateCommands.spectatorList.get(player.getNameForScoreboard());
                    for (String spectator : spectators) {
                        ServerPlayerEntity spectatorPlayer = server.getPlayerManager().getPlayer(spectator);
                        if (spectatorPlayer != null) {
                            if (!spectatorPlayer.getNameForScoreboard().equals(player.getNameForScoreboard())) {
                                sendSpecEfficiency(spectatorPlayer, last_jump_speed, jump_count, last_efficiency);
                            }
                        }
                    }
                }
            });
        });
    }

}
