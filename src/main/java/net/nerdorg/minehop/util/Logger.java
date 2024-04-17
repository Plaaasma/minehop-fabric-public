package net.nerdorg.minehop.util;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

public class Logger {
    private static Text prefix = Text.literal("NerdOrg ").withColor(Formatting.GREEN.getColorValue()).append(Text.literal("(").withColor(Formatting.GRAY.getColorValue())).append(Text.literal("Minehop").withColor(Formatting.LIGHT_PURPLE.getColorValue()).formatted(Formatting.ITALIC)).append(Text.literal(") ").withColor(Formatting.GRAY.getColorValue())).append(Text.literal("-> ").withColor(Formatting.DARK_GRAY.getColorValue()));

    public static void logGlobal(MinecraftServer server, String message) {
        List<ServerPlayerEntity> playerEntities = server.getPlayerManager().getPlayerList();

        for (ServerPlayerEntity playerEntity : playerEntities) {
            playerEntity.sendMessage(prefix.copy().append(Text.literal(message).withColor(Formatting.AQUA.getColorValue())));
        }
    }

    public static void logGlobalColor(MinecraftServer server, String message, Formatting color) {
        List<ServerPlayerEntity> playerEntities = server.getPlayerManager().getPlayerList();

        for (ServerPlayerEntity playerEntity : playerEntities) {
            playerEntity.sendMessage(prefix.copy().append(Text.literal(message).withColor(color.getColorValue())));
        }
    }

    public static void logGlobal(MinecraftServer server, Text message) {
        List<ServerPlayerEntity> playerEntities = server.getPlayerManager().getPlayerList();

        for (ServerPlayerEntity playerEntity : playerEntities) {
            playerEntity.sendMessage(prefix.copy().append(message.copy().withColor(Formatting.AQUA.getColorValue())));
        }
    }

    public static void logServer(MinecraftServer server, String message) {
        server.sendMessage(prefix.copy().append(Text.literal(message).withColor(Formatting.AQUA.getColorValue())));
    }

    public static void logActionBar(PlayerEntity playerEntity, String message) {
        if (playerEntity != null) {
            playerEntity.sendMessage(Text.literal(message).withColor(Formatting.AQUA.getColorValue()), true);
        }
    }

    public static void logSuccess(PlayerEntity playerEntity, String message) {
        if (playerEntity != null) {
            playerEntity.sendMessage(prefix.copy().append(Text.literal(message).withColor(Formatting.GOLD.getColorValue())));
        }
    }

    public static void log(PlayerEntity playerEntity, Text message) {
        if (playerEntity != null) {
            playerEntity.sendMessage(prefix.copy().append(message));
        }
    }

    public static void logFailure(PlayerEntity playerEntity, String message) {
        if (playerEntity != null) {
            playerEntity.sendMessage(prefix.copy().append(Text.literal(message).withColor(Formatting.RED.getColorValue())));
        }
    }
}