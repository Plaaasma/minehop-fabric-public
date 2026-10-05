package net.nerdorg.minehop.neoforge.platform;

import net.minecraft.network.Connection;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.extensions.ICommonPacketListener;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.handling.IPayloadHandler;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.neoforge.MinehopNeoForge;
import net.nerdorg.minehop.neoforge.network.FabricRegistrySync;
import net.nerdorg.minehop.platform.services.INetworkHelper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * NeoForge implementation of {@link INetworkHelper}.
 *
 * <p>Wire format: every Minehop payload is registered under its own id with its own codec in the play protocol, so
 * NeoForge sends it as a plain vanilla custom payload (id + codec bytes), byte-identical to Fabric. All payloads are
 * {@code optional()}: a Fabric server (or a Fabric client on this server) performs no NeoForge channel negotiation,
 * and NeoForge only lets optional payloads through on such "other" connections.</p>
 *
 * <p>Handlers: NeoForge needs a handler per payload type up front, while Minehop sets its receivers lazily (server: on
 * the first play connection; client: on every connection). Each registered payload type gets a dispatcher that looks
 * the receiver up when the packet is handled (no receiver = ignored, first registration wins, like Fabric's
 * {@code registerGlobalReceiver}). Dispatchers run on the main thread (the registrar's default {@code HandlerThread.MAIN}),
 * like Fabric's play payload handlers.</p>
 */
public class NeoForgeNetworkHelper implements INetworkHelper {
    /** The payload types declared by the common code, in declaration order. */
    private static final Map<Identifier, PayloadType<?>> PAYLOAD_TYPES = new LinkedHashMap<>();
    /** Server receivers (C2S), keyed by payload id. */
    private static final Map<Identifier, ServerPayloadHandler<?>> SERVER_RECEIVERS = new ConcurrentHashMap<>();
    /**
     * Client receivers (S2C), keyed by payload id. Filled by {@link NeoForgeClientNetworkHelper} (client only); kept here
     * as plain NeoForge handlers so that this class never references client classes.
     */
    static final Map<Identifier, IPayloadHandler<CustomPacketPayload>> CLIENT_RECEIVERS = new ConcurrentHashMap<>();

    private static final List<PlayConnectionListener> INIT_LISTENERS = new CopyOnWriteArrayList<>();
    private static final List<PlayConnectionListener> JOIN_LISTENERS = new CopyOnWriteArrayList<>();
    private static final List<PlayConnectionListener> DISCONNECT_LISTENERS = new CopyOnWriteArrayList<>();

    private static boolean payloadEventListenerAdded;
    private static boolean payloadsRegistered;
    private static boolean connectionEventListenersAdded;

    @Override
    public <T extends CustomPacketPayload> void registerPayloadS2C(CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
        declare(type, codec, true, false);
    }

    @Override
    public <T extends CustomPacketPayload> void registerPayloadC2S(CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
        declare(type, codec, false, true);
    }

    private static synchronized <T extends CustomPacketPayload> void declare(CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec, boolean s2c, boolean c2s) {
        if (payloadsRegistered) {
            throw new IllegalStateException("Minehop payload " + type.id() + " declared after RegisterPayloadHandlersEvent");
        }
        if (!payloadEventListenerAdded) {
            payloadEventListenerAdded = true;
            MinehopNeoForge.modEventBus().addListener(NeoForgeNetworkHelper::onRegisterPayloadHandlers);
        }
        @SuppressWarnings("unchecked")
        PayloadType<T> existing = (PayloadType<T>) PAYLOAD_TYPES.get(type.id());
        if (existing == null) {
            PAYLOAD_TYPES.put(type.id(), new PayloadType<>(type, codec, s2c, c2s));
        } else {
            // Declared in both directions (Fabric: playS2C().register + playC2S().register with the same codec).
            PAYLOAD_TYPES.put(type.id(), new PayloadType<>(type, existing.codec(), existing.s2c() || s2c, existing.c2s() || c2s));
        }
    }

    private static synchronized void onRegisterPayloadHandlers(RegisterPayloadHandlersEvent event) {
        payloadsRegistered = true;
        // Version "1": only compared on NeoForge<->NeoForge connections. optional(): see the class javadoc.
        PayloadRegistrar registrar = event.registrar("1").optional();
        for (PayloadType<?> payloadType : PAYLOAD_TYPES.values()) {
            payloadType.register(registrar);
        }
        FabricRegistrySync.registerPayloads(registrar);
    }

    /**
     * Runs on the main thread (registrar default). Serverbound packets go to the server receiver, clientbound packets
     * to the client receiver.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void dispatch(CustomPacketPayload payload, IPayloadContext context) {
        Identifier id = payload.type().id();
        if (context.flow().isServerbound()) {
            ServerPayloadHandler handler = SERVER_RECEIVERS.get(id);
            if (handler != null && context.player() instanceof ServerPlayer player) {
                handler.receive(payload, new ServerContext(player, player.level().getServer()));
            }
        } else {
            IPayloadHandler<CustomPacketPayload> handler = CLIENT_RECEIVERS.get(id);
            if (handler != null) {
                handler.handle(payload, context);
            }
        }
    }

    @Override
    public <T extends CustomPacketPayload> void registerServerReceiver(CustomPacketPayload.Type<T> type, ServerPayloadHandler<T> handler) {
        SERVER_RECEIVERS.putIfAbsent(type.id(), handler);
    }

    @Override
    public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        ServerGamePacketListenerImpl connection = player.connection;
        if (!(player instanceof FakePlayer)) {
            // Fake players have no network channel (their listener drops every packet anyway).
            ensureChannel(connection, payload.type().id());
        }
        connection.send(new ClientboundCustomPayloadPacket(payload));
    }

    /**
     * NeoForge refuses to send a modded payload on a channel the other side has not announced. Fabric's
     * {@code ServerPlayNetworking.send} / {@code ClientPlayNetworking.send} send unconditionally, and Minehop relies on
     * that: e.g. a Fabric client announces its play channels with {@code minecraft:register} only after it received the
     * login packet, while the server's join listeners already send payloads. Mark the channel as known on this
     * connection (NeoForge's ad-hoc channel set, the one {@code minecraft:register} fills) so the packet goes out exactly
     * as on Fabric. A peer that does not know the payload discards it, like a vanilla peer would. On NeoForge-NeoForge
     * connections the channels were negotiated, so this is a no-op.
     */
    public static void ensureChannel(ICommonPacketListener listener, Identifier id) {
        if (!listener.hasChannel(id)) {
            Connection connection = listener.getConnection();
            NetworkRegistry.onMinecraftRegister(connection, Set.of(id));
        }
    }

    @Override
    public void onPlayConnectionInit(PlayConnectionListener listener) {
        addConnectionListener(INIT_LISTENERS, listener);
    }

    @Override
    public void onPlayConnectionJoin(PlayConnectionListener listener) {
        addConnectionListener(JOIN_LISTENERS, listener);
    }

    @Override
    public void onPlayConnectionDisconnect(PlayConnectionListener listener) {
        addConnectionListener(DISCONNECT_LISTENERS, listener);
    }

    private static synchronized void addConnectionListener(List<PlayConnectionListener> listeners, PlayConnectionListener listener) {
        if (!connectionEventListenersAdded) {
            connectionEventListenersAdded = true;
            NeoForge.EVENT_BUS.addListener(NeoForgeNetworkHelper::onPlayerLoggedIn);
            NeoForge.EVENT_BUS.addListener(NeoForgeNetworkHelper::onPlayerLoggedOut);
        }
        listeners.add(listener);
    }

    /**
     * Fabric fires INIT when the play listener is created and JOIN once the player was placed in the world. NeoForge
     * only has the latter ({@code PlayerLoggedInEvent}, end of {@code PlayerList#placeNewPlayer}), so both run from it,
     * INIT first. Minehop only (idempotently) registers receivers and listeners in INIT - including JOIN listeners,
     * which Fabric then runs for that same connection; the JOIN list is read after the INIT listeners ran, so they run
     * here too. (Packets the client sends are handled after this event, so the receivers are in place in time.)
     */
    private static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.connection != null) {
            for (PlayConnectionListener listener : INIT_LISTENERS) {
                listener.onPlayConnection(player.connection, player.level().getServer());
            }
            for (PlayConnectionListener listener : JOIN_LISTENERS) {
                listener.onPlayConnection(player.connection, player.level().getServer());
            }
        }
    }

    private static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.connection != null) {
            for (PlayConnectionListener listener : DISCONNECT_LISTENERS) {
                listener.onPlayConnection(player.connection, player.level().getServer());
            }
        }
    }

    private record PayloadType<T extends CustomPacketPayload>(CustomPacketPayload.Type<T> type,
                                                              StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
                                                              boolean s2c, boolean c2s) {
        void register(PayloadRegistrar registrar) {
            IPayloadHandler<T> handler = NeoForgeNetworkHelper::dispatch;
            if (this.s2c && this.c2s) {
                // NeoForge 21.11 needs the client-side handler too (otherwise it must come from
                // RegisterClientPayloadHandlersEvent): the same dispatcher, which only looks receivers up.
                registrar.playBidirectional(this.type, this.codec, handler, handler);
            } else if (this.s2c) {
                registrar.playToClient(this.type, this.codec, handler);
            } else {
                registrar.playToServer(this.type, this.codec, handler);
            }
            Minehop.LOGGER.debug("Registered Minehop payload {} (s2c={}, c2s={})", this.type.id(), this.s2c, this.c2s);
        }
    }

    private record ServerContext(ServerPlayer player, MinecraftServer server) implements ServerPayloadContext {
    }
}
