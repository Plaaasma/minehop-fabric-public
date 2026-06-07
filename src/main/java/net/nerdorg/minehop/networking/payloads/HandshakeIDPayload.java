package net.nerdorg.minehop.networking.payloads;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.nerdorg.minehop.Minehop;

public record HandshakeIDPayload(int mod_version) implements CustomPayload {
    public static final Identifier HANDSHAKE_ID = Identifier.of(Minehop.MOD_ID, "handshake_id");
    public static final CustomPayload.Id<HandshakeIDPayload> ID = new CustomPayload.Id<>(HANDSHAKE_ID);
    public static final PacketCodec<PacketByteBuf, HandshakeIDPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.INTEGER, HandshakeIDPayload::mod_version,
            HandshakeIDPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
