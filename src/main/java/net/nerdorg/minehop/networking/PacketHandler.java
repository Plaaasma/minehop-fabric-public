package net.nerdorg.minehop.networking;

import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.nerdorg.minehop.config.MinehopConfig;
import net.nerdorg.minehop.entity.custom.ResetEntity;

import javax.swing.text.html.parser.Entity;

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

    public static void sendAntiCheatCheck(ServerPlayerEntity player) {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        ServerPlayNetworking.send(player, ModMessages.ANTI_CHEAT_CHECK, buf);
    }



}
