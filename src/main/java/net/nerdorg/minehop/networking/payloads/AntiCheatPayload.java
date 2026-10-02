package net.nerdorg.minehop.networking.payloads;

import net.minecraft.network.PacketByteBuf;
import net.nerdorg.minehop.networking.codec.PacketCodec;
import net.nerdorg.minehop.networking.codec.PacketCodecs;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.util.Identifier;
import net.nerdorg.minehop.Minehop;

public record AntiCheatPayload(String buff) implements FabricPacket {
    public static final Identifier HANDSHAKE_ID = new Identifier(Minehop.MOD_ID, "anti_cheat_check");
    public static final PacketType<AntiCheatPayload> ID = PacketType.create(HANDSHAKE_ID, buf -> AntiCheatPayload.CODEC.decode(buf));
    // Bound the buff to the server's MAX_BUFF_CHARS instead of the protocol default (32767).
    public static final PacketCodec<PacketByteBuf, AntiCheatPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.string(net.nerdorg.minehop.networking.PacketHandler.MAX_BUFF_CHARS), AntiCheatPayload::buff,
            AntiCheatPayload::new);

    @Override
    public void write(PacketByteBuf buf) {
        CODEC.encode(buf, this);
    }

    @Override
    public PacketType<?> getType() {
        return ID;
    }
}
