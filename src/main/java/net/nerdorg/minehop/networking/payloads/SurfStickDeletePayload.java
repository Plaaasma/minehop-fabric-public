package net.nerdorg.minehop.networking.payloads;

import net.minecraft.network.PacketByteBuf;
import net.nerdorg.minehop.networking.codec.PacketCodec;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.util.Identifier;
import net.nerdorg.minehop.Minehop;

public record SurfStickDeletePayload(boolean delete) implements FabricPacket {
    public static final Identifier HANDSHAKE_ID = new Identifier(Minehop.MOD_ID, "surf_stick_delete");
    public static final PacketType<SurfStickDeletePayload> ID = PacketType.create(HANDSHAKE_ID, buf -> SurfStickDeletePayload.CODEC.decode(buf));
    public static final PacketCodec<PacketByteBuf, SurfStickDeletePayload> CODEC = PacketCodec.of(
            (value, buf) -> buf.writeBoolean(value.delete),
            buf -> new SurfStickDeletePayload(buf.readBoolean())
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
