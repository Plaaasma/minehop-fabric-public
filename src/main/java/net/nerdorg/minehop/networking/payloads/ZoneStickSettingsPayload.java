package net.nerdorg.minehop.networking.payloads;

import net.minecraft.network.PacketByteBuf;
import net.nerdorg.minehop.networking.codec.PacketCodec;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.util.Identifier;
import net.nerdorg.minehop.Minehop;

public record ZoneStickSettingsPayload(
        String mapName,
        int checkpointIndex,
        boolean applyBounds,
        boolean preserveSpeed
) implements FabricPacket {
    private static final int MAX_MAP_NAME_LENGTH = 128;
    public static final Identifier HANDSHAKE_ID = new Identifier(Minehop.MOD_ID, "zone_stick_settings");
    public static final PacketType<ZoneStickSettingsPayload> ID = PacketType.create(HANDSHAKE_ID, buf -> ZoneStickSettingsPayload.CODEC.decode(buf));
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
    public void write(PacketByteBuf buf) {
        CODEC.encode(buf, this);
    }

    @Override
    public PacketType<?> getType() {
        return ID;
    }
}
