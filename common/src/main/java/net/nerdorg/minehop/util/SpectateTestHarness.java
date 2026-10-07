package net.nerdorg.minehop.util;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundTeleportToEntityPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.platform.Services;
import net.nerdorg.minehop.replays.ReplayManager;
import net.nerdorg.minehop.replays.ReplayPathSimplifier;
import net.nerdorg.minehop.replays.RunStats;
import net.nerdorg.minehop.spectate.SpectateSessions;

import java.util.Locale;

/**
 * DEV ONLY (system property {@code minehop.spectatetest}, set by {@code gradlew :fabric:runServer -Pspectatetest}):
 * operator commands that feed a player's connection the packets a vanilla or modified client would send - the
 * spectator teleport request, sneak, an own movement packet - through the real handlers (and so through the
 * spectate-session guards), plus state dumps. Lets the server-side enforcement be verified without key input.
 * Not registered otherwise.
 */
public final class SpectateTestHarness {
    private SpectateTestHarness() {
    }

    public static void register() {
        if (!Boolean.getBoolean("minehop.spectatetest")) {
            return;
        }
        Minehop.LOGGER.warn("[SPECTEST] spectate test commands ENABLED (dev only)");
        Services.EVENTS.onRegisterCommands((dispatcher, registryAccess, environment) -> dispatcher.register(
                LiteralArgumentBuilder.<CommandSourceStack>literal("spectest")
                        .requires(source -> source.hasPermission(4))
                        .then(LiteralArgumentBuilder.<CommandSourceStack>literal("state")
                                .then(RequiredArgumentBuilder.<CommandSourceStack, net.minecraft.commands.arguments.selector.EntitySelector>argument("player", EntityArgument.player())
                                        .executes(context -> reply(context, describe(EntityArgument.getPlayer(context, "player"))))))
                        .then(LiteralArgumentBuilder.<CommandSourceStack>literal("teleport")
                                .then(RequiredArgumentBuilder.<CommandSourceStack, net.minecraft.commands.arguments.selector.EntitySelector>argument("player", EntityArgument.player())
                                        .then(RequiredArgumentBuilder.<CommandSourceStack, net.minecraft.commands.arguments.selector.EntitySelector>argument("target", EntityArgument.entity())
                                                .executes(context -> {
                                                    ServerPlayer player = EntityArgument.getPlayer(context, "player");
                                                    Entity target = EntityArgument.getEntity(context, "target");
                                                    String before = describe(player);
                                                    // What the vanilla spectator menu ("teleport to player") sends.
                                                    player.connection.handleTeleportToEntityPacket(new ServerboundTeleportToEntityPacket(target.getUUID()));
                                                    return reply(context, "teleport-to-entity " + target.getScoreboardName() + "\n before: " + before + "\n after:  " + describe(player));
                                                }))))
                        .then(LiteralArgumentBuilder.<CommandSourceStack>literal("sneak")
                                .then(RequiredArgumentBuilder.<CommandSourceStack, net.minecraft.commands.arguments.selector.EntitySelector>argument("player", EntityArgument.player())
                                        .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("state", StringArgumentType.word())
                                                .executes(context -> {
                                                    ServerPlayer player = EntityArgument.getPlayer(context, "player");
                                                    boolean shift = "on".equals(StringArgumentType.getString(context, "state"));
                                                    // A client sends both when sneak is pressed; the command packet sets the
                                                    // server's sneak state (what ends a vanilla spectator camera).
                                                    // 1.21.1: the command packet is the only sneak packet a client sends
                                                    // (ServerboundPlayerInputPacket is vehicle-only before 1.21.2).
                                                    player.connection.handlePlayerCommand(new ServerboundPlayerCommandPacket(player,
                                                            shift ? ServerboundPlayerCommandPacket.Action.PRESS_SHIFT_KEY
                                                                    : ServerboundPlayerCommandPacket.Action.RELEASE_SHIFT_KEY));
                                                    return reply(context, "input shift=" + shift + " -> " + describe(player));
                                                }))))
                        .then(LiteralArgumentBuilder.<CommandSourceStack>literal("camera")
                                .then(RequiredArgumentBuilder.<CommandSourceStack, net.minecraft.commands.arguments.selector.EntitySelector>argument("player", EntityArgument.player())
                                        .then(RequiredArgumentBuilder.<CommandSourceStack, net.minecraft.commands.arguments.selector.EntitySelector>argument("target", EntityArgument.entity())
                                                .executes(context -> {
                                                    ServerPlayer player = EntityArgument.getPlayer(context, "player");
                                                    Entity target = EntityArgument.getEntity(context, "target");
                                                    // What a spectator's attack on an entity, or a camera release, does.
                                                    player.setCamera(target);
                                                    return reply(context, "setCamera(" + target.getScoreboardName() + ") -> " + describe(player));
                                                }))))
                        .then(LiteralArgumentBuilder.<CommandSourceStack>literal("attack")
                                .then(RequiredArgumentBuilder.<CommandSourceStack, net.minecraft.commands.arguments.selector.EntitySelector>argument("player", EntityArgument.player())
                                        .then(RequiredArgumentBuilder.<CommandSourceStack, net.minecraft.commands.arguments.selector.EntitySelector>argument("target", EntityArgument.entity())
                                                .executes(context -> {
                                                    ServerPlayer player = EntityArgument.getPlayer(context, "player");
                                                    Entity target = EntityArgument.getEntity(context, "target");
                                                    player.attack(target);
                                                    return reply(context, "attack " + target.getScoreboardName() + " -> " + describe(player));
                                                }))))
                        .then(LiteralArgumentBuilder.<CommandSourceStack>literal("move")
                                .then(RequiredArgumentBuilder.<CommandSourceStack, net.minecraft.commands.arguments.selector.EntitySelector>argument("player", EntityArgument.player())
                                        .then(RequiredArgumentBuilder.<CommandSourceStack, Double>argument("dx", DoubleArgumentType.doubleArg())
                                                .then(RequiredArgumentBuilder.<CommandSourceStack, Double>argument("dy", DoubleArgumentType.doubleArg())
                                                        .then(RequiredArgumentBuilder.<CommandSourceStack, Double>argument("dz", DoubleArgumentType.doubleArg())
                                                                .executes(context -> {
                                                                    ServerPlayer player = EntityArgument.getPlayer(context, "player");
                                                                    double x = player.getX() + DoubleArgumentType.getDouble(context, "dx");
                                                                    double y = player.getY() + DoubleArgumentType.getDouble(context, "dy");
                                                                    double z = player.getZ() + DoubleArgumentType.getDouble(context, "dz");
                                                                    String before = describe(player);
                                                                    player.connection.handleMovePlayer(new ServerboundMovePlayerPacket.Pos(x, y, z, false));
                                                                    return reply(context, "move packet to " + fmt(x, y, z) + "\n before: " + before + "\n after:  " + describe(player));
                                                                }))))))
                        .then(LiteralArgumentBuilder.<CommandSourceStack>literal("stats")
                                .then(RequiredArgumentBuilder.<CommandSourceStack, net.minecraft.commands.arguments.selector.EntitySelector>argument("player", EntityArgument.player())
                                        .executes(context -> {
                                            RunStats.Snapshot stats = RunStats.of(EntityArgument.getPlayer(context, "player"));
                                            return reply(context, String.format(Locale.ROOT, "jumps=%d lastJumpSpeed=%.4f b/t (%.2f b/s) efficiency=%.2f",
                                                    stats.jumpCount(), stats.lastJumpSpeed(), stats.lastJumpSpeed() * 20.0D, stats.efficiency()));
                                        })))
                        .then(LiteralArgumentBuilder.<CommandSourceStack>literal("respawn")
                                .then(RequiredArgumentBuilder.<CommandSourceStack, net.minecraft.commands.arguments.selector.EntitySelector>argument("player", EntityArgument.player())
                                        .executes(context -> {
                                            ServerPlayer player = EntityArgument.getPlayer(context, "player");
                                            // What the death screen's "Respawn" button sends.
                                            player.connection.handleClientCommand(new net.minecraft.network.protocol.game.ServerboundClientCommandPacket(
                                                    net.minecraft.network.protocol.game.ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));
                                            return reply(context, "respawn requested for " + player.getScoreboardName());
                                        })))
                        .then(LiteralArgumentBuilder.<CommandSourceStack>literal("replays")
                                .executes(context -> reply(context, replayCensus())))
                        .then(LiteralArgumentBuilder.<CommandSourceStack>literal("pulsehop")
                                .then(RequiredArgumentBuilder.<CommandSourceStack, net.minecraft.commands.arguments.selector.EntitySelector>argument("player", EntityArgument.player())
                                        .then(RequiredArgumentBuilder.<CommandSourceStack, Integer>argument("jumps", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 20))
                                                .executes(context -> {
                                                    ServerPlayer player = EntityArgument.getPlayer(context, "player");
                                                    int jumps = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "jumps");
                                                    planPulseHop(player, jumps);
                                                    return reply(context, "making " + player.getScoreboardName() + "'s client jump " + jumps + " times");
                                                }))))
                        .then(LiteralArgumentBuilder.<CommandSourceStack>literal("path")
                                .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("map", StringArgumentType.string())
                                        .executes(context -> pathStats(context, StringArgumentType.getString(context, "map")))))
        ));
    }

    /**
     * Which stored replays can be watched (they back a current PB or WR row), using the same lookups as the commands;
     * the rest are kept but never offered. Lists every replay under 1 s with its status.
     */
    private static String replayCensus() {
        java.util.Set<ReplayManager.Replay> watchable = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        java.util.Set<String> maps = new java.util.TreeSet<>();
        for (ReplayManager.Replay replay : Minehop.replayList) {
            if (replay != null && replay.map_name != null) {
                maps.add(replay.map_name);
            }
        }
        for (String map : maps) {
            for (java.util.Map.Entry<net.nerdorg.minehop.data.DataManager.RecordData, ReplayManager.Replay> entry : ReplayManager.watchablePersonalBests(map)) {
                watchable.add(entry.getValue());
            }
        }
        watchable.addAll(ReplayManager.worldRecordReplays().values());
        int total = 0;
        int invalidated = 0;
        int noPbRow = 0;
        int fasterThanPb = 0;
        int slowerThanPb = 0;
        StringBuilder impossible = new StringBuilder();
        for (ReplayManager.Replay replay : Minehop.replayList) {
            if (replay == null) {
                continue;
            }
            total++;
            if (!ReplayManager.isPlayable(replay)) {
                invalidated++;
            } else if (!watchable.contains(replay)) {
                net.nerdorg.minehop.data.DataManager.RecordData pb = net.nerdorg.minehop.data.DataManager.getPersonalRecord(
                        replay.player_name, replay.player_uuid, replay.map_name);
                if (pb == null) {
                    noPbRow++;
                } else if (replay.time < pb.time) {
                    fasterThanPb++;
                } else {
                    slowerThanPb++;
                }
            }
            if (replay.time < 1.0D) {
                impossible.append(String.format(Locale.ROOT, "\n  %s %s %.3fs watchable=%s", replay.map_name, replay.player_name,
                        replay.time, watchable.contains(replay)));
            }
        }
        return "replays=" + total + " watchable(back a current PB/WR)=" + watchable.size() + " hidden: noPbRow=" + noPbRow
                + " fasterThanPb(orphan)=" + fasterThanPb + " slowerThanPb(kept run)=" + slowerThanPb + " invalidated=" + invalidated
                + "\n under 1s:" + impossible;
    }

    private static final java.util.Map<java.util.UUID, int[]> PULSES = new java.util.HashMap<>();
    private static boolean pulseTickRegistered;

    /**
     * Makes a real client jump without key input: every 20 ticks the client is given a take-off velocity
     * (ResetVelocityCarry, which the client applies to itself), so its own physics produce the arc and its own move
     * packets carry it. Jump k leaves at (0.40 + 0.05k) blocks/tick horizontally.
     *
     * <p>1.21.1: a pre-1.21.2 client never sends its jump key, so nothing is marked held; the server infers the jump
     * chain from the on-ground flag (MovementValidator#trackJumps), so pulses that land and rest count as separate
     * chains of one jump.
     */
    private static void planPulseHop(ServerPlayer player, int jumps) {
        PULSES.put(player.getUUID(), new int[]{jumps, 0, 0});
        if (!pulseTickRegistered) {
            pulseTickRegistered = true;
            Services.EVENTS.onServerTickEnd(server -> {
                for (java.util.Iterator<java.util.Map.Entry<java.util.UUID, int[]>> it = PULSES.entrySet().iterator(); it.hasNext(); ) {
                    java.util.Map.Entry<java.util.UUID, int[]> entry = it.next();
                    ServerPlayer target = server.getPlayerList().getPlayer(entry.getKey());
                    int[] state = entry.getValue(); // jumps left, ticks until next, jumps done
                    if (target == null) {
                        it.remove();
                        continue;
                    }
                    if (state[1]-- > 0) {
                        continue;
                    }
                    if (state[0] <= 0) {
                        it.remove();
                        continue;
                    }
                    double vx = 0.40D + 0.05D * state[2];
                    net.nerdorg.minehop.networking.PacketHandler.sendResetVelocityCarry(target, new net.minecraft.world.phys.Vec3(vx, 0.42D, 0.0D), 1);
                    Minehop.LOGGER.info("[SPECTEST] pulse {} vx={} -> server stats before: {}", state[2] + 1, vx, RunStats.of(target));
                    state[0]--;
                    state[2]++;
                    state[1] = 20;
                }
            });
        }
    }

    /** Frame count vs points sent for a map's WR replay route ("longest": the longest stored replay of any map). */
    private static int pathStats(CommandContext<CommandSourceStack> context, String mapName) {
        ReplayManager.Replay replay = ReplayManager.getReplay(mapName);
        if ("longest".equals(mapName) && Minehop.replayList != null) {
            for (ReplayManager.Replay candidate : Minehop.replayList) {
                if (candidate != null && ReplayManager.isPlayable(candidate)
                        && (replay == null || ReplayManager.frameCount(candidate) > ReplayManager.frameCount(replay))) {
                    replay = candidate;
                }
            }
            mapName = replay == null ? mapName : replay.map_name + " (longest)";
        }
        if (replay == null) {
            return reply(context, "no WR replay on " + mapName);
        }
        String label = mapName;
        ReplayManager.Replay chosen = replay;
        long loadStart = System.nanoTime();
        ReplayManager.loadFrames(chosen, frames -> {
            double loadMs = (System.nanoTime() - loadStart) / 1.0E6D;
            if (frames == null) {
                reply(context, label + ": frames unavailable (" + ReplayManager.unavailableReason(chosen) + ")");
                return;
            }
            long start = System.nanoTime();
            ReplayPathSimplifier.Result result = ReplayPathSimplifier.simplify(frames, ReplayPathSimplifier.MAX_POINTS);
            double ms = (System.nanoTime() - start) / 1.0E6D;
            int finite = 0;
            for (org.joml.Vector3f point : result.points()) {
                if (Float.isFinite(point.x())) {
                    finite++;
                }
            }
            reply(context, String.format(Locale.ROOT,
                    "%s WR %s: %d frames -> %d points (%d polylines, tolerance %.3f blocks, %d bytes of points) in %.1f ms (frames ready after %.1f ms)",
                    label, chosen.player_name, frames.size(), result.points().size(), result.polylines(),
                    result.tolerance(), result.points().size() * 12, ms, loadMs) + " finite=" + finite);
        });
        return 1;
    }

    private static String describe(ServerPlayer player) {
        Entity camera = player.getCamera();
        SpectateSessions.Session session = SpectateSessions.session(player);
        return player.getScoreboardName() + " mode=" + player.gameMode.getGameModeForPlayer().getName()
                + " dim=" + player.level().dimension().location()
                + " pos=" + fmt(player.getX(), player.getY(), player.getZ())
                + " camera=" + (camera == player ? "self" : camera.getScoreboardName() + "#" + camera.getId())
                + " session=" + (session == null ? "none" : session.kind() + ":" + (session.mapName() != null ? session.mapName() + "/" : "") + session.targetName()
                        + " clientAttached=" + session.clientAttached() + " cameraSends=" + session.cameraSends()
                        + " [" + SpectateSessions.trackingDebug(player) + "]");
    }

    private static String fmt(double x, double y, double z) {
        return String.format(Locale.ROOT, "%.2f %.2f %.2f", x, y, z);
    }

    private static int reply(CommandContext<CommandSourceStack> context, String message) {
        Minehop.LOGGER.info("[SPECTEST] {}", message);
        context.getSource().sendSuccess(() -> Component.literal(message), false);
        return 1;
    }
}
