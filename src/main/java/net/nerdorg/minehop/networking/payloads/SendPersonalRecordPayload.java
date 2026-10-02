package net.nerdorg.minehop.networking.payloads;

import net.minecraft.network.PacketByteBuf;
import net.nerdorg.minehop.networking.codec.PacketCodec;
import net.nerdorg.minehop.networking.codec.PacketCodecs;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.util.Identifier;
import net.nerdorg.minehop.Minehop;

public record SendPersonalRecordPayload(String buff) implements FabricPacket {
	public static final Identifier HANDSHAKE_ID = new Identifier(Minehop.MOD_ID, "send_personal_records");
	public static final PacketType<SendPersonalRecordPayload> ID = PacketType.create(HANDSHAKE_ID, buf -> SendPersonalRecordPayload.CODEC.decode(buf));
	public static final PacketCodec<PacketByteBuf, SendPersonalRecordPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.STRING, SendPersonalRecordPayload::buff,
			SendPersonalRecordPayload::new);

	@Override
    public void write(PacketByteBuf buf) {
        CODEC.encode(buf, this);
    }

    @Override
    public PacketType<?> getType() {
        return ID;
    }
}
