package net.nerdorg.minehop.networking.payloads;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.nerdorg.minehop.Minehop;

public record AntiCheatPayload(String buff) implements CustomPacketPayload {
    public static final Identifier HANDSHAKE_ID = Identifier.fromNamespaceAndPath(Minehop.MOD_ID, "anti_cheat_check");
    public static final Type<AntiCheatPayload> ID = new Type<>(HANDSHAKE_ID);
    // Bound the buff to the server's MAX_BUFF_CHARS instead of the protocol default (32767).
    public static final StreamCodec<FriendlyByteBuf, AntiCheatPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(net.nerdorg.minehop.networking.PacketHandler.MAX_BUFF_CHARS), AntiCheatPayload::buff,
            AntiCheatPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
