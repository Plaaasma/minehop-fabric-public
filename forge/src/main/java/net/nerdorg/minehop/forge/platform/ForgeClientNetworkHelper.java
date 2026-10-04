package net.nerdorg.minehop.forge.platform;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.nerdorg.minehop.platform.services.IClientNetworkHelper;

/**
 * PHASE 3 TODO: Forge implementation of {@link IClientNetworkHelper} (client only).
 */
public class ForgeClientNetworkHelper implements IClientNetworkHelper {

    @Override
    public <T extends CustomPacketPayload> void registerClientReceiver(CustomPacketPayload.Type<T> type, ClientPayloadHandler<T> handler) {
        // TODO(phase 3): putIfAbsent into the client handler map read by ForgeNetworkHelper's channel dispatcher.
        //  Context: client() = Minecraft.getInstance(), player() = Minecraft.getInstance().player.
        throw Todo.notImplemented("IClientNetworkHelper.registerClientReceiver");
    }

    @Override
    public void sendToServer(CustomPacketPayload payload) {
        // TODO(phase 3): channel.send(payload, net.minecraftforge.network.PacketDistributor.SERVER.noArg())
        throw Todo.notImplemented("IClientNetworkHelper.sendToServer");
    }

    @Override
    public void onConnectionInit(ConnectionListener listener) {
        // TODO(phase 3): fire from ClientPlayerNetworkEvent.LoggingIn at EventPriority.HIGHEST
        throw Todo.notImplemented("IClientNetworkHelper.onConnectionInit");
    }

    @Override
    public void onConnectionJoin(ConnectionListener listener) {
        // TODO(phase 3): MinecraftForge.EVENT_BUS ClientPlayerNetworkEvent.LoggingIn
        throw Todo.notImplemented("IClientNetworkHelper.onConnectionJoin");
    }

    @Override
    public void onConnectionDisconnect(ConnectionListener listener) {
        // TODO(phase 3): ClientPlayerNetworkEvent.LoggingOut
        throw Todo.notImplemented("IClientNetworkHelper.onConnectionDisconnect");
    }
}
