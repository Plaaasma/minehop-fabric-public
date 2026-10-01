package net.nerdorg.minehop.commands;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.authlib.AuthenticationService;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Uuids;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.nerdorg.minehop.block.entity.BoostBlockEntity;
import net.nerdorg.minehop.entity.ModEntities;
import net.nerdorg.minehop.entity.custom.ResetEntity;
import net.nerdorg.minehop.util.Logger;

import java.util.UUID;

public class TestCommands {
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
            LiteralArgumentBuilder.<ServerCommandSource>literal("mtest")
                .requires(source -> source.hasPermissionLevel(4))
                    .executes(context -> {
                        handleTest(context);
                        return Command.SINGLE_SUCCESS;
                    })
            ));

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
            LiteralArgumentBuilder.<ServerCommandSource>literal("mdebug")
                .requires(source -> source.hasPermissionLevel(4))
                    .executes(context -> {
                        handleDebugToggle(context, context.getSource().getPlayer());
                        return Command.SINGLE_SUCCESS;
                    })
                    // Targeted form so the server console (or another admin) can enable debug logging
                    // for a NON-op player — an op-4 player is anticheat-exempt, so self-toggling then
                    // testing as that same op captures nothing.
                    .then(RequiredArgumentBuilder.<ServerCommandSource, net.minecraft.command.EntitySelector>argument(
                                    "player", net.minecraft.command.argument.EntityArgumentType.player())
                            .executes(context -> {
                                handleDebugToggle(context,
                                        net.minecraft.command.argument.EntityArgumentType.getPlayer(context, "player"));
                                return Command.SINGLE_SUCCESS;
                            }))
            ));
    }

    private static void handleDebugToggle(CommandContext<ServerCommandSource> context, ServerPlayerEntity target) {
        if (target == null) {
            context.getSource().sendFeedback(() -> net.minecraft.text.Text.literal("Usage from console: mdebug <player>"), false);
            return;
        }
        UUID uuid = target.getUuid();
        boolean enabled;
        if (net.nerdorg.minehop.Minehop.surfDebugPlayers.remove(uuid)) {
            enabled = false;
        } else {
            net.nerdorg.minehop.Minehop.surfDebugPlayers.add(uuid);
            enabled = true;
        }
        String name = target.getNameForScoreboard();
        context.getSource().sendFeedback(() -> net.minecraft.text.Text.literal(
                "Movement debug logging " + (enabled ? "ON" : "OFF") + " for " + name
                        + (enabled ? ". Check latest.log for [ACDBG]/[M1PROBE] lines." : ".")), true);
    }

    private static void handleTest(CommandContext<ServerCommandSource> context) {
        MinecraftServer server = context.getSource().getServer();
        FakePlayer fakePlayer = FakePlayer.get(context.getSource().getWorld(), new GameProfile(UUID.randomUUID(), "Replay"));
        fakePlayer.setInvisible(false);
        server.getPlayerManager().sendToAll(new PlayerListS2CPacket(PlayerListS2CPacket.Action.ADD_PLAYER, fakePlayer));

        context.getSource().getWorld().spawnEntity(fakePlayer);
        server.getPlayerManager().sendToAll(new EntitySpawnS2CPacket(
                fakePlayer.getId(),
                fakePlayer.getUuid(),
                fakePlayer.getX(),
                fakePlayer.getY(),
                fakePlayer.getZ(),
                fakePlayer.getPitch(),
                fakePlayer.getYaw(),
                fakePlayer.getType(),
                0,
                fakePlayer.getVelocity(),
                fakePlayer.getHeadYaw()));
    }
}
