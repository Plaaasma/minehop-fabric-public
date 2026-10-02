package net.nerdorg.minehop.networking.payloads;

import net.minecraft.network.PacketByteBuf;
import net.nerdorg.minehop.networking.codec.PacketCodec;
import net.nerdorg.minehop.networking.codec.PacketCodecs;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.util.Identifier;
import net.nerdorg.minehop.Minehop;

public record RunTimerHudPayload(boolean visible, float time, float personalBest) implements FabricPacket {
    public static final Identifier HANDSHAKE_ID = new Identifier(Minehop.MOD_ID, "run_timer_hud");
    public static final PacketType<RunTimerHudPayload> ID = PacketType.create(HANDSHAKE_ID, buf -> RunTimerHudPayload.CODEC.decode(buf));
    public static final PacketCodec<PacketByteBuf, RunTimerHudPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.BOOLEAN, RunTimerHudPayload::visible,
            PacketCodecs.FLOAT, RunTimerHudPayload::time,
            PacketCodecs.FLOAT, RunTimerHudPayload::personalBest,
            RunTimerHudPayload::new
    );

    @Override
    public void write(PacketByteBuf buf) {
        CODEC.encode(buf, this);
    }

    @Override
    public PacketType<?> getType() {
        return ID;
    }
}
