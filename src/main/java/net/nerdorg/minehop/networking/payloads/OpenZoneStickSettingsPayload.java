package net.nerdorg.minehop.networking.payloads;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.nerdorg.minehop.Minehop;

public record OpenZoneStickSettingsPayload(
        String zoneType,
        String mapName,
        int checkpointIndex,
        boolean checkpointEditable,
        boolean preserveSpeed,
        boolean preserveSpeedEditable
) implements CustomPayload {
    private static final int MAX_ZONE_TYPE_LENGTH = 32;
    private static final int MAX_MAP_NAME_LENGTH = 128;
    public static final Identifier HANDSHAKE_ID = Identifier.of(Minehop.MOD_ID, "open_zone_stick_settings");
    public static final Id<OpenZoneStickSettingsPayload> ID = new Id<>(HANDSHAKE_ID);
    public static final PacketCodec<PacketByteBuf, OpenZoneStickSettingsPayload> CODEC = PacketCodec.of(
            (value, buf) -> {
                buf.writeString(value.zoneType == null ? "" : value.zoneType, MAX_ZONE_TYPE_LENGTH);
                buf.writeString(value.mapName == null ? "" : value.mapName, MAX_MAP_NAME_LENGTH);
                buf.writeInt(value.checkpointIndex);
                buf.writeBoolean(value.checkpointEditable);
                buf.writeBoolean(value.preserveSpeed);
                buf.writeBoolean(value.preserveSpeedEditable);
            },
            buf -> new OpenZoneStickSettingsPayload(
                    buf.readString(MAX_ZONE_TYPE_LENGTH),
                    buf.readString(MAX_MAP_NAME_LENGTH),
                    buf.readInt(),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readBoolean()
            )
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
