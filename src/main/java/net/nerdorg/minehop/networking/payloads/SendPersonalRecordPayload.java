package net.nerdorg.minehop.networking.payloads;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.nerdorg.minehop.Minehop;

public record SendPersonalRecordPayload(String buff) implements CustomPayload {
	public static final Identifier HANDSHAKE_ID = Identifier.of(Minehop.MOD_ID, "send_personal_records");
	public static final Id<SendPersonalRecordPayload> ID = new Id<>(HANDSHAKE_ID);
	public static final PacketCodec<PacketByteBuf, SendPersonalRecordPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.STRING, SendPersonalRecordPayload::buff,
			SendPersonalRecordPayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
