package net.nerdorg.minehop.forge.platform;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;
import net.nerdorg.minehop.networking.codec.CustomPacketPayload;
import net.nerdorg.minehop.platform.services.IClientNetworkHelper;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Forge implementation of {@link IClientNetworkHelper} (physical client only). Payloads arrive through the per-payload
 * channels built by {@link ForgeNetworkHelper}, which hands them to {@link #dispatch} on the client thread.
 */
public class ForgeClientNetworkHelper implements IClientNetworkHelper {
    private static final Map<ResourceLocation, ClientPayloadHandler<?>> HANDLERS = new ConcurrentHashMap<>();

    public ForgeClientNetworkHelper() {
        ForgeNetworkHelper.setClientDispatcher(ForgeClientNetworkHelper::dispatch);
    }

    @SuppressWarnings("unchecked")
    private static <T extends CustomPacketPayload> void dispatch(T payload, Connection connection) {
        ClientPayloadHandler<T> handler = (ClientPayloadHandler<T>) HANDLERS.get(payload.type().id());
        // Fabric skips the handler once the connection closed (it was scheduled from the netty thread).
        if (handler != null && connection.isConnected()) {
            Minecraft client = Minecraft.getInstance();
            handler.receive(payload, new Context(client, client.player));
        }
    }

    @Override
    public <T extends CustomPacketPayload> void registerClientReceiver(CustomPacketPayload.Type<T> type, ClientPayloadHandler<T> handler) {
        // Fabric's registerGlobalReceiver keeps the first handler of a type (Minehop re-registers on every connection).
        HANDLERS.putIfAbsent(type.id(), handler);
    }

    @Override
    public void sendToServer(CustomPacketPayload payload) {
        // Fabric: ClientPlayNetworking.send(id, buf) == getConnection().send(new ServerboundCustomPayloadPacket(id, buf)).
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null) {
            throw new IllegalStateException("Cannot send packets when not in game!");
        }
        connection.send(new ServerboundCustomPayloadPacket(payload.type().id(), ForgeNetworkHelper.encode(payload, false)));
    }

    @Override
    public void onConnectionInit(ConnectionListener listener) {
        // Fabric's INIT (play connection created) fires before JOIN; Forge has one login event, so INIT runs first.
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, ClientPlayerNetworkEvent.LoggingIn.class,
                event -> fire(listener));
    }

    @Override
    public void onConnectionJoin(ConnectionListener listener) {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, ClientPlayerNetworkEvent.LoggingIn.class,
                event -> fire(listener));
    }

    @Override
    public void onConnectionDisconnect(ConnectionListener listener) {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, ClientPlayerNetworkEvent.LoggingOut.class,
                event -> fire(listener));
    }

    private static void fire(ConnectionListener listener) {
        Minecraft client = Minecraft.getInstance();
        listener.onConnection(client.getConnection(), client);
    }

    private record Context(Minecraft client, LocalPlayer player) implements ClientPayloadContext {
    }
}
