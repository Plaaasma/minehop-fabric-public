package net.nerdorg.minehop.networking.payloads;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.nerdorg.minehop.Minehop;

public record SSpecEfficiencyPayload(double last_jump_speed, double jump_count, double last_efficiency) implements CustomPayload {
    public static final Identifier HANDSHAKE_ID = Identifier.of(Minehop.MOD_ID, "server_spec_efficiency");
    public static final Id<SSpecEfficiencyPayload> ID = new Id<>(HANDSHAKE_ID);
    public static final PacketCodec<PacketByteBuf, SSpecEfficiencyPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.DOUBLE, SSpecEfficiencyPayload::last_jump_speed,
            PacketCodecs.DOUBLE, SSpecEfficiencyPayload::jump_count,
            PacketCodecs.DOUBLE, SSpecEfficiencyPayload::last_efficiency,
            SSpecEfficiencyPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
