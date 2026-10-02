package net.nerdorg.minehop.networking.payloads;

import net.minecraft.network.PacketByteBuf;
import net.nerdorg.minehop.networking.codec.PacketCodec;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.util.Identifier;
import net.nerdorg.minehop.Minehop;

public record ZoneStickCancelPayload(boolean cancel) implements FabricPacket {
    public static final Identifier HANDSHAKE_ID = new Identifier(Minehop.MOD_ID, "zone_stick_cancel");
    public static final PacketType<ZoneStickCancelPayload> ID = PacketType.create(HANDSHAKE_ID, buf -> ZoneStickCancelPayload.CODEC.decode(buf));
    public static final PacketCodec<PacketByteBuf, ZoneStickCancelPayload> CODEC = PacketCodec.of(
            (value, buf) -> buf.writeBoolean(value.cancel),
            buf -> new ZoneStickCancelPayload(buf.readBoolean())
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
