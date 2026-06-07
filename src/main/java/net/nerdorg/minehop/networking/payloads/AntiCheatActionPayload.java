package net.nerdorg.minehop.networking.payloads;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.nerdorg.minehop.Minehop;

public record AntiCheatActionPayload(String action, String targetUuid) implements CustomPayload {
    public static final Identifier HANDSHAKE_ID = Identifier.of(Minehop.MOD_ID, "anticheat_action");
    public static final Id<AntiCheatActionPayload> ID = new Id<>(HANDSHAKE_ID);
    public static final PacketCodec<PacketByteBuf, AntiCheatActionPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING, AntiCheatActionPayload::action,
            PacketCodecs.STRING, AntiCheatActionPayload::targetUuid,
            AntiCheatActionPayload::new
    );

    public static final String ACTION_CLEAR_FLAGS = "clear";
    public static final String ACTION_EXEMPT_ADD = "exempt_add";
    public static final String ACTION_EXEMPT_REMOVE = "exempt_remove";
    public static final String ACTION_KICK = "kick";
    public static final String ACTION_REFRESH = "refresh";

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
