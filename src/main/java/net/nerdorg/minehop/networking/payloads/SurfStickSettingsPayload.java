package net.nerdorg.minehop.networking.payloads;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.nerdorg.minehop.Minehop;

public record SurfStickSettingsPayload(
        float width,
        float drop,
        String textureBlockId,
        boolean oneSided,
        boolean outsideCurve,
        String renderMode,
        int wireframeColor,
        boolean wireframeFill,
        int wireframeFillColor,
        int wireframeFillAlpha
) implements CustomPayload {
    private static final int MAX_TEXTURE_ID_LENGTH = 128;
    private static final int MAX_RENDER_MODE_LENGTH = 24;
    public static final Identifier HANDSHAKE_ID = Identifier.of(Minehop.MOD_ID, "surf_stick_settings");
    public static final Id<SurfStickSettingsPayload> ID = new Id<>(HANDSHAKE_ID);
    public static final PacketCodec<PacketByteBuf, SurfStickSettingsPayload> CODEC = PacketCodec.of(
            (value, buf) -> {
                buf.writeFloat(value.width);
                buf.writeFloat(value.drop);
                buf.writeString(value.textureBlockId == null ? "" : value.textureBlockId, MAX_TEXTURE_ID_LENGTH);
                buf.writeBoolean(value.oneSided);
                buf.writeBoolean(value.outsideCurve);
                buf.writeString(value.renderMode == null ? "" : value.renderMode, MAX_RENDER_MODE_LENGTH);
                buf.writeInt(value.wireframeColor);
                buf.writeBoolean(value.wireframeFill);
                buf.writeInt(value.wireframeFillColor);
                buf.writeInt(value.wireframeFillAlpha);
            },
            buf -> new SurfStickSettingsPayload(
                    buf.readFloat(),
                    buf.readFloat(),
                    buf.readString(MAX_TEXTURE_ID_LENGTH),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readString(MAX_RENDER_MODE_LENGTH),
                    buf.readInt(),
                    buf.readBoolean(),
                    buf.readInt(),
                    buf.readInt()
            )
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
