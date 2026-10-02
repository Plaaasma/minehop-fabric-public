package net.nerdorg.minehop.networking.payloads;

import net.minecraft.network.PacketByteBuf;
import net.nerdorg.minehop.networking.codec.PacketCodec;
import net.nerdorg.minehop.networking.codec.PacketCodecs;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.util.Identifier;
import net.nerdorg.minehop.Minehop;
import org.joml.Vector3f;

public record SendMapPayload(String buff) implements FabricPacket {
	public static final Identifier HANDSHAKE_ID = new Identifier(Minehop.MOD_ID, "send_maps");
	public static final PacketType<SendMapPayload> ID = PacketType.create(HANDSHAKE_ID, buf -> SendMapPayload.CODEC.decode(buf));
	public static final PacketCodec<PacketByteBuf, SendMapPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.STRING, SendMapPayload::buff,
			SendMapPayload::new);

	@Override
    public void write(PacketByteBuf buf) {
        CODEC.encode(buf, this);
    }

    @Override
    public PacketType<?> getType() {
        return ID;
    }
}
