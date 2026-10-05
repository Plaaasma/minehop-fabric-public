package net.nerdorg.minehop.networking.payloads;

import net.minecraft.network.FriendlyByteBuf;
import net.nerdorg.minehop.networking.codec.StreamCodec;
import net.nerdorg.minehop.networking.codec.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.nerdorg.minehop.Minehop;

public record ConfigSyncPayload(double sv_friction, double sv_accelerate,
                                double sv_airaccelerate, double sv_maxairspeed, double sv_jump_impulse,
                                double speed_mul, double sv_gravity, double speedCoef, double speedCap, boolean autoStepUp,
                                boolean cssCrouchJump, boolean isHNS, boolean isKZ, boolean isEnabled, boolean fallDamage,
                                double sv_stopspeed, boolean disableSprint) implements CustomPacketPayload {
    public static final ResourceLocation HANDSHAKE_ID = new ResourceLocation(Minehop.MOD_ID, "config");
    public static final Type<ConfigSyncPayload> ID = new Type<>(HANDSHAKE_ID);
    public static final StreamCodec<FriendlyByteBuf, ConfigSyncPayload> CODEC = StreamCodec.ofMember(
            (buf, value) -> {
                value.writeDouble(buf.sv_friction);
                value.writeDouble(buf.sv_accelerate);
                value.writeDouble(buf.sv_airaccelerate);
                value.writeDouble(buf.sv_maxairspeed);
                value.writeDouble(buf.sv_jump_impulse);
                value.writeDouble(buf.speed_mul);
                value.writeDouble(buf.sv_gravity);
                value.writeDouble(buf.speedCoef);
                value.writeDouble(buf.speedCap);
                value.writeBoolean(buf.autoStepUp);
                value.writeBoolean(buf.cssCrouchJump);
                value.writeBoolean(buf.isHNS);
                value.writeBoolean(buf.isKZ);
                value.writeBoolean(buf.isEnabled);
                value.writeBoolean(buf.fallDamage);
                value.writeDouble(buf.sv_stopspeed);
                value.writeBoolean(buf.disableSprint);
            },
            buf -> new ConfigSyncPayload(
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readDouble(),
                    buf.readBoolean()
            )
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
