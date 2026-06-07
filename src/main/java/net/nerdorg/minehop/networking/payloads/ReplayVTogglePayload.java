package net.nerdorg.minehop.networking.payloads;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.nerdorg.minehop.Minehop;

public record ReplayVTogglePayload(boolean test) implements CustomPayload {
    public static final Identifier HANDSHAKE_ID = Identifier.of(Minehop.MOD_ID, "replay_v_toggle");
    public static final Id<ReplayVTogglePayload> ID = new Id<>(HANDSHAKE_ID);
    public static final PacketCodec<PacketByteBuf, ReplayVTogglePayload> CODEC = PacketCodec.tuple(
            PacketCodecs.BOOLEAN, ReplayVTogglePayload::test,
            ReplayVTogglePayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
