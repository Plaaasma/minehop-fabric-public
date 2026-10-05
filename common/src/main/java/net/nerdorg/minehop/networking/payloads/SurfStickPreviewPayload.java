package net.nerdorg.minehop.networking.payloads;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.nerdorg.minehop.Minehop;

import java.util.ArrayList;
import java.util.List;

public record SurfStickPreviewPayload(
        boolean clear,
        float width,
        float drop,
        boolean oneSided,
        boolean outsideCurve,
        List<BlockPos> points
) implements CustomPacketPayload {
    private static final int MAX_POINTS = 512;
    public static final Identifier HANDSHAKE_ID = Identifier.fromNamespaceAndPath(Minehop.MOD_ID, "surf_stick_preview");
    public static final Type<SurfStickPreviewPayload> ID = new Type<>(HANDSHAKE_ID);
    public static final StreamCodec<FriendlyByteBuf, SurfStickPreviewPayload> CODEC = StreamCodec.ofMember(
            (value, buf) -> {
                buf.writeBoolean(value.clear);
                buf.writeFloat(value.width);
                buf.writeFloat(value.drop);
                buf.writeBoolean(value.oneSided);
                buf.writeBoolean(value.outsideCurve);

                List<BlockPos> list = value.points != null ? value.points : List.of();
                int count = Math.min(list.size(), MAX_POINTS);
                buf.writeVarInt(count);
                for (int i = 0; i < count; i++) {
                    buf.writeBlockPos(list.get(i));
                }
            },
            buf -> {
                boolean clear = buf.readBoolean();
                float width = buf.readFloat();
                float drop = buf.readFloat();
                boolean oneSided = buf.readBoolean();
                boolean outsideCurve = buf.readBoolean();
                int count = Math.max(0, Math.min(buf.readVarInt(), MAX_POINTS));
                List<BlockPos> points = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    points.add(buf.readBlockPos().immutable());
                }
                return new SurfStickPreviewPayload(clear, width, drop, oneSided, outsideCurve, points);
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
