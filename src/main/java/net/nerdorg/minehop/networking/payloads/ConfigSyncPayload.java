package net.nerdorg.minehop.networking.payloads;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.nerdorg.minehop.Minehop;

public record ConfigSyncPayload(double sv_friction, double sv_accelerate,
                                double sv_airaccelerate, double sv_maxairspeed, double sv_jump_impulse,
                                double speed_mul, double sv_gravity, double speedCoef, double speedCap, boolean autoStepUp,
                                boolean cssCrouchJump, boolean isHNS, boolean isEnabled, boolean fallDamage,
                                double sv_stopspeed, boolean disableSprint) implements CustomPayload {
    public static final Identifier HANDSHAKE_ID = Identifier.of(Minehop.MOD_ID, "config");
    public static final Id<ConfigSyncPayload> ID = new Id<>(HANDSHAKE_ID);
    public static final PacketCodec<PacketByteBuf, ConfigSyncPayload> CODEC = PacketCodec.of(
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
                    buf.readDouble(),
                    buf.readBoolean()
            )
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
