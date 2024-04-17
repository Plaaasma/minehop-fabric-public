package net.nerdorg.minehop.util;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

public class Logger {
    private static Text prefix = Text.literal("NerdOrg ").formatted(Formatting.GREEN).append(Text.literal("(").formatted(Formatting.GRAY)).append(Text.literal("Minehop").formatted(Formatting.LIGHT_PURPLE).formatted(Formatting.ITALIC)).append(Text.literal(") ").formatted(Formatting.GRAY)).append(Text.literal("-> ").formatted(Formatting.DARK_GRAY));

    public static void logGlobal(MinecraftServer server, String message) {
        List<ServerPlayerEntity> playerEntities = server.getPlayerManager().getPlayerList();

        for (ServerPlayerEntity playerEntity : playerEntities) {
            playerEntity.sendMessage(prefix.copy().append(Text.literal(message).formatted(Formatting.AQUA)));
        }
    }

    public static void logGlobalColor(MinecraftServer server, String message, Formatting color) {
        List<ServerPlayerEntity> playerEntities = server.getPlayerManager().getPlayerList();

        for (ServerPlayerEntity playerEntity : playerEntities) {
            playerEntity.sendMessage(prefix.copy().append(Text.literal(message).formatted(color)));
        }
    }

    public static void logGlobal(MinecraftServer server, Text message) {
        List<ServerPlayerEntity> playerEntities = server.getPlayerManager().getPlayerList();

        for (ServerPlayerEntity playerEntity : playerEntities) {
            playerEntity.sendMessage(prefix.copy().append(message.copy().formatted(Formatting.AQUA)));
        }
    }

    public static void logServer(MinecraftServer server, String message) {
        server.sendMessage(prefix.copy().append(Text.literal(message).formatted(Formatting.AQUA)));
    }

    public static void logActionBar(PlayerEntity playerEntity, String message) {
        if (playerEntity != null) {
            playerEntity.sendMessage(Text.literal(message).formatted(Formatting.AQUA), true);
        }
    }

    public static void logSuccess(PlayerEntity playerEntity, String message) {
        if (playerEntity != null) {
            playerEntity.sendMessage(prefix.copy().append(Text.literal(message).formatted(Formatting.GOLD)));
        }
    }

    public static void log(PlayerEntity playerEntity, Text message) {
        if (playerEntity != null) {
            playerEntity.sendMessage(prefix.copy().append(message));
        }
    }

    public static void logFailure(PlayerEntity playerEntity, String message) {
        if (playerEntity != null) {
            playerEntity.sendMessage(prefix.copy().append(Text.literal(message).formatted(Formatting.RED)));
        }
    }
}