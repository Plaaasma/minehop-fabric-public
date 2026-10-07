package net.nerdorg.minehop.networking.payloads;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.networking.ReplayProtocol;

/** Server -> client (1.1.7+ only): {@code data} is the replay file's bytes at {@code offset}; chunks come in order. */
public record ReplayChunkPayload(int requestId, int offset, byte[] data) implements CustomPacketPayload {
    public static final Identifier PAYLOAD_ID = Identifier.fromNamespaceAndPath(Minehop.MOD_ID, "replay_chunk");
    public static final Type<ReplayChunkPayload> ID = new Type<>(PAYLOAD_ID);
    public static final StreamCodec<FriendlyByteBuf, ReplayChunkPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, ReplayChunkPayload::requestId,
            ByteBufCodecs.INT, ReplayChunkPayload::offset,
            ByteBufCodecs.byteArray(ReplayProtocol.CHUNK_BYTES), ReplayChunkPayload::data,
            ReplayChunkPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
