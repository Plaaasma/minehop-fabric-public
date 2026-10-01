package net.nerdorg.minehop.networking.payloads;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.nerdorg.minehop.Minehop;

public record AntiCheatPayload(String buff) implements CustomPayload {
    public static final Identifier HANDSHAKE_ID = Identifier.of(Minehop.MOD_ID, "anti_cheat_check");
    public static final Id<AntiCheatPayload> ID = new Id<>(HANDSHAKE_ID);
    // Bound the buff to the server's MAX_BUFF_CHARS instead of the protocol default (32767).
    public static final PacketCodec<PacketByteBuf, AntiCheatPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.string(net.nerdorg.minehop.networking.PacketHandler.MAX_BUFF_CHARS), AntiCheatPayload::buff,
            AntiCheatPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
