package net.nerdorg.minehop.mixin;

import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundTeleportToEntityPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.nerdorg.minehop.spectate.SpectateSessions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Server-side enforcement of spectate sessions: a spectating player can't use the vanilla spectator teleport
 * ("teleport to player", which works across dimensions) and the client's own movement is ignored - the server
 * keeps the player on the spectated target. Both checks run after the handler has moved to the server thread.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerPlayNetworkHandlerSpectateMixin {
    private static final String FORCE_MAIN_THREAD =
            "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V";

    @Shadow
    public ServerPlayer player;

    @Shadow
    private boolean clientIsFloating;

    @Inject(method = "handleTeleportToEntityPacket", at = @At(value = "INVOKE", target = FORCE_MAIN_THREAD, shift = At.Shift.AFTER), cancellable = true)
    private void minehop$noSpectatorTeleportInSession(ServerboundTeleportToEntityPacket packet, CallbackInfo ci) {
        if (SpectateSessions.isSpectating(this.player)) {
            SpectateSessions.onTeleportRequestRefused(this.player);
            ci.cancel();
        }
    }

    @Inject(method = "handleMovePlayer", at = @At(value = "INVOKE", target = FORCE_MAIN_THREAD, shift = At.Shift.AFTER), cancellable = true)
    private void minehop$ignoreMovesInSession(ServerboundMovePlayerPacket packet, CallbackInfo ci) {
        if (SpectateSessions.isSpectating(this.player)) {
            // Nothing from this packet is applied, so it can't leave a stale "floating" state for the flight kick either.
            this.clientIsFloating = false;
            ci.cancel();
        }
    }
}
