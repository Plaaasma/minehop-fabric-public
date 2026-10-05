package net.nerdorg.minehop.networking.payloads;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.nerdorg.minehop.Minehop;

public record CSpecEfficiencyPayload(double last_jump_speed, int jump_count, double last_efficiency) implements CustomPacketPayload {
    public static final Identifier HANDSHAKE_ID = Identifier.fromNamespaceAndPath(Minehop.MOD_ID, "client_spec_efficiency");
    public static final Type<CSpecEfficiencyPayload> ID = new Type<>(HANDSHAKE_ID);
    public static final StreamCodec<FriendlyByteBuf, CSpecEfficiencyPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.DOUBLE, CSpecEfficiencyPayload::last_jump_speed,
            ByteBufCodecs.INT, CSpecEfficiencyPayload::jump_count,
            ByteBufCodecs.DOUBLE, CSpecEfficiencyPayload::last_efficiency,
            CSpecEfficiencyPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
