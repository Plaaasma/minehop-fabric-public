package net.nerdorg.minehop.fabric.platform;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.nerdorg.minehop.networking.codec.CustomPacketPayload;
import net.nerdorg.minehop.networking.codec.StreamCodec;
import net.nerdorg.minehop.platform.services.IClientNetworkHelper;

/**
 * Fabric client networking (Fabric API 0.92.x for 1.20.1). Receivers behave like Fabric's {@code FabricPacket}
 * receivers: decoded on the netty thread, handled on the client thread (skipped once the connection closed).
 */
@Environment(EnvType.CLIENT)
public class FabricClientNetworkHelper implements IClientNetworkHelper {

    @Override
    public <T extends CustomPacketPayload> void registerClientReceiver(CustomPacketPayload.Type<T> type, ClientPayloadHandler<T> handler) {
        StreamCodec<? super FriendlyByteBuf, T> codec = FabricNetworkHelper.codec(true, type.id());
        ClientPlayNetworking.registerGlobalReceiver(type.id(), (client, networkHandler, buf, responseSender) -> {
            T payload = codec.decode(buf);
            if (client.isSameThread()) {
                handler.receive(payload, new Context(client, client.player));
            } else {
                client.execute(() -> {
                    if (networkHandler.getConnection().isConnected()) {
                        handler.receive(payload, new Context(client, client.player));
                    }
                });
            }
        });
    }

    @Override
    public void sendToServer(CustomPacketPayload payload) {
        ResourceLocation id = payload.type().id();
        StreamCodec<? super FriendlyByteBuf, CustomPacketPayload> codec = FabricNetworkHelper.codec(false, id);
        FriendlyByteBuf buf = PacketByteBufs.create();
        codec.encode(buf, payload);
        ClientPlayNetworking.send(id, buf);
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

    private record Context(Minecraft client, LocalPlayer player) implements ClientPayloadContext {
    }
}
