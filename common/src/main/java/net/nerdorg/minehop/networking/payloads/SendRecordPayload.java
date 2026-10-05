package net.nerdorg.minehop.networking.payloads;

import net.minecraft.network.FriendlyByteBuf;
import net.nerdorg.minehop.networking.codec.ByteBufCodecs;
import net.nerdorg.minehop.networking.codec.StreamCodec;
import net.nerdorg.minehop.networking.codec.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.nerdorg.minehop.Minehop;

public record SendRecordPayload(String buff) implements CustomPacketPayload {
	public static final ResourceLocation HANDSHAKE_ID = new ResourceLocation(Minehop.MOD_ID, "send_records");
	public static final Type<SendRecordPayload> ID = new Type<>(HANDSHAKE_ID);
	public static final StreamCodec<FriendlyByteBuf, SendRecordPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8, SendRecordPayload::buff,
			SendRecordPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return ID;
	}
}
