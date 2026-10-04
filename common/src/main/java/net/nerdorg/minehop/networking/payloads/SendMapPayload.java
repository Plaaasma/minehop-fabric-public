package net.nerdorg.minehop.networking.payloads;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.nerdorg.minehop.Minehop;
import org.joml.Vector3f;

public record SendMapPayload(String buff) implements CustomPacketPayload {
	public static final ResourceLocation HANDSHAKE_ID = ResourceLocation.fromNamespaceAndPath(Minehop.MOD_ID, "send_maps");
	public static final Type<SendMapPayload> ID = new Type<>(HANDSHAKE_ID);
	public static final StreamCodec<FriendlyByteBuf, SendMapPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8, SendMapPayload::buff,
			SendMapPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return ID;
	}
}
