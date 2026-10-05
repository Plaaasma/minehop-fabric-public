package net.nerdorg.minehop.networking.payloads;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.nerdorg.minehop.Minehop;

public record SendPersonalRecordPayload(String buff) implements CustomPacketPayload {
	public static final Identifier HANDSHAKE_ID = Identifier.fromNamespaceAndPath(Minehop.MOD_ID, "send_personal_records");
	public static final Type<SendPersonalRecordPayload> ID = new Type<>(HANDSHAKE_ID);
	public static final StreamCodec<FriendlyByteBuf, SendPersonalRecordPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8, SendPersonalRecordPayload::buff,
			SendPersonalRecordPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return ID;
	}
}
