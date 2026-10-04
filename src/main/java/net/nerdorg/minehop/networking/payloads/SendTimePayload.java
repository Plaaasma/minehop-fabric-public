package net.nerdorg.minehop.networking.payloads;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.nerdorg.minehop.Minehop;

public record SendTimePayload(float time) implements CustomPacketPayload {
    public static final ResourceLocation HANDSHAKE_ID = ResourceLocation.fromNamespaceAndPath(Minehop.MOD_ID, "send_time");
    public static final Type<SendTimePayload> ID = new Type<>(HANDSHAKE_ID);
    public static final StreamCodec<FriendlyByteBuf, SendTimePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, SendTimePayload::time,
            SendTimePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
