package net.nerdorg.minehop.mixin;

import net.minecraft.network.PacketCallbacks;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayPongC2SPacket;
import net.minecraft.network.packet.s2c.play.PlayPingS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
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
 * this hooks {@link ServerPlayNetworkHandler#sendPacket(Packet, PacketCallbacks)} (every play packet
 * goes through it) and uses the play-phase {@code PlayPingS2CPacket}/{@code PlayPongC2SPacket}, which
 * a 1.20.1 client answers on its main thread in packet order, exactly like the later common ping.
 * The class name is kept so the mixin config matches the other branches.
 */
@Mixin(ServerPlayNetworkHandler.class)
public abstract class ServerCommonNetworkHandlerStreamMixin {
    @Shadow
    @Final
    private MinecraftServer server;

    @Shadow
    public ServerPlayerEntity player;

    @Shadow
    public abstract void sendPacket(Packet<?> packet);

    @Inject(method = "sendPacket(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/PacketCallbacks;)V", at = @At("TAIL"))
    private void minehop$afterSend(Packet<?> packet, @Nullable PacketCallbacks callbacks, CallbackInfo ci) {
        int pingId = MovementValidator.onPacketSent(this.player, packet);
        if (pingId != -1) {
            this.sendPacket(new PlayPingS2CPacket(pingId));
        }
    }

    @Inject(method = "onPong", at = @At("HEAD"))
    private void minehop$onPong(PlayPongC2SPacket packet, CallbackInfo ci) {
        int id = packet.getParameter();
        if (!MovementValidator.isTransactionPing(id)) {
            return;
        }
        ServerPlayerEntity player = this.player;
        if (player == null) {
            return;
        }
        UUID uuid = player.getUuid();
        // Pongs arrive on the netty thread; queue behind the move packets that arrived before it.
        this.server.execute(() -> MovementValidator.onTransactionPong(uuid, id));
    }
}
