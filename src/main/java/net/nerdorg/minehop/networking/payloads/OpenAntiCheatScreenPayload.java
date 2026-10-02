package net.nerdorg.minehop.networking.payloads;

import net.minecraft.network.PacketByteBuf;
import net.nerdorg.minehop.networking.codec.PacketCodec;
import net.nerdorg.minehop.networking.codec.PacketCodecs;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.util.Identifier;
import net.nerdorg.minehop.Minehop;

public record OpenAntiCheatScreenPayload(String json) implements FabricPacket {
    public static final Identifier HANDSHAKE_ID = new Identifier(Minehop.MOD_ID, "open_anticheat_screen");
    public static final PacketType<OpenAntiCheatScreenPayload> ID = PacketType.create(HANDSHAKE_ID, buf -> OpenAntiCheatScreenPayload.CODEC.decode(buf));
    public static final PacketCodec<PacketByteBuf, OpenAntiCheatScreenPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING, OpenAntiCheatScreenPayload::json,
            OpenAntiCheatScreenPayload::new
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
