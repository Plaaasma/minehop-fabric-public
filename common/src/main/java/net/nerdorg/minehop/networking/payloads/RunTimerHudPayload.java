package net.nerdorg.minehop.networking.payloads;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.nerdorg.minehop.Minehop;

public record RunTimerHudPayload(boolean visible, float time, float personalBest) implements CustomPacketPayload {
    public static final ResourceLocation HANDSHAKE_ID = ResourceLocation.fromNamespaceAndPath(Minehop.MOD_ID, "run_timer_hud");
    public static final Type<RunTimerHudPayload> ID = new Type<>(HANDSHAKE_ID);
    public static final StreamCodec<FriendlyByteBuf, RunTimerHudPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, RunTimerHudPayload::visible,
            ByteBufCodecs.FLOAT, RunTimerHudPayload::time,
            ByteBufCodecs.FLOAT, RunTimerHudPayload::personalBest,
            RunTimerHudPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
