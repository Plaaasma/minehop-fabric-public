package net.nerdorg.minehop.networking.payloads;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.nerdorg.minehop.Minehop;

public record BoundsStickSelectionPayload(
        boolean hasFirst,
        BlockPos first,
        boolean hasSecond,
        BlockPos second
) implements CustomPacketPayload {
    public static final Identifier HANDSHAKE_ID = Identifier.fromNamespaceAndPath(Minehop.MOD_ID, "bounds_stick_selection");
    public static final Type<BoundsStickSelectionPayload> ID = new Type<>(HANDSHAKE_ID);
    public static final StreamCodec<FriendlyByteBuf, BoundsStickSelectionPayload> CODEC = StreamCodec.ofMember(
            (value, buf) -> {
                buf.writeBoolean(value.hasFirst);
                buf.writeBlockPos(value.first == null ? BlockPos.ZERO : value.first);
                buf.writeBoolean(value.hasSecond);
                buf.writeBlockPos(value.second == null ? BlockPos.ZERO : value.second);
            },
            buf -> new BoundsStickSelectionPayload(
                    buf.readBoolean(),
                    buf.readBlockPos().immutable(),
                    buf.readBoolean(),
                    buf.readBlockPos().immutable()
            )
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
