package net.nerdorg.minehop.neoforge.platform;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.nerdorg.minehop.platform.services.IClientNetworkHelper;

/**
 * PHASE 3 TODO: NeoForge implementation of {@link IClientNetworkHelper} (client only).
 */
public class NeoForgeClientNetworkHelper implements IClientNetworkHelper {

    @Override
    public <T extends CustomPacketPayload> void registerClientReceiver(CustomPacketPayload.Type<T> type, ClientPayloadHandler<T> handler) {
        // TODO(phase 3): putIfAbsent into the client handler map read by NeoForgeNetworkHelper's dispatcher (first wins).
        //  Context: client() = Minecraft.getInstance(), player() = (LocalPlayer) ctx.player().
        throw Todo.notImplemented("IClientNetworkHelper.registerClientReceiver");
    }

    @Override
    public void sendToServer(CustomPacketPayload payload) {
        // TODO(phase 3): net.neoforged.neoforge.network.PacketDistributor.sendToServer(payload). On a Fabric server this
        //  only works for channels the server advertised (minecraft:register, which Fabric sends on join) - verify.
        throw Todo.notImplemented("IClientNetworkHelper.sendToServer");
    }

    @Override
    public void onConnectionInit(ConnectionListener listener) {
        // TODO(phase 3): no exact equivalent; fire from ClientPlayerNetworkEvent.LoggingIn at EventPriority.HIGHEST
        //  (before the join listeners). Minehop only registers its client receivers here.
        throw Todo.notImplemented("IClientNetworkHelper.onConnectionInit");
    }

    @Override
    public void onConnectionJoin(ConnectionListener listener) {
        // TODO(phase 3): NeoForge.EVENT_BUS ClientPlayerNetworkEvent.LoggingIn -> (Minecraft.getInstance().getConnection(), Minecraft.getInstance())
        throw Todo.notImplemented("IClientNetworkHelper.onConnectionJoin");
    }

    @Override
    public void onConnectionDisconnect(ConnectionListener listener) {
        // TODO(phase 3): ClientPlayerNetworkEvent.LoggingOut -> (connection's ClientPacketListener or null, Minecraft.getInstance())
        throw Todo.notImplemented("IClientNetworkHelper.onConnectionDisconnect");
    }
}
