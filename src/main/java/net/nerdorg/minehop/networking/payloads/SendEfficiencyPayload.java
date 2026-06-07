package net.nerdorg.minehop.networking.payloads;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.nerdorg.minehop.Minehop;

public record SendEfficiencyPayload(double efficiency) implements CustomPayload {
    public static final Identifier HANDSHAKE_ID = Identifier.of(Minehop.MOD_ID, "send_efficiency");
    public static final Id<SendEfficiencyPayload> ID = new Id<>(HANDSHAKE_ID);
    public static final PacketCodec<PacketByteBuf, SendEfficiencyPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.DOUBLE, SendEfficiencyPayload::efficiency,
            SendEfficiencyPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
