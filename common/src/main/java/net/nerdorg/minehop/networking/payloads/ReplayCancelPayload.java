package net.nerdorg.minehop.networking.payloads;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.nerdorg.minehop.Minehop;

/** Client -> server: stop sending the stream of this request (the client no longer needs it). */
public record ReplayCancelPayload(int requestId) implements CustomPacketPayload {
    public static final Identifier PAYLOAD_ID = Identifier.fromNamespaceAndPath(Minehop.MOD_ID, "replay_cancel");
    public static final Type<ReplayCancelPayload> ID = new Type<>(PAYLOAD_ID);
    public static final StreamCodec<FriendlyByteBuf, ReplayCancelPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, ReplayCancelPayload::requestId,
            ReplayCancelPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
