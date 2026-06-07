package net.nerdorg.minehop.networking.payloads;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.nerdorg.minehop.Minehop;

public record SendTimePayload(float time) implements CustomPayload {
    public static final Identifier HANDSHAKE_ID = Identifier.of(Minehop.MOD_ID, "send_time");
    public static final Id<SendTimePayload> ID = new Id<>(HANDSHAKE_ID);
    public static final PacketCodec<PacketByteBuf, SendTimePayload> CODEC = PacketCodec.tuple(
            PacketCodecs.FLOAT, SendTimePayload::time,
            SendTimePayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
