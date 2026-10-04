package net.nerdorg.minehop.networking.payloads;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.nerdorg.minehop.Minehop;

public record OpenSurfStickSettingsPayload(
        float width,
        float drop,
        String textureBlockId,
        boolean oneSided,
        boolean outsideCurve,
        String renderMode,
        int wireframeColor,
        boolean wireframeFill,
        int wireframeFillColor,
        int wireframeFillAlpha,
        boolean editingExisting
) implements CustomPacketPayload {
    private static final int MAX_TEXTURE_ID_LENGTH = 128;
    private static final int MAX_RENDER_MODE_LENGTH = 24;
    public static final ResourceLocation HANDSHAKE_ID = ResourceLocation.fromNamespaceAndPath(Minehop.MOD_ID, "open_surf_stick_settings");
    public static final Type<OpenSurfStickSettingsPayload> ID = new Type<>(HANDSHAKE_ID);
    public static final StreamCodec<FriendlyByteBuf, OpenSurfStickSettingsPayload> CODEC = StreamCodec.ofMember(
            (value, buf) -> {
                buf.writeFloat(value.width);
                buf.writeFloat(value.drop);
                buf.writeUtf(value.textureBlockId == null ? "" : value.textureBlockId, MAX_TEXTURE_ID_LENGTH);
                buf.writeBoolean(value.oneSided);
                buf.writeBoolean(value.outsideCurve);
                buf.writeUtf(value.renderMode == null ? "" : value.renderMode, MAX_RENDER_MODE_LENGTH);
                buf.writeInt(value.wireframeColor);
                buf.writeBoolean(value.wireframeFill);
                buf.writeInt(value.wireframeFillColor);
                buf.writeInt(value.wireframeFillAlpha);
                buf.writeBoolean(value.editingExisting);
            },
            buf -> new OpenSurfStickSettingsPayload(
                    buf.readFloat(),
                    buf.readFloat(),
                    buf.readUtf(MAX_TEXTURE_ID_LENGTH),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readUtf(MAX_RENDER_MODE_LENGTH),
                    buf.readInt(),
                    buf.readBoolean(),
                    buf.readInt(),
                    buf.readInt(),
                    buf.readBoolean()
            )
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
