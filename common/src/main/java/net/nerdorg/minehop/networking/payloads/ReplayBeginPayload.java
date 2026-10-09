package net.nerdorg.minehop.networking.payloads;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.networking.ReplayProtocol;

/**
 * Server -> client (1.1.7+ only): the request was accepted. {@code totalBytes} bytes of an MHRP file follow in
 * {@link ReplayChunkPayload}s, unless {@code flags} has BEGIN_CACHED (the client's cached copy is current).
 */
public record ReplayBeginPayload(int requestId, String replayId, int totalBytes, byte flags) implements CustomPacketPayload {
    public static final Identifier PAYLOAD_ID = Identifier.fromNamespaceAndPath(Minehop.MOD_ID, "replay_begin");
    public static final Type<ReplayBeginPayload> ID = new Type<>(PAYLOAD_ID);
    public static final StreamCodec<FriendlyByteBuf, ReplayBeginPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, ReplayBeginPayload::requestId,
            ByteBufCodecs.stringUtf8(ReplayProtocol.MAX_ID_CHARS), ReplayBeginPayload::replayId,
            ByteBufCodecs.INT, ReplayBeginPayload::totalBytes,
            ByteBufCodecs.BYTE, ReplayBeginPayload::flags,
            ReplayBeginPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
