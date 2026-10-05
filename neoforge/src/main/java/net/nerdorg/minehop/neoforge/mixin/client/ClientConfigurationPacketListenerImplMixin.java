package net.nerdorg.minehop.neoforge.mixin.client;

import net.minecraft.client.multiplayer.ClientConfigurationPacketListenerImpl;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.neoforged.neoforge.network.payload.MinecraftRegisterPayload;
import net.neoforged.neoforge.client.network.registration.ClientNetworkRegistry;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.neoforge.network.FabricRegistrySync;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lets a NeoForge client join a Fabric server.
 *
 * <p>On a non-NeoForge ("other") connection, NeoForge announces the client's channels ({@code minecraft:register})
 * only when it receives the server's brand. A Fabric server starts the configuration phase differently: it first sends
 * its own {@code minecraft:register} and a ping, and decides from the client's answer (register packet before the
 * pong, or not) which configuration tasks to run - before it sends the brand. Fabric API's registry sync task then
 * requires the client to have announced {@code fabric:registry/sync}, and disconnects it otherwise ("This
 * server requires Fabric Loader and Fabric API installed on your client!").</p>
 *
 * <p>So when a server announces Fabric's registry sync ({@code fabric:registry/sync/complete} in its first
 * {@code minecraft:register}) before NeoForge initialised the connection, run the same "other connection"
 * initialisation NeoForge would run on the brand right away: it announces the client's optional configuration channels
 * (including the Fabric registry sync payload, see {@link FabricRegistrySync}) in time. NeoForge runs this
 * initialisation again on the brand and on the enabled-features packet (it is idempotent).</p>
 *
 * <p>Only for Fabric servers: a NeoForge server (dedicated or integrated) also sends {@code minecraft:register}
 * before its NeoForge query, and must not be treated as an "other" connection (that would, among other things, load
 * the default server configs over the integrated server's own).</p>
 */
@Mixin(ClientConfigurationPacketListenerImpl.class)
public abstract class ClientConfigurationPacketListenerImplMixin {
    @Shadow(remap = false)
    private boolean initializedConnection;

    @Inject(method = "handleCustomPayload(Lnet/minecraft/network/protocol/common/ClientboundCustomPayloadPacket;)V", at = @At("TAIL"))
    private void minehop$announceChannelsToFabricServer(ClientboundCustomPayloadPacket packet, CallbackInfo ci) {
        ClientConfigurationPacketListenerImpl self = (ClientConfigurationPacketListenerImpl) (Object) this;
        if (!this.initializedConnection
                && packet.payload() instanceof MinecraftRegisterPayload register
                && register.newChannels().contains(FabricRegistrySync.COMPLETE_ID)
                && self.getConnectionType().isOther()
                && !self.getConnection().isMemoryConnection()) {
            Minehop.LOGGER.info("Fabric server detected (registry sync channel announced): announcing this client's channels before the server's configuration starts");
            this.initializedConnection = true;
            ClientNetworkRegistry.initializeOtherConnection(self);
        }
    }
}
