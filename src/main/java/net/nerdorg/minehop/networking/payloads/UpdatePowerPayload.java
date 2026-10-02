package net.nerdorg.minehop.networking.payloads;

import net.minecraft.network.PacketByteBuf;
import net.nerdorg.minehop.networking.codec.PacketCodec;
import net.nerdorg.minehop.networking.codec.PacketCodecs;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.util.Identifier;
import net.nerdorg.minehop.Minehop;

public record UpdatePowerPayload(double x_power, double y_power, double z_power, int posX, int posY, int posZ) implements FabricPacket {
    public static final Identifier HANDSHAKE_ID = new Identifier(Minehop.MOD_ID, "update_power");
    public static final PacketType<UpdatePowerPayload> ID = PacketType.create(HANDSHAKE_ID, buf -> UpdatePowerPayload.CODEC.decode(buf));
    public static final PacketCodec<PacketByteBuf, UpdatePowerPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.DOUBLE, UpdatePowerPayload::x_power,
            PacketCodecs.DOUBLE, UpdatePowerPayload::y_power,
            PacketCodecs.DOUBLE, UpdatePowerPayload::z_power,
            PacketCodecs.INTEGER, UpdatePowerPayload::posX,
            PacketCodecs.INTEGER, UpdatePowerPayload::posY,
            PacketCodecs.INTEGER, UpdatePowerPayload::posZ,
            UpdatePowerPayload::new);

    @Override
    public void write(PacketByteBuf buf) {
        CODEC.encode(buf, this);
    }

    @Override
    public PacketType<?> getType() {
        return ID;
    }
}
