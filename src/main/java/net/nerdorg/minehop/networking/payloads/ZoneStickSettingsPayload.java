package net.nerdorg.minehop.networking.payloads;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.nerdorg.minehop.Minehop;

public record ZoneStickSettingsPayload(
        String mapName,
        int checkpointIndex,
        boolean applyBounds,
        boolean preserveSpeed
) implements CustomPayload {
    private static final int MAX_MAP_NAME_LENGTH = 128;
    public static final Identifier HANDSHAKE_ID = Identifier.of(Minehop.MOD_ID, "zone_stick_settings");
    public static final Id<ZoneStickSettingsPayload> ID = new Id<>(HANDSHAKE_ID);
    public static final PacketCodec<PacketByteBuf, ZoneStickSettingsPayload> CODEC = PacketCodec.of(
            (value, buf) -> {
                buf.writeString(value.mapName == null ? "" : value.mapName, MAX_MAP_NAME_LENGTH);
                buf.writeInt(value.checkpointIndex);
                buf.writeBoolean(value.applyBounds);
                buf.writeBoolean(value.preserveSpeed);
            },
            buf -> new ZoneStickSettingsPayload(
                    buf.readString(MAX_MAP_NAME_LENGTH),
                    buf.readInt(),
                    buf.readBoolean(),
                    buf.readBoolean()
            )
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
