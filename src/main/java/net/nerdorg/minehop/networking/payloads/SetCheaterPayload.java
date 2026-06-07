package net.nerdorg.minehop.networking.payloads;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.nerdorg.minehop.Minehop;

public record SetCheaterPayload(String uuid, boolean isCheater) implements CustomPayload {
    public static final Identifier HANDSHAKE_ID = Identifier.of(Minehop.MOD_ID, "set_player_cheater");
    public static final Id<SetCheaterPayload> ID = new Id<>(HANDSHAKE_ID);
    public static final PacketCodec<PacketByteBuf, SetCheaterPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING, SetCheaterPayload::uuid,
            PacketCodecs.BOOLEAN, SetCheaterPayload::isCheater,
            SetCheaterPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
