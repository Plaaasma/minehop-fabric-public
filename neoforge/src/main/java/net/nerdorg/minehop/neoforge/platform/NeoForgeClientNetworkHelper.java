package net.nerdorg.minehop.neoforge.platform;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.nerdorg.minehop.platform.services.IClientNetworkHelper;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * NeoForge implementation of {@link IClientNetworkHelper} (physical client only). The payload types and their
 * dispatchers are registered by {@link NeoForgeNetworkHelper}.
 */
public class NeoForgeClientNetworkHelper implements IClientNetworkHelper {
    private static final List<ConnectionListener> INIT_LISTENERS = new CopyOnWriteArrayList<>();
    private static final List<ConnectionListener> JOIN_LISTENERS = new CopyOnWriteArrayList<>();
    private static final List<ConnectionListener> DISCONNECT_LISTENERS = new CopyOnWriteArrayList<>();
    private static boolean eventListenersAdded;

    @Override
    public <T extends CustomPacketPayload> void registerClientReceiver(CustomPacketPayload.Type<T> type, ClientPayloadHandler<T> handler) {
        // First registration wins (Fabric's registerGlobalReceiver ignores later ones). Runs on the client thread.
        NeoForgeNetworkHelper.CLIENT_RECEIVERS.putIfAbsent(type.id(), (payload, context) -> {
            @SuppressWarnings("unchecked")
            T typed = (T) payload;
            handler.receive(typed, ClientContext.INSTANCE);
        });
    }

    @Override
    public void sendToServer(CustomPacketPayload payload) {
        ClientPacketListener listener = Minecraft.getInstance().getConnection();
        if (listener == null) {
            // Same contract as Fabric's ClientPlayNetworking.send.
            throw new IllegalStateException("Cannot send packets when not in game!");
        }
        NeoForgeNetworkHelper.ensureChannel(listener, payload.type().id());
        listener.send(new ServerboundCustomPayloadPacket(payload));
    }

    @Override
    public void onConnectionInit(ConnectionListener listener) {
        addListener(INIT_LISTENERS, listener);
    }

    @Override
    public void onConnectionJoin(ConnectionListener listener) {
        addListener(JOIN_LISTENERS, listener);
    }

    @Override
    public void onConnectionDisconnect(ConnectionListener listener) {
        addListener(DISCONNECT_LISTENERS, listener);
    }

    private static synchronized void addListener(List<ConnectionListener> listeners, ConnectionListener listener) {
        if (!eventListenersAdded) {
            eventListenersAdded = true;
            NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingIn.class, NeoForgeClientNetworkHelper::onLoggingIn);
            NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, NeoForgeClientNetworkHelper::onLoggingOut);
        }
        listeners.add(listener);
    }

    /**
     * Fabric fires INIT when the play listener is created and JOIN when the login packet was handled. NeoForge has
     * {@code LoggingIn} (fired while handling the login packet) only; Minehop's INIT just (re)registers the client
     * receivers, which only have to be in place before the first play payload is handled - payloads are handled on
     * the client thread after the login packet, so running INIT right before JOIN is enough.
     */
    private static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        Minecraft client = Minecraft.getInstance();
        ClientPacketListener handler = event.getPlayer().connection;
        for (ConnectionListener listener : INIT_LISTENERS) {
            listener.onConnection(handler, client);
        }
        for (ConnectionListener listener : JOIN_LISTENERS) {
            listener.onConnection(handler, client);
        }
    }

    private static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        Minecraft client = Minecraft.getInstance();
        ClientPacketListener handler = event.getPlayer() != null ? event.getPlayer().connection : client.getConnection();
        for (ConnectionListener listener : DISCONNECT_LISTENERS) {
            listener.onConnection(handler, client);
        }
    }

    private enum ClientContext implements ClientPayloadContext {
        INSTANCE;

        @Override
        public Minecraft client() {
            return Minecraft.getInstance();
        }

        @Override
        public LocalPlayer player() {
            return Minecraft.getInstance().player;
        }
    }
}
