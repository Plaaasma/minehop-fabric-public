package net.nerdorg.minehop.networking.payloads;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.nerdorg.minehop.Minehop;
import org.joml.Vector3f;

public record ZoneSyncIDPayload(int entityId, Vector3f pos1, Vector3f pos2, String name, int check_index) implements CustomPacketPayload {
    public static final ResourceLocation HANDSHAKE_ID = ResourceLocation.fromNamespaceAndPath(Minehop.MOD_ID, "zone");
    public static final Type<ZoneSyncIDPayload> ID = new Type<>(HANDSHAKE_ID);
    public static final StreamCodec<FriendlyByteBuf, ZoneSyncIDPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, ZoneSyncIDPayload::entityId,
            ByteBufCodecs.VECTOR3F, ZoneSyncIDPayload::pos1,
            ByteBufCodecs.VECTOR3F, ZoneSyncIDPayload::pos2,
            ByteBufCodecs.STRING_UTF8, ZoneSyncIDPayload::name,
            ByteBufCodecs.INT, ZoneSyncIDPayload::check_index,
            ZoneSyncIDPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
