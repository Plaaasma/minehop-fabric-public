package net.nerdorg.minehop.networking.payloads;

import net.minecraft.network.PacketByteBuf;
import net.nerdorg.minehop.networking.codec.PacketCodec;
import net.nerdorg.minehop.networking.codec.PacketCodecs;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.util.Identifier;
import net.nerdorg.minehop.Minehop;

public record SelfVTogglePayload(boolean test) implements FabricPacket {
    public static final Identifier HANDSHAKE_ID = new Identifier(Minehop.MOD_ID, "self_v_toggle");
    public static final PacketType<SelfVTogglePayload> ID = PacketType.create(HANDSHAKE_ID, buf -> SelfVTogglePayload.CODEC.decode(buf));
    public static final PacketCodec<PacketByteBuf, SelfVTogglePayload> CODEC = PacketCodec.tuple(
            PacketCodecs.BOOLEAN, SelfVTogglePayload::test,
            SelfVTogglePayload::new);

    @Override
    public void write(PacketByteBuf buf) {
        CODEC.encode(buf, this);
    }

    @Override
    public PacketType<?> getType() {
        return ID;
    }
}
