package net.nerdorg.minehop.networking.payloads;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.nerdorg.minehop.Minehop;

public record OtherVTogglePayload(boolean test) implements CustomPayload {
    public static final Identifier HANDSHAKE_ID = Identifier.of(Minehop.MOD_ID, "other_v_toggle");
    public static final Id<OtherVTogglePayload> ID = new Id<>(HANDSHAKE_ID);
    public static final PacketCodec<PacketByteBuf, OtherVTogglePayload> CODEC = PacketCodec.tuple(
            PacketCodecs.BOOLEAN, OtherVTogglePayload::test,
            OtherVTogglePayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
