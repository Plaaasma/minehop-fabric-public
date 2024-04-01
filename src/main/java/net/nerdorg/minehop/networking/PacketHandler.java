package net.nerdorg.minehop.networking;

import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.anticheat.AutoDisconnect;
import net.nerdorg.minehop.config.MinehopConfig;
import net.nerdorg.minehop.data.DataManager;
import net.nerdorg.minehop.util.Logger;
import net.nerdorg.minehop.util.ZoneUtil;

import java.util.HashMap;
import java.util.List;

public class PacketHandler {
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

    public static void sendAntiCheatCheck(ServerPlayerEntity player) {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());

        ServerPlayNetworking.send(player, ModMessages.ANTI_CHEAT_CHECK, buf);

        AutoDisconnect.startPlayerTimer(player);
    }

    private static void handleMapCompletion(ServerPlayerEntity player, MinecraftServer server, float time) {
        if (!player.isCreative() && !player.isSpectator()) {
            if (Minehop.timerManager.containsKey(player.getNameForScoreboard())) {
                String map_name = ZoneUtil.getCurrentMapName(player, player.getServerWorld());

                HashMap<String, Long> timerMap = Minehop.timerManager.get(player.getNameForScoreboard());
                List<String> keyList = timerMap.keySet().stream().toList();
                double rawTime = (double) (System.nanoTime() - timerMap.get(keyList.get(0))) / 1000000000;
                if (time < rawTime + 0.05 && time > rawTime - 0.05) {
                    String formattedNumber = String.format("%.5f", time);
                    DataManager.RecordData mapRecord = DataManager.getRecord(map_name);
                    if (mapRecord != null) {
                        if (time < mapRecord.time) {
                            Logger.logGlobal(server, player.getNameForScoreboard() + " just beat " + mapRecord.name + "'s time (" + String.format("%.5f", mapRecord.time) + ") on " + mapRecord.map_name + " and now hold the world record with a time of " + formattedNumber + "!");
                            Minehop.recordList.remove(mapRecord);
                            Minehop.recordList.add(new DataManager.RecordData(player.getNameForScoreboard(), map_name, time));
                            DataManager.saveRecordData(player.getServerWorld(), Minehop.recordList);
                        }
                    } else {
                        Logger.logGlobal(server, player.getNameForScoreboard() + " just claimed the world record on " + map_name + " with a time of " + formattedNumber + "!");
                        Minehop.recordList.add(new DataManager.RecordData(player.getNameForScoreboard(), map_name, time));
                        DataManager.saveRecordData(player.getServerWorld(), Minehop.recordList);
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

    public static void registerReceivers() {
        ServerPlayNetworking.registerGlobalReceiver(ModMessages.ANTI_CHEAT_CHECK, (server, player, handler, buf, responseSender) -> {
            boolean cheatSoftwareOpen = buf.readBoolean();
            String cheatSoftwareName = buf.readString();

            AutoDisconnect.stopPlayerTimer(player);

            if (cheatSoftwareOpen) {
                player.networkHandler.disconnect(Text.of("Please close " + cheatSoftwareName + "\n This software is not permitted"));
            }
        });
        ServerPlayNetworking.registerGlobalReceiver(ModMessages.SEND_TIME, (server, player, handler, buf, responseSender) -> {
            float time = buf.readFloat();
            for (String playerName : Minehop.timerManager.keySet()) {
                ServerPlayerEntity serverPlayerEntity = server.getPlayerManager().getPlayer(playerName);
                if (serverPlayerEntity != null) {
                    HashMap<String, Long> timerMap = Minehop.timerManager.get(playerName);
                    List<String> keyList = timerMap.keySet().stream().toList();
                    String mapName = keyList.get(0);
                    DataManager.RecordData personalRecordData = DataManager.getPersonalRecord(playerName, mapName);
                    double personalRecord = 0;
                    if (personalRecordData != null) {
                        personalRecord = personalRecordData.time;
                    }
                    String formattedNumber = String.format("%.5f", time);
                    Logger.logActionBar(serverPlayerEntity, "Time: " + formattedNumber + " PB: " + (personalRecord != 0 ? String.format("%.5f", personalRecord) : "No PB"));
                }
            }
        });
        ServerPlayNetworking.registerGlobalReceiver(ModMessages.MAP_FINISH, (server, player, handler, buf, responseSender) -> {
            float time = buf.readFloat();
            handleMapCompletion(player, server, time);
        });
    }

}
