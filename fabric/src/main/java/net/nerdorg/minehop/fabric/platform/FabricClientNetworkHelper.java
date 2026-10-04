package net.nerdorg.minehop.fabric.platform;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.nerdorg.minehop.platform.services.IClientNetworkHelper;

@Environment(EnvType.CLIENT)
public class FabricClientNetworkHelper implements IClientNetworkHelper {

    @Override
    public <T extends CustomPacketPayload> void registerClientReceiver(CustomPacketPayload.Type<T> type, ClientPayloadHandler<T> handler) {
        ClientPlayNetworking.registerGlobalReceiver(type, (payload, context) -> handler.receive(payload, new Context(context)));
    }

    @Override
    public void sendToServer(CustomPacketPayload payload) {
        ClientPlayNetworking.send(payload);
    }

    @Override
    public void onConnectionInit(ConnectionListener listener) {
        ClientPlayConnectionEvents.INIT.register(listener::onConnection);
    }

    @Override
    public void onConnectionJoin(ConnectionListener listener) {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> listener.onConnection(handler, client));
    }

    @Override
    public void onConnectionDisconnect(ConnectionListener listener) {
        ClientPlayConnectionEvents.DISCONNECT.register(listener::onConnection);
    }

    private record Context(ClientPlayNetworking.Context fabric) implements ClientPayloadContext {
        @Override
        public Minecraft client() {
            return this.fabric.client();
        }

        @Override
        public LocalPlayer player() {
            return this.fabric.player();
        }
    }
}
