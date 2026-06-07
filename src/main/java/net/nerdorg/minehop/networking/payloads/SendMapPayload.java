package net.nerdorg.minehop.networking.payloads;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.nerdorg.minehop.Minehop;
import org.joml.Vector3f;

public record SendMapPayload(String buff) implements CustomPayload {
	public static final Identifier HANDSHAKE_ID = Identifier.of(Minehop.MOD_ID, "send_maps");
	public static final Id<SendMapPayload> ID = new Id<>(HANDSHAKE_ID);
	public static final PacketCodec<PacketByteBuf, SendMapPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.STRING, SendMapPayload::buff,
			SendMapPayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
