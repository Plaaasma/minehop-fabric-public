package net.nerdorg.minehop.networking.payloads;

import net.minecraft.network.PacketByteBuf;
import net.nerdorg.minehop.networking.codec.PacketCodec;
import net.nerdorg.minehop.networking.codec.PacketCodecs;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.util.Identifier;
import net.nerdorg.minehop.Minehop;

public record SSpecEfficiencyPayload(double last_jump_speed, double jump_count, double last_efficiency) implements FabricPacket {
    public static final Identifier HANDSHAKE_ID = new Identifier(Minehop.MOD_ID, "server_spec_efficiency");
    public static final PacketType<SSpecEfficiencyPayload> ID = PacketType.create(HANDSHAKE_ID, buf -> SSpecEfficiencyPayload.CODEC.decode(buf));
    public static final PacketCodec<PacketByteBuf, SSpecEfficiencyPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.DOUBLE, SSpecEfficiencyPayload::last_jump_speed,
            PacketCodecs.DOUBLE, SSpecEfficiencyPayload::jump_count,
            PacketCodecs.DOUBLE, SSpecEfficiencyPayload::last_efficiency,
            SSpecEfficiencyPayload::new);

    @Override
    public void write(PacketByteBuf buf) {
        CODEC.encode(buf, this);
    }

    @Override
    public PacketType<?> getType() {
        return ID;
    }
}
