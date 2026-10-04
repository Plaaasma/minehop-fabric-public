package net.nerdorg.minehop.neoforge.platform;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.nerdorg.minehop.platform.services.INetworkHelper;

/**
 * PHASE 3 TODO: NeoForge implementation of {@link INetworkHelper}. Must stay wire-compatible with the Fabric server
 * (see docs/MULTILOADER.md, "Networking").
 */
public class NeoForgeNetworkHelper implements INetworkHelper {

    @Override
    public <T extends CustomPacketPayload> void registerPayloadS2C(CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
        // TODO(phase 3): record (type, codec, S2C). In the mod-bus RegisterPayloadHandlersEvent:
        //  PayloadRegistrar r = event.registrar("1").optional();   // optional: the Fabric server has no NeoForge handshake
        //  types recorded in both directions -> r.playBidirectional(type, codec, dispatcher), else playToClient/playToServer.
        //  The dispatcher looks up the handler set later via registerServerReceiver / IClientNetworkHelper#registerClientReceiver
        //  (keyed by type) and ignores the packet if none is set. Default HandlerThread.MAIN = Fabric's threading.
        throw Todo.notImplemented("INetworkHelper.registerPayloadS2C");
    }

    @Override
    public <T extends CustomPacketPayload> void registerPayloadC2S(CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
        // TODO(phase 3): see registerPayloadS2C.
        throw Todo.notImplemented("INetworkHelper.registerPayloadC2S");
    }

    @Override
    public <T extends CustomPacketPayload> void registerServerReceiver(CustomPacketPayload.Type<T> type, ServerPayloadHandler<T> handler) {
        // TODO(phase 3): putIfAbsent into the server handler map read by the dispatcher (first registration wins, like
        //  Fabric). Context: player() = (ServerPlayer) ctx.player(), server() = player.server.
        throw Todo.notImplemented("INetworkHelper.registerServerReceiver");
    }

    @Override
    public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        // TODO(phase 3): net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, payload)
        //  (or player.connection.send(payload) to skip NeoForge's "channel negotiated?" check for vanilla-ish clients)
        throw Todo.notImplemented("INetworkHelper.sendToPlayer");
    }

    @Override
    public void onPlayConnectionInit(PlayConnectionListener listener) {
        // TODO(phase 3): no NeoForge equivalent of Fabric's INIT. Minehop only uses it to register its receivers, so firing
        //  it from PlayerEvent.PlayerLoggedInEvent at EventPriority.HIGHEST (before the JOIN listeners) is enough:
        //  listener.onPlayConnection(((ServerPlayer) event.getEntity()).connection, player.server).
        throw Todo.notImplemented("INetworkHelper.onPlayConnectionInit");
    }

    @Override
    public void onPlayConnectionJoin(PlayConnectionListener listener) {
        // TODO(phase 3): NeoForge.EVENT_BUS PlayerEvent.PlayerLoggedInEvent -> (serverPlayer.connection, serverPlayer.server)
        throw Todo.notImplemented("INetworkHelper.onPlayConnectionJoin");
    }

    @Override
    public void onPlayConnectionDisconnect(PlayConnectionListener listener) {
        // TODO(phase 3): NeoForge.EVENT_BUS PlayerEvent.PlayerLoggedOutEvent -> (serverPlayer.connection, serverPlayer.server)
        throw Todo.notImplemented("INetworkHelper.onPlayConnectionDisconnect");
    }
}
