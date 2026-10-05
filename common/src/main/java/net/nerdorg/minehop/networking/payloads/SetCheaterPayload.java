package net.nerdorg.minehop.networking.payloads;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.nerdorg.minehop.Minehop;

public record SetCheaterPayload(String uuid, boolean isCheater) implements CustomPacketPayload {
    public static final Identifier HANDSHAKE_ID = Identifier.fromNamespaceAndPath(Minehop.MOD_ID, "set_player_cheater");
    public static final Type<SetCheaterPayload> ID = new Type<>(HANDSHAKE_ID);
    public static final StreamCodec<FriendlyByteBuf, SetCheaterPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, SetCheaterPayload::uuid,
            ByteBufCodecs.BOOL, SetCheaterPayload::isCheater,
            SetCheaterPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
