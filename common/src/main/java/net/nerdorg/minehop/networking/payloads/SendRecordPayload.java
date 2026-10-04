package net.nerdorg.minehop.networking.payloads;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.nerdorg.minehop.Minehop;

public record SendRecordPayload(String buff) implements CustomPacketPayload {
	public static final ResourceLocation HANDSHAKE_ID = ResourceLocation.fromNamespaceAndPath(Minehop.MOD_ID, "send_records");
	public static final Type<SendRecordPayload> ID = new Type<>(HANDSHAKE_ID);
	public static final StreamCodec<FriendlyByteBuf, SendRecordPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8, SendRecordPayload::buff,
			SendRecordPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return ID;
	}
}
