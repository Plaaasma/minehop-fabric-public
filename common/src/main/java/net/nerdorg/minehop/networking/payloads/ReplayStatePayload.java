package net.nerdorg.minehop.networking.payloads;

import net.minecraft.network.FriendlyByteBuf;
import net.nerdorg.minehop.networking.codec.ByteBufCodecs;
import net.nerdorg.minehop.networking.codec.StreamCodec;
import net.nerdorg.minehop.networking.codec.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.networking.ReplayProtocol;

/**
 * Client -> server: where the client's playback of its watch session is ({@code frame}, fractional frame index into the
 * replay's frames), its speed and STATE_* flags (see {@link ReplayProtocol}). The server clamps every value and only
 * uses it to keep the viewer, a spectator, near that frame's recorded position (so the chunks there load); STATE_STOP
 * ends the session.
 */
public record ReplayStatePayload(int sessionId, float frame, float speed, byte flags) implements CustomPacketPayload {
    public static final ResourceLocation PAYLOAD_ID = new ResourceLocation(Minehop.MOD_ID, "replay_state");
    public static final Type<ReplayStatePayload> ID = new Type<>(PAYLOAD_ID);
    public static final StreamCodec<FriendlyByteBuf, ReplayStatePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, ReplayStatePayload::sessionId,
            ByteBufCodecs.FLOAT, ReplayStatePayload::frame,
            ByteBufCodecs.FLOAT, ReplayStatePayload::speed,
            ByteBufCodecs.BYTE, ReplayStatePayload::flags,
            ReplayStatePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
