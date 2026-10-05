package net.nerdorg.minehop.fabric.platform;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.nerdorg.minehop.networking.codec.CustomPacketPayload;
import net.nerdorg.minehop.networking.codec.StreamCodec;
import net.nerdorg.minehop.platform.services.INetworkHelper;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fabric networking (Fabric API 0.92.x for 1.20.1, the pre-1.20.5 channel API). Every payload is a vanilla custom
 * payload packet {@code (type.id(), bytes written by its codec)}: the reference wire format every other loader must
 * match. Receiving works exactly like Fabric's {@code FabricPacket}/{@code PacketType} receivers (which the
 * single-module 1.20.1 build used): the bytes are decoded on the netty thread, the handler runs on the server thread
 * (directly if already there, otherwise scheduled and skipped once the connection closed).
 */
public class FabricNetworkHelper implements INetworkHelper {
    private static final Map<ResourceLocation, StreamCodec<? super FriendlyByteBuf, ?>> S2C = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, StreamCodec<? super FriendlyByteBuf, ?>> C2S = new ConcurrentHashMap<>();

    @Override
    public <T extends CustomPacketPayload> void registerPayloadS2C(CustomPacketPayload.Type<T> type, StreamCodec<? super FriendlyByteBuf, T> codec) {
        register(S2C, type, codec);
    }

    @Override
    public <T extends CustomPacketPayload> void registerPayloadC2S(CustomPacketPayload.Type<T> type, StreamCodec<? super FriendlyByteBuf, T> codec) {
        register(C2S, type, codec);
    }

    private static void register(Map<ResourceLocation, StreamCodec<? super FriendlyByteBuf, ?>> map, CustomPacketPayload.Type<?> type,
                                 StreamCodec<? super FriendlyByteBuf, ?> codec) {
        if (map.putIfAbsent(type.id(), codec) != null) {
            throw new IllegalArgumentException("Packet type " + type.id() + " is already registered!");
        }
    }

    @Override
    public <T extends CustomPacketPayload> void registerServerReceiver(CustomPacketPayload.Type<T> type, ServerPayloadHandler<T> handler) {
        // Only payloads declared C2S are ever decoded (the 1.20.5+ PayloadTypeRegistry rule).
        StreamCodec<? super FriendlyByteBuf, T> codec = codec(false, type.id());
        ServerPlayNetworking.registerGlobalReceiver(type.id(), (server, player, networkHandler, buf, responseSender) -> {
            T payload = codec.decode(buf);
            Context context = new Context(player, server);
            if (server.isSameThread()) {
                handler.receive(payload, context);
            } else {
                server.execute(() -> {
                    if (networkHandler.isAcceptingMessages()) {
                        handler.receive(payload, context);
                    }
                });
            }
        });
    }

    @Override
    public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        ResourceLocation id = payload.type().id();
        StreamCodec<? super FriendlyByteBuf, CustomPacketPayload> codec = codec(true, id);
        FriendlyByteBuf buf = PacketByteBufs.create();
        codec.encode(buf, payload);
        ServerPlayNetworking.send(player, id, buf);
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

    @SuppressWarnings("unchecked")
    static <T extends CustomPacketPayload> StreamCodec<? super FriendlyByteBuf, T> codec(boolean clientbound, ResourceLocation id) {
        StreamCodec<? super FriendlyByteBuf, T> codec = (StreamCodec<? super FriendlyByteBuf, T>) (clientbound ? S2C : C2S).get(id);
        if (codec == null) {
            throw new IllegalArgumentException("Payload " + id + " is not registered for " + (clientbound ? "clientbound" : "serverbound") + " play");
        }
        return codec;
    }

    private record Context(ServerPlayer player, MinecraftServer server) implements ServerPayloadContext {
    }
}
