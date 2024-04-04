package net.nerdorg.minehop.commands;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.entity.SpawnReason;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.data.DataManager;
import net.nerdorg.minehop.entity.ModEntities;
import net.nerdorg.minehop.entity.custom.ReplayEntity;
import net.nerdorg.minehop.replays.ReplayManager;

import java.util.UUID;

public class ReplayCommands {
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
            LiteralArgumentBuilder.<ServerCommandSource>literal("replay")
                .requires(source -> source.hasPermissionLevel(4))
                    .then(RequiredArgumentBuilder.<ServerCommandSource, String>argument("map_name", StringArgumentType.string())
                        .executes(context -> {
                            handleAddReplay(context);
                            return Command.SINGLE_SUCCESS;
                        })
                    )
            ));
    }

    private static void handleAddReplay(CommandContext<ServerCommandSource> context) {
        MinecraftServer server = context.getSource().getServer();
        String mapName = StringArgumentType.getString(context, "map_name");
        ReplayManager.Replay replay = ReplayManager.getReplay(mapName);
        if (replay != null) {
            DataManager.MapData mapData = DataManager.getMap(mapName);
            if (mapData != null) {
                ServerWorld foundWorld = null;
                for (ServerWorld serverWorld : context.getSource().getServer().getWorlds()) {
                    if (serverWorld.getRegistryKey().toString().equals(mapData.worldKey)) {
                        foundWorld = serverWorld;
                        break;
                    }
                }
                if (foundWorld != null) {
                    ReplayEntity replayEntity = ModEntities.REPLAY_ENTITY.spawn(foundWorld, new BlockPos((int) mapData.x, (int) mapData.y, (int) mapData.z), SpawnReason.NATURAL);
                    replayEntity.setMapName(mapName);
                }
            }
        }
    }
}
