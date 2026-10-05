package net.nerdorg.minehop.networking.payloads;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.nerdorg.minehop.Minehop;

public record ZoneStickSettingsPayload(
        String mapName,
        int checkpointIndex,
        boolean applyBounds,
        boolean preserveSpeed
) implements CustomPacketPayload {
    private static final int MAX_MAP_NAME_LENGTH = 128;
    public static final Identifier HANDSHAKE_ID = Identifier.fromNamespaceAndPath(Minehop.MOD_ID, "zone_stick_settings");
    public static final Type<ZoneStickSettingsPayload> ID = new Type<>(HANDSHAKE_ID);
    public static final StreamCodec<FriendlyByteBuf, ZoneStickSettingsPayload> CODEC = StreamCodec.ofMember(
            (value, buf) -> {
                buf.writeUtf(value.mapName == null ? "" : value.mapName, MAX_MAP_NAME_LENGTH);
                buf.writeInt(value.checkpointIndex);
                buf.writeBoolean(value.applyBounds);
                buf.writeBoolean(value.preserveSpeed);
            },
            buf -> new ZoneStickSettingsPayload(
                    buf.readUtf(MAX_MAP_NAME_LENGTH),
                    buf.readInt(),
                    buf.readBoolean(),
                    buf.readBoolean()
            )
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
