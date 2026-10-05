package net.nerdorg.minehop.networking.payloads;

import net.minecraft.network.FriendlyByteBuf;
import net.nerdorg.minehop.networking.codec.StreamCodec;
import net.nerdorg.minehop.networking.codec.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.nerdorg.minehop.Minehop;

public record OpenZoneStickSettingsPayload(
        String zoneType,
        String mapName,
        int checkpointIndex,
        boolean checkpointEditable,
        boolean preserveSpeed,
        boolean preserveSpeedEditable
) implements CustomPacketPayload {
    private static final int MAX_ZONE_TYPE_LENGTH = 32;
    private static final int MAX_MAP_NAME_LENGTH = 128;
    public static final ResourceLocation HANDSHAKE_ID = new ResourceLocation(Minehop.MOD_ID, "open_zone_stick_settings");
    public static final Type<OpenZoneStickSettingsPayload> ID = new Type<>(HANDSHAKE_ID);
    public static final StreamCodec<FriendlyByteBuf, OpenZoneStickSettingsPayload> CODEC = StreamCodec.ofMember(
            (value, buf) -> {
                buf.writeUtf(value.zoneType == null ? "" : value.zoneType, MAX_ZONE_TYPE_LENGTH);
                buf.writeUtf(value.mapName == null ? "" : value.mapName, MAX_MAP_NAME_LENGTH);
                buf.writeInt(value.checkpointIndex);
                buf.writeBoolean(value.checkpointEditable);
                buf.writeBoolean(value.preserveSpeed);
                buf.writeBoolean(value.preserveSpeedEditable);
            },
            buf -> new OpenZoneStickSettingsPayload(
                    buf.readUtf(MAX_ZONE_TYPE_LENGTH),
                    buf.readUtf(MAX_MAP_NAME_LENGTH),
                    buf.readInt(),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readBoolean()
            )
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
