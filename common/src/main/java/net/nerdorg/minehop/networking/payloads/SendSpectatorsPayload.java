package net.nerdorg.minehop.networking.payloads;

import net.minecraft.network.FriendlyByteBuf;
import net.nerdorg.minehop.networking.codec.ByteBufCodecs;
import net.nerdorg.minehop.networking.codec.StreamCodec;
import net.nerdorg.minehop.networking.codec.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.nerdorg.minehop.Minehop;

public record SendSpectatorsPayload(String spectatorBuff) implements CustomPacketPayload {
    public static final ResourceLocation HANDSHAKE_ID = new ResourceLocation(Minehop.MOD_ID, "send_spectators");
    public static final Type<SendSpectatorsPayload> ID = new Type<>(HANDSHAKE_ID);
    public static final StreamCodec<FriendlyByteBuf, SendSpectatorsPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, SendSpectatorsPayload::spectatorBuff,
            SendSpectatorsPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
