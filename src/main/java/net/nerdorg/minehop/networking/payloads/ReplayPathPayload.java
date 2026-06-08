package net.nerdorg.minehop.networking.payloads;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.nerdorg.minehop.Minehop;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

public record ReplayPathPayload(
        boolean clear,
        List<Vector3f> points
) implements CustomPayload {
    public static final int MAX_POINTS_PER_PACKET = 2048;
    public static final Identifier HANDSHAKE_ID = Identifier.of(Minehop.MOD_ID, "replay_path");
    public static final Id<ReplayPathPayload> ID = new Id<>(HANDSHAKE_ID);
    public static final PacketCodec<PacketByteBuf, ReplayPathPayload> CODEC = PacketCodec.of(
            (value, buf) -> {
                buf.writeBoolean(value.clear);
                List<Vector3f> list = value.points == null ? List.of() : value.points;
                int count = Math.min(list.size(), MAX_POINTS_PER_PACKET);
                buf.writeVarInt(count);
                for (int i = 0; i < count; i++) {
                    Vector3f point = list.get(i);
                    buf.writeFloat(point == null ? 0.0F : point.x());
                    buf.writeFloat(point == null ? 0.0F : point.y());
                    buf.writeFloat(point == null ? 0.0F : point.z());
                }
            },
            buf -> {
                boolean clear = buf.readBoolean();
                int count = Math.max(0, Math.min(buf.readVarInt(), MAX_POINTS_PER_PACKET));
                List<Vector3f> points = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    points.add(new Vector3f(buf.readFloat(), buf.readFloat(), buf.readFloat()));
                }
                return new ReplayPathPayload(clear, points);
            }
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
