package net.nerdorg.minehop.networking.codec;

import net.minecraft.network.FriendlyByteBuf;
import org.joml.Vector3f;

/**
 * 1.20.1 backport shim of the 1.20.5+ {@code net.minecraft.network.codec.ByteBufCodecs} entries the payloads use. Wire
 * formats match the vanilla codecs (STRING_UTF8 = VarInt-length UTF-8 capped at 32767 chars, INT = 4-byte int,
 * VECTOR3F = three floats).
 */
public final class ByteBufCodecs {
    private ByteBufCodecs() {
    }

    public static final StreamCodec<FriendlyByteBuf, Boolean> BOOL = StreamCodec.ofMember(
            (value, buf) -> buf.writeBoolean(value), FriendlyByteBuf::readBoolean);
    public static final StreamCodec<FriendlyByteBuf, Integer> INT = StreamCodec.ofMember(
            (value, buf) -> buf.writeInt(value), FriendlyByteBuf::readInt);
    public static final StreamCodec<FriendlyByteBuf, Float> FLOAT = StreamCodec.ofMember(
            (value, buf) -> buf.writeFloat(value), FriendlyByteBuf::readFloat);
    public static final StreamCodec<FriendlyByteBuf, Double> DOUBLE = StreamCodec.ofMember(
            (value, buf) -> buf.writeDouble(value), FriendlyByteBuf::readDouble);
    public static final StreamCodec<FriendlyByteBuf, String> STRING_UTF8 = stringUtf8(32767);
    public static final StreamCodec<FriendlyByteBuf, Vector3f> VECTOR3F = StreamCodec.ofMember(
            (value, buf) -> buf.writeVector3f(value), FriendlyByteBuf::readVector3f);

    public static StreamCodec<FriendlyByteBuf, String> stringUtf8(int maxLength) {
        return StreamCodec.ofMember((value, buf) -> buf.writeUtf(value, maxLength), buf -> buf.readUtf(maxLength));
    }
}
