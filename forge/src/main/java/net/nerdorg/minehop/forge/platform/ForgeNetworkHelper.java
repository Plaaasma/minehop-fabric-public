package net.nerdorg.minehop.forge.platform;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.network.CustomPayloadEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.network.Channel;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.ForgePayload;
import net.minecraftforge.network.payload.PayloadFlow;
import net.minecraftforge.network.payload.PayloadProtocol;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.platform.services.INetworkHelper;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Forge networking, wire-compatible with the Fabric production server (see docs/MULTILOADER.md, "Networking").
 *
 * <ul>
 *     <li><b>Channel:</b> one optional Forge {@code PayloadChannel} ({@code minehop:network}) that holds every Minehop
 *     payload under its own id ({@code minehop:<name>}) with its own codec. A payload channel puts each payload in a
 *     plain vanilla custom payload packet, id + codec bytes, no discriminator: exactly Fabric's format. Being
 *     {@code optional()}, it accepts servers and clients that do not have it (vanilla, Fabric). The payload ids are
 *     advertised in {@code minecraft:register} like on Fabric.</li>
 *     <li><b>Receiving:</b> Forge decodes the bytes with the declared codec and calls the channel handler on the netty
 *     thread; the handler hands the payload to the main thread ({@code enqueueWork}, the same queue vanilla packets
 *     use, like Fabric's {@code server.execute}/{@code client.execute}) and looks the receiver up there (receivers may be
 *     registered lazily, first registration wins, no receiver = ignored).</li>
 *     <li><b>Sending:</b> like Fabric, a {@link ClientboundCustomPayloadPacket} carrying the payload object itself goes
 *     through {@code player.connection.send(...)}, so everything that inspects outgoing packets sees the real payload
 *     (Minehop's anticheat watches {@code ResetVelocityCarryPayload}). Vanilla's payload codec falls back to
 *     {@code ForgeHooks.getCustomPayloadCodec} for these ids; {@code ForgeHooksMixin} lets that codec encode the typed
 *     payload with its declared codec ({@link #wrapPayloadCodec}).</li>
 * </ul>
 */
public class ForgeNetworkHelper implements INetworkHelper {
    public static final ResourceLocation CHANNEL_NAME = ResourceLocation.fromNamespaceAndPath(Minehop.MOD_ID, "network");

    private static final Map<ResourceLocation, Declaration<?>> DECLARATIONS = Collections.synchronizedMap(new LinkedHashMap<>());
    private static final Map<ResourceLocation, ServerPayloadHandler<?>> SERVER_HANDLERS = new ConcurrentHashMap<>();
    @Nullable
    private static volatile ClientDispatcher clientDispatcher;
    @Nullable
    private static Channel<CustomPacketPayload> channel;

    @Override
    public <T extends CustomPacketPayload> void registerPayloadS2C(CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
        declare(type, codec, true, false);
    }

    @Override
    public <T extends CustomPacketPayload> void registerPayloadC2S(CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
        declare(type, codec, false, true);
    }

    private static synchronized <T extends CustomPacketPayload> void declare(CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec, boolean s2c, boolean c2s) {
        if (channel != null) {
            throw new IllegalStateException("Minehop payload " + type.id() + " declared after the Forge channel was built");
        }
        Declaration<?> previous = DECLARATIONS.get(type.id());
        if (previous == null) {
            DECLARATIONS.put(type.id(), new Declaration<>(type, codec, s2c, c2s));
        } else {
            // Same type declared for the other direction too (Fabric: registered in both PayloadTypeRegistry.playS2C()
            // and playC2S() with the same codec) -> bidirectional.
            DECLARATIONS.put(type.id(), new Declaration<>(type, codec, previous.s2c() || s2c, previous.c2s() || c2s));
        }
    }

    /**
     * Builds the {@code minehop:network} channel from every payload declared during the common init. Called once by
     * {@code MinehopForge} right after the common init (Forge locks channel registration after mod loading).
     */
    public static synchronized void buildChannel() {
        if (channel != null) {
            return;
        }
        PayloadProtocol<RegistryFriendlyByteBuf, CustomPacketPayload> play = ChannelBuilder.named(CHANNEL_NAME)
                .optional()
                .payloadChannel()
                .play();
        PayloadFlow<RegistryFriendlyByteBuf, CustomPacketPayload> last = null;
        for (Declaration<?> declaration : DECLARATIONS.values()) {
            last = addToChannel(play, declaration);
        }
        if (last == null) {
            throw new IllegalStateException("No Minehop payloads were declared");
        }
        channel = last.build();
        Minehop.LOGGER.info("Minehop Forge network channel {} built with {} payloads", CHANNEL_NAME, DECLARATIONS.size());
    }

    @SuppressWarnings("unchecked")
    private static <T extends CustomPacketPayload> PayloadFlow<RegistryFriendlyByteBuf, CustomPacketPayload> addToChannel(PayloadProtocol<RegistryFriendlyByteBuf, CustomPacketPayload> play, Declaration<T> declaration) {
        PacketFlow flow = declaration.s2c() && declaration.c2s() ? null : declaration.s2c() ? PacketFlow.CLIENTBOUND : PacketFlow.SERVERBOUND;
        // The codecs are StreamCodec<FriendlyByteBuf, T> (or <? super RegistryFriendlyByteBuf, T>); play payloads are
        // always read from/written to a RegistryFriendlyByteBuf, so this view is safe.
        StreamCodec<RegistryFriendlyByteBuf, T> codec = (StreamCodec<RegistryFriendlyByteBuf, T>) (StreamCodec<?, T>) declaration.codec();
        return play.flow(flow).add(declaration.type(), codec, ForgeNetworkHelper::onPayloadReceived);
    }

    /**
     * Channel handler (netty thread). Mirrors Fabric's play payload receiving: schedule on the main thread of the
     * receiving side and dispatch to the receiver registered for the payload type there.
     */
    private static <T extends CustomPacketPayload> void onPayloadReceived(T payload, CustomPayloadEvent.Context context) {
        context.setPacketHandled(true);
        if (context.isServerSide()) {
            context.enqueueWork(() -> receiveOnServer(payload, context));
        } else {
            ClientDispatcher dispatcher = clientDispatcher;
            if (dispatcher != null) {
                context.enqueueWork(() -> dispatcher.dispatch(payload));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends CustomPacketPayload> void receiveOnServer(T payload, CustomPayloadEvent.Context context) {
        ServerPayloadHandler<T> handler = (ServerPayloadHandler<T>) SERVER_HANDLERS.get(payload.type().id());
        ServerPlayer player = context.getSender();
        if (handler == null || player == null) {
            return;
        }
        handler.receive(payload, new Context(player, player.getServer()));
    }

    @Override
    public <T extends CustomPacketPayload> void registerServerReceiver(CustomPacketPayload.Type<T> type, ServerPayloadHandler<T> handler) {
        // Fabric's registerGlobalReceiver keeps the first handler of a type.
        SERVER_HANDLERS.putIfAbsent(type.id(), handler);
    }

    @Override
    public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        // Fabric: ServerPlayNetworking.send(player, payload) == player.connection.send(new ClientboundCustomPayloadPacket(payload)).
        player.connection.send(new ClientboundCustomPayloadPacket(payload));
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
    // Hooks for the client network helper and ForgeHooksMixin
    // ---------------------------------------------------------------------------------------------

    /**
     * Installed by {@link ForgeClientNetworkHelper} on the physical client (keeps client classes off the dedicated
     * server). Runs on the client thread.
     */
    @FunctionalInterface
    interface ClientDispatcher {
        void dispatch(CustomPacketPayload payload);
    }

    static void setClientDispatcher(ClientDispatcher dispatcher) {
        clientDispatcher = dispatcher;
    }

    /**
     * Called from {@code ForgeHooksMixin} with the codec {@code ForgeHooks.getCustomPayloadCodec} returned for
     * {@code id}. For Minehop payload ids, returns a codec that encodes both Forge's own {@link ForgePayload} (what
     * Forge channels send) and the typed Minehop payload object (what {@link #sendToPlayer} and
     * {@code ForgeClientNetworkHelper#sendToServer} send) with the same bytes; decoding is left to Forge (it hands the
     * bytes to the channel, which decodes them with the declared codec). Returns {@code null} for other ids.
     */
    @Nullable
    @SuppressWarnings("unchecked")
    public static <B extends FriendlyByteBuf> StreamCodec<B, CustomPacketPayload> wrapPayloadCodec(ResourceLocation id, StreamCodec<B, ? extends CustomPacketPayload> forgeCodec) {
        Declaration<?> declaration = DECLARATIONS.get(id);
        if (declaration == null || forgeCodec == null) {
            return null;
        }
        StreamCodec<B, CustomPacketPayload> forge = (StreamCodec<B, CustomPacketPayload>) forgeCodec;
        StreamCodec<? super B, CustomPacketPayload> typed = (StreamCodec<? super B, CustomPacketPayload>) (StreamCodec<?, ?>) declaration.codec();
        return new StreamCodec<>() {
            @Override
            public CustomPacketPayload decode(B buffer) {
                return forge.decode(buffer);
            }

            @Override
            public void encode(B buffer, CustomPacketPayload value) {
                if (value instanceof ForgePayload) {
                    forge.encode(buffer, value);
                } else {
                    typed.encode(buffer, value);
                }
            }
        };
    }

    private record Declaration<T extends CustomPacketPayload>(CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec, boolean s2c, boolean c2s) {
    }

    private record Context(ServerPlayer player, MinecraftServer server) implements ServerPayloadContext {
    }
}
