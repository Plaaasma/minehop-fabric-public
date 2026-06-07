package net.nerdorg.minehop.networking.payloads;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.nerdorg.minehop.Minehop;

public record ResetVelocityCarryPayload(float x, float y, float z, int ticks) implements CustomPayload {
    public static final Identifier HANDSHAKE_ID = Identifier.of(Minehop.MOD_ID, "reset_velocity_carry");
    public static final Id<ResetVelocityCarryPayload> ID = new Id<>(HANDSHAKE_ID);
    public static final PacketCodec<PacketByteBuf, ResetVelocityCarryPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.FLOAT, ResetVelocityCarryPayload::x,
            PacketCodecs.FLOAT, ResetVelocityCarryPayload::y,
            PacketCodecs.FLOAT, ResetVelocityCarryPayload::z,
            PacketCodecs.INTEGER, ResetVelocityCarryPayload::ticks,
            ResetVelocityCarryPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
