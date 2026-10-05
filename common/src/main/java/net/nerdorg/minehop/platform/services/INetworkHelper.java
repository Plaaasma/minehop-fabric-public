package net.nerdorg.minehop.platform.services;

import net.minecraft.network.FriendlyByteBuf;
import net.nerdorg.minehop.networking.codec.CustomPacketPayload;
import net.nerdorg.minehop.networking.codec.StreamCodec;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

/**
 * Play-phase custom payload networking (server side + payload type registration) and the server play-connection
 * events.
 *
 * <p><b>Wire compatibility (all loaders must interoperate with the Fabric production server):</b> every payload is a
 * plain vanilla custom payload packet: the payload id ({@code minehop:<name>}, exactly {@code type.id()}) followed by
 * the bytes written by its codec, with no loader framing, no discriminator byte and no version/handshake channel
 * requirement. Implementations must not change the id, the codec or add a prefix. See docs/MULTILOADER.md.</p>
 *
 * <p>Minecraft 1.20.1 has no typed custom payloads: {@link CustomPacketPayload} / {@link StreamCodec} are Minehop's own
 * backport shims, and the loaders send {@code ClientboundCustomPayloadPacket(type.id(), bytes)} themselves.</p>
 *
 * <p>Fabric: {@code ServerPlayNetworking} (raw channel receivers, pre-1.20.5 API) + {@code ServerPlayConnectionEvents}.
 * Forge: one optional {@code EventNetworkChannel} per payload id (see the doc).</p>
 */
public interface INetworkHelper {

    /**
     * Declares a server-to-client play payload. Must be called during mod initialization (before any connection).
     * The same type may additionally be declared {@link #registerPayloadC2S C2S} (bidirectional payload).
     */
    <T extends CustomPacketPayload> void registerPayloadS2C(CustomPacketPayload.Type<T> type, StreamCodec<? super FriendlyByteBuf, T> codec);

    /**
     * Declares a client-to-server play payload. Must be called during mod initialization (before any connection).
     */
    <T extends CustomPacketPayload> void registerPayloadC2S(CustomPacketPayload.Type<T> type, StreamCodec<? super FriendlyByteBuf, T> codec);

    /**
     * Sets the server-side handler of a C2S payload type (one handler per type; Fabric ignores a second registration).
     * May be called at any time (Minehop registers them lazily from {@link #onPlayConnectionInit}); loaders that need
     * handlers up front must dispatch to whatever handler is set when the packet arrives (no handler = packet ignored).
     *
     * <p>The handler runs on the server thread (like Fabric's play payload handlers).</p>
     */
    <T extends CustomPacketPayload> void registerServerReceiver(CustomPacketPayload.Type<T> type, ServerPayloadHandler<T> handler);

    /**
     * Sends a S2C payload to one player. Fabric: {@code ServerPlayNetworking.send}.
     */
    void sendToPlayer(ServerPlayer player, CustomPacketPayload payload);

    /**
     * A play connection was created (Fabric {@code ServerPlayConnectionEvents.INIT}: before {@link #onPlayConnectionJoin}).
     * Minehop only uses it to (idempotently) register its receivers.
     */
    void onPlayConnectionInit(PlayConnectionListener listener);

    /**
     * A player joined and can receive packets (Fabric {@code ServerPlayConnectionEvents.JOIN},
     * NeoForge/Forge {@code PlayerEvent.PlayerLoggedInEvent}).
     */
    void onPlayConnectionJoin(PlayConnectionListener listener);

    /**
     * A player's play connection closed (Fabric {@code ServerPlayConnectionEvents.DISCONNECT},
     * NeoForge/Forge {@code PlayerEvent.PlayerLoggedOutEvent}).
     */
    void onPlayConnectionDisconnect(PlayConnectionListener listener);

    @FunctionalInterface
    interface ServerPayloadHandler<T extends CustomPacketPayload> {
        void receive(T payload, ServerPayloadContext context);
    }

    /**
     * What a server payload handler knows about the packet's origin.
     */
    interface ServerPayloadContext {
        /**
         * @return the player that sent the payload.
         */
        ServerPlayer player();

        /**
         * @return the server.
         */
        MinecraftServer server();
    }

    @FunctionalInterface
    interface PlayConnectionListener {
        void onPlayConnection(ServerGamePacketListenerImpl handler, MinecraftServer server);
    }
}
