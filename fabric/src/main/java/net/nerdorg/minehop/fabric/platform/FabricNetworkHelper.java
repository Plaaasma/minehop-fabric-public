package net.nerdorg.minehop.fabric.platform;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.nerdorg.minehop.platform.services.INetworkHelper;

/**
 * Fabric networking: payloads are registered with {@link PayloadTypeRegistry} (vanilla custom payload packets, the
 * reference wire format every other loader must match).
 */
public class FabricNetworkHelper implements INetworkHelper {

    @Override
    public <T extends CustomPacketPayload> void registerPayloadS2C(CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
        PayloadTypeRegistry.clientboundPlay().register(type, codec);
    }

    @Override
    public <T extends CustomPacketPayload> void registerPayloadC2S(CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
        PayloadTypeRegistry.serverboundPlay().register(type, codec);
    }

    @Override
    public <T extends CustomPacketPayload> void registerServerReceiver(CustomPacketPayload.Type<T> type, ServerPayloadHandler<T> handler) {
        ServerPlayNetworking.registerGlobalReceiver(type, (payload, context) -> handler.receive(payload, new Context(context)));
    }

    @Override
    public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        ServerPlayNetworking.send(player, payload);
    }

    @Override
    public void onPlayConnectionInit(PlayConnectionListener listener) {
        ServerPlayConnectionEvents.INIT.register(listener::onPlayConnection);
    }

    @Override
    public void onPlayConnectionJoin(PlayConnectionListener listener) {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> listener.onPlayConnection(handler, server));
    }

    @Override
    public void onPlayConnectionDisconnect(PlayConnectionListener listener) {
        ServerPlayConnectionEvents.DISCONNECT.register(listener::onPlayConnection);
    }

    private record Context(ServerPlayNetworking.Context fabric) implements ServerPayloadContext {
        @Override
        public ServerPlayer player() {
            return this.fabric.player();
        }

        @Override
        public MinecraftServer server() {
            return this.fabric.server();
        }
    }
}
