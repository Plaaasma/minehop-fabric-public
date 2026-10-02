package net.nerdorg.minehop.networking.payloads;

import net.minecraft.network.PacketByteBuf;
import net.nerdorg.minehop.networking.codec.PacketCodec;
import net.nerdorg.minehop.networking.codec.PacketCodecs;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.util.Identifier;
import net.nerdorg.minehop.Minehop;

public record HandshakeIDPayload(int mod_version) implements FabricPacket {
    public static final Identifier HANDSHAKE_ID = new Identifier(Minehop.MOD_ID, "handshake_id");
    public static final PacketType<HandshakeIDPayload> ID = PacketType.create(HANDSHAKE_ID, buf -> HandshakeIDPayload.CODEC.decode(buf));
    public static final PacketCodec<PacketByteBuf, HandshakeIDPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.INTEGER, HandshakeIDPayload::mod_version,
            HandshakeIDPayload::new);

    @Override
    public void write(PacketByteBuf buf) {
        CODEC.encode(buf, this);
    }

    @Override
    public PacketType<?> getType() {
        return ID;
    }
}
