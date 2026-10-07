package net.nerdorg.minehop.networking.payloads;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.networking.ReplayProtocol;

/** Server -> client (1.1.7+ only): a CONTROL_* action with its value (see {@link ReplayProtocol}). */
public record ReplayControlPayload(byte action, double value) implements CustomPacketPayload {
    public static final ResourceLocation PAYLOAD_ID = ResourceLocation.fromNamespaceAndPath(Minehop.MOD_ID, "replay_control");
    public static final Type<ReplayControlPayload> ID = new Type<>(PAYLOAD_ID);
    public static final StreamCodec<FriendlyByteBuf, ReplayControlPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.BYTE, ReplayControlPayload::action,
            ByteBufCodecs.DOUBLE, ReplayControlPayload::value,
            ReplayControlPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
