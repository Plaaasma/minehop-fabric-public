package net.nerdorg.minehop.networking.payloads;

import net.minecraft.network.PacketByteBuf;
import net.nerdorg.minehop.networking.codec.PacketCodec;
import net.nerdorg.minehop.networking.codec.PacketCodecs;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.util.Identifier;
import net.nerdorg.minehop.Minehop;

public record CSpecEfficiencyPayload(double last_jump_speed, int jump_count, double last_efficiency) implements FabricPacket {
    public static final Identifier HANDSHAKE_ID = new Identifier(Minehop.MOD_ID, "client_spec_efficiency");
    public static final PacketType<CSpecEfficiencyPayload> ID = PacketType.create(HANDSHAKE_ID, buf -> CSpecEfficiencyPayload.CODEC.decode(buf));
    public static final PacketCodec<PacketByteBuf, CSpecEfficiencyPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.DOUBLE, CSpecEfficiencyPayload::last_jump_speed,
            PacketCodecs.INTEGER, CSpecEfficiencyPayload::jump_count,
            PacketCodecs.DOUBLE, CSpecEfficiencyPayload::last_efficiency,
            CSpecEfficiencyPayload::new);

    @Override
    public void write(PacketByteBuf buf) {
        CODEC.encode(buf, this);
    }

    @Override
    public PacketType<?> getType() {
        return ID;
    }
}
