package net.nerdorg.minehop.networking.payloads;

import net.minecraft.network.PacketByteBuf;
import net.nerdorg.minehop.networking.codec.PacketCodec;
import net.nerdorg.minehop.networking.codec.PacketCodecs;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.util.Identifier;
import net.nerdorg.minehop.Minehop;
import org.joml.Vector3f;

public record ZoneSyncIDPayload(int entityId, Vector3f pos1, Vector3f pos2, String name, int check_index) implements FabricPacket {
    public static final Identifier HANDSHAKE_ID = new Identifier(Minehop.MOD_ID, "zone");
    public static final PacketType<ZoneSyncIDPayload> ID = PacketType.create(HANDSHAKE_ID, buf -> ZoneSyncIDPayload.CODEC.decode(buf));
    public static final PacketCodec<PacketByteBuf, ZoneSyncIDPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.INTEGER, ZoneSyncIDPayload::entityId,
            PacketCodecs.VECTOR_3F, ZoneSyncIDPayload::pos1,
            PacketCodecs.VECTOR_3F, ZoneSyncIDPayload::pos2,
            PacketCodecs.STRING, ZoneSyncIDPayload::name,
            PacketCodecs.INTEGER, ZoneSyncIDPayload::check_index,
            ZoneSyncIDPayload::new);

    @Override
    public void write(PacketByteBuf buf) {
        CODEC.encode(buf, this);
    }

    @Override
    public PacketType<?> getType() {
        return ID;
    }
}
