package net.nerdorg.minehop.networking;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.netty.buffer.Unpooled;
import net.nerdorg.minehop.platform.Services;
import net.nerdorg.minehop.platform.services.INetworkHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.commands.ReplayCommands;
import net.nerdorg.minehop.spectate.SpectateSessions;
import net.nerdorg.minehop.config.ConfigWrapper;
import net.nerdorg.minehop.config.MinehopConfig;
import net.nerdorg.minehop.data.DataManager;
import net.nerdorg.minehop.discord.DiscordIntegration;
import net.nerdorg.minehop.entity.custom.EndEntity;
import net.nerdorg.minehop.networking.payloads.*;
import net.nerdorg.minehop.replays.ReplayEvents;
import net.nerdorg.minehop.replays.ReplayManager;
import net.nerdorg.minehop.replays.ReplayPathSimplifier;
import net.nerdorg.minehop.replays.RunStats;
import net.nerdorg.minehop.util.Logger;
import net.nerdorg.minehop.util.MapCreationManager;
import net.nerdorg.minehop.util.PacketRateLimiter;
import net.nerdorg.minehop.util.SurfRampPlacementManager;
import net.nerdorg.minehop.util.ZonePlacementManager;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class PacketHandler {
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private static boolean registered = false;
    public static final int MAX_BUFF_CHARS = 24_000;
    // L1 anti-cheat: max allowed divergence (seconds) between the client's sub-tick wall-clock run
    // time and the server's own nano-stamp span (finishTimeManager - timerManager). The client keeps
    // its framerate-precise time as the RECORDED value; the server only rejects times that drift
    // beyond this (covers tick-phase + network; a fake low time is off by whole seconds).
    public static final double TIMER_VALIDATION_TOLERANCE_SECONDS = 0.30D;

    public static void register() {
        registerC2S();
        registerS2C();
    }

    private static void registerS2C() {
        // server to client
        Services.NETWORK.registerPayloadS2C(AntiCheatPayload.ID, AntiCheatPayload.CODEC);
        Services.NETWORK.registerPayloadS2C(BoundsStickSelectionPayload.ID, BoundsStickSelectionPayload.CODEC);
        Services.NETWORK.registerPayloadS2C(ConfigSyncPayload.ID, ConfigSyncPayload.CODEC);
        Services.NETWORK.registerPayloadS2C(CSpecEfficiencyPayload.ID, CSpecEfficiencyPayload.CODEC);
        Services.NETWORK.registerPayloadS2C(HandshakeIDPayload.ID, HandshakeIDPayload.CODEC);
        Services.NETWORK.registerPayloadS2C(MapFinishPayload.ID, MapFinishPayload.CODEC);
        Services.NETWORK.registerPayloadS2C(OpenMapCreatorScreenPayload.ID, OpenMapCreatorScreenPayload.CODEC);
        Services.NETWORK.registerPayloadS2C(OpenMapScreenPayload.ID, OpenMapScreenPayload.CODEC);
        Services.NETWORK.registerPayloadS2C(OtherVTogglePayload.ID, OtherVTogglePayload.CODEC);
        Services.NETWORK.registerPayloadS2C(ResetVelocityCarryPayload.ID, ResetVelocityCarryPayload.CODEC);
        Services.NETWORK.registerPayloadS2C(ReplayVTogglePayload.ID, ReplayVTogglePayload.CODEC);
        Services.NETWORK.registerPayloadS2C(ReplayPathPayload.ID, ReplayPathPayload.CODEC);
        Services.NETWORK.registerPayloadS2C(RunTimerHudPayload.ID, RunTimerHudPayload.CODEC);
        Services.NETWORK.registerPayloadS2C(SelfVTogglePayload.ID, SelfVTogglePayload.CODEC);
        Services.NETWORK.registerPayloadS2C(SendEfficiencyPayload.ID, SendEfficiencyPayload.CODEC);
        Services.NETWORK.registerPayloadS2C(SendMapPayload.ID, SendMapPayload.CODEC);
        Services.NETWORK.registerPayloadS2C(SendPersonalRecordPayload.ID, SendPersonalRecordPayload.CODEC);
        Services.NETWORK.registerPayloadS2C(SendRecordPayload.ID, SendRecordPayload.CODEC);
        Services.NETWORK.registerPayloadS2C(SendSpectatorsPayload.ID, SendSpectatorsPayload.CODEC);
        Services.NETWORK.registerPayloadS2C(SendTimePayload.ID, SendTimePayload.CODEC);
        Services.NETWORK.registerPayloadS2C(SetCheaterPayload.ID, SetCheaterPayload.CODEC);
        Services.NETWORK.registerPayloadS2C(SSpecEfficiencyPayload.ID, SSpecEfficiencyPayload.CODEC);
        Services.NETWORK.registerPayloadS2C(OpenSurfStickSettingsPayload.ID, OpenSurfStickSettingsPayload.CODEC);
        Services.NETWORK.registerPayloadS2C(OpenZoneStickSettingsPayload.ID, OpenZoneStickSettingsPayload.CODEC);
        Services.NETWORK.registerPayloadS2C(SurfStickPreviewPayload.ID, SurfStickPreviewPayload.CODEC);
        Services.NETWORK.registerPayloadS2C(UpdatePowerPayload.ID, UpdatePowerPayload.CODEC);
        Services.NETWORK.registerPayloadS2C(ZoneSyncIDPayload.ID, ZoneSyncIDPayload.CODEC);
        Services.NETWORK.registerPayloadS2C(OpenAntiCheatScreenPayload.ID, OpenAntiCheatScreenPayload.CODEC);
    }

    private static void registerC2S() {
        // client to server — ONLY payloads the client actually sends. S2C-only payloads were
        // previously also registered C2S, so a malicious client could send those (some with
        // unbounded strings) and have netty decode then drop them. Keep this list = what the client
        // genuinely sends (see ClientPacketHandler / AntiCheatScreen).
        Services.NETWORK.registerPayloadC2S(AntiCheatPayload.ID, AntiCheatPayload.CODEC);
        Services.NETWORK.registerPayloadC2S(HandshakeIDPayload.ID, HandshakeIDPayload.CODEC);
        Services.NETWORK.registerPayloadC2S(MapCreatorActionPayload.ID, MapCreatorActionPayload.CODEC);
        Services.NETWORK.registerPayloadC2S(MapFinishPayload.ID, MapFinishPayload.CODEC);
        Services.NETWORK.registerPayloadC2S(SendTimePayload.ID, SendTimePayload.CODEC);
        Services.NETWORK.registerPayloadC2S(SSpecEfficiencyPayload.ID, SSpecEfficiencyPayload.CODEC);
        Services.NETWORK.registerPayloadC2S(SurfStickCancelPayload.ID, SurfStickCancelPayload.CODEC);
        Services.NETWORK.registerPayloadC2S(SurfStickDeletePayload.ID, SurfStickDeletePayload.CODEC);
        Services.NETWORK.registerPayloadC2S(SurfStickSettingsPayload.ID, SurfStickSettingsPayload.CODEC);
        Services.NETWORK.registerPayloadC2S(ZoneStickCancelPayload.ID, ZoneStickCancelPayload.CODEC);
        Services.NETWORK.registerPayloadC2S(ZoneStickDeletePayload.ID, ZoneStickDeletePayload.CODEC);
        Services.NETWORK.registerPayloadC2S(ZoneStickSettingsPayload.ID, ZoneStickSettingsPayload.CODEC);
        Services.NETWORK.registerPayloadC2S(AntiCheatActionPayload.ID, AntiCheatActionPayload.CODEC);
    }

    public static void sendConfigToClient(ServerPlayer player, MinehopConfig config) {
        MinehopConfig effectiveConfig = ConfigWrapper.getEffectiveConfig(player);
        DataManager.MapData currentMap = ConfigWrapper.resolveEffectiveMap(player);
        Services.NETWORK.sendToPlayer(player,  new ConfigSyncPayload(
            effectiveConfig.movement.sv_friction,
            effectiveConfig.movement.sv_accelerate,
            effectiveConfig.movement.sv_airaccelerate,
            effectiveConfig.movement.sv_maxairspeed,
            effectiveConfig.movement.sv_jump_impulse,
            effectiveConfig.movement.speed_mul,
            effectiveConfig.movement.sv_gravity,
            effectiveConfig.movement.speed_coefficient,
            ConfigWrapper.resolveSpeedCap(player),
            effectiveConfig.movement.auto_step_up,
            effectiveConfig.movement.css_crouch_jump,
            currentMap != null && currentMap.hns,
            currentMap != null && currentMap.kz,
            effectiveConfig.enabled,
            effectiveConfig.fall_damage,
            effectiveConfig.movement.sv_stopspeed,
            effectiveConfig.movement.disable_sprint
        ));
    }
    public static void updateZone(ServerPlayer player, int entityId, BlockPos pos1, BlockPos pos2, String name, int check_index) {
        Services.NETWORK.sendToPlayer(player,  new ZoneSyncIDPayload(
                entityId,
                new Vector3f(pos1.getX(), pos1.getY(), pos1.getZ()),
                new Vector3f(pos2.getX(), pos2.getY(), pos2.getZ()),
                name,
                check_index
        ));
    }

    public static void sendSelfVToggle(ServerPlayer player) {
        Services.NETWORK.sendToPlayer(player, new SelfVTogglePayload(true));
    }

    public static void sendBoundsStickSelection(ServerPlayer player, BlockPos first, BlockPos second) {
        if (player == null) {
            return;
        }
        Services.NETWORK.sendToPlayer(
                player,
                new BoundsStickSelectionPayload(
                        first != null,
                        first == null ? BlockPos.ZERO : first,
                        second != null,
                        second == null ? BlockPos.ZERO : second
                )
        );
    }

    public static void sendOtherVToggle(ServerPlayer player) {
        Services.NETWORK.sendToPlayer(player,  new OtherVTogglePayload(true));
    }

    public static void sendReplayVToggle(ServerPlayer player) {
        Services.NETWORK.sendToPlayer(player,  new ReplayVTogglePayload(true));
    }

    public static void clearReplayPath(ServerPlayer player) {
        if (player == null) {
            return;
        }
        Services.NETWORK.sendToPlayer(player, new ReplayPathPayload(true, List.of()));
    }

    /**
     * Sends a replay's route: at most {@link ReplayPathSimplifier#MAX_POINTS} points (two packets), simplified and
     * split at teleports (see ReplayPathSimplifier). Returns the number of points sent.
     */
    public static int sendReplayPath(ServerPlayer player, List<ReplayManager.ReplayEntry> entries) {
        if (player == null || entries == null || entries.size() < 2) {
            clearReplayPath(player);
            return 0;
        }

        List<Vector3f> chunk = new ArrayList<>(ReplayPathPayload.MAX_POINTS_PER_PACKET);
        boolean firstPacket = true;
        int sent = 0;
        for (Vector3f point : ReplayPathSimplifier.simplify(entries)) {
            chunk.add(point);
            if (chunk.size() >= ReplayPathPayload.MAX_POINTS_PER_PACKET) {
                Services.NETWORK.sendToPlayer(player, new ReplayPathPayload(firstPacket, List.copyOf(chunk)));
                firstPacket = false;
                sent += chunk.size();
                chunk.clear();
            }
        }

        if (!chunk.isEmpty() || firstPacket) {
            Services.NETWORK.sendToPlayer(player, new ReplayPathPayload(firstPacket, List.copyOf(chunk)));
            sent += chunk.size();
        }
        return sent;
    }

    public static void sendEfficiency(ServerPlayer player, double efficiency) {
        Services.NETWORK.sendToPlayer(player,  new SendEfficiencyPayload(efficiency));
    }

    /**
     * Tells a spectated player who is watching them (sent by SpectateSessions whenever the list changes; an empty
     * list clears it). Format "{count}~{name}~{name}..." with count = names + 1: clients up to 1.1.6 read the names
     * at indexes 1 .. count-1, so this makes them show every name (they dropped the last one, and never showed a
     * single spectator), and "1" makes them clear the list. Newer clients read every name after the count.
     */
    public static void sendSpectatorList(ServerPlayer player, List<String> spectators) {
        if (player == null) {
            return;
        }
        List<String> names = spectators == null ? List.of() : spectators;
        StringBuilder buff = new StringBuilder();
        buff.append(names.size() + 1);
        for (String spectator : names) {
            buff.append('~').append(spectator);
        }
        Services.NETWORK.sendToPlayer(player, new SendSpectatorsPayload(buff.toString()));
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

    private static boolean isInsideZoneBounds(Vec3 playerPos, BlockPos corner1, BlockPos corner2) {
        AABB box = getZoneBoundsBox(corner1, corner2);
        if (playerPos == null || box == null) {
            return false;
        }
        return box.contains(playerPos);
    }

    private static AABB getZoneBoundsBox(BlockPos corner1, BlockPos corner2) {
        if (corner1 == null || corner2 == null) {
            return null;
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
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static boolean isPlayerInsideMatchingEndZone(ServerPlayer player, String mapName) {
        if (player == null || mapName == null || mapName.isBlank()) {
            return false;
        }
        return isPositionInsideMatchingEndZone(player, mapName, player.position());
    }

    private static boolean isPositionInsideMatchingEndZone(ServerPlayer player, String mapName, Vec3 pos) {
        if (player == null || mapName == null || mapName.isBlank() || pos == null) {
            return false;
        }
        for (net.minecraft.world.entity.Entity entity : player.level().getAllEntities()) {
            if (!(entity instanceof EndEntity endEntity)) {
                continue;
            }
            if (!mapName.equals(endEntity.getPairedMap())) {
                continue;
            }
            if (isInsideZoneBounds(pos, endEntity.getCorner1(), endEntity.getCorner2())) {
                return true;
            }
        }
        return false;
    }

    private static boolean didServerMovementIntersectMatchingEndZone(ServerPlayer player, String mapName) {
        if (player == null || mapName == null || mapName.isBlank()) {
            return false;
        }
        Vec3 previousPos = new Vec3(player.xo, player.yo, player.zo);
        Vec3 currentPos = player.position();
        for (net.minecraft.world.entity.Entity entity : player.level().getAllEntities()) {
            if (!(entity instanceof EndEntity endEntity)) {
                continue;
            }
            if (!mapName.equals(endEntity.getPairedMap())) {
                continue;
            }
            AABB box = getZoneBoundsBox(endEntity.getCorner1(), endEntity.getCorner2());
            if (box != null && segmentIntersectsBox(box, previousPos, currentPos)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isPlausibleClientFinishPosition(ServerPlayer player, Vec3 clientFinishPos) {
        if (player == null || clientFinishPos == null) {
            return false;
        }
        if (!Double.isFinite(clientFinishPos.x) || !Double.isFinite(clientFinishPos.y) || !Double.isFinite(clientFinishPos.z)) {
            return false;
        }
        Vec3 serverPos = player.position();
        Vec3 previousPos = new Vec3(player.xo, player.yo, player.zo);
        double allowedDistance = allowedClientFinishDistance(player);
        double allowedDistanceSq = allowedDistance * allowedDistance;
        return clientFinishPos.distanceToSqr(serverPos) <= allowedDistanceSq
                || clientFinishPos.distanceToSqr(previousPos) <= allowedDistanceSq;
    }

    private static double allowedClientFinishDistance(ServerPlayer player) {
        int latencyMs = player == null || player.connection == null ? 0 : Math.max(0, player.connection.latency());
        double latencyTicks = Math.min(20.0D, (latencyMs / 50.0D) + 3.0D);
        Vec3 velocity = player == null ? Vec3.ZERO : player.getDeltaMovement();
        double speedPerTick = velocity == null ? 0.0D : Math.sqrt((velocity.x * velocity.x) + (velocity.y * velocity.y) + (velocity.z * velocity.z));
        if (!Double.isFinite(speedPerTick)) {
            speedPerTick = 0.0D;
        }
        return Mth.clamp(Math.max(2.0D, speedPerTick * latencyTicks) + 2.0D, 2.0D, 64.0D);
    }

    private static boolean segmentIntersectsBox(AABB box, Vec3 start, Vec3 end) {
        return Double.isFinite(segmentEntryFraction(box, start, end));
    }

    private static double segmentEntryFraction(AABB box, Vec3 start, Vec3 end) {
        if (box == null || start == null || end == null) {
            return Double.NaN;
        }
        if (box.contains(start) || box.contains(end)) {
            return 0.0D;
        }
        double tMin = 0.0D;
        double tMax = 1.0D;
        double[] startValues = {start.x, start.y, start.z};
        double[] deltas = {end.x - start.x, end.y - start.y, end.z - start.z};
        double[] mins = {box.minX, box.minY, box.minZ};
        double[] maxs = {box.maxX, box.maxY, box.maxZ};

        for (int i = 0; i < 3; i++) {
            double delta = deltas[i];
            if (Math.abs(delta) < 1.0E-12D) {
                if (startValues[i] < mins[i] || startValues[i] >= maxs[i]) {
                    return Double.NaN;
                }
                continue;
            }
            double invDelta = 1.0D / delta;
            double t1 = (mins[i] - startValues[i]) * invDelta;
            double t2 = (maxs[i] - startValues[i]) * invDelta;
            if (t1 > t2) {
                double swap = t1;
                t1 = t2;
                t2 = swap;
            }
            tMin = Math.max(tMin, t1);
            tMax = Math.min(tMax, t2);
            if (tMin > tMax) {
                return Double.NaN;
            }
        }
        return tMin >= 0.0D && tMin <= 1.0D ? tMin : Double.NaN;
    }

    private static boolean isValidClientFinishAtMatchingEndZone(ServerPlayer player, String mapName, Vec3 clientFinishPos) {
        return isPositionInsideMatchingEndZone(player, mapName, clientFinishPos)
                && isPlausibleClientFinishPosition(player, clientFinishPos);
    }

    private static void clearRunTimerHudForRunnerAndSpectators(ServerPlayer runner, MinecraftServer server) {
        if (runner == null) {
            return;
        }
        clearRunTimerHud(runner);
        if (server == null) {
            return;
        }
        for (ServerPlayer spectator : SpectateSessions.spectatorsOf(runner)) {
            clearRunTimerHud(spectator);
        }
    }

    /**
     * Ends the player's run without saving anything: timer, run bookkeeping and the replay recording. Used when the
     * player disconnects or joins, so a run (and its recording) can't continue across sessions.
     */
    public static void clearRunState(ServerPlayer player, MinecraftServer server) {
        if (player == null) {
            return;
        }
        String playerName = player.getScoreboardName();
        if (!Minehop.timerManager.containsKey(playerName) && !Minehop.finishTimeManager.containsKey(playerName)
                && !ReplayEvents.replayEntryMap.containsKey(playerName)) {
            return;
        }
        clearFinishedRunState(player, server);
    }

    private static void clearFinishedRunState(ServerPlayer player, MinecraftServer server) {
        if (player == null) {
            return;
        }
        String playerName = player.getScoreboardName();
        Minehop.timerManager.remove(playerName);
        Minehop.finishTimeManager.remove(playerName);
        Minehop.runSignatureManager.remove(playerName);
        Minehop.runStartClientTicks.remove(playerName);
        Minehop.runFinishClientTicks.remove(playerName);
        ReplayEvents.replayEntryMap.remove(playerName);
        clearRunTimerHudForRunnerAndSpectators(player, server);
    }

    private static void handleMapCompletion(ServerPlayer player, MinecraftServer server, String mapName, double time, Vec3 clientFinishPos) {
        if (player == null || server == null) {
            return;
        }
        if (player.isCreative() || player.isSpectator()) {
            clearFinishedRunState(player, server);
            return;
        }
        if (net.nerdorg.minehop.data.LeaderboardIntegrity.isBanned(player)) {
            Logger.logFailure(player, "You are banned from the leaderboards; this run was not recorded.");
            clearFinishedRunState(player, server);
            return;
        }
        HashMap<String, Long> timerMap = Minehop.timerManager.get(player.getScoreboardName());
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
            Logger.logServer(server, "Rejected map finish from " + player.getScoreboardName() + " due to map mismatch (" + mapName + " vs " + activeMapName + ").");
            clearFinishedRunState(player, server);
            return;
        }
        if (DataManager.getMap(activeMapName) == null) {
            Logger.logServer(server, "Rejected map finish from " + player.getScoreboardName() + " because active map " + activeMapName + " no longer exists.");
            clearFinishedRunState(player, server);
            return;
        }

        if (!timerMap.containsKey(activeMapName)) {
            clearFinishedRunState(player, server);
            return;
        }
        if (!Double.isFinite(time) || time <= 0.0D) {
            Logger.logServer(server, "Rejected map finish from " + player.getScoreboardName() + " due to invalid time value.");
            clearFinishedRunState(player, server);
            return;
        }
        // The server stamps end-zone entry in EndEntity.tick (finishTimeManager). Accept the finish
        // if that stamp exists OR the player is still inside the zone now — a fast bhop/surf finish
        // can carry the player THROUGH the thin end zone before this packet is processed, which made
        // the "currently inside" check falsely reject legit runs.
        HashMap<String, Long> finishMap = Minehop.finishTimeManager.get(player.getScoreboardName());
        Long finishStamp = finishMap == null ? null : finishMap.get(activeMapName);
        boolean validFinishPosition = finishStamp != null
                || isPlayerInsideMatchingEndZone(player, activeMapName)
                || didServerMovementIntersectMatchingEndZone(player, activeMapName)
                || isValidClientFinishAtMatchingEndZone(player, activeMapName, clientFinishPos);
        if (!validFinishPosition) {
            Logger.logServer(server, "Rejected map finish from " + player.getScoreboardName() + " because they were not inside an end zone for " + activeMapName + ".");
            clearFinishedRunState(player, server);
            return;
        }

        // L1: validate the client-reported time against the server's own nano-stamp span. We KEEP the
        // client time (it samples the finish crossing at framerate via interpolation -> sub-tick
        // precision the 20Hz server can't match) but reject it if it diverges beyond tolerance.
        // timerManager[map] = launch nanoTime (StartEntity), finishTimeManager[map] = first end-zone
        // crossing nanoTime (EndEntity); their difference is continuous nanoseconds, not 0.05-quantized.
        Long startStamp = timerMap.get(activeMapName);
        if (startStamp != null) {
            boolean haveServerFinish = finishStamp != null;
            long endNanos = haveServerFinish ? finishStamp : System.nanoTime();
            double serverSpan = (endNanos - startStamp) / 1_000_000_000.0D;
            // Without a precise server finish stamp (ultra-fast pass-through caught by movement-
            // intersect / client-pos fallback), endNanos = now includes packet latency, so widen.
            double tolerance = haveServerFinish
                    ? TIMER_VALIDATION_TOLERANCE_SECONDS
                    : TIMER_VALIDATION_TOLERANCE_SECONDS + 0.25D;
            if (!Double.isFinite(serverSpan) || serverSpan <= 0.0D || Math.abs(time - serverSpan) > tolerance) {
                Logger.logServer(server, "Rejected map finish from " + player.getScoreboardName()
                        + " on " + activeMapName + ": client time " + String.format("%.5f", time)
                        + "s diverges from server-measured " + String.format("%.5f", serverSpan)
                        + "s (tolerance " + String.format("%.2f", tolerance) + "s).");
                clearFinishedRunState(player, server);
                return;
            }
            // The run can't contain more client ticks than the real time it took allows. Catches a
            // timer cheat that banked lag credit before launch and spent it during the run (the
            // timer check alone can't see that, and the client simply reports the server span).
            Long startTicks = Minehop.runStartClientTicks.get(player.getScoreboardName());
            Long endTicks = haveServerFinish
                    ? Minehop.runFinishClientTicks.get(player.getScoreboardName())
                    : Long.valueOf(net.nerdorg.minehop.anticheat.stream.MovementValidator.clientTicks(player));
            if (startTicks != null && endTicks != null && startTicks >= 0L && endTicks >= startTicks) {
                double secondsPerTick = server.tickRateManager().millisecondsPerTick() / 1000.0D;
                long runTicks = endTicks - startTicks;
                double maxTicks = (serverSpan + tolerance) / secondsPerTick + 2.0D;
                if (runTicks > maxTicks) {
                    Logger.logServer(server, "Rejected map finish from " + player.getScoreboardName()
                            + " on " + activeMapName + ": " + runTicks + " client ticks in "
                            + String.format("%.3f", serverSpan) + "s of real time (max "
                            + String.format("%.0f", maxTicks) + ") — client ran faster than real time.");
                    net.nerdorg.minehop.anticheat.AntiCheatManager.reportMovementViolation(player,
                            net.nerdorg.minehop.anticheat.stream.MovementValidator.CHECK_TIMER, 2.0D,
                            "runTicks=" + runTicks + " maxForRealTime=" + String.format("%.0f", maxTicks), false, null);
                    clearFinishedRunState(player, server);
                    return;
                }
            }
        }

        // L6: reject if the map's physics/geometry changed (or it was edited) after this run started.
        Long runSignature = Minehop.runSignatureManager.get(player.getScoreboardName());
        if (runSignature != null) {
            long currentSignature = DataManager.computeRunSignature(DataManager.getMap(activeMapName));
            if (currentSignature != runSignature) {
                Logger.logServer(server, "Rejected map finish from " + player.getScoreboardName()
                        + " on " + activeMapName + ": map was modified mid-run.");
                clearFinishedRunState(player, server);
                return;
            }
        }

        String formattedNumber = String.format("%.5f", time);
        String playerName = player.getScoreboardName();
        String playerUuid = player.getStringUUID();
        // Evidence for later review: anticheat flags raised during this run travel with its replay,
        // PB and WR rows (see /map manage history and /map manage flagged).
        String acFlags = net.nerdorg.minehop.anticheat.AntiCheatManager.runFlagSummary(player);
        if (!acFlags.isEmpty()) {
            net.nerdorg.minehop.anticheat.AntiCheatManager.announceFlaggedRun(player, activeMapName, time, acFlags);
        }
        List<ReplayManager.ReplayEntry> replayEntries = ReplayEvents.replayEntryMap.get(playerName);
        if (replayEntries != null && replayEntries.size() >= ReplayEvents.MAX_RECORDED_FRAMES) {
            Minehop.LOGGER.info("Not saving a replay of {}'s {} run on {}: longer than the recording cap", playerName, formattedNumber, activeMapName);
        } else if (replayEntries != null && !replayEntries.isEmpty()) {
            ReplayManager.Replay replay = new ReplayManager.Replay(
                    activeMapName,
                    playerName,
                    playerUuid,
                    time,
                    ReplayManager.copyReplayEntries(replayEntries)
            );
            replay.ac_flags = acFlags;
            ReplayManager.saveReplay(player.level(), replay);
        }

        DataManager.RecordData existingPersonalRecord = DataManager.getPersonalRecord(playerName, playerUuid, activeMapName);
        boolean isNewPersonalRecord = existingPersonalRecord == null || time < existingPersonalRecord.time;
        if (isNewPersonalRecord) {
            if (existingPersonalRecord != null) {
                Logger.logSuccess(player, "You just beat your time (" + String.format("%.5f", existingPersonalRecord.time) + ") on " + existingPersonalRecord.map_name + ", your new record is " + formattedNumber + "!");
            } else {
                Logger.logSuccess(player, "You just claimed a personal record of " + formattedNumber + "!");
            }

            DataManager.upsertPersonalRecord(playerName, playerUuid, activeMapName, time, acFlags);
        }

        // L4: plot (user) maps must NOT feed the global competitive pipeline. Their owner can tune
        // physics freely, so their times stay local to that map — never a global broadcast, Discord
        // post, or LuckPerms record_holder grant. Only real server maps do those.
        DataManager.MapData finishedMap = DataManager.getMap(activeMapName);
        boolean isPlotMap = finishedMap != null && finishedMap.userMap;

        DataManager.RecordData existingRecord = DataManager.getRecord(activeMapName);
        boolean newWorldRecord = existingRecord == null || time < existingRecord.time;
        if (newWorldRecord) {
            String previousHolder = existingRecord == null ? "" : existingRecord.name;
            String previousHolderUuid = existingRecord == null || existingRecord.uuid == null ? "" : existingRecord.uuid;
            double previousTime = existingRecord == null ? 0.0D : existingRecord.time;
            boolean firstWorldRecord = existingRecord == null;

            DataManager.upsertRecord(playerName, playerUuid, activeMapName, time, acFlags);

            String recordMessage;
            if (!previousHolder.isBlank()) {
                recordMessage = playerName + " just beat " + previousHolder + "'s time (" + String.format("%.5f", previousTime) + ") on " + activeMapName + " and now hold the world record with a time of " + formattedNumber + "!";
            } else {
                recordMessage = playerName + " just claimed the world record on " + activeMapName + " with a time of " + formattedNumber + "!";
            }

            if (isPlotMap) {
                // Local-only acknowledgement; no global broadcast / Discord / LuckPerms rank.
                Logger.logSuccess(player, "New record on plot map " + activeMapName + ": " + formattedNumber + "!");
            } else {
                // L3: grant/revoke record_holder by UUID (survives name changes); legacy records
                // without a stored UUID fall back to the old safe name-based path.
                boolean previousIsDifferent = !previousHolderUuid.isBlank()
                        ? !previousHolderUuid.equals(playerUuid)
                        : (!previousHolder.isBlank() && !previousHolder.equals(playerName));
                if (previousIsDifferent) {
                    // Only WRs on real (existing, non-plot) maps keep the rank.
                    boolean stillHolds = net.nerdorg.minehop.data.LeaderboardIntegrity.holdsCompetitiveRecord(
                            new net.nerdorg.minehop.data.LeaderboardIntegrity.Target(previousHolderUuid, previousHolder));
                    if (!stillHolds) {
                        if (!previousHolderUuid.isBlank() && isUuid(previousHolderUuid)) {
                            runConsoleCommand(server, "lp user " + previousHolderUuid + " parent remove record_holder");
                        } else if (previousHolderUuid.isBlank() && isSafeMinecraftPlayerName(previousHolder)) {
                            runConsoleCommand(server, "lp user " + previousHolder + " parent remove record_holder");
                        }
                    }
                }
                if (isUuid(playerUuid)) {
                    runConsoleCommand(server, "lp user " + playerUuid + " parent add record_holder");
                }
                Logger.logGlobal(server, recordMessage);
                DiscordIntegration.sendRecordToDiscord(recordMessage);
            }
            ReplayCommands.ensureWorldRecordReplayEntity(server, activeMapName);
        }

        // L5: commit as a unit. All in-memory mutations (replay add, PB/WR upserts, rank) are done
        // above; persist the affected files together at the end so the on-disk state transitions in
        // one batch. Each save is atomic (JsonStorage), and the in-memory lists remain the consistent
        // source of truth, so a single failed write self-heals on the next autosave/shutdown.
        if (isNewPersonalRecord) {
            DataManager.saveData(player.level(), DataManager.pbListLocation, Minehop.personalRecordList);
        }
        if (newWorldRecord) {
            DataManager.saveData(player.level(), DataManager.recordsListLocation, Minehop.recordList);
        }

        Logger.logSuccess(player, "Completed " + activeMapName + " in " + formattedNumber + " seconds.");
        clearFinishedRunState(player, server);
    }

    private static boolean isSafeMinecraftPlayerName(String playerName) {
        return playerName != null && playerName.matches("[A-Za-z0-9_]{1,16}");
    }

    private static boolean isUuid(String value) {
        return value != null && value.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    }

    private static void runConsoleCommand(MinecraftServer server, String command) {
        server.getCommands().performCommand(
                server.getCommands().getDispatcher().parse(command, server.createCommandSourceStack()),
                command
        );
    }

    public static void sendSpecEfficiency(ServerPlayer player, double last_jump_speed, int jump_count, double last_efficiency) {
        Services.NETWORK.sendToPlayer(player,  new CSpecEfficiencyPayload(last_jump_speed, jump_count, last_efficiency));
    }

    public static void sendRunTimerHud(ServerPlayer player, float time, float personalBest) {
        if (player == null) {
            return;
        }
        Services.NETWORK.sendToPlayer(player, new RunTimerHudPayload(true, time, personalBest));
    }

    public static void clearRunTimerHud(ServerPlayer player) {
        if (player == null) {
            return;
        }
        Services.NETWORK.sendToPlayer(player, new RunTimerHudPayload(false, 0.0F, 0.0F));
    }

    public static void sendResetVelocityCarry(ServerPlayer player, Vec3 velocity, int ticks) {
        if (player == null || velocity == null || ticks <= 0) {
            return;
        }
        if (!Double.isFinite(velocity.x) || !Double.isFinite(velocity.y) || !Double.isFinite(velocity.z)) {
            return;
        }
        Services.NETWORK.sendToPlayer(
                player,
                new ResetVelocityCarryPayload(
                        (float) velocity.x,
                        (float) velocity.y,
                        (float) velocity.z,
                        Math.max(1, ticks)
                )
        );
    }

    public static void sendOpenMapScreen(ServerPlayer player, String title) {
        Services.NETWORK.sendToPlayer(player,  new OpenMapScreenPayload(title));
    }

    public static void sendOpenMapCreatorScreen(
            ServerPlayer player,
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
            double movementSpeedCap,
            boolean movementAutoStepUp,
            boolean movementCssCrouchJump,
            boolean movementDisableSprint,
            boolean movementFallDamage,
            int checkpointIndex
    ) {
        Services.NETWORK.sendToPlayer(
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
                        movementSpeedCap,
                        movementAutoStepUp,
                        movementCssCrouchJump,
                        movementDisableSprint,
                        movementFallDamage,
                        checkpointIndex
                )
        );
    }

    public static void sendMaps(ServerPlayer player) {
        Services.NETWORK.sendToPlayer(player, new SendMapPayload("#RESET"));

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
            buff += mapData.movement_speed_cap;buff += "~";
            buff += mapData.movement_auto_step_up;buff += "~";
            buff += mapData.movement_css_crouch_jump;buff += "~";
            buff += mapData.movement_fall_damage;buff += "~";
            buff += mapData.movement_disable_sprint;

            Services.NETWORK.sendToPlayer(player,  new SendMapPayload(buff));
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

    public static void sendRecords(ServerPlayer player) {
        Services.NETWORK.sendToPlayer(player, new SendRecordPayload("#RESET"));

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
                Services.NETWORK.sendToPlayer(player, new SendRecordPayload(sb.toString()));
                sb.setLength(0);
            }
            sb.append(line);
        }

        if (sb.length() > 0) {
            Services.NETWORK.sendToPlayer(player, new SendRecordPayload(sb.toString()));
        }
    }

    public static void sendPersonalRecords(ServerPlayer player) {
        Services.NETWORK.sendToPlayer(player, new SendPersonalRecordPayload("#RESET"));

        StringBuilder sb = new StringBuilder(8192);

        for (DataManager.RecordData rd : Minehop.personalRecordList) {
            String line = rd.map_name + "~" + rd.name + "~" + rd.time + "\n";

            if (sb.length() + line.length() > MAX_BUFF_CHARS) {
                Services.NETWORK.sendToPlayer(player, new SendPersonalRecordPayload(sb.toString()));
                sb.setLength(0);
            }
            sb.append(line);
        }

        if (!sb.isEmpty()) {
            Services.NETWORK.sendToPlayer(player, new SendPersonalRecordPayload(sb.toString()));
        }
    }

    public static void sendPower(ServerPlayer player, double x_power, double y_power, double z_power, BlockPos boosterPos) {
        Services.NETWORK.sendToPlayer(player,  new UpdatePowerPayload(x_power, y_power, z_power, boosterPos.getX(), boosterPos.getY(), boosterPos.getZ()));
    }

    public static void sendSurfStickPreview(
            ServerPlayer player,
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
                    immutablePoints.add(point.immutable());
                }
            }
        }
        Services.NETWORK.sendToPlayer(
                player,
                new SurfStickPreviewPayload(false, (float) width, (float) drop, oneSided, outsideCurve, immutablePoints)
        );
    }

    public static void clearSurfStickPreview(ServerPlayer player) {
        if (player == null) {
            return;
        }
        Services.NETWORK.sendToPlayer(player, new SurfStickPreviewPayload(true, 0.0F, 0.0F, false, false, List.of()));
    }

    public static void openSurfStickSettings(
            ServerPlayer player,
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
        Services.NETWORK.sendToPlayer(
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
            ServerPlayer player,
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
        Services.NETWORK.sendToPlayer(
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


    // Per-channel minimum intervals (ms) for C2S throttling. FAST = once-per-client-tick streams,
    // GUI = occasional GUI actions, FINISH = rare run finishes.
    private static final int RL_FAST = 40;
    private static final int RL_GUI = 100;
    private static final int RL_FINISH = 200;

    /** Register a C2S receiver that drops packets exceeding the per-player rate for its channel. */
    private static <T extends CustomPacketPayload> void registerLimited(CustomPacketPayload.Type<T> id, int minIntervalMs, INetworkHelper.ServerPayloadHandler<T> handler) {
        Services.NETWORK.registerServerReceiver(id, (payload, ctx) -> {
            if (!PacketRateLimiter.allow(ctx.player(), id.id().toString(), minIntervalMs)) {
                return;
            }
            handler.receive(payload, ctx);
        });
    }

    public static void registerReceivers() {
        if (registered) {return;}
        registered = true;

        net.nerdorg.minehop.commands.AntiCheatCommands.wireServerHandlers();

        registerLimited(SendTimePayload.ID, RL_FAST, (payload, ctx) -> {
            ServerPlayer player = ctx.player();
            MinecraftServer server = ctx.server();
            float time = payload.time();
            ctx.server().execute(() -> {
                if (player == null || player.isSpectator() || !Float.isFinite(time)) {
                    return;
                }
                HashMap<String, Long> timerMap = Minehop.timerManager.get(player.getScoreboardName());
                String mapName = resolveActiveMapName(timerMap);
                if (mapName == null) {
                    clearRunTimerHudForRunnerAndSpectators(player, server);
                    return;
                }
                DataManager.RecordData personalRecordData = DataManager.getPersonalRecord(player.getScoreboardName(), player.getStringUUID(), mapName);
                double personalRecord = 0;
                if (personalRecordData != null) {
                    personalRecord = personalRecordData.time;
                }
                float safeTime = Math.max(0.0F, time);
                float safePb = (float) Math.max(0.0D, personalRecord);
                // Spectators see the runner's timer. (Their camera and position are kept by SpectateSessions.)
                for (ServerPlayer spectator : SpectateSessions.spectatorsOf(player)) {
                    sendRunTimerHud(spectator, safeTime, safePb);
                }
                sendRunTimerHud(player, safeTime, safePb);
            });
        });
        registerLimited(MapFinishPayload.ID, RL_FINISH, (payload, ctx) -> {
            ServerPlayer player = ctx.player();
            MinecraftServer server = ctx.server();
            String mapName = payload.map_name();
            double time = payload.time();
            Vec3 finishPos = new Vec3(payload.x(), payload.y(), payload.z());
            ctx.server().execute(() -> handleMapCompletion(player, server, mapName, time, finishPos));
        });
        registerLimited(MapCreatorActionPayload.ID, RL_GUI, (payload, ctx) -> {
            ServerPlayer player = ctx.player();
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
                    payload.movementSpeedCap(),
                    payload.movementAutoStepUp(),
                    payload.movementCssCrouchJump(),
                    payload.movementDisableSprint(),
                    payload.movementFallDamage(),
                    payload.checkpointIndex()
            ));
        });
        registerLimited(SurfStickSettingsPayload.ID, RL_GUI, (payload, ctx) -> {
            ServerPlayer player = ctx.player();
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
        registerLimited(ZoneStickSettingsPayload.ID, RL_GUI, (payload, ctx) -> {
            ServerPlayer player = ctx.player();
            ctx.server().execute(() -> ZonePlacementManager.applyOptionsFromGui(
                    player,
                    payload.mapName(),
                    payload.checkpointIndex(),
                    payload.applyBounds(),
                    payload.preserveSpeed()
            ));
        });
        registerLimited(SurfStickCancelPayload.ID, RL_GUI, (payload, ctx) -> {
            ServerPlayer player = ctx.player();
            if (!payload.cancel()) {
                return;
            }
            ctx.server().execute(() -> SurfRampPlacementManager.cancelSelectionFromGui(player));
        });
        registerLimited(ZoneStickCancelPayload.ID, RL_GUI, (payload, ctx) -> {
            ServerPlayer player = ctx.player();
            if (!payload.cancel()) {
                return;
            }
            ctx.server().execute(() -> ZonePlacementManager.cancelEditing(player));
        });
        registerLimited(SurfStickDeletePayload.ID, RL_GUI, (payload, ctx) -> {
            ServerPlayer player = ctx.player();
            if (!payload.delete()) {
                return;
            }
            ctx.server().execute(() -> SurfRampPlacementManager.deleteEditedRamp(player));
        });
        registerLimited(ZoneStickDeletePayload.ID, RL_GUI, (payload, ctx) -> {
            ServerPlayer player = ctx.player();
            if (!payload.delete()) {
                return;
            }
            ctx.server().execute(() -> ZonePlacementManager.deleteEditedZone(player));
        });
        // A client's answer to an efficiency request (RunStats). Its jump count/speed are ignored: the server derives
        // those from the accepted movement. Unrequested stats are dropped, so a client can't feed made-up values into
        // its replay or its spectators' HUD.
        registerLimited(SSpecEfficiencyPayload.ID, RL_FAST, (payload, ctx) -> {
            ServerPlayer player = ctx.player();
            double last_efficiency = payload.last_efficiency();
            ctx.server().execute(() -> RunStats.onReply(player, last_efficiency));
        });
    }

}
