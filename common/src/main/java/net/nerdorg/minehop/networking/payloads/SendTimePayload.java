package net.nerdorg.minehop.networking.payloads;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.nerdorg.minehop.Minehop;

public record SendTimePayload(float time) implements CustomPacketPayload {
    public static final Identifier HANDSHAKE_ID = Identifier.fromNamespaceAndPath(Minehop.MOD_ID, "send_time");
    public static final Type<SendTimePayload> ID = new Type<>(HANDSHAKE_ID);
    public static final StreamCodec<FriendlyByteBuf, SendTimePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, SendTimePayload::time,
            SendTimePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
