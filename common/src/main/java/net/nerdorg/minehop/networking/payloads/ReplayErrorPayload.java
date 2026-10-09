package net.nerdorg.minehop.networking.payloads;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.networking.ReplayProtocol;

/** Server -> client (1.1.7+ only): the request was refused, or its stream was aborted; {@code message} says why. */
public record ReplayErrorPayload(int requestId, String message) implements CustomPacketPayload {
    public static final Identifier PAYLOAD_ID = Identifier.fromNamespaceAndPath(Minehop.MOD_ID, "replay_error");
    public static final Type<ReplayErrorPayload> ID = new Type<>(PAYLOAD_ID);
    public static final StreamCodec<FriendlyByteBuf, ReplayErrorPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, ReplayErrorPayload::requestId,
            ByteBufCodecs.stringUtf8(ReplayProtocol.MAX_MESSAGE_CHARS), ReplayErrorPayload::message,
            ReplayErrorPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
