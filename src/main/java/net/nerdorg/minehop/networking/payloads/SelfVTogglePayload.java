package net.nerdorg.minehop.networking.payloads;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.nerdorg.minehop.Minehop;

public record SelfVTogglePayload(boolean test) implements CustomPayload {
    public static final Identifier HANDSHAKE_ID = Identifier.of(Minehop.MOD_ID, "self_v_toggle");
    public static final Id<SelfVTogglePayload> ID = new Id<>(HANDSHAKE_ID);
    public static final PacketCodec<PacketByteBuf, SelfVTogglePayload> CODEC = PacketCodec.tuple(
            PacketCodecs.BOOLEAN, SelfVTogglePayload::test,
            SelfVTogglePayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
