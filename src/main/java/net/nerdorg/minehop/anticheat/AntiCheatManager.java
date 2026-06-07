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
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.anticheat.checks.FlightCheck;
import net.nerdorg.minehop.anticheat.checks.InvalidJumpCheck;
import net.nerdorg.minehop.anticheat.checks.NoClipCheck;
import net.nerdorg.minehop.anticheat.checks.SpeedCheck;
import net.nerdorg.minehop.anticheat.checks.TeleportCheck;
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
    private static final double TELEPORT_THRESHOLD_SQ = 64.0D * 64.0D;

    private static final Map<UUID, AntiCheatPlayerState> PLAYER_STATES = new ConcurrentHashMap<>();
    private static final Set<UUID> EXEMPT_PLAYERS = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static final Set<UUID> VERBOSE_LISTENERS = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static final Map<UUID, Long> LAST_VERBOSE_TICK = new ConcurrentHashMap<>();
    private static final List<AntiCheatCheck> CHECKS = new ArrayList<>();
    private static volatile MinecraftServer serverInstance;
    private static volatile boolean enabled = true;
    private static volatile long currentServerTick = 0L;
    private static int autoSaveCountdown = 1200;

    private AntiCheatManager() {
    }

    public static void register() {
        CHECKS.add(new SpeedCheck());
        CHECKS.add(new FlightCheck());
        CHECKS.add(new NoClipCheck());
        CHECKS.add(new InvalidJumpCheck());
        CHECKS.add(new TeleportCheck());

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            serverInstance = server;
            AntiCheatStorage.load(server, PLAYER_STATES, EXEMPT_PLAYERS);
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            AntiCheatStorage.save(server, PLAYER_STATES, EXEMPT_PLAYERS);
            serverInstance = null;
        });
        ServerTickEvents.END_SERVER_TICK.register(AntiCheatManager::onServerTick);
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
            }
        });
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean enabledValue) {
        enabled = enabledValue;
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
        if (isExempt(player)) {
            AntiCheatPlayerState exemptState = stateOf(player);
            if (exemptState != null) {
                exemptState.setLastReportedPos(postMovePos);
                exemptState.setLastVerifiedPos(postMovePos);
                exemptState.setLastTickVelocity(postMoveVelocity == null ? Vec3d.ZERO : postMoveVelocity);
                exemptState.setLastTickTime(currentServerTick);
                exemptState.markAuthorizedTeleport(currentServerTick);
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
        boolean justTeleported = (currentServerTick - state.lastAuthorizedTeleportTick()) <= 2L;
        long previousTick = state.lastTickTime();
        if (previousTick != Long.MIN_VALUE && currentServerTick - previousTick > 4L) {
            justTeleported = true;
        }
        if (preMovePos.squaredDistanceTo(state.lastReportedPos() == null ? preMovePos : state.lastReportedPos()) > TELEPORT_THRESHOLD_SQ) {
            justTeleported = true;
        }

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

        if (lagback && shouldAllowLagback(state)) {
            if (Minehop.surfDebugPlayers.contains(player.getUuid())) {
                Vec3d t = state.lastVerifiedPos();
                Minehop.LOGGER.info(String.format(Locale.ROOT,
                        "[ACDBG] %s LAGBACK from=(%.2f,%.2f,%.2f) to=(%.2f,%.2f,%.2f)",
                        player.getNameForScoreboard(), postMovePos.x, postMovePos.y, postMovePos.z,
                        t == null ? 0 : t.x, t == null ? 0 : t.y, t == null ? 0 : t.z));
            }
            triggerLagback(player, state);
        } else {
            state.setLastVerifiedPos(postMovePos);
        }
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

    private static boolean shouldAllowLagback(AntiCheatPlayerState state) {
        long sinceLast = currentServerTick - state.lastLagbackTick();
        return sinceLast >= LAGBACK_COOLDOWN_TICKS;
    }

    private static void triggerLagback(ServerPlayerEntity player, AntiCheatPlayerState state) {
        Vec3d target = state.lastVerifiedPos();
        if (target == null) {
            target = player.getPos();
        }
        state.markLagback(currentServerTick);
        player.networkHandler.requestTeleport(target.x, target.y, target.z, player.getYaw(), player.getPitch());
        player.setVelocity(Vec3d.ZERO);
        player.velocityDirty = true;
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
        autoSaveCountdown--;
        if (autoSaveCountdown <= 0) {
            autoSaveCountdown = 6000;
            AntiCheatStorage.save(server, PLAYER_STATES, EXEMPT_PLAYERS);
        }
    }

    private static void scheduleSave() {
        autoSaveCountdown = Math.min(autoSaveCountdown, 100);
    }

    public static MinecraftServer getServer() {
        return serverInstance;
    }
}
