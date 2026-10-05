package net.nerdorg.minehop.forge.mixin;

import io.netty.channel.Channel;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Works around a Forge 1.20.1 race that stalls connections at "Logging in" (client joins time out after 30 s, on
 * Forge, Fabric and vanilla servers alike, roughly every third local join in testing).
 *
 * <p>Forge 47 patches {@code Connection#setProtocol} to re-enable auto-read in a task queued on the event loop instead of
 * immediately. {@code Connection#sendPacket} disables auto-read synchronously on the calling thread when a packet
 * switches the protocol (the client's login hello), which also queues netty's "clear pending read" task. When the
 * deferred re-enable from {@code channelActive} runs between those two, the flag ends up {@code true} while the queued
 * clear removes the channel's read interest; the re-enable queued by the hello's own protocol switch is then a no-op
 * (the flag is already {@code true}) and nothing is ever read again.</p>
 *
 * <p>After every protocol switch this queues one more task, behind Forge's re-enable, that issues a read if auto-read
 * is on. With auto-read on, netty reads continuously anyway, so the extra read request is harmless.</p>
 */
@Mixin(Connection.class)
public abstract class ConnectionMixin {
    @Shadow
    private Channel channel;

    @Inject(method = "doSendPacket", at = @At("RETURN"))
    private void minehop$resumeReadingAfterProtocolSwitch(Packet<?> packet, @Nullable PacketSendListener listener,
                                                          ConnectionProtocol newProtocol, ConnectionProtocol currentProtocol,
                                                          CallbackInfo ci) {
        if (newProtocol == currentProtocol) {
            return;
        }
        Channel channel = this.channel;
        if (channel == null) {
            return;
        }
        channel.eventLoop().execute(() -> {
            if (channel.isOpen() && channel.config().isAutoRead()) {
                channel.read();
            }
        });
    }
}
