package net.nerdorg.minehop.networking.payloads;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.networking.ReplayProtocol;

/**
 * Server -> client (1.1.7+ only): a watch session started ({@code active}: play {@code replayId}, a WATCH_* {@code kind}
 * run of {@code player} on {@code map}, {@code time} seconds, {@code frameCount} frames) or ended ({@code active} false,
 * only {@code sessionId} matters). The client fetches the replay (a KIND_SESSION request) unless it has it.
 */
public record ReplayWatchPayload(int sessionId, boolean active, byte kind, String replayId, String map, String player, double time, int frameCount) implements CustomPacketPayload {
    public static final Identifier PAYLOAD_ID = Identifier.fromNamespaceAndPath(Minehop.MOD_ID, "replay_watch");
    public static final Type<ReplayWatchPayload> ID = new Type<>(PAYLOAD_ID);
    public static final StreamCodec<FriendlyByteBuf, ReplayWatchPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, ReplayWatchPayload::sessionId,
            ByteBufCodecs.BOOL, ReplayWatchPayload::active,
            ByteBufCodecs.BYTE, ReplayWatchPayload::kind,
            ByteBufCodecs.stringUtf8(ReplayProtocol.MAX_ID_CHARS), ReplayWatchPayload::replayId,
            ByteBufCodecs.stringUtf8(ReplayProtocol.MAX_MAP_CHARS), ReplayWatchPayload::map,
            ByteBufCodecs.stringUtf8(ReplayProtocol.MAX_NAME_CHARS), ReplayWatchPayload::player,
            ByteBufCodecs.DOUBLE, ReplayWatchPayload::time,
            ByteBufCodecs.INT, ReplayWatchPayload::frameCount,
            ReplayWatchPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
