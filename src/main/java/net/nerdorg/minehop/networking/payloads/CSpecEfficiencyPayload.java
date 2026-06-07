package net.nerdorg.minehop.networking.payloads;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.nerdorg.minehop.Minehop;

public record CSpecEfficiencyPayload(double last_jump_speed, int jump_count, double last_efficiency) implements CustomPayload {
    public static final Identifier HANDSHAKE_ID = Identifier.of(Minehop.MOD_ID, "client_spec_efficiency");
    public static final Id<CSpecEfficiencyPayload> ID = new Id<>(HANDSHAKE_ID);
    public static final PacketCodec<PacketByteBuf, CSpecEfficiencyPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.DOUBLE, CSpecEfficiencyPayload::last_jump_speed,
            PacketCodecs.INTEGER, CSpecEfficiencyPayload::jump_count,
            PacketCodecs.DOUBLE, CSpecEfficiencyPayload::last_efficiency,
            CSpecEfficiencyPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
