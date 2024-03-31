package net.nerdorg.minehop.networking;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.anticheat.ProcessChecker;
import net.nerdorg.minehop.entity.custom.EndEntity;
import net.nerdorg.minehop.entity.custom.ResetEntity;
import net.nerdorg.minehop.entity.custom.StartEntity;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class ClientPacketHandler {
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
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

            // Ensure you are on the main thread when modifying the game or accessing client-side only classes
            client.execute(() -> {
                // Assign the read values to your variables or fields here
                Entity entity = client.world.getEntityById(entity_id);
                if (entity instanceof ResetEntity resetEntity) {
                    resetEntity.setCorner1(pos1);
                    resetEntity.setCorner2(pos2);
                    resetEntity.setPairedMap(name);
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

        ClientPlayNetworking.registerGlobalReceiver(ModMessages.ANTI_CHEAT_CHECK, (client, handler, buf, responseSender) -> {

            // Ensure you are on the main thread when modifying the game or accessing client-side only classes
            client.execute(() -> {
                // Assign the read values to your variables or fields here
                Boolean antiCheatCheck = ProcessChecker.isProcessRunning("rawaccel.exe");
                System.out.println(antiCheatCheck + "=> rawaccel check");
            });
        });
    }
}
