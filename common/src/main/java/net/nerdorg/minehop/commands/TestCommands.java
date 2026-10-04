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
import net.nerdorg.minehop.platform.Services;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.nerdorg.minehop.block.entity.BoostBlockEntity;
import net.nerdorg.minehop.entity.ModEntities;
import net.nerdorg.minehop.entity.custom.ResetEntity;
import net.nerdorg.minehop.util.Logger;

import java.util.UUID;

public class TestCommands {
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public static void register() {
        Services.EVENTS.onRegisterCommands((dispatcher, registryAccess, environment) -> dispatcher.register(
            LiteralArgumentBuilder.<CommandSourceStack>literal("mtest")
                .requires(source -> source.hasPermission(4))
                    .executes(context -> {
                        handleTest(context);
                        return Command.SINGLE_SUCCESS;
                    })
            ));

        Services.EVENTS.onRegisterCommands((dispatcher, registryAccess, environment) -> dispatcher.register(
            LiteralArgumentBuilder.<CommandSourceStack>literal("mdebug")
                .requires(source -> source.hasPermission(4))
                    .executes(context -> {
                        handleDebugToggle(context, context.getSource().getPlayer());
                        return Command.SINGLE_SUCCESS;
                    })
                    // Targeted form so the server console (or another admin) can enable debug logging
                    // for a NON-op player — an op-4 player is anticheat-exempt, so self-toggling then
                    // testing as that same op captures nothing.
                    .then(RequiredArgumentBuilder.<CommandSourceStack, net.minecraft.commands.arguments.selector.EntitySelector>argument(
                                    "player", net.minecraft.commands.arguments.EntityArgument.player())
                            .executes(context -> {
                                handleDebugToggle(context,
                                        net.minecraft.commands.arguments.EntityArgument.getPlayer(context, "player"));
                                return Command.SINGLE_SUCCESS;
                            }))
            ));
    }

    private static void handleDebugToggle(CommandContext<CommandSourceStack> context, ServerPlayer target) {
        if (target == null) {
            context.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal("Usage from console: mdebug <player>"), false);
            return;
        }
        UUID uuid = target.getUUID();
        boolean enabled;
        if (net.nerdorg.minehop.Minehop.surfDebugPlayers.remove(uuid)) {
            enabled = false;
        } else {
            net.nerdorg.minehop.Minehop.surfDebugPlayers.add(uuid);
            enabled = true;
        }
        String name = target.getScoreboardName();
        context.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal(
                "Movement debug logging " + (enabled ? "ON" : "OFF") + " for " + name
                        + (enabled ? ". Check latest.log for [ACDBG]/[M1PROBE] lines." : ".")), true);
    }

    private static void handleTest(CommandContext<CommandSourceStack> context) {
        MinecraftServer server = context.getSource().getServer();
        ServerPlayer fakePlayer = Services.PLATFORM.createFakePlayer(context.getSource().getLevel(), new GameProfile(UUID.randomUUID(), "Replay"));
        fakePlayer.setInvisible(false);
        server.getPlayerList().broadcastAll(new ClientboundPlayerInfoUpdatePacket(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER, fakePlayer));

        context.getSource().getLevel().addFreshEntity(fakePlayer);
        server.getPlayerList().broadcastAll(new ClientboundAddEntityPacket(
                fakePlayer.getId(),
                fakePlayer.getUUID(),
                fakePlayer.getX(),
                fakePlayer.getY(),
                fakePlayer.getZ(),
                fakePlayer.getXRot(),
                fakePlayer.getYRot(),
                fakePlayer.getType(),
                0,
                fakePlayer.getDeltaMovement(),
                fakePlayer.getYHeadRot()));
    }
}
