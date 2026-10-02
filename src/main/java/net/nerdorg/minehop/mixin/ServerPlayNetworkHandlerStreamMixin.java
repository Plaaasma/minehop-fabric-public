package net.nerdorg.minehop.mixin;

import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.TeleportConfirmC2SPacket;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.nerdorg.minehop.anticheat.stream.MovementValidator;
import org.objectweb.asm.Opcodes;
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
 * <p>1.21.1 port (pre-1.21.2 protocol): there is no {@code ClientTickEndC2SPacket} and
 * {@code PlayerInputC2SPacket} is vehicle-only, so each processed move packet is the client tick
 * boundary (see {@link MovementValidator}), and sneak/sprint come from {@code ClientCommandC2SPacket}.
 */
@Mixin(ServerPlayNetworkHandler.class)
public abstract class ServerPlayNetworkHandlerStreamMixin {
    private static final String FORCE_MAIN_THREAD =
            "Lnet/minecraft/network/NetworkThreadUtils;forceMainThread(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/listener/PacketListener;Lnet/minecraft/server/world/ServerWorld;)V";

    @Shadow
    public ServerPlayerEntity player;

    @Unique
    private boolean minehop$moveAccepted;

    // 1.21.1: replaces the 1.21.2+ onPlayerInput hook. Sneak/sprint state changes, sent by the client
    // right before the move packet of the same tick.
    @Inject(method = "onClientCommand", at = @At(value = "INVOKE", target = FORCE_MAIN_THREAD, shift = At.Shift.AFTER))
    private void minehop$onClientCommand(ClientCommandC2SPacket packet, CallbackInfo ci) {
        MovementValidator.onClientCommand(this.player, packet.getMode());
    }

    // Netty-thread pass: a move packet with no tick-end since the previous move is an extra tick
    // (1.21.1: no tick-end packet exists, so every move packet but the teleport echo counts).
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

    // Only reached when vanilla accepted the move and applied the client's position. 1.21.1 has no
    // handleMovement(); increaseTravelMotionStats is the last call of the same accepted branch.
    @Inject(method = "onPlayerMove", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/network/ServerPlayerEntity;increaseTravelMotionStats(DDD)V"))
    private void minehop$markMoveAccepted(PlayerMoveC2SPacket packet, CallbackInfo ci) {
        this.minehop$moveAccepted = true;
    }

    @Inject(method = "onPlayerMove", at = @At("RETURN"))
    private void minehop$afterPlayerMove(PlayerMoveC2SPacket packet, CallbackInfo ci) {
        // RETURN is only reached on the server thread (forceMainThread throws on the netty thread).
        if (this.minehop$moveAccepted) {
            this.minehop$moveAccepted = false;
            MovementValidator.onMoveAccepted(this.player, packet);
        } else {
            // 1.21.1: the packet still marks a client tick (stand-in for the tick-end packet).
            MovementValidator.onMoveNotAccepted(this.player);
        }
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

    // 1.21.1: onTeleportationDone() is only called while in the dimension-change teleportation state,
    // so hook the end of the accepted-confirm branch (requestedTeleportPos = null) instead.
    @Inject(method = "onTeleportConfirm", at = @At(value = "FIELD",
            target = "Lnet/minecraft/server/network/ServerPlayNetworkHandler;requestedTeleportPos:Lnet/minecraft/util/math/Vec3d;",
            opcode = Opcodes.PUTFIELD))
    private void minehop$teleportConfirmed(TeleportConfirmC2SPacket packet, CallbackInfo ci) {
        MovementValidator.onTeleportConfirmed(this.player);
    }

    // 1.21.1: no PlayerPosition overload; every teleport funnels into this one.
    @Inject(method = "requestTeleport(DDDFFLjava/util/Set;)V", at = @At("TAIL"))
    private void minehop$teleportRequested(double x, double y, double z, float yaw, float pitch,
                                           Set<PositionFlag> flags, CallbackInfo ci) {
        MovementValidator.onTeleportRequested(this.player, flags);
    }
}
