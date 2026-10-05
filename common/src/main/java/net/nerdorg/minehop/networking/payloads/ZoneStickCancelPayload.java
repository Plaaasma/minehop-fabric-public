package net.nerdorg.minehop.networking.payloads;

import net.minecraft.network.FriendlyByteBuf;
import net.nerdorg.minehop.networking.codec.StreamCodec;
import net.nerdorg.minehop.networking.codec.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.nerdorg.minehop.Minehop;

public record ZoneStickCancelPayload(boolean cancel) implements CustomPacketPayload {
    public static final ResourceLocation HANDSHAKE_ID = new ResourceLocation(Minehop.MOD_ID, "zone_stick_cancel");
    public static final Type<ZoneStickCancelPayload> ID = new Type<>(HANDSHAKE_ID);
    public static final StreamCodec<FriendlyByteBuf, ZoneStickCancelPayload> CODEC = StreamCodec.ofMember(
            (value, buf) -> buf.writeBoolean(value.cancel),
            buf -> new ZoneStickCancelPayload(buf.readBoolean())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
