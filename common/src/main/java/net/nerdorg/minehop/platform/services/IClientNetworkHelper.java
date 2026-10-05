package net.nerdorg.minehop.platform.services;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client side of the play-phase payload networking, plus the client play-connection events.
 * Obtain through {@link net.nerdorg.minehop.platform.ClientServices#NETWORK}. The payload types themselves are declared
 * once, for both sides, through {@link INetworkHelper}; the wire format rules there apply here too.
 */
public interface IClientNetworkHelper {

    /**
     * Sets the client-side handler of a S2C payload type (one handler per type; Fabric ignores a second registration).
     * Minehop calls this again on every connection (from {@link #onConnectionInit}); loaders that need handlers up front
     * must dispatch to whatever handler is set when the packet arrives (no handler = packet ignored).
     *
     * <p>The handler runs on the client (render) thread, like Fabric's play payload handlers.</p>
     */
    <T extends CustomPacketPayload> void registerClientReceiver(CustomPacketPayload.Type<T> type, ClientPayloadHandler<T> handler);

    /**
     * Sends a C2S payload to the server of the current play connection.
     * Fabric {@code ClientPlayNetworking.send} (throws if not in game).
     */
    void sendToServer(CustomPacketPayload payload);

    /**
     * The client play connection was created (Fabric {@code ClientPlayConnectionEvents.INIT}, before join).
     */
    void onConnectionInit(ConnectionListener listener);

    /**
     * The client joined the world and can send packets (Fabric {@code ClientPlayConnectionEvents.JOIN};
     * NeoForge/Forge {@code ClientPlayerNetworkEvent.LoggingIn}).
     */
    void onConnectionJoin(ConnectionListener listener);

    /**
     * The client play connection closed (Fabric {@code ClientPlayConnectionEvents.DISCONNECT};
     * NeoForge/Forge {@code ClientPlayerNetworkEvent.LoggingOut}).
     */
    void onConnectionDisconnect(ConnectionListener listener);

    @FunctionalInterface
    interface ClientPayloadHandler<T extends CustomPacketPayload> {
        void receive(T payload, ClientPayloadContext context);
    }

    /**
     * What a client payload handler knows about the connection.
     */
    interface ClientPayloadContext {
        Minecraft client();

        LocalPlayer player();
    }

    @FunctionalInterface
    interface ConnectionListener {
        void onConnection(ClientPacketListener handler, Minecraft client);
    }
}
