package net.nerdorg.minehop.networking.payloads;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.nerdorg.minehop.Minehop;
import org.joml.Vector3f;

public record ZoneSyncIDPayload(int entityId, Vector3f pos1, Vector3f pos2, String name, int check_index) implements CustomPayload {
    public static final Identifier HANDSHAKE_ID = Identifier.of(Minehop.MOD_ID, "zone");
    public static final Id<ZoneSyncIDPayload> ID = new Id<>(HANDSHAKE_ID);
    public static final PacketCodec<PacketByteBuf, ZoneSyncIDPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.INTEGER, ZoneSyncIDPayload::entityId,
            PacketCodecs.VECTOR_3F, ZoneSyncIDPayload::pos1,
            PacketCodecs.VECTOR_3F, ZoneSyncIDPayload::pos2,
            PacketCodecs.STRING, ZoneSyncIDPayload::name,
            PacketCodecs.INTEGER, ZoneSyncIDPayload::check_index,
            ZoneSyncIDPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
