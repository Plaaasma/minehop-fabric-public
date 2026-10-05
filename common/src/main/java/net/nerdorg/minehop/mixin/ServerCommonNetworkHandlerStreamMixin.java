package net.nerdorg.minehop.mixin;

import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundPingPacket;
import net.minecraft.network.protocol.game.ServerboundPongPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.nerdorg.minehop.anticheat.stream.MovementValidator;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/**
 * Velocity transactions: whenever the server changes the player's own velocity (knockback,
 * explosions, reset-zone carry, ...) a ping follows it. The client answers in packet order once it
 * has applied that velocity, so the movement check knows exactly which client tick changed —
 * independent of latency.
 *
 * <p>1.20.1 backport: there is no {@code ServerCommonNetworkHandler} yet (split off in 1.20.2), so
 * this hooks {@link ServerGamePacketListenerImpl#send(Packet, PacketSendListener)} (every play packet
 * goes through it) and uses the play-phase {@code PlayPingS2CPacket}/{@code PlayPongC2SPacket}, which
 * a 1.20.1 client answers on its main thread in packet order, exactly like the later common ping.
 * The class name is kept so the mixin config matches the other branches.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerCommonNetworkHandlerStreamMixin {
    @Shadow
    @Final
    private MinecraftServer server;

    @Shadow
    public ServerPlayer player;

    @Shadow
    public abstract void send(Packet<?> packet);

    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketSendListener;)V", at = @At("TAIL"))
    private void minehop$afterSend(Packet<?> packet, @Nullable PacketSendListener callbacks, CallbackInfo ci) {
        int pingId = MovementValidator.onPacketSent(this.player, packet);
        if (pingId != -1) {
            this.send(new ClientboundPingPacket(pingId));
        }
    }

    // 1.20.1: ServerGamePacketListenerImpl.handlePong has no ensureRunningOnSameThread (it is empty), so this always
    // runs on the netty thread, in packet order with the move packets' netty-thread pass.
    @Inject(method = "handlePong", at = @At("HEAD"))
    private void minehop$onPong(ServerboundPongPacket packet, CallbackInfo ci) {
        int id = packet.getId();
        if (MovementValidator.isHeartbeatPing(id)) {
            // Timer heartbeat: timestamp it right here, in order with the tick packets around it.
            MovementValidator.onHeartbeatPongNetwork(this.player, id);
            return;
        }
        if (!MovementValidator.isTransactionPing(id)) {
            return;
        }
        ServerPlayer player = this.player;
        if (player == null) {
            return;
        }
        UUID uuid = player.getUUID();
        // Pongs arrive on the netty thread; queue behind the move packets that arrived before it.
        this.server.execute(() -> MovementValidator.onTransactionPong(uuid, id));
    }
}
