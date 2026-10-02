package net.nerdorg.minehop.networking.payloads;

import net.minecraft.network.PacketByteBuf;
import net.nerdorg.minehop.networking.codec.PacketCodec;
import net.nerdorg.minehop.networking.codec.PacketCodecs;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.util.Identifier;
import net.nerdorg.minehop.Minehop;

public record OpenMapScreenPayload(String title) implements FabricPacket {
    public static final Identifier HANDSHAKE_ID = new Identifier(Minehop.MOD_ID, "open_map_screen");
    public static final PacketType<OpenMapScreenPayload> ID = PacketType.create(HANDSHAKE_ID, buf -> OpenMapScreenPayload.CODEC.decode(buf));
    public static final PacketCodec<PacketByteBuf, OpenMapScreenPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING, OpenMapScreenPayload::title,
            OpenMapScreenPayload::new);

    @Override
    public void write(PacketByteBuf buf) {
        CODEC.encode(buf, this);
    }

    @Override
    public PacketType<?> getType() {
        return ID;
    }
}
