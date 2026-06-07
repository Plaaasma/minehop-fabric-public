package net.nerdorg.minehop.networking.payloads;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.nerdorg.minehop.Minehop;

public record RunTimerHudPayload(boolean visible, float time, float personalBest) implements CustomPayload {
    public static final Identifier HANDSHAKE_ID = Identifier.of(Minehop.MOD_ID, "run_timer_hud");
    public static final Id<RunTimerHudPayload> ID = new Id<>(HANDSHAKE_ID);
    public static final PacketCodec<PacketByteBuf, RunTimerHudPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.BOOLEAN, RunTimerHudPayload::visible,
            PacketCodecs.FLOAT, RunTimerHudPayload::time,
            PacketCodecs.FLOAT, RunTimerHudPayload::personalBest,
            RunTimerHudPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
