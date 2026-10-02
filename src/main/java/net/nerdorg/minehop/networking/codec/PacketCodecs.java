package net.nerdorg.minehop.networking.codec;

import net.minecraft.network.PacketByteBuf;
import org.joml.Vector3f;

/**
 * 1.20.1 backport shim of the 1.20.5+ {@code net.minecraft.network.codec.PacketCodecs} entries the
 * payloads use. Wire formats match the vanilla codecs (STRING = VarInt-length UTF-8 capped at
 * 32767 chars, INTEGER = 4-byte int, VECTOR_3F = three floats).
 */
public final class PacketCodecs {
    private PacketCodecs() {
    }

    public static final PacketCodec<PacketByteBuf, Boolean> BOOLEAN = PacketCodec.of(
            (value, buf) -> buf.writeBoolean(value), PacketByteBuf::readBoolean);
    public static final PacketCodec<PacketByteBuf, Integer> INTEGER = PacketCodec.of(
            (value, buf) -> buf.writeInt(value), PacketByteBuf::readInt);
    public static final PacketCodec<PacketByteBuf, Float> FLOAT = PacketCodec.of(
            (value, buf) -> buf.writeFloat(value), PacketByteBuf::readFloat);
    public static final PacketCodec<PacketByteBuf, Double> DOUBLE = PacketCodec.of(
            (value, buf) -> buf.writeDouble(value), PacketByteBuf::readDouble);
    public static final PacketCodec<PacketByteBuf, String> STRING = string(32767);
    public static final PacketCodec<PacketByteBuf, Vector3f> VECTOR_3F = PacketCodec.of(
            (value, buf) -> buf.writeVector3f(value), PacketByteBuf::readVector3f);

    public static PacketCodec<PacketByteBuf, String> string(int maxLength) {
        return PacketCodec.of((value, buf) -> buf.writeString(value, maxLength), buf -> buf.readString(maxLength));
    }
}
