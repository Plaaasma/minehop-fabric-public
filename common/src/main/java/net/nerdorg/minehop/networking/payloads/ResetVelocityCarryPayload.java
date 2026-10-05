package net.nerdorg.minehop.networking.payloads;

import net.minecraft.network.FriendlyByteBuf;
import net.nerdorg.minehop.networking.codec.ByteBufCodecs;
import net.nerdorg.minehop.networking.codec.StreamCodec;
import net.nerdorg.minehop.networking.codec.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.nerdorg.minehop.Minehop;

public record ResetVelocityCarryPayload(float x, float y, float z, int ticks) implements CustomPacketPayload {
    public static final ResourceLocation HANDSHAKE_ID = new ResourceLocation(Minehop.MOD_ID, "reset_velocity_carry");
    public static final Type<ResetVelocityCarryPayload> ID = new Type<>(HANDSHAKE_ID);
    public static final StreamCodec<FriendlyByteBuf, ResetVelocityCarryPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, ResetVelocityCarryPayload::x,
            ByteBufCodecs.FLOAT, ResetVelocityCarryPayload::y,
            ByteBufCodecs.FLOAT, ResetVelocityCarryPayload::z,
            ByteBufCodecs.INT, ResetVelocityCarryPayload::ticks,
            ResetVelocityCarryPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
