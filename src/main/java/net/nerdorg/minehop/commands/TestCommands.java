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
    }

    private static void handleTest(CommandContext<ServerCommandSource> context) {

    }
}
