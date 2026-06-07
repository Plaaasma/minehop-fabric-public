package net.nerdorg.minehop.networking.payloads;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.nerdorg.minehop.Minehop;

public record BoundsStickSelectionPayload(
        boolean hasFirst,
        BlockPos first,
        boolean hasSecond,
        BlockPos second
) implements CustomPayload {
    public static final Identifier HANDSHAKE_ID = Identifier.of(Minehop.MOD_ID, "bounds_stick_selection");
    public static final Id<BoundsStickSelectionPayload> ID = new Id<>(HANDSHAKE_ID);
    public static final PacketCodec<PacketByteBuf, BoundsStickSelectionPayload> CODEC = PacketCodec.of(
            (value, buf) -> {
                buf.writeBoolean(value.hasFirst);
                buf.writeBlockPos(value.first == null ? BlockPos.ORIGIN : value.first);
                buf.writeBoolean(value.hasSecond);
                buf.writeBlockPos(value.second == null ? BlockPos.ORIGIN : value.second);
            },
            buf -> new BoundsStickSelectionPayload(
                    buf.readBoolean(),
                    buf.readBlockPos().toImmutable(),
                    buf.readBoolean(),
                    buf.readBlockPos().toImmutable()
            )
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
