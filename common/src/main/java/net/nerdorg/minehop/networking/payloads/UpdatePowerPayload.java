package net.nerdorg.minehop.networking.payloads;

import net.minecraft.network.FriendlyByteBuf;
import net.nerdorg.minehop.networking.codec.ByteBufCodecs;
import net.nerdorg.minehop.networking.codec.StreamCodec;
import net.nerdorg.minehop.networking.codec.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.nerdorg.minehop.Minehop;

public record UpdatePowerPayload(double x_power, double y_power, double z_power, int posX, int posY, int posZ) implements CustomPacketPayload {
    public static final ResourceLocation HANDSHAKE_ID = new ResourceLocation(Minehop.MOD_ID, "update_power");
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
