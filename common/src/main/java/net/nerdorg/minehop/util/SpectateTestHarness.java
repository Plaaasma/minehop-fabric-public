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
                        .then(LiteralArgumentBuilder.<CommandSourceStack>literal("path")
                                .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("map", StringArgumentType.string())
                                        .executes(context -> pathStats(context, StringArgumentType.getString(context, "map")))))
        ));
    }

    /** Frame count vs points sent for a map's WR replay route. */
    private static int pathStats(CommandContext<CommandSourceStack> context, String mapName) {
        ReplayManager.Replay replay = ReplayManager.getReplay(mapName);
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
                + " dim=" + player.level().dimension().location()
                + " pos=" + fmt(player.getX(), player.getY(), player.getZ())
                + " camera=" + (camera == player ? "self" : camera.getScoreboardName() + "#" + camera.getId())
                + " session=" + (session == null ? "none" : session.kind() + ":" + (session.mapName() != null ? session.mapName() + "/" : "") + session.targetName());
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
