package net.nerdorg.minehop.networking.payloads;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.nerdorg.minehop.Minehop;

public record AntiCheatActionPayload(String action, String targetUuid) implements CustomPacketPayload {
    public static final Identifier HANDSHAKE_ID = Identifier.fromNamespaceAndPath(Minehop.MOD_ID, "anticheat_action");
    public static final Type<AntiCheatActionPayload> ID = new Type<>(HANDSHAKE_ID);
    // Bound strings: action is a short keyword, targetUuid is a 36-char UUID.
    public static final StreamCodec<FriendlyByteBuf, AntiCheatActionPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(32), AntiCheatActionPayload::action,
            ByteBufCodecs.stringUtf8(40), AntiCheatActionPayload::targetUuid,
            AntiCheatActionPayload::new
    );

    public static final String ACTION_CLEAR_FLAGS = "clear";
    public static final String ACTION_EXEMPT_ADD = "exempt_add";
    public static final String ACTION_EXEMPT_REMOVE = "exempt_remove";
    public static final String ACTION_KICK = "kick";
    public static final String ACTION_REFRESH = "refresh";

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
