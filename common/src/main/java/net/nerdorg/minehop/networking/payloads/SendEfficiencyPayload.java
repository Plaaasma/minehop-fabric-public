package net.nerdorg.minehop.networking.payloads;

import net.minecraft.network.FriendlyByteBuf;
import net.nerdorg.minehop.networking.codec.ByteBufCodecs;
import net.nerdorg.minehop.networking.codec.StreamCodec;
import net.nerdorg.minehop.networking.codec.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.nerdorg.minehop.Minehop;

public record SendEfficiencyPayload(double efficiency) implements CustomPacketPayload {
    public static final ResourceLocation HANDSHAKE_ID = new ResourceLocation(Minehop.MOD_ID, "send_efficiency");
    public static final Type<SendEfficiencyPayload> ID = new Type<>(HANDSHAKE_ID);
    public static final StreamCodec<FriendlyByteBuf, SendEfficiencyPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.DOUBLE, SendEfficiencyPayload::efficiency,
            SendEfficiencyPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
