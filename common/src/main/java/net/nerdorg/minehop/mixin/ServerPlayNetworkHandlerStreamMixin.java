package net.nerdorg.minehop.mixin;

import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.RelativeMovement;
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
 * <p>1.21.1 port (pre-1.21.2 protocol): there is no {@code ServerboundClientTickEndPacket} and
 * {@code ServerboundPlayerInputPacket} is vehicle-only, so each processed move packet is the client tick
 * boundary (see {@link MovementValidator}), and sneak/sprint come from {@code ServerboundPlayerCommandPacket}.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerPlayNetworkHandlerStreamMixin {
    private static final String FORCE_MAIN_THREAD =
            "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V";

    @Shadow
    public ServerPlayer player;

    @Unique
    private boolean minehop$moveAccepted;

    // 1.21.1: replaces the 1.21.2+ handlePlayerInput hook. Sneak/sprint state changes, sent by the client
    // right before the move packet of the same tick.
    @Inject(method = "handlePlayerCommand", at = @At(value = "INVOKE", target = FORCE_MAIN_THREAD, shift = At.Shift.AFTER))
    private void minehop$onPlayerCommand(ServerboundPlayerCommandPacket packet, CallbackInfo ci) {
        MovementValidator.onPlayerCommand(this.player, packet.getAction());
    }

    // Netty-thread pass: a move packet with no tick-end since the previous move is an extra tick
    // (1.21.1: no tick-end packet exists, so every move packet but the teleport echo counts).
    @Inject(method = "handleMovePlayer", at = @At("HEAD"))
    private void minehop$playerMoveArrival(ServerboundMovePlayerPacket packet, CallbackInfo ci) {
        ServerPlayer current = this.player;
        MinecraftServer server = current == null ? null : current.getServer();
        if (server != null && !server.isSameThread()) {
            MovementValidator.onMovePacketNetwork(current);
        } else if (server != null) {
            // Server-thread pass of every move packet: pair it with its network arrival time. 1.21.1: taken here at
            // HEAD rather than after forceMainThread, so a pass another injection cancels there (a spectate session
            // ignoring the viewer's moves) still takes its arrival: the move packet's arrival times are this
            // version's tick timing, and a skipped one would leave every later tick with an older packet's time.
            MovementValidator.onMovePacketProcessing(current);
        }
    }

    @Inject(method = "handleMovePlayer", at = @At(value = "INVOKE", target = FORCE_MAIN_THREAD, shift = At.Shift.AFTER))
    private void minehop$beginPlayerMove(ServerboundMovePlayerPacket packet, CallbackInfo ci) {
        this.minehop$moveAccepted = false;
    }

    // Only reached when vanilla accepted the move and applied the client's position. 1.21.1 has no
    // handlePlayerKnownMovement(); checkMovementStatistics is the last call of the same accepted branch.
    @Inject(method = "handleMovePlayer", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;checkMovementStatistics(DDD)V"))
    private void minehop$markMoveAccepted(ServerboundMovePlayerPacket packet, CallbackInfo ci) {
        this.minehop$moveAccepted = true;
    }

    @Inject(method = "handleMovePlayer", at = @At("RETURN"))
    private void minehop$afterPlayerMove(ServerboundMovePlayerPacket packet, CallbackInfo ci) {
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
    @Inject(method = "handleAcceptTeleportPacket", at = @At("HEAD"))
    private void minehop$teleportConfirmArrival(ServerboundAcceptTeleportationPacket packet, CallbackInfo ci) {
        ServerPlayer current = this.player;
        MinecraftServer server = current == null ? null : current.getServer();
        if (server != null && !server.isSameThread()) {
            MovementValidator.onTeleportConfirmNetwork(current);
        }
    }

    // 1.21.1: hasChangedDimension() is only called while the player is changing dimension, so hook the
    // end of the accepted-confirm branch (awaitingPositionFromClient = null) instead.
    @Inject(method = "handleAcceptTeleportPacket", at = @At(value = "FIELD",
            target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;awaitingPositionFromClient:Lnet/minecraft/world/phys/Vec3;",
            opcode = 181 /* Opcodes.PUTFIELD (ASM is not on common's compile classpath) */))
    private void minehop$teleportConfirmed(ServerboundAcceptTeleportationPacket packet, CallbackInfo ci) {
        MovementValidator.onTeleportConfirmed(this.player);
    }

    // 1.21.1: no PositionMoveRotation overload; every teleport funnels into this one.
    @Inject(method = "teleport(DDDFFLjava/util/Set;)V", at = @At("TAIL"))
    private void minehop$teleportRequested(double x, double y, double z, float yRot, float xRot,
                                           Set<RelativeMovement> flags, CallbackInfo ci) {
        MovementValidator.onTeleportRequested(this.player, flags);
    }
}
