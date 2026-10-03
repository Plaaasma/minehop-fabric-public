package net.nerdorg.minehop.anticheat;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import net.minecraft.entity.player.PlayerPosition;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.anticheat.checks.NoClipCheck;
import net.nerdorg.minehop.anticheat.stream.MovementValidator;
import net.nerdorg.minehop.config.ConfigWrapper;
import net.nerdorg.minehop.config.MinehopConfig;
import net.nerdorg.minehop.util.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AntiCheatManager {
    private static final double DEFAULT_VIOLATION_DECAY = 0.04D;
    private static final int LAGBACK_COOLDOWN_TICKS = 5;
    private static final int VERBOSE_COOLDOWN_TICKS = 6;

    private static final Map<UUID, AntiCheatPlayerState> PLAYER_STATES = new ConcurrentHashMap<>();
    private static final Set<UUID> EXEMPT_PLAYERS = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static final Set<UUID> VERBOSE_LISTENERS = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static final Map<UUID, Long> LAST_VERBOSE_TICK = new ConcurrentHashMap<>();
    private static final List<AntiCheatCheck> CHECKS = new ArrayList<>();
    private static volatile MinecraftServer serverInstance;
    private static volatile boolean enabled = true;
    // Kill switch: when off, every check still flags but nobody is ever lagged back.
    private static volatile boolean lagbacksEnabled = true;
    private static volatile long currentServerTick = 0L;
    private static int autoSaveCountdown = 1200;

    private AntiCheatManager() {
    }

    public static void register() {
        // Movement itself is validated per client tick from the packet stream (MovementValidator).
        // The server-tick checks only see the server's own simulation, which on a dedicated server
        // never receives the client's inputs; NoClip is the one that still says something (flag-only).
        CHECKS.add(new NoClipCheck());

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            serverInstance = server;
            AntiCheatStorage.load(server, PLAYER_STATES, EXEMPT_PLAYERS);
            AntiCheatAllowLog.load(server);
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            AntiCheatStorage.save(server, PLAYER_STATES, EXEMPT_PLAYERS);
            AntiCheatAllowLog.save(server);
            serverInstance = null;
        });
        ServerTickEvents.END_SERVER_TICK.register(AntiCheatManager::onServerTick);
        // Respawn relocates the (recreated) player entity to spawn — an authorized move, or the
        // reconciliation check would flag the death->spawn position jump.
        net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            if (newPlayer != null) {
                markAuthorizedTeleport(newPlayer);
            }
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.player;
            if (player != null) {
                AntiCheatPlayerState state = stateOf(player);
                state.setLastKnownName(player.getNameForScoreboard());
                state.setLastVerifiedPos(player.getPos());
                state.setLastReportedPos(player.getPos());
                state.markAuthorizedTeleport(currentServerTick);
                state.resetAirborneTicks();
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayerEntity player = handler.player;
            if (player != null) {
                VERBOSE_LISTENERS.remove(player.getUuid());
                LAST_VERBOSE_TICK.remove(player.getUuid());
                AntiCheatAllowLog.clearForDisconnect(player.getUuid());
                MovementValidator.clear(player.getUuid());
                clearConsoleFlagThrottle(player.getUuid());
                LAST_AC_STATUS.remove(player.getUuid());
            }
        });
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean enabledValue) {
        enabled = enabledValue;
    }

    public static boolean lagbacksEnabled() {
        return lagbacksEnabled;
    }

    public static void setLagbacksEnabled(boolean value) {
        lagbacksEnabled = value;
    }

    /**
     * Records a violation found by the packet-stream movement checks and, if requested and allowed,
     * lags the player back to {@code target}. Returns true if a lagback teleport was issued.
     */
    public static boolean reportMovementViolation(ServerPlayerEntity player, String checkName, double increment,
                                                  String details, boolean wantLagback, Vec3d target) {
        if (player == null || checkName == null) {
            return false;
        }
        AntiCheatPlayerState state = stateOf(player);
        double level = state.addViolation(checkName, increment);
        Vec3d pos = player.getPos();
        state.addFlag(new AntiCheatFlag(checkName, System.currentTimeMillis(), increment, details, pos.x, pos.y, pos.z));
        broadcastFlag(player, checkName, details, level);
        logFlagToConsole(player, checkName, level, 0.0D, details, pos);
        if (!wantLagback || !lagbacksEnabled || target == null || player.networkHandler == null) {
            return false;
        }
        if (!shouldAllowLagback(state)) {
            return false;
        }
        state.markLagback(currentServerTick);
        Minehop.LOGGER.info(String.format(Locale.ROOT,
                "[AC] %s LAGBACK (%s) from=(%.2f,%.2f,%.2f) to=(%.2f,%.2f,%.2f)",
                player.getNameForScoreboard(), checkName, pos.x, pos.y, pos.z, target.x, target.y, target.z));
        MovementValidator.onLagbackIssued(player);
        player.networkHandler.requestTeleport(
                new PlayerPosition(target, Vec3d.ZERO, player.getYaw(), player.getPitch()), Collections.emptySet());
        player.setVelocity(Vec3d.ZERO);
        int consecutive = state.consecutiveLagbacks();
        if (consecutive == 10 || consecutive == 25 || consecutive == 50 || (consecutive > 50 && consecutive % 50 == 0)) {
            Minehop.LOGGER.warn("[AC] {} has {} consecutive lagbacks (persistent invalid movement)",
                    player.getNameForScoreboard(), consecutive);
        }
        return true;
    }

    /** A run is armed in a start zone: flags counted from here belong to that run. */
    public static void onRunArmed(ServerPlayerEntity player) {
        AntiCheatPlayerState state = stateOf(player);
        if (state != null) {
            state.resetRunFlags();
        }
    }

    /** A run finished with anticheat flags: tell the console and every verbose admin. */
    public static void announceFlaggedRun(ServerPlayerEntity player, String mapName, double time, String flags) {
        String line = String.format(Locale.ROOT, "%s finished %s in %.5fs with anticheat flags: %s",
                player.getNameForScoreboard(), mapName, time, flags);
        Minehop.LOGGER.info("[AC] {}", line);
        if (serverInstance == null) {
            return;
        }
        Text msg = Text.literal("[AC] ").formatted(Formatting.RED)
                .append(Text.literal(line).formatted(Formatting.GOLD));
        for (UUID listenerUuid : new HashSet<>(VERBOSE_LISTENERS)) {
            ServerPlayerEntity listener = serverInstance.getPlayerManager().getPlayer(listenerUuid);
            if (listener != null) {
                listener.sendMessage(msg, false);
            }
        }
    }

    /** Anticheat flags raised since the current run was armed, e.g. "Speed x3, Fly x1"; "" if none. */
    public static String runFlagSummary(ServerPlayerEntity player) {
        AntiCheatPlayerState state = stateOf(player);
        return state == null ? "" : state.runFlagSummary();
    }

    public static AntiCheatPlayerState stateOf(ServerPlayerEntity player) {
        if (player == null) {
            return null;
        }
        return PLAYER_STATES.computeIfAbsent(player.getUuid(), uuid -> {
            AntiCheatPlayerState state = new AntiCheatPlayerState(uuid);
            state.setLastKnownName(player.getNameForScoreboard());
            return state;
        });
    }

    public static AntiCheatPlayerState stateByUuid(UUID uuid) {
        if (uuid == null) {
            return null;
        }
        return PLAYER_STATES.get(uuid);
    }

    public static Map<UUID, AntiCheatPlayerState> allStates() {
        return PLAYER_STATES;
    }

    public static boolean isExempt(ServerPlayerEntity player) {
        if (player == null) {
            return true;
        }
        if (player.hasPermissionLevel(4)) {
            return true;
        }
        if (player.isCreative() || player.isSpectator()) {
            return true;
        }
        return EXEMPT_PLAYERS.contains(player.getUuid());
    }

    private static String exemptReason(ServerPlayerEntity player) {
        if (player.hasPermissionLevel(4)) {
            return "exempt:op4";
        }
        if (player.isCreative()) {
            return "exempt:creative";
        }
        if (player.isSpectator()) {
            return "exempt:spectator";
        }
        if (EXEMPT_PLAYERS.contains(player.getUuid())) {
            return "exempt:list";
        }
        return "checked";
    }

    // Logs once whenever a player switches between being checked and being exempt (and why), so a
    // log with zero flags can be told apart from a log where the player was never evaluated.
    private static final Map<UUID, String> LAST_AC_STATUS = new ConcurrentHashMap<>();

    private static void logStatusTransition(ServerPlayerEntity player) {
        String status = exemptReason(player);
        String previous = LAST_AC_STATUS.put(player.getUuid(), status);
        if (!status.equals(previous)) {
            Minehop.LOGGER.info("[AC] {} status {} -> {}", player.getNameForScoreboard(),
                    previous == null ? "none" : previous, status);
        }
    }

    public static Set<UUID> exemptList() {
        return EXEMPT_PLAYERS;
    }

    public static boolean addExempt(UUID uuid) {
        if (uuid == null) {
            return false;
        }
        boolean added = EXEMPT_PLAYERS.add(uuid);
        if (added) {
            scheduleSave();
        }
        return added;
    }

    public static boolean removeExempt(UUID uuid) {
        if (uuid == null) {
            return false;
        }
        boolean removed = EXEMPT_PLAYERS.remove(uuid);
        if (removed) {
            scheduleSave();
        }
        return removed;
    }

    public static boolean toggleVerbose(ServerPlayerEntity admin) {
        if (admin == null) {
            return false;
        }
        UUID uuid = admin.getUuid();
        if (VERBOSE_LISTENERS.remove(uuid)) {
            return false;
        }
        VERBOSE_LISTENERS.add(uuid);
        return true;
    }

    public static boolean isVerboseListener(UUID uuid) {
        return uuid != null && VERBOSE_LISTENERS.contains(uuid);
    }

    public static long currentServerTick() {
        return currentServerTick;
    }

    public static void markAuthorizedTeleport(ServerPlayerEntity player) {
        if (player == null) {
            return;
        }
        AntiCheatPlayerState state = stateOf(player);
        if (state != null) {
            state.markAuthorizedTeleport(currentServerTick);
            state.setLastVerifiedPos(player.getPos());
            state.setLastReportedPos(player.getPos());
            state.resetAirborneTicks();
            state.resetConsecutiveLagbacks();
        }
    }

    public static void clearFlagsFor(UUID uuid) {
        AntiCheatPlayerState state = PLAYER_STATES.get(uuid);
        if (state != null) {
            state.clearRecentFlags();
        }
        scheduleSave();
    }

    public static void clearAllFlags() {
        for (AntiCheatPlayerState state : PLAYER_STATES.values()) {
            if (state != null) {
                state.clearRecentFlags();
            }
        }
        scheduleSave();
    }

    public static void onMovementTick(
            ServerPlayerEntity player,
            Vec3d preMovePos,
            Vec3d postMovePos,
            Vec3d preMoveVelocity,
            Vec3d postMoveVelocity,
            boolean onGround,
            boolean wasOnGround,
            boolean climbing,
            boolean inFluid,
            boolean surfing,
            boolean jumpThisTick,
            double speedCap
    ) {
        if (!enabled || player == null || preMovePos == null || postMovePos == null) {
            return;
        }
        if (player.getWorld() == null || player.getWorld().isClient) {
            return;
        }
        // Fake players (movement harness, replay stand-ins) are entirely server-driven — there is no
        // client to anticheat, and lagging one back would hit a dummy network handler.
        if (player instanceof net.fabricmc.fabric.api.entity.FakePlayer) {
            return;
        }
        logStatusTransition(player);
        if (isExempt(player)) {
            AntiCheatPlayerState exemptState = stateOf(player);
            if (exemptState != null) {
                exemptState.setLastReportedPos(postMovePos);
                exemptState.setLastVerifiedPos(postMovePos);
                exemptState.setLastTickVelocity(postMoveVelocity == null ? Vec3d.ZERO : postMoveVelocity);
                exemptState.setLastTickTime(currentServerTick);
                // Keep transient counters benign while exempt so leaving creative/exempt can't insta-flag.
                // Do NOT markAuthorizedTeleport here: spamming it every tick made every probe/grace
                // window read "just teleported" for OPs and would mask real divergence data.
                exemptState.resetAirborneTicks();
                exemptState.resetConsecutiveLagbacks();
            }
            return;
        }

        AntiCheatPlayerState state = stateOf(player);
        if (state == null) {
            return;
        }
        state.setLastKnownName(player.getNameForScoreboard());

        MinehopConfig config = ConfigWrapper.config;
        boolean usingPlotCreative = player.isCreative();
        boolean creativeOrSpectator = player.isCreative() || player.isSpectator();
        // M3: teleport grace is granted ONLY by a server-initiated teleport (markAuthorizedTeleport,
        // called from ZoneUtil teleports + JOIN). The old self-grants — a >4-tick packet gap, or a
        // >64-block reported-position jump — let a client lag-switch or blink to earn a free unchecked
        // tick. Every check operates on travel's movedDelta (bounded by physics regardless of teleports
        // or server lag), so removing the self-grants does not false-flag legit teleports or lag spikes.
        long authorizedTeleportTick = state.lastAuthorizedTeleportTick();
        boolean justTeleported = authorizedTeleportTick != Long.MIN_VALUE
                && currentServerTick - authorizedTeleportTick <= 2L;

        AntiCheatContext context = new AntiCheatContext(
                player,
                state,
                config,
                preMovePos,
                postMovePos,
                preMoveVelocity,
                postMoveVelocity,
                onGround,
                wasOnGround,
                climbing,
                inFluid,
                creativeOrSpectator,
                surfing,
                usingPlotCreative,
                justTeleported,
                jumpThisTick,
                speedCap,
                currentServerTick
        );

        boolean lagback = false;
        for (AntiCheatCheck check : CHECKS) {
            try {
                AntiCheatCheck.CheckResult result = check.run(context);
                state.decayAllViolations(0.0D);
                double level = state.addViolation(check.name(), -check.decayPerTick());
                if (result == null || result == AntiCheatCheck.CheckResult.OK || result.violationIncrement <= 0.0D) {
                    continue;
                }
                level = state.addViolation(check.name(), result.violationIncrement);
                AntiCheatFlag flag = new AntiCheatFlag(
                        check.name(),
                        System.currentTimeMillis(),
                        result.violationIncrement,
                        result.details,
                        postMovePos.x,
                        postMovePos.y,
                        postMovePos.z
                );
                state.addFlag(flag);
                broadcastFlag(player, check.name(), result.details, level);
                logFlagToConsole(player, check.name(), level, check.lagbackThreshold(), result.details, postMovePos);
                if (Minehop.surfDebugPlayers.contains(player.getUuid())) {
                    Minehop.LOGGER.info(String.format(Locale.ROOT,
                            "[ACDBG] %s FLAG %s vl=%.1f thr=%.1f surfing=%b onGround=%b wasOnGround=%b airTicks=%d pos=(%.2f,%.2f,%.2f) %s",
                            player.getNameForScoreboard(), check.name(), level, check.lagbackThreshold(),
                            surfing, onGround, wasOnGround, state.airborneTicks(),
                            postMovePos.x, postMovePos.y, postMovePos.z, result.details));
                }

                if (result.forceLagback || level >= check.lagbackThreshold()) {
                    lagback = true;
                }
            } catch (Throwable throwable) {
                Minehop.LOGGER.warn("AntiCheat check {} threw", check.name(), throwable);
            }
        }

        boolean performLagback = lagback && lagbacksEnabled && shouldAllowLagback(state);
        if (performLagback) {
            // Lagbacks always reach the server console (they are already rate-limited by the lagback
            // cooldown) so an admin has a trail without needing /mdebug on the offender.
            Vec3d t = state.lastVerifiedPos();
            Minehop.LOGGER.info(String.format(Locale.ROOT,
                    "[AC] %s LAGBACK from=(%.2f,%.2f,%.2f) to=(%.2f,%.2f,%.2f) surfing=%b onGround=%b airTicks=%d",
                    player.getNameForScoreboard(), postMovePos.x, postMovePos.y, postMovePos.z,
                    t == null ? 0 : t.x, t == null ? 0 : t.y, t == null ? 0 : t.z,
                    surfing, onGround, state.airborneTicks()));
            triggerLagback(player, state);
        } else if (!lagback) {
            // Movement was CLEAN this tick: advance the verified anchor and log any allowed fast move.
            state.setLastVerifiedPos(postMovePos);
            // F6: when fast movement was ALLOWED (no lagback), record why an exemption let it
            // through, so abnormal velocity can be explained (boost/surf/teleport/fluid/...).
            recordAllowReason(player, preMovePos, postMovePos, postMoveVelocity,
                    surfing, justTeleported, inFluid, climbing, speedCap);
        }
        // M4: if a lagback was warranted but suppressed by the cooldown, do NOT advance the verified
        // anchor — freeze it so the next allowed lagback returns the player to the last clean position,
        // not somewhere they reached while cheating during the cooldown window.
        state.setLastReportedPos(postMovePos);
        state.setLastTickVelocity(postMoveVelocity == null ? Vec3d.ZERO : postMoveVelocity);
        state.setLastTickTime(currentServerTick);
        if (onGround || climbing || inFluid || surfing) {
            state.resetAirborneTicks();
        } else {
            state.incrementAirborneTicks();
        }
        if (postMovePos.squaredDistanceTo(preMovePos) < 1.0E-8D) {
            state.incrementTicksSinceMoved();
        } else {
            state.resetTicksSinceMoved();
        }
        if (jumpThisTick) {
            state.markJumpThisTick();
        } else {
            state.incrementTicksSinceJump();
        }
        state.updateTopRecentHorizontalSpeed(context.horizontalSpeed());
    }

    // Floor (blocks/tick) above which allowed movement is "fast enough" to be worth logging the
    // exemption reason — keeps normal walking/running out of the allow log.
    private static final double ALLOW_LOG_SPEED_FLOOR = 0.45D;

    private static void recordAllowReason(
            ServerPlayerEntity player,
            Vec3d preMovePos,
            Vec3d postMovePos,
            Vec3d postMoveVelocity,
            boolean surfing,
            boolean justTeleported,
            boolean inFluid,
            boolean climbing,
            double speedCap
    ) {
        double speed = postMoveVelocity == null ? 0.0D : Math.hypot(postMoveVelocity.x, postMoveVelocity.z);
        double movedHoriz = Math.hypot(postMovePos.x - preMovePos.x, postMovePos.z - preMovePos.z);
        boolean vehicle = player.hasVehicle() || player.isGliding();

        AntiCheatAllowLog.Reason reason = null;
        if (surfing && speed > ALLOW_LOG_SPEED_FLOOR) {
            reason = AntiCheatAllowLog.Reason.SURF_CONTACT;
        } else if (justTeleported && movedHoriz > ALLOW_LOG_SPEED_FLOOR) {
            reason = AntiCheatAllowLog.Reason.TELEPORT;
        } else if (vehicle && speed > ALLOW_LOG_SPEED_FLOOR) {
            reason = AntiCheatAllowLog.Reason.VEHICLE;
        } else if (inFluid && speed > ALLOW_LOG_SPEED_FLOOR) {
            reason = AntiCheatAllowLog.Reason.FLUID;
        } else if (climbing && speed > ALLOW_LOG_SPEED_FLOOR) {
            reason = AntiCheatAllowLog.Reason.CLIMB;
        }
        if (reason != null) {
            AntiCheatAllowLog.record(player, reason, speed, speedCap, postMovePos, currentServerTick);
        }
    }

    private static boolean shouldAllowLagback(AntiCheatPlayerState state) {
        long lastLagback = state.lastLagbackTick();
        // Sentinel-safe: `tick - Long.MIN_VALUE` overflows negative, which made this ALWAYS false for
        // a player's FIRST offense — i.e. the lagback mechanism never fired at all.
        if (lastLagback == Long.MIN_VALUE) {
            return true;
        }
        return currentServerTick - lastLagback >= LAGBACK_COOLDOWN_TICKS;
    }

    private static void triggerLagback(ServerPlayerEntity player, AntiCheatPlayerState state) {
        if (player.networkHandler == null) {
            return;
        }
        Vec3d target = state.lastVerifiedPos();
        if (target == null) {
            target = player.getPos();
        }
        state.markLagback(currentServerTick);
        player.networkHandler.requestTeleport(target.x, target.y, target.z, player.getYaw(), player.getPitch());
        player.setVelocity(Vec3d.ZERO);
        player.velocityDirty = true;
        // M4 escalation: surface persistent rollback (likely a cheater, or a severe desync worth
        // investigating) to the server log — non-punitive, no auto-kick, to avoid false action.
        int consecutive = state.consecutiveLagbacks();
        if (consecutive == 10 || consecutive == 25 || consecutive == 50 || (consecutive > 50 && consecutive % 50 == 0)) {
            Minehop.LOGGER.warn("[AC] {} has {} consecutive lagbacks (persistent invalid movement)",
                    player.getNameForScoreboard(), consecutive);
        }
    }

    // Console trail for flags, independent of /mdebug and verbose listeners. Throttled per
    // (player, check) so a sustained violation can't flood the log; the suppressed count is reported
    // on the next emitted line.
    private static final long CONSOLE_FLAG_INTERVAL_TICKS = 10L;
    private static final Map<String, long[]> CONSOLE_FLAG_THROTTLE = new ConcurrentHashMap<>();

    private static void logFlagToConsole(ServerPlayerEntity player, String checkName, double level,
                                         double lagbackThreshold, String details, Vec3d pos) {
        String key = player.getUuid() + "|" + checkName;
        long[] slot = CONSOLE_FLAG_THROTTLE.computeIfAbsent(key, k -> new long[]{Long.MIN_VALUE, 0L});
        boolean first = slot[0] == Long.MIN_VALUE;
        if (!first && currentServerTick >= slot[0] && currentServerTick - slot[0] < CONSOLE_FLAG_INTERVAL_TICKS) {
            slot[1]++;
            return;
        }
        long suppressed = slot[1];
        slot[0] = currentServerTick;
        slot[1] = 0L;
        Minehop.LOGGER.info(String.format(Locale.ROOT,
                "[AC] %s FLAG %s vl=%.1f/%.1f pos=(%.2f,%.2f,%.2f) %s%s",
                player.getNameForScoreboard(), checkName, level, lagbackThreshold,
                pos.x, pos.y, pos.z, details == null ? "" : details,
                suppressed > 0 ? " (+" + suppressed + " suppressed)" : ""));
    }

    private static void clearConsoleFlagThrottle(UUID uuid) {
        String prefix = uuid + "|";
        CONSOLE_FLAG_THROTTLE.keySet().removeIf(k -> k.startsWith(prefix));
    }

    private static void broadcastFlag(ServerPlayerEntity offender, String checkName, String details, double level) {
        if (VERBOSE_LISTENERS.isEmpty() || serverInstance == null) {
            return;
        }
        String offenderName = offender.getNameForScoreboard();
        String preview = details == null || details.isEmpty() ? "" : (" " + details);
        Text msg = Text.literal("[AC] ")
                .formatted(Formatting.RED)
                .append(Text.literal(offenderName).formatted(Formatting.YELLOW))
                .append(Text.literal(" @ ").formatted(Formatting.GRAY))
                .append(Text.literal(checkName).formatted(Formatting.AQUA))
                .append(Text.literal(String.format(Locale.ROOT, " (vl=%.1f)", level)).formatted(Formatting.GOLD))
                .append(Text.literal(preview).formatted(Formatting.GRAY));

        for (UUID listenerUuid : new HashSet<>(VERBOSE_LISTENERS)) {
            ServerPlayerEntity listener = serverInstance.getPlayerManager().getPlayer(listenerUuid);
            if (listener == null) {
                VERBOSE_LISTENERS.remove(listenerUuid);
                continue;
            }
            Long lastTick = LAST_VERBOSE_TICK.get(listenerUuid);
            if (lastTick != null && currentServerTick - lastTick < VERBOSE_COOLDOWN_TICKS) {
                continue;
            }
            LAST_VERBOSE_TICK.put(listenerUuid, currentServerTick);
            listener.sendMessage(msg, false);
        }
    }

    public static void notifyPlayer(ServerPlayerEntity player, String text) {
        if (player != null && text != null) {
            Logger.log(player, Text.literal(text));
        }
    }

    private static void onServerTick(MinecraftServer server) {
        if (server == null) {
            return;
        }
        serverInstance = server;
        currentServerTick = server.getOverworld() == null ? currentServerTick + 1 : server.getOverworld().getTime();
        MovementValidator.onServerTick(server);
        autoSaveCountdown--;
        if (autoSaveCountdown <= 0) {
            autoSaveCountdown = 6000;
            AntiCheatStorage.save(server, PLAYER_STATES, EXEMPT_PLAYERS);
            AntiCheatAllowLog.save(server);
        }
    }

    private static void scheduleSave() {
        autoSaveCountdown = Math.min(autoSaveCountdown, 100);
    }

    public static MinecraftServer getServer() {
        return serverInstance;
    }
}
