package net.nerdorg.minehop.networking.payloads;

import net.minecraft.network.PacketByteBuf;
import net.nerdorg.minehop.networking.codec.PacketCodec;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.util.Identifier;
import net.nerdorg.minehop.Minehop;

public record SurfStickCancelPayload(boolean cancel) implements FabricPacket {
    public static final Identifier HANDSHAKE_ID = new Identifier(Minehop.MOD_ID, "surf_stick_cancel");
    public static final PacketType<SurfStickCancelPayload> ID = PacketType.create(HANDSHAKE_ID, buf -> SurfStickCancelPayload.CODEC.decode(buf));
    public static final PacketCodec<PacketByteBuf, SurfStickCancelPayload> CODEC = PacketCodec.of(
            (value, buf) -> buf.writeBoolean(value.cancel),
            buf -> new SurfStickCancelPayload(buf.readBoolean())
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
