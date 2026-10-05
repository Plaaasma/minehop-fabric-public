package net.nerdorg.minehop.mixin;

import io.netty.channel.ChannelFutureListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundPingPacket;
import net.minecraft.network.protocol.common.ServerboundPongPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
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
 */
@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class ServerCommonNetworkHandlerStreamMixin {
    @Shadow
    @Final
    protected MinecraftServer server;

    @Shadow
    public abstract void send(Packet<?> packet);

    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;)V", at = @At("TAIL"))
    private void minehop$afterSend(Packet<?> packet, @Nullable ChannelFutureListener listener, CallbackInfo ci) {
        if (!((Object) this instanceof ServerGamePacketListenerImpl handler)) {
            return;
        }
        int pingId = MovementValidator.onPacketSent(handler.player, packet);
        if (pingId != -1) {
            this.send(new ClientboundPingPacket(pingId));
        }
    }

    @Inject(method = "handlePong", at = @At("HEAD"))
    private void minehop$onPong(ServerboundPongPacket packet, CallbackInfo ci) {
        int id = packet.getId();
        if (MovementValidator.isHeartbeatPing(id)) {
            // Timer heartbeat: timestamp it right here, in order with the tick packets around it.
            if ((Object) this instanceof ServerGamePacketListenerImpl handler) {
                MovementValidator.onHeartbeatPongNetwork(handler.player, id);
            }
            return;
        }
        if (!MovementValidator.isTransactionPing(id)) {
            return;
        }
        if (!((Object) this instanceof ServerGamePacketListenerImpl handler)) {
            return;
        }
        ServerPlayer player = handler.player;
        if (player == null) {
            return;
        }
        UUID uuid = player.getUUID();
        // Pongs arrive on the netty thread; queue behind the move packets that arrived before it.
        this.server.execute(() -> MovementValidator.onTransactionPong(uuid, id));
    }
}
