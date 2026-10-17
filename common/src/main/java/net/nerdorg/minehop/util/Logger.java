package net.nerdorg.minehop.util;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.chat.TextColor;
import java.util.List;

public class Logger {
    private static Component prefix = Component.literal("NerdOrg ").withColor(TextColor.GREEN.getValue()).append(Component.literal("(").withColor(TextColor.GRAY.getValue())).append(Component.literal("Minehop").withColor(TextColor.LIGHT_PURPLE.getValue()).withStyle(ChatFormatting.ITALIC)).append(Component.literal(") ").withColor(TextColor.GRAY.getValue())).append(Component.literal("-> ").withColor(TextColor.DARK_GRAY.getValue()));

    public static void logGlobal(MinecraftServer server, String message) {
        List<ServerPlayer> playerEntities = server.getPlayerList().getPlayers();

        for (ServerPlayer playerEntity : playerEntities) {
            playerEntity.sendSystemMessage(prefix.copy().append(Component.literal(message).withColor(TextColor.AQUA.getValue())));
        }
    }

    public static void logGlobalColor(MinecraftServer server, String message, ChatFormatting color) {
        List<ServerPlayer> playerEntities = server.getPlayerList().getPlayers();

        for (ServerPlayer playerEntity : playerEntities) {
            playerEntity.sendSystemMessage(prefix.copy().append(Component.literal(message).withColor(TextColor.fromLegacyFormat(color).getValue())));
        }
    }

    public static void logGlobal(MinecraftServer server, Component message) {
        List<ServerPlayer> playerEntities = server.getPlayerList().getPlayers();

        for (ServerPlayer playerEntity : playerEntities) {
            playerEntity.sendSystemMessage(prefix.copy().append(message.copy().withColor(TextColor.AQUA.getValue())));
        }
    }

    public static void logServer(MinecraftServer server, String message) {
        server.sendSystemMessage(prefix.copy().append(Component.literal(message).withColor(TextColor.AQUA.getValue())));
    }

    public static void logActionBar(Player playerEntity, String message) {
        if (playerEntity != null) {
            playerEntity.sendOverlayMessage(Component.literal(message).withColor(TextColor.AQUA.getValue()));
        }
    }

    public static void logSuccess(Player playerEntity, String message) {
        if (playerEntity != null) {
            playerEntity.sendSystemMessage(prefix.copy().append(Component.literal(message).withColor(TextColor.GOLD.getValue())));
        }
    }

    public static void log(Player playerEntity, Component message) {
        if (playerEntity != null) {
            playerEntity.sendSystemMessage(prefix.copy().append(message));
        }
    }

    public static void logFailure(Player playerEntity, String message) {
        if (playerEntity != null) {
            playerEntity.sendSystemMessage(prefix.copy().append(Component.literal(message).withColor(TextColor.RED.getValue())));
        }
    }
}