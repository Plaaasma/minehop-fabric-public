package net.nerdorg.minehop.mixin;

import net.minecraft.entity.player.PlayerPosition;
import net.minecraft.network.packet.c2s.play.ClientTickEndC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInputC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.TeleportConfirmC2SPacket;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import net.nerdorg.minehop.anticheat.stream.MovementValidator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

/**
 * Feeds the client's movement packet stream to {@link MovementValidator}. Handlers first run on the
 * netty thread and re-schedule themselves on the server thread via {@code forceMainThread}; the
 * injections after that call therefore only run on the server thread, in packet order.
 */
@Mixin(ServerPlayNetworkHandler.class)
public abstract class ServerPlayNetworkHandlerStreamMixin {
    private static final String FORCE_MAIN_THREAD =
            "Lnet/minecraft/network/NetworkThreadUtils;forceMainThread(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/listener/PacketListener;Lnet/minecraft/server/world/ServerWorld;)V";

    @Shadow
    public ServerPlayerEntity player;

    @Unique
    private boolean minehop$moveAccepted;

    @Inject(method = "onPlayerInput", at = @At("TAIL"))
    private void minehop$onPlayerInput(PlayerInputC2SPacket packet, CallbackInfo ci) {
        MovementValidator.onPlayerInput(this.player, packet.input());
    }

    // Netty-thread pass: a move packet with no tick-end since the previous move is an extra tick.
    @Inject(method = "onPlayerMove", at = @At("HEAD"))
    private void minehop$playerMoveArrival(PlayerMoveC2SPacket packet, CallbackInfo ci) {
        ServerPlayerEntity current = this.player;
        MinecraftServer server = current == null ? null : current.getServer();
        if (server != null && !server.isOnThread()) {
            MovementValidator.onMovePacketNetwork(current);
        }
    }

    @Inject(method = "onPlayerMove", at = @At(value = "INVOKE", target = FORCE_MAIN_THREAD, shift = At.Shift.AFTER))
    private void minehop$beginPlayerMove(PlayerMoveC2SPacket packet, CallbackInfo ci) {
        this.minehop$moveAccepted = false;
    }

    // Only reached when vanilla accepted the move and applied the client's position.
    @Inject(method = "handleMovement", at = @At("HEAD"))
    private void minehop$markMoveAccepted(Vec3d movement, CallbackInfo ci) {
        this.minehop$moveAccepted = true;
    }

    @Inject(method = "onPlayerMove", at = @At("RETURN"))
    private void minehop$afterPlayerMove(PlayerMoveC2SPacket packet, CallbackInfo ci) {
        if (this.minehop$moveAccepted) {
            this.minehop$moveAccepted = false;
            MovementValidator.onMoveAccepted(this.player, packet);
        }
    }

    @Inject(method = "onClientTickEnd", at = @At("HEAD"))
    private void minehop$clientTickEndArrival(ClientTickEndC2SPacket packet, CallbackInfo ci) {
        ServerPlayerEntity current = this.player;
        MinecraftServer server = current == null ? null : current.getServer();
        // The netty-thread pass: timestamp arrival for the timer check (immune to server lag).
        if (server != null && !server.isOnThread()) {
            MovementValidator.onClientTickEndNetwork(current);
        }
    }

    @Inject(method = "onClientTickEnd", at = @At(value = "INVOKE", target = FORCE_MAIN_THREAD, shift = At.Shift.AFTER))
    private void minehop$clientTickEnd(ClientTickEndC2SPacket packet, CallbackInfo ci) {
        MovementValidator.onClientTickEnd(this.player);
    }

    // Netty-thread pass: the move packet the client echoes after confirming is not a tick.
    @Inject(method = "onTeleportConfirm", at = @At("HEAD"))
    private void minehop$teleportConfirmArrival(TeleportConfirmC2SPacket packet, CallbackInfo ci) {
        ServerPlayerEntity current = this.player;
        MinecraftServer server = current == null ? null : current.getServer();
        if (server != null && !server.isOnThread()) {
            MovementValidator.onTeleportConfirmNetwork(current);
        }
    }

    @Inject(method = "onTeleportConfirm", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/network/ServerPlayerEntity;onTeleportationDone()V"))
    private void minehop$teleportConfirmed(TeleportConfirmC2SPacket packet, CallbackInfo ci) {
        MovementValidator.onTeleportConfirmed(this.player);
    }

    @Inject(method = "requestTeleport(Lnet/minecraft/entity/player/PlayerPosition;Ljava/util/Set;)V", at = @At("TAIL"))
    private void minehop$teleportRequested(PlayerPosition pos, Set<PositionFlag> flags, CallbackInfo ci) {
        MovementValidator.onTeleportRequested(this.player, pos, flags);
    }
}
