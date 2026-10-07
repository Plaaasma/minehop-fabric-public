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
 * <p>1.20.1 backport: the 1.21.2+ protocol's {@code ClientTickEndC2SPacket} (tick boundary) and
 * key-state {@code PlayerInputC2SPacket} don't exist. A pre-1.21.2 client sends exactly one move
 * packet per client tick in which it moved, turned or changed its ground flag (plus a forced
 * position packet every 20 idle ticks), so every move packet the server processes is treated as the
 * end of one client tick. Sneak/sprint key states come from {@code ClientCommandC2SPacket}, which
 * the client sends right before that tick's move packet (same order as the 1.21.2+ input packet).
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerPlayNetworkHandlerStreamMixin {
    private static final String FORCE_MAIN_THREAD =
            "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V";

    @Shadow
    public ServerPlayer player;

    @Unique
    private boolean minehop$moveAccepted;
    @Unique
    private boolean minehop$moveOnMainThread;

    // Sneak / sprint key states (the only keys a pre-1.21.2 client reports outside vehicles).
    @Inject(method = "handlePlayerCommand", at = @At(value = "INVOKE", target = FORCE_MAIN_THREAD, shift = At.Shift.AFTER))
    private void minehop$onClientCommand(ServerboundPlayerCommandPacket packet, CallbackInfo ci) {
        MovementValidator.onClientCommand(this.player, packet.getAction());
    }

    // Netty-thread pass: every move packet is one client tick for the timer check; its arrival time
    // travels with the packet to the server-thread pass (the tick stream, see ClientTick).
    @Inject(method = "handleMovePlayer", at = @At("HEAD"))
    private void minehop$playerMoveArrival(ServerboundMovePlayerPacket packet, CallbackInfo ci) {
        ServerPlayer current = this.player;
        MinecraftServer server = current == null ? null : current.getServer();
        if (server != null && !server.isSameThread()) {
            MovementValidator.onMovePacketNetwork(current, packet);
        }
    }

    @Inject(method = "handleMovePlayer", at = @At(value = "INVOKE", target = FORCE_MAIN_THREAD, shift = At.Shift.AFTER))
    private void minehop$beginPlayerMove(ServerboundMovePlayerPacket packet, CallbackInfo ci) {
        this.minehop$moveAccepted = false;
        this.minehop$moveOnMainThread = true;
    }

    // Only reached when vanilla accepted the move and applied the client's position (1.20.1 has no
    // handlePlayerKnownMovement(); checkMovementStatistics is called only on that branch).
    @Inject(method = "handleMovePlayer", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;checkMovementStatistics(DDD)V"))
    private void minehop$markMoveAccepted(ServerboundMovePlayerPacket packet, CallbackInfo ci) {
        this.minehop$moveAccepted = true;
    }

    // Main-thread pass end: the move packet closes one client tick (accepted or not, like a
    // 1.21.2+ tick-end that follows a rejected move).
    @Inject(method = "handleMovePlayer", at = @At("RETURN"))
    private void minehop$afterPlayerMove(ServerboundMovePlayerPacket packet, CallbackInfo ci) {
        if (!this.minehop$moveOnMainThread) {
            return;
        }
        this.minehop$moveOnMainThread = false;
        boolean accepted = this.minehop$moveAccepted;
        this.minehop$moveAccepted = false;
        MovementValidator.onMovePacketProcessed(this.player, packet, accepted);
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

    // 1.20.1 only calls hasChangedDimension() while in the teleportation state, so hook the
    // position update that every accepted confirm performs instead.
    @Inject(method = "handleAcceptTeleportPacket", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;absMoveTo(DDDFF)V",
            shift = At.Shift.AFTER))
    private void minehop$teleportConfirmed(ServerboundAcceptTeleportationPacket packet, CallbackInfo ci) {
        MovementValidator.onTeleportConfirmed(this.player);
    }

    @Inject(method = "teleport(DDDFFLjava/util/Set;)V", at = @At("TAIL"))
    private void minehop$teleportRequested(double x, double y, double z, float yaw, float pitch,
                                           Set<RelativeMovement> flags, CallbackInfo ci) {
        MovementValidator.onTeleportRequested(this.player, flags);
    }
}
