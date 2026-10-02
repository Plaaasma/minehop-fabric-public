package net.nerdorg.minehop.networking.payloads;

import net.minecraft.network.PacketByteBuf;
import net.nerdorg.minehop.networking.codec.PacketCodec;
import net.nerdorg.minehop.networking.codec.PacketCodecs;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.util.Identifier;
import net.nerdorg.minehop.Minehop;

public record SendSpectatorsPayload(String spectatorBuff) implements FabricPacket {
    public static final Identifier HANDSHAKE_ID = new Identifier(Minehop.MOD_ID, "send_spectators");
    public static final PacketType<SendSpectatorsPayload> ID = PacketType.create(HANDSHAKE_ID, buf -> SendSpectatorsPayload.CODEC.decode(buf));
    public static final PacketCodec<PacketByteBuf, SendSpectatorsPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING, SendSpectatorsPayload::spectatorBuff,
            SendSpectatorsPayload::new);

    @Override
    public void write(PacketByteBuf buf) {
        CODEC.encode(buf, this);
    }

    @Override
    public PacketType<?> getType() {
        return ID;
    }
}
