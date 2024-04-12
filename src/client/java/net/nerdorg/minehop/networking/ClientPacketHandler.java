package net.nerdorg.minehop.networking;

import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.impl.lib.sat4j.core.Vec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.MinehopClient;
import net.nerdorg.minehop.anticheat.ProcessChecker;
import net.nerdorg.minehop.block.entity.BoostBlockEntity;
import net.nerdorg.minehop.data.DataManager;
import net.nerdorg.minehop.entity.custom.EndEntity;
import net.nerdorg.minehop.entity.custom.ResetEntity;
import net.nerdorg.minehop.entity.custom.StartEntity;
import net.nerdorg.minehop.render.RenderUtil;
import net.nerdorg.minehop.screen.SelectMapScreen;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class ClientPacketHandler {
    public static void registerReceivers() {
        ClientPlayNetworking.registerGlobalReceiver(ModMessages.CONFIG_SYNC_ID, (client, handler, buf, responseSender) -> {
            double o_sv_friction = buf.readDouble();
            double o_sv_accelerate = buf.readDouble();
            double o_sv_airaccelerate = buf.readDouble();
            double o_sv_maxairspeed = buf.readDouble();
            double o_speed_mul = buf.readDouble();
            double o_sv_gravity = buf.readDouble();

            // Ensure you are on the main thread when modifying the game or accessing client-side only classes
            client.execute(() -> {
                // Assign the read values to your variables or fields here
                Minehop.o_sv_friction = o_sv_friction;
                Minehop.o_sv_accelerate = o_sv_accelerate;
                Minehop.o_sv_airaccelerate = o_sv_airaccelerate;
                Minehop.o_sv_maxairspeed = o_sv_maxairspeed;
                Minehop.o_speed_mul = o_speed_mul;
                Minehop.o_sv_gravity = o_sv_gravity;
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(ModMessages.ZONE_SYNC_ID, (client, handler, buf, responseSender) -> {
            int entity_id = buf.readInt();
            BlockPos pos1 = buf.readBlockPos();
            BlockPos pos2 = buf.readBlockPos();
            String name = buf.readString();
            int check_index = buf.readInt();

            // Ensure you are on the main thread when modifying the game or accessing client-side only classes
            client.execute(() -> {
                // Assign the read values to your variables or fields here
                Entity entity = client.world.getEntityById(entity_id);
                if (entity instanceof ResetEntity resetEntity) {
                    resetEntity.setCorner1(pos1);
                    resetEntity.setCorner2(pos2);
                    resetEntity.setPairedMap(name);
                    resetEntity.setCheckIndex(check_index);
                }
                else if (entity instanceof StartEntity startEntity) {
                    startEntity.setCorner1(pos1);
                    startEntity.setCorner2(pos2);
                    startEntity.setPairedMap(name);
                }
                else if (entity instanceof EndEntity endEntity) {
                    endEntity.setCorner1(pos1);
                    endEntity.setCorner2(pos2);
                    endEntity.setPairedMap(name);
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(ModMessages.SELF_V_TOGGLE, (client, handler, buf, responseSender) -> {
            // Ensure you are on the main thread when modifying the game or accessing client-side only classes
            client.execute(() -> {
                MinehopClient.hideSelf = !MinehopClient.hideSelf;
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(ModMessages.OTHER_V_TOGGLE, (client, handler, buf, responseSender) -> {
            // Ensure you are on the main thread when modifying the game or accessing client-side only classes
            client.execute(() -> {
                MinehopClient.hideOthers = !MinehopClient.hideOthers;
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(ModMessages.REPLAY_V_TOGGLE, (client, handler, buf, responseSender) -> {
            // Ensure you are on the main thread when modifying the game or accessing client-side only classes
            client.execute(() -> {
                MinehopClient.hideReplay = !MinehopClient.hideReplay;
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(ModMessages.SEND_SPECTATORS, (client, handler, buf, responseSender) -> {
            // Ensure you are on the main thread when modifying the game or accessing client-side only classes
            client.execute(() -> {
                List<String> newSpectatorList = new ArrayList<>();
                int stringCount = buf.readInt();

                for (int i = 0; i < stringCount; i++) {
                    String spectatorName = buf.readString(); // This reads a string from the buffer
                    newSpectatorList.add(spectatorName);
                }

                MinehopClient.spectatorList = newSpectatorList;
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(ModMessages.SEND_EFFICIENCY, (client, handler, buf, responseSender) -> {
            // Ensure you are on the main thread when modifying the game or accessing client-side only classes
            double efficiency = buf.readDouble();

            client.execute(() -> {
                if (efficiency != 0) {
                    MinehopClient.last_efficiency = efficiency;
                }
                else {
                    if (Minehop.efficiencyListMap.containsKey(client.player.getNameForScoreboard())) {
                        List<Double> efficiencyList = Minehop.efficiencyListMap.get(client.player.getNameForScoreboard());
                        if (efficiencyList != null && efficiencyList.size() > 1) {
                            double averageEfficiency = efficiencyList.stream().mapToDouble(Double::doubleValue).average().orElse(Double.NaN);
                            MinehopClient.last_efficiency = averageEfficiency;
                            Minehop.efficiencyListMap.put(client.player.getNameForScoreboard(), new ArrayList<>());
                        }
                    }
                }
                sendSpecEfficiency();
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(ModMessages.CLIENT_SPEC_EFFICIENCY, (client, handler, buf, responseSender) -> {
            // Ensure you are on the main thread when modifying the game or accessing client-side only classes
            double last_jump_speed = buf.readDouble();
            int jump_count = buf.readInt();
            double last_efficiency = buf.readDouble();

            client.execute(() -> {
                MinehopClient.last_jump_speed = last_jump_speed;
                MinehopClient.jump_count = jump_count;
                MinehopClient.last_efficiency = last_efficiency;
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(ModMessages.OPEN_MAP_SCREEN, (client, handler, buf, responseSender) -> {

            String title = buf.readString();

            client.execute(() -> {
                client.setScreen(new SelectMapScreen(Text.literal(title)));
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(ModMessages.SEND_RECORDS, (client, handler, buf, responseSender) -> {

            List<DataManager.RecordData> newRecordList = new ArrayList<>();
            int recordCount = buf.readInt();

            for (int i = 0; i < recordCount; i++) {
                String map_name = buf.readString();
                String name = buf.readString();
                double time = buf.readDouble();
                if (time > 0) {
                    newRecordList.add(new DataManager.RecordData(name, map_name, time));
                }
            }

            client.execute(() -> {
                Minehop.recordList = newRecordList;
            });
        });
        ClientPlayNetworking.registerGlobalReceiver(ModMessages.SEND_PERSONAL_RECORDS, (client, handler, buf, responseSender) -> {

            List<DataManager.RecordData> newRecordList = new ArrayList<>();
            int recordCount = buf.readInt();

            for (int i = 0; i < recordCount; i++) {
                String map_name = buf.readString();
                String name = buf.readString();
                double time = buf.readDouble();
                if (time > 0) {
                    newRecordList.add(new DataManager.RecordData(name, map_name, time));
                }
            }

            client.execute(() -> {
                Minehop.personalRecordList = newRecordList;
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(ModMessages.UPDATE_POWER, (client, handler, buf, responseSender) -> {

            double power_x = buf.readDouble();
            double power_y = buf.readDouble();
            double power_z = buf.readDouble();

            BlockPos boosterPos = new BlockPos(buf.readInt(), buf.readInt(), buf.readInt());

            // Ensure you are on the main thread when modifying the game or accessing client-side only classes
            client.execute(() -> {
                // Assign the read values to your variables or fields here
                new Thread(() -> {
                    BlockEntity blockEntity = client.player.getWorld().getBlockEntity(boosterPos);
                    if (blockEntity instanceof BoostBlockEntity boostBlockEntity) {
                        boostBlockEntity.setXPower(power_x);
                        boostBlockEntity.setYPower(power_y);
                        boostBlockEntity.setZPower(power_z);
                    }
                }).start();
            });
        });
    }

    public static void sendHandshake() {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeInt(Minehop.MOD_VERSION);

        ClientPlayNetworking.send(ModMessages.HANDSHAKE_ID, buf);
    }

    public static void sendSpecEfficiency() {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());

        buf.writeDouble(MinehopClient.last_jump_speed);
        buf.writeInt(MinehopClient.jump_count);
        buf.writeDouble(MinehopClient.last_efficiency);

        ClientPlayNetworking.send(ModMessages.SERVER_SPEC_EFFICIENCY, buf);
    }

    public static void sendEndMapEvent(float time) {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());

        buf.writeFloat(time);

        ClientPlayNetworking.send(ModMessages.MAP_FINISH, buf);
    }

    public static void sendCurrentTime(float time) {
        if (time > MinehopClient.lastSendTime + 0.01) {
            PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());

            buf.writeFloat(time);

            ClientPlayNetworking.send(ModMessages.SEND_TIME, buf);
            MinehopClient.lastSendTime = time;
        }
    }
}
