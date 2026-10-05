package net.nerdorg.minehop.networking.payloads;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.nerdorg.minehop.Minehop;

public record OtherVTogglePayload(boolean test) implements CustomPacketPayload {
    public static final Identifier HANDSHAKE_ID = Identifier.fromNamespaceAndPath(Minehop.MOD_ID, "other_v_toggle");
    public static final Type<OtherVTogglePayload> ID = new Type<>(HANDSHAKE_ID);
    public static final StreamCodec<FriendlyByteBuf, OtherVTogglePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, OtherVTogglePayload::test,
            OtherVTogglePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
