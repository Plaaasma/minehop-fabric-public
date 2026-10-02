package net.nerdorg.minehop.networking.payloads;

import net.minecraft.network.PacketByteBuf;
import net.nerdorg.minehop.networking.codec.PacketCodec;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.util.Identifier;
import net.nerdorg.minehop.Minehop;

public record MapFinishPayload(String map_name, double time, double x, double y, double z) implements FabricPacket {
    private static final int MAX_MAP_NAME_LENGTH = 128;
    public static final Identifier HANDSHAKE_ID = new Identifier(Minehop.MOD_ID, "map_finish");
    public static final PacketType<MapFinishPayload> ID = PacketType.create(HANDSHAKE_ID, buf -> MapFinishPayload.CODEC.decode(buf));
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
    public void write(PacketByteBuf buf) {
        CODEC.encode(buf, this);
    }

    @Override
    public PacketType<?> getType() {
        return ID;
    }
}
