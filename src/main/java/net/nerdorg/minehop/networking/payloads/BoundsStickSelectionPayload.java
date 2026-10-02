package net.nerdorg.minehop.networking.payloads;

import net.minecraft.network.PacketByteBuf;
import net.nerdorg.minehop.networking.codec.PacketCodec;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.nerdorg.minehop.Minehop;

public record BoundsStickSelectionPayload(
        boolean hasFirst,
        BlockPos first,
        boolean hasSecond,
        BlockPos second
) implements FabricPacket {
    public static final Identifier HANDSHAKE_ID = new Identifier(Minehop.MOD_ID, "bounds_stick_selection");
    public static final PacketType<BoundsStickSelectionPayload> ID = PacketType.create(HANDSHAKE_ID, buf -> BoundsStickSelectionPayload.CODEC.decode(buf));
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
    public void write(PacketByteBuf buf) {
        CODEC.encode(buf, this);
    }

    @Override
    public PacketType<?> getType() {
        return ID;
    }
}
