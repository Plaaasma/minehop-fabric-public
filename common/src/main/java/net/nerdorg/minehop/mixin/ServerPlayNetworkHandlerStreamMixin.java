package net.nerdorg.minehop.mixin;

import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.network.protocol.game.ServerboundClientTickEndPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.phys.Vec3;
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
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerPlayNetworkHandlerStreamMixin {
    private static final String FORCE_MAIN_THREAD =
            "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V";

    @Shadow
    public ServerPlayer player;

    @Unique
    private boolean minehop$moveAccepted;

    @Inject(method = "handlePlayerInput", at = @At("TAIL"))
    private void minehop$onPlayerInput(ServerboundPlayerInputPacket packet, CallbackInfo ci) {
        MovementValidator.onPlayerInput(this.player, packet.input());
    }

    // Netty-thread pass: a move packet with no tick-end since the previous move is an extra tick.
    @Inject(method = "handleMovePlayer", at = @At("HEAD"))
    private void minehop$playerMoveArrival(ServerboundMovePlayerPacket packet, CallbackInfo ci) {
        ServerPlayer current = this.player;
        MinecraftServer server = current == null ? null : current.getServer();
        if (server != null && !server.isSameThread()) {
            MovementValidator.onMovePacketNetwork(current);
        }
    }

    @Inject(method = "handleMovePlayer", at = @At(value = "INVOKE", target = FORCE_MAIN_THREAD, shift = At.Shift.AFTER))
    private void minehop$beginPlayerMove(ServerboundMovePlayerPacket packet, CallbackInfo ci) {
        this.minehop$moveAccepted = false;
        // Server-thread pass of every move packet: pair it with its network arrival time.
        MovementValidator.onMovePacketProcessing(this.player);
    }

    // Only reached when vanilla accepted the move and applied the client's position.
    @Inject(method = "handlePlayerKnownMovement", at = @At("HEAD"))
    private void minehop$markMoveAccepted(Vec3 movement, CallbackInfo ci) {
        this.minehop$moveAccepted = true;
    }

    @Inject(method = "handleMovePlayer", at = @At("RETURN"))
    private void minehop$afterPlayerMove(ServerboundMovePlayerPacket packet, CallbackInfo ci) {
        if (this.minehop$moveAccepted) {
            this.minehop$moveAccepted = false;
            MovementValidator.onMoveAccepted(this.player, packet);
        }
    }

    @Inject(method = "handleClientTickEnd", at = @At("HEAD"))
    private void minehop$clientTickEndArrival(ServerboundClientTickEndPacket packet, CallbackInfo ci) {
        ServerPlayer current = this.player;
        MinecraftServer server = current == null ? null : current.getServer();
        // The netty-thread pass: timestamp arrival for the timer check (immune to server lag).
        if (server != null && !server.isSameThread()) {
            MovementValidator.onClientTickEndNetwork(current);
        }
    }

    @Inject(method = "handleClientTickEnd", at = @At(value = "INVOKE", target = FORCE_MAIN_THREAD, shift = At.Shift.AFTER))
    private void minehop$clientTickEnd(ServerboundClientTickEndPacket packet, CallbackInfo ci) {
        MovementValidator.onClientTickEnd(this.player);
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

    @Inject(method = "handleAcceptTeleportPacket", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;hasChangedDimension()V"))
    private void minehop$teleportConfirmed(ServerboundAcceptTeleportationPacket packet, CallbackInfo ci) {
        MovementValidator.onTeleportConfirmed(this.player);
    }

    @Inject(method = "teleport(Lnet/minecraft/world/entity/PositionMoveRotation;Ljava/util/Set;)V", at = @At("TAIL"))
    private void minehop$teleportRequested(PositionMoveRotation pos, Set<Relative> flags, CallbackInfo ci) {
        MovementValidator.onTeleportRequested(this.player, pos, flags);
    }
}
