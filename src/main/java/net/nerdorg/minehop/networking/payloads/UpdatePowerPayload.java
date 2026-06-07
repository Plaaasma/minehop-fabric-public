package net.nerdorg.minehop.networking.payloads;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.nerdorg.minehop.Minehop;

public record UpdatePowerPayload(double x_power, double y_power, double z_power, int posX, int posY, int posZ) implements CustomPayload {
    public static final Identifier HANDSHAKE_ID = Identifier.of(Minehop.MOD_ID, "update_power");
    public static final Id<UpdatePowerPayload> ID = new Id<>(HANDSHAKE_ID);
    public static final PacketCodec<PacketByteBuf, UpdatePowerPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.DOUBLE, UpdatePowerPayload::x_power,
            PacketCodecs.DOUBLE, UpdatePowerPayload::y_power,
            PacketCodecs.DOUBLE, UpdatePowerPayload::z_power,
            PacketCodecs.INTEGER, UpdatePowerPayload::posX,
            PacketCodecs.INTEGER, UpdatePowerPayload::posY,
            PacketCodecs.INTEGER, UpdatePowerPayload::posZ,
            UpdatePowerPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
