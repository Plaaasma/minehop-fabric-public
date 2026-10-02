package net.nerdorg.minehop.networking.payloads;

import net.minecraft.network.PacketByteBuf;
import net.nerdorg.minehop.networking.codec.PacketCodec;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.util.Identifier;
import net.nerdorg.minehop.Minehop;

public record OpenZoneStickSettingsPayload(
        String zoneType,
        String mapName,
        int checkpointIndex,
        boolean checkpointEditable,
        boolean preserveSpeed,
        boolean preserveSpeedEditable
) implements FabricPacket {
    private static final int MAX_ZONE_TYPE_LENGTH = 32;
    private static final int MAX_MAP_NAME_LENGTH = 128;
    public static final Identifier HANDSHAKE_ID = new Identifier(Minehop.MOD_ID, "open_zone_stick_settings");
    public static final PacketType<OpenZoneStickSettingsPayload> ID = PacketType.create(HANDSHAKE_ID, buf -> OpenZoneStickSettingsPayload.CODEC.decode(buf));
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
    public void write(PacketByteBuf buf) {
        CODEC.encode(buf, this);
    }

    @Override
    public PacketType<?> getType() {
        return ID;
    }
}
