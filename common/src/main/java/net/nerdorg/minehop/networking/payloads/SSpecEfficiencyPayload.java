package net.nerdorg.minehop.networking.payloads;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.nerdorg.minehop.Minehop;

public record SSpecEfficiencyPayload(double last_jump_speed, double jump_count, double last_efficiency) implements CustomPacketPayload {
    public static final Identifier HANDSHAKE_ID = Identifier.fromNamespaceAndPath(Minehop.MOD_ID, "server_spec_efficiency");
    public static final Type<SSpecEfficiencyPayload> ID = new Type<>(HANDSHAKE_ID);
    public static final StreamCodec<FriendlyByteBuf, SSpecEfficiencyPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.DOUBLE, SSpecEfficiencyPayload::last_jump_speed,
            ByteBufCodecs.DOUBLE, SSpecEfficiencyPayload::jump_count,
            ByteBufCodecs.DOUBLE, SSpecEfficiencyPayload::last_efficiency,
            SSpecEfficiencyPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
