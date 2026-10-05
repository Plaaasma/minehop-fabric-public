package net.nerdorg.minehop.forge.mixin.client;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundStartConfigurationPacket;
import net.minecraftforge.network.NetworkContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lets a Forge client switch servers behind a proxy (Velocity) whose backends are Fabric servers.
 *
 * <p>A proxy switches servers by sending the client back into the configuration phase on the same connection
 * ({@code ClientboundStartConfigurationPacket}). The new Fabric backend then starts its configuration as on a fresh
 * join: it sends {@code minecraft:register} and decides from the client's answer whether the client can take part in
 * Fabric's registry sync ({@code fabric:registry/sync/direct}, see {@code FabricRegistrySyncClient}); without that
 * answer it disconnects the client ("This server requires Fabric Loader and Fabric API installed on your client!").
 * Forge answers a server's {@code minecraft:register} with the channels it has not announced on this connection yet
 * ({@code ChannelListManager#addChannels}), i.e. nothing after the first server.</p>
 *
 * <p>So, when a new configuration phase starts (main thread, before the client acknowledges it, so before the new
 * server's packets can arrive), forget which channels were announced: Forge then announces all of them again to the new
 * server, exactly as on the first join (NeoForge and Fabric clients re-announce in every configuration phase too).
 * Only reconfiguration uses this packet; the first join enters the configuration phase from the login phase.</p>
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
    @Inject(method = "handleConfigurationStart", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/util/thread/BlockableEventLoop;)V",
            shift = At.Shift.AFTER))
    private void minehop$reannounceChannels(ClientboundStartConfigurationPacket packet, CallbackInfo ci) {
        ((NetworkContextAccessor) NetworkContext.get(((ClientPacketListener) (Object) this).getConnection())).minehop$getSentChannels().clear();
    }
}
