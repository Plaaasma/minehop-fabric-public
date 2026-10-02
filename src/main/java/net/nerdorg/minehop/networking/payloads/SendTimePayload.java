package net.nerdorg.minehop.networking.payloads;

import net.minecraft.network.PacketByteBuf;
import net.nerdorg.minehop.networking.codec.PacketCodec;
import net.nerdorg.minehop.networking.codec.PacketCodecs;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.util.Identifier;
import net.nerdorg.minehop.Minehop;

public record SendTimePayload(float time) implements FabricPacket {
    public static final Identifier HANDSHAKE_ID = new Identifier(Minehop.MOD_ID, "send_time");
    public static final PacketType<SendTimePayload> ID = PacketType.create(HANDSHAKE_ID, buf -> SendTimePayload.CODEC.decode(buf));
    public static final PacketCodec<PacketByteBuf, SendTimePayload> CODEC = PacketCodec.tuple(
            PacketCodecs.FLOAT, SendTimePayload::time,
            SendTimePayload::new);

    @Override
    public void write(PacketByteBuf buf) {
        CODEC.encode(buf, this);
    }

    @Override
    public PacketType<?> getType() {
        return ID;
    }
}
