package net.nerdorg.minehop.networking.payloads;

import net.minecraft.network.PacketByteBuf;
import net.nerdorg.minehop.networking.codec.PacketCodec;
import net.nerdorg.minehop.networking.codec.PacketCodecs;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.util.Identifier;
import net.nerdorg.minehop.Minehop;

public record AntiCheatActionPayload(String action, String targetUuid) implements FabricPacket {
    public static final Identifier HANDSHAKE_ID = new Identifier(Minehop.MOD_ID, "anticheat_action");
    public static final PacketType<AntiCheatActionPayload> ID = PacketType.create(HANDSHAKE_ID, buf -> AntiCheatActionPayload.CODEC.decode(buf));
    // Bound strings: action is a short keyword, targetUuid is a 36-char UUID.
    public static final PacketCodec<PacketByteBuf, AntiCheatActionPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.string(32), AntiCheatActionPayload::action,
            PacketCodecs.string(40), AntiCheatActionPayload::targetUuid,
            AntiCheatActionPayload::new
    );

    public static final String ACTION_CLEAR_FLAGS = "clear";
    public static final String ACTION_EXEMPT_ADD = "exempt_add";
    public static final String ACTION_EXEMPT_REMOVE = "exempt_remove";
    public static final String ACTION_KICK = "kick";
    public static final String ACTION_REFRESH = "refresh";

    @Override
    public void write(PacketByteBuf buf) {
        CODEC.encode(buf, this);
    }

    @Override
    public PacketType<?> getType() {
        return ID;
    }
}
