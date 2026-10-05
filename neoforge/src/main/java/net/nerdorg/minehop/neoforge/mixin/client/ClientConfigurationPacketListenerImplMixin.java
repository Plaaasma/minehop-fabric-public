package net.nerdorg.minehop.neoforge.mixin.client;

import net.minecraft.client.multiplayer.ClientConfigurationPacketListenerImpl;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.neoforged.neoforge.client.network.registration.ClientNetworkRegistry;
import net.neoforged.neoforge.network.payload.MinecraftRegisterPayload;
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
 * <p>A Fabric server starts the configuration phase with its own {@code minecraft:register} and a ping, decides from
 * the client's answer which configuration tasks to run, and sends Fabric API's registry sync payload
 * ({@code fabric:registry/sync}, see {@link FabricRegistrySync}) before the brand. Since 26.1 NeoForge answers that first
 * {@code minecraft:register} itself (ClientNetworkRegistry#sendInitialListeningChannels, so the server sees our optional
 * configuration channels), but it still sets up the "other connection" payload handling only when the brand (or the
 * enabled features) arrive; a modded payload before that is rejected ("Incompatible client! ... (No Payload Setup)").</p>
 *
 * <p>So when a server announces Fabric's registry sync ({@code fabric:registry/sync/complete} in its first
 * {@code minecraft:register}), run NeoForge's "other connection" initialisation right after NeoForge answered that
 * packet, as the 1.21.4 build did at the end of the packet. NeoForge would run the same initialisation on the brand;
 * {@code initializedConnection} makes it skip that.</p>
 *
 * <p>Only for Fabric servers: a NeoForge server (dedicated or integrated) also sends {@code minecraft:register}
 * before its NeoForge query, and must not be treated as an "other" connection (that would, among other things, load
 * the default server configs over the integrated server's own).</p>
 */
@Mixin(ClientConfigurationPacketListenerImpl.class)
public abstract class ClientConfigurationPacketListenerImplMixin {
    @Shadow(remap = false)
    private boolean initializedConnection;

    @Inject(method = "handleCustomPayload(Lnet/minecraft/network/protocol/common/ClientboundCustomPayloadPacket;)V",
            at = @At(value = "INVOKE", target = "Lnet/neoforged/neoforge/client/network/registration/ClientNetworkRegistry;sendInitialListeningChannels(Lnet/minecraft/network/protocol/configuration/ClientConfigurationPacketListener;)V",
                    shift = At.Shift.AFTER))
    private void minehop$initializeFabricServerConnection(ClientboundCustomPayloadPacket packet, CallbackInfo ci) {
        ClientConfigurationPacketListenerImpl self = (ClientConfigurationPacketListenerImpl) (Object) this;
        if (!this.initializedConnection
                && packet.payload() instanceof MinecraftRegisterPayload register
                && register.newChannels().contains(FabricRegistrySync.COMPLETE_ID)
                && self.getConnectionType().isOther()
                && !self.getConnection().isMemoryConnection()) {
            Minehop.LOGGER.info("Fabric server detected (registry sync channel announced): setting up this connection before the server's configuration starts");
            this.initializedConnection = true;
            ClientNetworkRegistry.initializeOtherConnection(self);
        }
    }
}
