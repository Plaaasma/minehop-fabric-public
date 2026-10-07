package net.nerdorg.minehop.networking.payloads;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.networking.ReplayProtocol;

/**
 * Server -> client (1.1.7+ only): a watch session started ({@code active}: play {@code replayId}, a WATCH_* {@code kind}
 * run of {@code player} on {@code map}, {@code time} seconds, {@code frameCount} frames) or ended ({@code active} false,
 * only {@code sessionId} matters). The client fetches the replay (a KIND_SESSION request) unless it has it.
 */
public record ReplayWatchPayload(int sessionId, boolean active, byte kind, String replayId, String map, String player, double time, int frameCount) implements CustomPacketPayload {
    public static final ResourceLocation PAYLOAD_ID = ResourceLocation.fromNamespaceAndPath(Minehop.MOD_ID, "replay_watch");
    public static final Type<ReplayWatchPayload> ID = new Type<>(PAYLOAD_ID);
    // 1.21.1: StreamCodec.composite takes at most 6 fields before 1.21.2, so the 8 fields are written in the same order
    // with the same codecs by hand (identical bytes on the wire).
    public static final StreamCodec<FriendlyByteBuf, ReplayWatchPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                ByteBufCodecs.INT.encode(buf, payload.sessionId());
                ByteBufCodecs.BOOL.encode(buf, payload.active());
                ByteBufCodecs.BYTE.encode(buf, payload.kind());
                ByteBufCodecs.stringUtf8(ReplayProtocol.MAX_ID_CHARS).encode(buf, payload.replayId());
                ByteBufCodecs.stringUtf8(ReplayProtocol.MAX_MAP_CHARS).encode(buf, payload.map());
                ByteBufCodecs.stringUtf8(ReplayProtocol.MAX_NAME_CHARS).encode(buf, payload.player());
                ByteBufCodecs.DOUBLE.encode(buf, payload.time());
                ByteBufCodecs.INT.encode(buf, payload.frameCount());
            },
            buf -> new ReplayWatchPayload(
                    ByteBufCodecs.INT.decode(buf),
                    ByteBufCodecs.BOOL.decode(buf),
                    ByteBufCodecs.BYTE.decode(buf),
                    ByteBufCodecs.stringUtf8(ReplayProtocol.MAX_ID_CHARS).decode(buf),
                    ByteBufCodecs.stringUtf8(ReplayProtocol.MAX_MAP_CHARS).decode(buf),
                    ByteBufCodecs.stringUtf8(ReplayProtocol.MAX_NAME_CHARS).decode(buf),
                    ByteBufCodecs.DOUBLE.decode(buf),
                    ByteBufCodecs.INT.decode(buf)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
