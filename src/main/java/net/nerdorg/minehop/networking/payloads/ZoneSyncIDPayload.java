package net.nerdorg.minehop.networking.payloads;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.nerdorg.minehop.Minehop;
import org.joml.Vector3f;

public record ZoneSyncIDPayload(int entityId, Vector3f pos1, Vector3f pos2, String name, int check_index) implements CustomPacketPayload {
    public static final Identifier HANDSHAKE_ID = Identifier.fromNamespaceAndPath(Minehop.MOD_ID, "zone");
    public static final Type<ZoneSyncIDPayload> ID = new Type<>(HANDSHAKE_ID);
    // PacketCodecs.VECTOR_3F is typed Vector3fc since 1.21.5; same 3-float wire format.
    private static final StreamCodec<io.netty.buffer.ByteBuf, Vector3f> VECTOR_3F = ByteBufCodecs.VECTOR3F.map(Vector3f::new, v -> v);
    public static final StreamCodec<FriendlyByteBuf, ZoneSyncIDPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, ZoneSyncIDPayload::entityId,
            VECTOR_3F, ZoneSyncIDPayload::pos1,
            VECTOR_3F, ZoneSyncIDPayload::pos2,
            ByteBufCodecs.STRING_UTF8, ZoneSyncIDPayload::name,
            ByteBufCodecs.INT, ZoneSyncIDPayload::check_index,
            ZoneSyncIDPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
