package net.nerdorg.minehop.networking;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.ParseResults;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.commands.SpectateCommands;
import net.nerdorg.minehop.config.MinehopConfig;
import net.nerdorg.minehop.data.DataManager;
import net.nerdorg.minehop.discord.DiscordIntegration;
import net.nerdorg.minehop.replays.ReplayEvents;
import net.nerdorg.minehop.replays.ReplayManager;
import net.nerdorg.minehop.util.Logger;
import net.nerdorg.minehop.util.ZoneUtil;

import java.util.HashMap;
import java.util.List;

public class PacketHandler {
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public static void sendConfigToClient(ServerPlayerEntity player, MinehopConfig config) {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());

        buf.writeDouble(config.sv_friction);
        buf.writeDouble(config.sv_accelerate);
        buf.writeDouble(config.sv_airaccelerate);
        buf.writeDouble(config.sv_maxairspeed);
        buf.writeDouble(config.speed_mul);
        buf.writeDouble(config.sv_gravity);

        ServerPlayNetworking.send(player, ModMessages.CONFIG_SYNC_ID, buf);
    }
    public static void updateZone(ServerPlayerEntity player, int entityId, BlockPos pos1, BlockPos pos2, String name, int check_index) {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());

        buf.writeInt(entityId);
        buf.writeBlockPos(pos1);
        buf.writeBlockPos(pos2);
        buf.writeString(name);
        buf.writeInt(check_index);

        ServerPlayNetworking.send(player, ModMessages.ZONE_SYNC_ID, buf);
    }

    public static void sendSelfVToggle(ServerPlayerEntity player) {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());

        ServerPlayNetworking.send(player, ModMessages.SELF_V_TOGGLE, buf);
    }

    public static void sendOtherVToggle(ServerPlayerEntity player) {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());

        ServerPlayNetworking.send(player, ModMessages.OTHER_V_TOGGLE, buf);
    }

    public static void sendReplayVToggle(ServerPlayerEntity player) {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());

        ServerPlayNetworking.send(player, ModMessages.REPLAY_V_TOGGLE, buf);
    }

    public static void sendEfficiency(ServerPlayerEntity player, double efficiency) {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeDouble(efficiency);

        ServerPlayNetworking.send(player, ModMessages.SEND_EFFICIENCY, buf);
    }

    public static void sendSpectators(ServerPlayerEntity player) {
        if (SpectateCommands.spectatorList.containsKey(player.getNameForScoreboard())) {
            List<String> spectators = SpectateCommands.spectatorList.get(player.getNameForScoreboard());
            PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
            buf.writeInt(spectators.size() - 1);
            for (String spectator : spectators) {
                if (!spectator.equals(player.getNameForScoreboard())) {
                    buf.writeString(spectator);
                }
            }

            ServerPlayNetworking.send(player, ModMessages.SEND_SPECTATORS, buf);
        }
    }

    private static void handleMapCompletion(ServerPlayerEntity player, MinecraftServer server, float time) {
        float ping_limit = 300; // ping limit in ms
        if (!player.isCreative() && !player.isSpectator()) {
            if (Minehop.timerManager.containsKey(player.getNameForScoreboard())) {
                String map_name = ZoneUtil.getCurrentMapName(player);

                HashMap<String, Long> timerMap = Minehop.timerManager.get(player.getNameForScoreboard());
                List<String> keyList = timerMap.keySet().stream().toList();
                double rawTime = (double) (System.nanoTime() - timerMap.get(keyList.get(0))) / 1000000000;
                if (time < rawTime + (ping_limit / 1000f) && time > rawTime - (ping_limit / 1000f)) {
                    String formattedNumber = String.format("%.5f", time);
                    DataManager.RecordData mapRecord = DataManager.getRecord(map_name);
                    if (mapRecord != null) {
                        if (time < mapRecord.time) {
                            String recordMessage = player.getNameForScoreboard() + " just beat " + mapRecord.name + "'s time (" + String.format("%.5f", mapRecord.time) + ") on " + mapRecord.map_name + " and now hold the world record with a time of " + formattedNumber + "!";
                            DiscordIntegration.sendRecordToDiscord(recordMessage);
                            Logger.logGlobal(server, recordMessage);
                            Minehop.recordList.remove(mapRecord);
                            if (DataManager.getAnyRecordFromName(mapRecord.name) == null) {
                                server.getCommandManager().execute(server.getCommandSource().getDispatcher().parse("lp user " + mapRecord.name + " parent remove record_holder", server.getCommandSource()), "lp user " + mapRecord.name + " parent remove record_holder");
                            }
                            server.getCommandManager().execute(server.getCommandSource().getDispatcher().parse("lp user " + player.getNameForScoreboard() + " parent add record_holder", server.getCommandSource()), "lp user " + player.getNameForScoreboard() + " parent add record_holder");
                            Minehop.recordList.add(new DataManager.RecordData(player.getNameForScoreboard(), map_name, time));
                            DataManager.saveRecordData(player.getServerWorld(), Minehop.recordList);
                            ReplayManager.Replay replay = new ReplayManager.Replay(map_name, player.getNameForScoreboard(), time, ReplayEvents.replayEntryMap.get(player.getNameForScoreboard()));
                            ReplayManager.saveRecordReplay(player.getServerWorld(), replay);
                        }
                    } else {
                        String recordMessage = player.getNameForScoreboard() + " just claimed the world record on " + map_name + " with a time of " + formattedNumber + "!";
                        DiscordIntegration.sendRecordToDiscord(recordMessage);
                        Logger.logGlobal(server, recordMessage);
                        server.getCommandManager().execute(server.getCommandSource().getDispatcher().parse("lp user " + player.getNameForScoreboard() + " parent add record_holder", server.getCommandSource()), "lp user " + player.getNameForScoreboard() + " parent add record_holder");
                        Minehop.recordList.add(new DataManager.RecordData(player.getNameForScoreboard(), map_name, time));
                        DataManager.saveRecordData(player.getServerWorld(), Minehop.recordList);
                        ReplayManager.Replay replay = new ReplayManager.Replay(map_name, player.getNameForScoreboard(), time, ReplayEvents.replayEntryMap.get(player.getNameForScoreboard()));
                        ReplayManager.saveRecordReplay(player.getServerWorld(), replay);
                    }
                    DataManager.RecordData mapPersonalRecord = DataManager.getPersonalRecord(player.getNameForScoreboard(), map_name);
                    if (mapPersonalRecord != null) {
                        if (time < mapPersonalRecord.time) {
                            Logger.logSuccess(player, "You just beat your time (" + String.format("%.5f", mapPersonalRecord.time) + ") on " + mapPersonalRecord.map_name + ", your new record is " + formattedNumber + "!");
                            Minehop.personalRecordList.remove(mapPersonalRecord);
                            Minehop.personalRecordList.add(new DataManager.RecordData(player.getNameForScoreboard(), map_name, time));
                            DataManager.savePersonalRecordData(player.getServerWorld(), Minehop.personalRecordList);
                        }
                    } else {
                        Logger.logSuccess(player, "You just claimed a personal record of " + formattedNumber + "!");
                        Minehop.personalRecordList.add(new DataManager.RecordData(player.getNameForScoreboard(), map_name, time));
                        DataManager.savePersonalRecordData(player.getServerWorld(), Minehop.personalRecordList);
                    }
                    Logger.logSuccess(player, "Completed " + map_name + " in " + formattedNumber + " seconds.");
                } else {
                    Logger.logServer(server, "Invalid time for " + player.getNameForScoreboard() + ".");
                }
                Minehop.timerManager.remove(player.getNameForScoreboard());
            }
        }
    }

    public static void sendSpecEfficiency(ServerPlayerEntity player, double last_jump_speed, int jump_count, double last_efficiency) {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());

        buf.writeDouble(last_jump_speed);
        buf.writeInt(jump_count);
        buf.writeDouble(last_efficiency);

        ServerPlayNetworking.send(player, ModMessages.CLIENT_SPEC_EFFICIENCY, buf);
    }

    public static void sendOpenMapScreen(ServerPlayerEntity player, String title) {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());

        buf.writeString(title);

        ServerPlayNetworking.send(player, ModMessages.OPEN_MAP_SCREEN, buf);
    }

    public static void sendRecords(ServerPlayerEntity player) {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());

        buf.writeInt(Minehop.mapList.size());

        for (DataManager.MapData mapData : Minehop.mapList) {
            DataManager.RecordData recordData = DataManager.getRecord(mapData.name);
            if (recordData != null) {
                buf.writeString(recordData.map_name);
                buf.writeString(recordData.name);
                buf.writeDouble(recordData.time);
            }
            else {
                buf.writeString(mapData.name);
                buf.writeString("None");
                buf.writeDouble(0);
            }
        }

        ServerPlayNetworking.send(player, ModMessages.SEND_RECORDS, buf);
    }

    public static void sendPersonalRecords(ServerPlayerEntity player) {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());

        buf.writeInt(Minehop.personalRecordList.size());

        for (DataManager.RecordData recordData : Minehop.personalRecordList) {
            buf.writeString(recordData.map_name);
            buf.writeString(recordData.name);
            buf.writeDouble(recordData.time);
        }

        ServerPlayNetworking.send(player, ModMessages.SEND_PERSONAL_RECORDS, buf);
    }

    public static void sendPower(ServerPlayerEntity player, double x_power, double y_power, double z_power, BlockPos boosterPos) {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());

        buf.writeDouble(x_power);
        buf.writeDouble(y_power);
        buf.writeDouble(z_power);

        buf.writeInt(boosterPos.getX());
        buf.writeInt(boosterPos.getY());
        buf.writeInt(boosterPos.getZ());

        ServerPlayNetworking.send(player, ModMessages.UPDATE_POWER, buf);
    }


    public static void registerReceivers() {
        ServerPlayNetworking.registerGlobalReceiver(ModMessages.SEND_TIME, (server, player, handler, buf, responseSender) -> {
            if (!player.isSpectator()) {
                float time = buf.readFloat();
                if (player != null && Minehop.timerManager.containsKey(player.getNameForScoreboard())) {
                    HashMap<String, Long> timerMap = Minehop.timerManager.get(player.getNameForScoreboard());
                    List<String> keyList = timerMap.keySet().stream().toList();
                    String mapName = keyList.get(0);
                    DataManager.RecordData personalRecordData = DataManager.getPersonalRecord(player.getNameForScoreboard(), mapName);
                    double personalRecord = 0;
                    if (personalRecordData != null) {
                        personalRecord = personalRecordData.time;
                    }
                    String formattedNumber = String.format("%.2f", time);
                    if (SpectateCommands.spectatorList.containsKey(player.getNameForScoreboard())) {
                        List<String> spectators = SpectateCommands.spectatorList.get(player.getNameForScoreboard());
                        for (String spectatorName : spectators) {
                            if (!spectatorName.equals(player.getNameForScoreboard())) {
                                ServerPlayerEntity spectatorPlayer = server.getPlayerManager().getPlayer(spectatorName);
                                if (!spectatorPlayer.isCreative()) {
                                    spectatorPlayer.getInventory().clear();
                                }
                                spectatorPlayer.teleport(player.getX(), player.getY(), player.getZ());
                                spectatorPlayer.setCameraEntity(player);
                                Logger.logActionBar(spectatorPlayer, "Time: " + formattedNumber + " PB: " + (personalRecord != 0 ? String.format("%.5f", personalRecord) : "No PB"));
                            }
                        }
                    }
                    Logger.logActionBar(player, "Time: " + formattedNumber + " PB: " + (personalRecord != 0 ? String.format("%.5f", personalRecord) : "No PB"));
                }
            }
        });
        ServerPlayNetworking.registerGlobalReceiver(ModMessages.MAP_FINISH, (server, player, handler, buf, responseSender) -> {
            float time = buf.readFloat();
            handleMapCompletion(player, server, time);
        });
        ServerPlayNetworking.registerGlobalReceiver(ModMessages.SERVER_SPEC_EFFICIENCY, (server, player, handler, buf, responseSender) -> {
            double last_jump_speed = buf.readDouble();
            int jump_count = buf.readInt();
            double last_efficiency = buf.readDouble();

            Minehop.lastEfficiencyMap.put(player.getNameForScoreboard(), new ReplayManager.SSJEntry(jump_count, last_jump_speed, last_efficiency));

            if (SpectateCommands.spectatorList.containsKey(player.getNameForScoreboard())) {
                List<String> spectators = SpectateCommands.spectatorList.get(player.getNameForScoreboard());
                for (String spectator : spectators) {
                    ServerPlayerEntity spectatorPlayer = server.getPlayerManager().getPlayer(spectator);
                    if (spectatorPlayer != null) {
                        if (!spectatorPlayer.getNameForScoreboard().equals(player.getNameForScoreboard())) {
                            sendSpecEfficiency(spectatorPlayer, last_jump_speed, jump_count, last_efficiency);
                        }
                    }
                }
            }
        });
    }

}
