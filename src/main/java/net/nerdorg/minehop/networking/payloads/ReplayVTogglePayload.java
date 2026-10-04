package net.nerdorg.minehop.networking.payloads;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.nerdorg.minehop.Minehop;

public record ReplayVTogglePayload(boolean test) implements CustomPacketPayload {
    public static final ResourceLocation HANDSHAKE_ID = ResourceLocation.fromNamespaceAndPath(Minehop.MOD_ID, "replay_v_toggle");
    public static final Type<ReplayVTogglePayload> ID = new Type<>(HANDSHAKE_ID);
    public static final StreamCodec<FriendlyByteBuf, ReplayVTogglePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, ReplayVTogglePayload::test,
            ReplayVTogglePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
