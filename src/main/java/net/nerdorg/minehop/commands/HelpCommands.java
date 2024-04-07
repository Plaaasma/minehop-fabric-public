package net.nerdorg.minehop.commands;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.nerdorg.minehop.block.entity.BoostBlockEntity;
import net.nerdorg.minehop.util.Logger;

public class HelpCommands {
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
            LiteralArgumentBuilder.<ServerCommandSource>literal("help")
                .executes(context -> {
                    handleHelp(context);
                    return Command.SINGLE_SUCCESS;
                })
            ));
    }

    private static void handleHelp(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity serverPlayerEntity = context.getSource().getPlayer();

        Logger.logSuccess(serverPlayerEntity, """
                Use /map and all of it's sub commands to list maps, see the top times, and go to maps.
                
                You can do /hide <self | others> in order to toggle hiding your hand and hotbar (this is different than f1 because it still shows other HUD elements) or to toggle hiding other players.
                
                You can use /spec <player | replay_name> in order to spectate a player/replay and /unspec to stop spectating.
                
                Thank you for playing with minehop! Please submit maps in the discord (/discord)!
                """);
    }
}
