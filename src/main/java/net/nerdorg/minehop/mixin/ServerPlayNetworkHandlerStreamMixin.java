package net.nerdorg.minehop.mixin;

import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.TeleportConfirmC2SPacket;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
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
 *
 * <p>1.20.1 backport: the 1.21.2+ protocol's {@code ClientTickEndC2SPacket} (tick boundary) and
 * key-state {@code PlayerInputC2SPacket} don't exist. A pre-1.21.2 client sends exactly one move
 * packet per client tick in which it moved, turned or changed its ground flag (plus a forced
 * position packet every 20 idle ticks), so every move packet the server processes is treated as the
 * end of one client tick. Sneak/sprint key states come from {@code ClientCommandC2SPacket}, which
 * the client sends right before that tick's move packet (same order as the 1.21.2+ input packet).
 */
@Mixin(ServerPlayNetworkHandler.class)
public abstract class ServerPlayNetworkHandlerStreamMixin {
    private static final String FORCE_MAIN_THREAD =
            "Lnet/minecraft/network/NetworkThreadUtils;forceMainThread(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/listener/PacketListener;Lnet/minecraft/server/world/ServerWorld;)V";

    @Shadow
    public ServerPlayerEntity player;

    @Unique
    private boolean minehop$moveAccepted;
    @Unique
    private boolean minehop$moveOnMainThread;

    // Sneak / sprint key states (the only keys a pre-1.21.2 client reports outside vehicles).
    @Inject(method = "onClientCommand", at = @At(value = "INVOKE", target = FORCE_MAIN_THREAD, shift = At.Shift.AFTER))
    private void minehop$onClientCommand(ClientCommandC2SPacket packet, CallbackInfo ci) {
        MovementValidator.onClientCommand(this.player, packet.getMode());
    }

    // Netty-thread pass: every move packet is one client tick for the timer check.
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
        this.minehop$moveOnMainThread = true;
    }

    // Only reached when vanilla accepted the move and applied the client's position (1.20.1 has no
    // handleMovement(); increaseTravelMotionStats is called only on that branch).
    @Inject(method = "onPlayerMove", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/network/ServerPlayerEntity;increaseTravelMotionStats(DDD)V"))
    private void minehop$markMoveAccepted(PlayerMoveC2SPacket packet, CallbackInfo ci) {
        this.minehop$moveAccepted = true;
    }

    // Main-thread pass end: the move packet closes one client tick (accepted or not, like a
    // 1.21.2+ tick-end that follows a rejected move).
    @Inject(method = "onPlayerMove", at = @At("RETURN"))
    private void minehop$afterPlayerMove(PlayerMoveC2SPacket packet, CallbackInfo ci) {
        if (!this.minehop$moveOnMainThread) {
            return;
        }
        this.minehop$moveOnMainThread = false;
        boolean accepted = this.minehop$moveAccepted;
        this.minehop$moveAccepted = false;
        MovementValidator.onMovePacketProcessed(this.player, packet, accepted);
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

    // 1.20.1 only calls onTeleportationDone() while in the teleportation state, so hook the
    // position update that every accepted confirm performs instead.
    @Inject(method = "onTeleportConfirm", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/network/ServerPlayerEntity;updatePositionAndAngles(DDDFF)V",
            shift = At.Shift.AFTER))
    private void minehop$teleportConfirmed(TeleportConfirmC2SPacket packet, CallbackInfo ci) {
        MovementValidator.onTeleportConfirmed(this.player);
    }

    @Inject(method = "requestTeleport(DDDFFLjava/util/Set;)V", at = @At("TAIL"))
    private void minehop$teleportRequested(double x, double y, double z, float yaw, float pitch,
                                           Set<PositionFlag> flags, CallbackInfo ci) {
        MovementValidator.onTeleportRequested(this.player, flags);
    }
}
