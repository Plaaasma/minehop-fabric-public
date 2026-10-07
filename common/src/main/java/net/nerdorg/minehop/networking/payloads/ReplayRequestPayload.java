package net.nerdorg.minehop.networking.payloads;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.networking.ReplayProtocol;

/**
 * Client -> server: asks for a replay's file (see {@link ReplayProtocol}). {@code kind}: KIND_SESSION (the replay of
 * the client's watch session, named by {@code replayId}), KIND_RACE_PB / KIND_RACE_WR (the requester's personal best /
 * the world record on {@code map}). {@code cachedId}: the replay the client already has for this request ("" = none);
 * if it is still the one, the server answers with BEGIN_CACHED and sends nothing else. Every field is validated.
 */
public record ReplayRequestPayload(int requestId, byte kind, String map, String replayId, String cachedId) implements CustomPacketPayload {
    public static final ResourceLocation PAYLOAD_ID = ResourceLocation.fromNamespaceAndPath(Minehop.MOD_ID, "replay_request");
    public static final Type<ReplayRequestPayload> ID = new Type<>(PAYLOAD_ID);
    public static final StreamCodec<FriendlyByteBuf, ReplayRequestPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, ReplayRequestPayload::requestId,
            ByteBufCodecs.BYTE, ReplayRequestPayload::kind,
            ByteBufCodecs.stringUtf8(ReplayProtocol.MAX_MAP_CHARS), ReplayRequestPayload::map,
            ByteBufCodecs.stringUtf8(ReplayProtocol.MAX_ID_CHARS), ReplayRequestPayload::replayId,
            ByteBufCodecs.stringUtf8(ReplayProtocol.MAX_ID_CHARS), ReplayRequestPayload::cachedId,
            ReplayRequestPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
