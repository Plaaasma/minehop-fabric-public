package net.nerdorg.minehop.mixin;

import net.minecraft.network.PacketCallbacks;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.common.CommonPongC2SPacket;
import net.minecraft.network.packet.s2c.common.CommonPingS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerCommonNetworkHandler;
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
 */
@Mixin(ServerCommonNetworkHandler.class)
public abstract class ServerCommonNetworkHandlerStreamMixin {
    @Shadow
    @Final
    protected MinecraftServer server;

    @Shadow
    public abstract void sendPacket(Packet<?> packet);

    @Inject(method = "send", at = @At("TAIL"))
    private void minehop$afterSend(Packet<?> packet, @Nullable PacketCallbacks callbacks, CallbackInfo ci) {
        if (!((Object) this instanceof ServerPlayNetworkHandler handler)) {
            return;
        }
        int pingId = MovementValidator.onPacketSent(handler.player, packet);
        if (pingId != -1) {
            this.sendPacket(new CommonPingS2CPacket(pingId));
        }
    }

    @Inject(method = "onPong", at = @At("HEAD"))
    private void minehop$onPong(CommonPongC2SPacket packet, CallbackInfo ci) {
        int id = packet.getParameter();
        if (!MovementValidator.isTransactionPing(id)) {
            return;
        }
        if (!((Object) this instanceof ServerPlayNetworkHandler handler)) {
            return;
        }
        ServerPlayerEntity player = handler.player;
        if (player == null) {
            return;
        }
        UUID uuid = player.getUuid();
        // Pongs arrive on the netty thread; queue behind the move packets that arrived before it.
        this.server.execute(() -> MovementValidator.onTransactionPong(uuid, id));
    }
}
