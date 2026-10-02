package net.nerdorg.minehop.networking.payloads;

import net.minecraft.network.PacketByteBuf;
import net.nerdorg.minehop.networking.codec.PacketCodec;
import net.nerdorg.minehop.networking.codec.PacketCodecs;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.util.Identifier;
import net.nerdorg.minehop.Minehop;

public record SetCheaterPayload(String uuid, boolean isCheater) implements FabricPacket {
    public static final Identifier HANDSHAKE_ID = new Identifier(Minehop.MOD_ID, "set_player_cheater");
    public static final PacketType<SetCheaterPayload> ID = PacketType.create(HANDSHAKE_ID, buf -> SetCheaterPayload.CODEC.decode(buf));
    public static final PacketCodec<PacketByteBuf, SetCheaterPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING, SetCheaterPayload::uuid,
            PacketCodecs.BOOLEAN, SetCheaterPayload::isCheater,
            SetCheaterPayload::new);

    @Override
    public void write(PacketByteBuf buf) {
        CODEC.encode(buf, this);
    }

    @Override
    public PacketType<?> getType() {
        return ID;
    }
}
