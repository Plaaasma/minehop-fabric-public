package net.nerdorg.minehop.networking.payloads;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.nerdorg.minehop.Minehop;

public record MapFinishPayload(String map_name, double time, double x, double y, double z) implements CustomPayload {
    private static final int MAX_MAP_NAME_LENGTH = 128;
    public static final Identifier HANDSHAKE_ID = Identifier.of(Minehop.MOD_ID, "map_finish");
    public static final Id<MapFinishPayload> ID = new Id<>(HANDSHAKE_ID);
    public static final PacketCodec<PacketByteBuf, MapFinishPayload> CODEC = PacketCodec.of(
            (value, buf) -> {
                buf.writeString(value.map_name == null ? "" : value.map_name, MAX_MAP_NAME_LENGTH);
                buf.writeDouble(value.time);
                buf.writeDouble(value.x);
                buf.writeDouble(value.y);
                buf.writeDouble(value.z);
            },
            buf -> new MapFinishPayload(
                    buf.readString(MAX_MAP_NAME_LENGTH),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readDouble()
            )
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
