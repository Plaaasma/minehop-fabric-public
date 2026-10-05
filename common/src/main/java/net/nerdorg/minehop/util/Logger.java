package net.nerdorg.minehop.util;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import java.util.List;

public class Logger {
    private static Component prefix = Component.literal("NerdOrg ").withStyle(s -> s.withColor(ChatFormatting.GREEN.getColor())).append(Component.literal("(").withStyle(s -> s.withColor(ChatFormatting.GRAY.getColor()))).append(Component.literal("Minehop").withStyle(s -> s.withColor(ChatFormatting.LIGHT_PURPLE.getColor())).withStyle(ChatFormatting.ITALIC)).append(Component.literal(") ").withStyle(s -> s.withColor(ChatFormatting.GRAY.getColor()))).append(Component.literal("-> ").withStyle(s -> s.withColor(ChatFormatting.DARK_GRAY.getColor())));

    public static void logGlobal(MinecraftServer server, String message) {
        List<ServerPlayer> playerEntities = server.getPlayerList().getPlayers();

        for (ServerPlayer playerEntity : playerEntities) {
            playerEntity.sendSystemMessage(prefix.copy().append(Component.literal(message).withStyle(s -> s.withColor(ChatFormatting.AQUA.getColor()))));
        }
    }

    public static void logGlobalColor(MinecraftServer server, String message, ChatFormatting color) {
        List<ServerPlayer> playerEntities = server.getPlayerList().getPlayers();

        for (ServerPlayer playerEntity : playerEntities) {
            playerEntity.sendSystemMessage(prefix.copy().append(Component.literal(message).withStyle(s -> s.withColor(color.getColor()))));
        }
    }

    public static void logGlobal(MinecraftServer server, Component message) {
        List<ServerPlayer> playerEntities = server.getPlayerList().getPlayers();

        for (ServerPlayer playerEntity : playerEntities) {
            playerEntity.sendSystemMessage(prefix.copy().append(message.copy().withStyle(s -> s.withColor(ChatFormatting.AQUA.getColor()))));
        }
    }

    public static void logServer(MinecraftServer server, String message) {
        server.sendSystemMessage(prefix.copy().append(Component.literal(message).withStyle(s -> s.withColor(ChatFormatting.AQUA.getColor()))));
    }

    public static void logActionBar(Player playerEntity, String message) {
        if (playerEntity != null) {
            playerEntity.displayClientMessage(Component.literal(message).withStyle(s -> s.withColor(ChatFormatting.AQUA.getColor())), true);
        }
    }

    public static void logSuccess(Player playerEntity, String message) {
        if (playerEntity != null) {
            playerEntity.displayClientMessage(prefix.copy().append(Component.literal(message).withStyle(s -> s.withColor(ChatFormatting.GOLD.getColor()))), false);
        }
    }

    public static void log(Player playerEntity, Component message) {
        if (playerEntity != null) {
            playerEntity.displayClientMessage(prefix.copy().append(message), false);
        }
    }

    public static void logFailure(Player playerEntity, String message) {
        if (playerEntity != null) {
            playerEntity.displayClientMessage(prefix.copy().append(Component.literal(message).withStyle(s -> s.withColor(ChatFormatting.RED.getColor()))), false);
        }
    }
}