package net.nerdorg.minehop.networking.payloads;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.nerdorg.minehop.Minehop;

public record UpdatePowerPayload(double x_power, double y_power, double z_power, int posX, int posY, int posZ) implements CustomPacketPayload {
    public static final Identifier HANDSHAKE_ID = Identifier.fromNamespaceAndPath(Minehop.MOD_ID, "update_power");
    public static final Type<UpdatePowerPayload> ID = new Type<>(HANDSHAKE_ID);
    public static final StreamCodec<FriendlyByteBuf, UpdatePowerPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.DOUBLE, UpdatePowerPayload::x_power,
            ByteBufCodecs.DOUBLE, UpdatePowerPayload::y_power,
            ByteBufCodecs.DOUBLE, UpdatePowerPayload::z_power,
            ByteBufCodecs.INT, UpdatePowerPayload::posX,
            ByteBufCodecs.INT, UpdatePowerPayload::posY,
            ByteBufCodecs.INT, UpdatePowerPayload::posZ,
            UpdatePowerPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
