package net.nerdorg.minehop.networking.payloads;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.nerdorg.minehop.Minehop;

public record HandshakeIDPayload(int mod_version) implements CustomPacketPayload {
    public static final Identifier HANDSHAKE_ID = Identifier.fromNamespaceAndPath(Minehop.MOD_ID, "handshake_id");
    public static final CustomPacketPayload.Type<HandshakeIDPayload> ID = new CustomPacketPayload.Type<>(HANDSHAKE_ID);
    public static final StreamCodec<FriendlyByteBuf, HandshakeIDPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, HandshakeIDPayload::mod_version,
            HandshakeIDPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
