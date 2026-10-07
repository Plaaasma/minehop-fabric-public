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
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.network.protocol.game.ServerboundTeleportToEntityPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Input;
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
                        .requires(source -> PermissionUtil.hasLevel(source, 4))
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
                                                    // 1.21.6+: the input packet's shift sets the server's sneak state (what ends a
                                                    // vanilla spectator camera); there is no shift command packet any more.
                                                    player.connection.handlePlayerInput(new ServerboundPlayerInputPacket(
                                                            new Input(false, false, false, false, false, shift, false)));
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
                                                                    player.connection.handleMovePlayer(new ServerboundMovePlayerPacket.Pos(x, y, z, false, false));
                                                                    return reply(context, "move packet to " + fmt(x, y, z) + "\n before: " + before + "\n after:  " + describe(player));
                                                                }))))))
                        .then(LiteralArgumentBuilder.<CommandSourceStack>literal("stats")
                                .then(RequiredArgumentBuilder.<CommandSourceStack, net.minecraft.commands.arguments.selector.EntitySelector>argument("player", EntityArgument.player())
                                        .executes(context -> {
                                            RunStats.Snapshot stats = RunStats.of(EntityArgument.getPlayer(context, "player"));
                                            return reply(context, String.format(Locale.ROOT, "jumps=%d lastJumpSpeed=%.4f b/t (%.2f b/s) efficiency=%.2f",
                                                    stats.jumpCount(), stats.lastJumpSpeed(), stats.lastJumpSpeed() * 20.0D, stats.efficiency()));
                                        })))
                        .then(LiteralArgumentBuilder.<CommandSourceStack>literal("bhop")
                                .then(RequiredArgumentBuilder.<CommandSourceStack, net.minecraft.commands.arguments.selector.EntitySelector>argument("player", EntityArgument.player())
                                        .then(RequiredArgumentBuilder.<CommandSourceStack, Integer>argument("jumps", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 20))
                                                .executes(context -> {
                                                    ServerPlayer player = EntityArgument.getPlayer(context, "player");
                                                    int jumps = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "jumps");
                                                    planBhop(player, jumps);
                                                    return reply(context, "feeding " + player.getScoreboardName() + " a synthetic bhop of " + jumps + " jumps");
                                                }))))
                        .then(LiteralArgumentBuilder.<CommandSourceStack>literal("path")
                                .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("map", StringArgumentType.string())
                                        .executes(context -> pathStats(context, StringArgumentType.getString(context, "map")))))
        ));
    }

    private record PlannedTick(java.util.UUID player, boolean jump, double x, double y, double z, boolean onGround) {
    }

    private static final java.util.ArrayDeque<PlannedTick> PLAN = new java.util.ArrayDeque<>();
    private static boolean tickRegistered;

    /**
     * Queues a bhop as client packets, one client tick per server tick: input (jump held), a position and the
     * tick-end packet, through the real handlers - what MovementValidator derives the jump stats from. Jump k
     * leaves the ground at (0.50 + 0.05k) blocks/tick; jump is released after the last landing.
     */
    private static void planBhop(ServerPlayer player, int jumps) {
        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();
        for (int k = 0; k < jumps; k++) {
            double vx = 0.50D + 0.05D * k;
            double vy = 0.42D;
            double height = 0.0D;
            do {
                x += vx;
                height += vy;
                vy -= 0.08D;
                PLAN.add(new PlannedTick(player.getUUID(), true, x, y + Math.max(0.0D, height), z, height <= 0.0D));
            } while (height > 0.0D);
        }
        PLAN.add(new PlannedTick(player.getUUID(), false, x, y, z, true));
        if (!tickRegistered) {
            tickRegistered = true;
            Services.EVENTS.onServerTickStart(server -> {
                PlannedTick tick = PLAN.poll();
                ServerPlayer target = tick == null ? null : server.getPlayerList().getPlayer(tick.player());
                if (target == null) {
                    return;
                }
                target.connection.handlePlayerInput(new ServerboundPlayerInputPacket(new Input(false, false, false, false, tick.jump(), false, false)));
                target.connection.handleMovePlayer(new ServerboundMovePlayerPacket.Pos(tick.x(), tick.y(), tick.z(), tick.onGround(), false));
                target.connection.handleClientTickEnd(net.minecraft.network.protocol.game.ServerboundClientTickEndPacket.INSTANCE);
                RunStats.Snapshot stats = RunStats.of(target);
                Minehop.LOGGER.info("[SPECTEST] bhop tick y={} jump={} -> jumps={} lastJumpSpeed={} b/t", String.format(Locale.ROOT, "%.3f", tick.y()),
                        tick.jump(), stats.jumpCount(), String.format(Locale.ROOT, "%.3f", stats.lastJumpSpeed()));
            });
        }
    }

    /** Frame count vs points sent for a map's WR replay route ("longest": the longest stored replay of any map). */
    private static int pathStats(CommandContext<CommandSourceStack> context, String mapName) {
        ReplayManager.Replay replay = ReplayManager.getReplay(mapName);
        if ("longest".equals(mapName) && Minehop.replayList != null) {
            for (ReplayManager.Replay candidate : Minehop.replayList) {
                if (candidate != null && candidate.replayEntries != null
                        && (replay == null || candidate.replayEntries.size() > replay.replayEntries.size())) {
                    replay = candidate;
                }
            }
            mapName = replay == null ? mapName : replay.map_name + " (longest)";
        }
        if (replay == null) {
            return reply(context, "no WR replay on " + mapName);
        }
        long start = System.nanoTime();
        ReplayPathSimplifier.Result result = ReplayPathSimplifier.simplify(replay.replayEntries, ReplayPathSimplifier.MAX_POINTS);
        double ms = (System.nanoTime() - start) / 1.0E6D;
        int finite = 0;
        for (org.joml.Vector3f point : result.points()) {
            if (Float.isFinite(point.x())) {
                finite++;
            }
        }
        return reply(context, String.format(Locale.ROOT,
                "%s WR %s: %d frames -> %d points (%d polylines, tolerance %.3f blocks, %d bytes of points) in %.1f ms",
                mapName, replay.player_name, replay.replayEntries.size(), result.points().size(), result.polylines(),
                result.tolerance(), result.points().size() * 12, ms) + " finite=" + finite);
    }

    private static String describe(ServerPlayer player) {
        Entity camera = player.getCamera();
        SpectateSessions.Session session = SpectateSessions.session(player);
        return player.getScoreboardName() + " mode=" + player.gameMode.getGameModeForPlayer().getName()
                + " dim=" + player.level().dimension().identifier()
                + " pos=" + fmt(player.getX(), player.getY(), player.getZ())
                + " camera=" + (camera == player ? "self" : camera.getScoreboardName() + "#" + camera.getId())
                + " session=" + (session == null ? "none" : session.kind() + ":" + (session.mapName() != null ? session.mapName() + "/" : "") + session.targetName()
                        + " clientAttached=" + session.clientAttached());
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
