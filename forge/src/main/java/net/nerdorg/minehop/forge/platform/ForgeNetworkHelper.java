package net.nerdorg.minehop.forge.platform;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.event.EventNetworkChannel;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.networking.codec.CustomPacketPayload;
import net.nerdorg.minehop.networking.codec.StreamCodec;
import net.nerdorg.minehop.platform.services.INetworkHelper;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Forge 1.20.1 networking, wire-compatible with the Fabric production server (see docs/MULTILOADER.md, "Networking").
 *
 * <ul>
 *     <li><b>Channels:</b> Minecraft 1.20.1 custom payload packets carry an id and raw bytes. Every Minehop payload id
 *     ({@code minehop:<name>}) gets its own Forge {@code EventNetworkChannel}, so the bytes on the wire are exactly the
 *     payload codec's, with no discriminator (a {@code SimpleChannel} would add one), and the ids are advertised in
 *     {@code minecraft:register} like on Fabric. Every channel accepts any remote version, including
 *     {@code NetworkRegistry.ABSENT} / {@code ACCEPTVANILLA}, so vanilla and Fabric peers (no FML handshake) are
 *     accepted.</li>
 *     <li><b>Receiving:</b> Forge fires the channel event on the netty thread; the bytes are decoded right there with the
 *     declared codec (like Fabric's {@code FabricPacket} receivers) and the payload is handed to the main thread
 *     ({@code enqueueWork}, like Fabric's {@code server.execute}/{@code client.execute}), where the receiver registered for
 *     the type is looked up (receivers may be registered lazily, first registration wins, no receiver = ignored). Only
 *     payloads declared C2S are decoded on the server and only payloads declared S2C on the client.</li>
 *     <li><b>Sending:</b> like Fabric, a vanilla {@link ClientboundCustomPayloadPacket} {@code (id, codec bytes)} goes
 *     through {@code player.connection.send(...)}, so Minehop's anticheat sees it (it watches
 *     {@code minehop:reset_velocity_carry} in {@code ServerGamePacketListenerImpl#send}).</li>
 * </ul>
 */
public class ForgeNetworkHelper implements INetworkHelper {
    /** Version string offered in the FML handshake; any remote version (or none) is accepted. */
    private static final String PROTOCOL_VERSION = "1";

    private static final Map<ResourceLocation, Declaration<?>> DECLARATIONS = Collections.synchronizedMap(new LinkedHashMap<>());
    private static final Map<ResourceLocation, ServerPayloadHandler<?>> SERVER_HANDLERS = new ConcurrentHashMap<>();
    @Nullable
    private static volatile ClientDispatcher clientDispatcher;
    private static boolean built;

    @Override
    public <T extends CustomPacketPayload> void registerPayloadS2C(CustomPacketPayload.Type<T> type, StreamCodec<? super FriendlyByteBuf, T> codec) {
        declare(type, codec, true, false);
    }

    @Override
    public <T extends CustomPacketPayload> void registerPayloadC2S(CustomPacketPayload.Type<T> type, StreamCodec<? super FriendlyByteBuf, T> codec) {
        declare(type, codec, false, true);
    }

    private static synchronized <T extends CustomPacketPayload> void declare(CustomPacketPayload.Type<T> type, StreamCodec<? super FriendlyByteBuf, T> codec, boolean s2c, boolean c2s) {
        if (built) {
            throw new IllegalStateException("Minehop payload " + type.id() + " declared after the Forge channels were built");
        }
        Declaration<?> previous = DECLARATIONS.get(type.id());
        if (previous == null) {
            DECLARATIONS.put(type.id(), new Declaration<>(type, codec, s2c, c2s));
        } else {
            // Same type declared for the other direction too (bidirectional payload, same codec).
            DECLARATIONS.put(type.id(), new Declaration<>(type, codec, previous.s2c() || s2c, previous.c2s() || c2s));
        }
    }

    /**
     * Creates one event channel per payload declared during the common init. Called once by {@code MinehopForge} right
     * after the common init (Forge locks channel registration after mod construction).
     */
    public static synchronized void buildChannels() {
        if (built) {
            return;
        }
        built = true;
        if (DECLARATIONS.isEmpty()) {
            throw new IllegalStateException("No Minehop payloads were declared");
        }
        for (Declaration<?> declaration : DECLARATIONS.values()) {
            EventNetworkChannel channel = NetworkRegistry.ChannelBuilder.named(declaration.type().id())
                    .networkProtocolVersion(() -> PROTOCOL_VERSION)
                    .clientAcceptedVersions(version -> true)
                    .serverAcceptedVersions(version -> true)
                    .eventNetworkChannel();
            // Forge 47 names the payload events after their origin: ClientCustomPayloadEvent = sent by a client (handled
            // on the server), ServerCustomPayloadEvent = sent by the server (handled on the client).
            channel.addListener((NetworkEvent.ClientCustomPayloadEvent event) -> onServerPayload(declaration, event));
            channel.addListener((NetworkEvent.ServerCustomPayloadEvent event) -> onClientPayload(declaration, event));
        }
        Minehop.LOGGER.info("Minehop Forge network: {} payload channels", DECLARATIONS.size());
    }

    /**
     * Netty thread: a payload from a client.
     */
    private static <T extends CustomPacketPayload> void onServerPayload(Declaration<T> declaration, NetworkEvent.ClientCustomPayloadEvent event) {
        NetworkEvent.Context context = event.getSource().get();
        context.setPacketHandled(true);
        ServerPlayer player = context.getSender();
        if (!declaration.c2s() || player == null) {
            return;
        }
        T payload = declaration.codec().decode(event.getPayload());
        context.enqueueWork(() -> receiveOnServer(declaration, payload, player));
    }

    @SuppressWarnings("unchecked")
    private static <T extends CustomPacketPayload> void receiveOnServer(Declaration<T> declaration, T payload, ServerPlayer player) {
        ServerPayloadHandler<T> handler = (ServerPayloadHandler<T>) SERVER_HANDLERS.get(declaration.type().id());
        // Fabric skips the handler once the connection closed (it was scheduled from the netty thread).
        if (handler == null || player.connection == null || !player.connection.isAcceptingMessages()) {
            return;
        }
        handler.receive(payload, new Context(player, player.getServer()));
    }

    /**
     * Netty thread: a payload from the server.
     */
    private static <T extends CustomPacketPayload> void onClientPayload(Declaration<T> declaration, NetworkEvent.ServerCustomPayloadEvent event) {
        NetworkEvent.Context context = event.getSource().get();
        context.setPacketHandled(true);
        ClientDispatcher dispatcher = clientDispatcher;
        if (!declaration.s2c() || dispatcher == null) {
            return;
        }
        T payload = declaration.codec().decode(event.getPayload());
        context.enqueueWork(() -> dispatcher.dispatch(payload, context.getNetworkManager()));
    }

    @Override
    public <T extends CustomPacketPayload> void registerServerReceiver(CustomPacketPayload.Type<T> type, ServerPayloadHandler<T> handler) {
        // Fabric's registerGlobalReceiver keeps the first handler of a type.
        SERVER_HANDLERS.putIfAbsent(type.id(), handler);
    }

    @Override
    public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        // Fabric: ServerPlayNetworking.send(player, id, buf) == player.connection.send(new ClientboundCustomPayloadPacket(id, buf)).
        player.connection.send(new ClientboundCustomPayloadPacket(payload.type().id(), encode(payload, true)));
    }

    /**
     * The payload's codec bytes, in a fresh buffer (Fabric's {@code PacketByteBufs.create()}).
     */
    @SuppressWarnings("unchecked")
    static FriendlyByteBuf encode(CustomPacketPayload payload, boolean clientbound) {
        Declaration<?> declaration = DECLARATIONS.get(payload.type().id());
        if (declaration == null || !(clientbound ? declaration.s2c() : declaration.c2s())) {
            throw new IllegalArgumentException("Payload " + payload.type().id() + " is not registered for "
                    + (clientbound ? "clientbound" : "serverbound") + " play");
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        ((StreamCodec<? super FriendlyByteBuf, CustomPacketPayload>) declaration.codec()).encode(buf, payload);
        return buf;
    }

    @Override
    public void onPlayConnectionInit(PlayConnectionListener listener) {
        // Fabric's INIT fires before JOIN for a new play connection; on Forge both come from PlayerLoggedInEvent, INIT first.
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, PlayerEvent.PlayerLoggedInEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player && player.connection != null) {
                listener.onPlayConnection(player.connection, player.getServer());
            }
        });
    }

    @Override
    public void onPlayConnectionJoin(PlayConnectionListener listener) {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, PlayerEvent.PlayerLoggedInEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player && player.connection != null) {
                listener.onPlayConnection(player.connection, player.getServer());
            }
        });
    }

    @Override
    public void onPlayConnectionDisconnect(PlayConnectionListener listener) {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, PlayerEvent.PlayerLoggedOutEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player && player.connection != null) {
                listener.onPlayConnection(player.connection, player.getServer());
            }
        });
    }

    // ---------------------------------------------------------------------------------------------
    // Hook for the client network helper
    // ---------------------------------------------------------------------------------------------

    /**
     * Installed by {@link ForgeClientNetworkHelper} on the physical client (keeps client classes off the dedicated
     * server). Runs on the client thread.
     */
    @FunctionalInterface
    interface ClientDispatcher {
        void dispatch(CustomPacketPayload payload, net.minecraft.network.Connection connection);
    }

    static void setClientDispatcher(ClientDispatcher dispatcher) {
        clientDispatcher = dispatcher;
    }

    private record Declaration<T extends CustomPacketPayload>(CustomPacketPayload.Type<T> type, StreamCodec<? super FriendlyByteBuf, T> codec, boolean s2c, boolean c2s) {
    }

    private record Context(ServerPlayer player, MinecraftServer server) implements ServerPayloadContext {
    }
}
