package net.nerdorg.minehop.forge.network;

import io.netty.buffer.Unpooled;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraftforge.event.network.CustomPayloadEvent;
import net.minecraftforge.network.Channel;
import net.minecraftforge.network.ChannelBuilder;
import net.nerdorg.minehop.Minehop;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Client side of Fabric API's registry sync, so a Forge client can join a Fabric server (the production server).
 *
 * <p>A Fabric server whose synced registries contain modded entries (Minehop's items, blocks, entity types, ...) sends
 * them during the configuration phase as one {@code fabric:registry/sync} payload (fabric-registry-sync-v0 7.x,
 * {@code RegistrySyncPayload}; up to 1.21.x it was split into {@code fabric:registry/sync/direct} slices ended by an
 * empty one) and disconnects clients that did not register that channel. It finishes the configuration task once the
 * client answers {@code fabric:registry/sync/complete}.</p>
 *
 * <p>This channel registers both ids, so the Forge client advertises them in its {@code minecraft:register} reply.
 * The payload lists, per registry,
 * every (id, raw id) of the server. A Fabric client would remap its registries to the server's raw ids; Minehop checks
 * that every received entry exists locally with the same raw id (true when both sides run vanilla + Minehop, which
 * registers in the same order on every loader), answers {@code complete} when they all match, and otherwise
 * disconnects with an explanation instead of joining with mismatched ids.</p>
 */
public final class FabricRegistrySyncClient {
    public static final Identifier CHANNEL_NAME = Identifier.fromNamespaceAndPath(Minehop.MOD_ID, "fabric_registry_sync");
    private static Channel<CustomPacketPayload> channel;

    private FabricRegistrySyncClient() {
    }

    /**
     * Registers the channel. Physical client only, during mod construction.
     */
    public static synchronized void register() {
        if (channel != null) {
            return;
        }
        channel = ChannelBuilder.named(CHANNEL_NAME)
                .optional()
                .payloadChannel()
                .configuration()
                .clientbound()
                .add(SyncPayload.TYPE, SyncPayload.CODEC, FabricRegistrySyncClient::onSync)
                .serverbound()
                .add(CompletePayload.TYPE, CompletePayload.CODEC, (payload, context) -> context.setPacketHandled(true))
                .build();
    }

    /**
     * Netty thread, configuration phase.
     */
    private static void onSync(SyncPayload payload, CustomPayloadEvent.Context context) {
        context.setPacketHandled(true);
        Connection connection = context.getConnection();
        byte[] data = payload.data();
        // Compare on the client thread (registries are only ever changed there).
        context.enqueueWork(() -> verify(connection, data));
    }

    private static void verify(Connection connection, byte[] data) {
        Map<Identifier, Map<Identifier, Integer>> remote;
        Map<Identifier, Byte> attributes = new LinkedHashMap<>();
        try {
            remote = parse(data, attributes);
        } catch (RuntimeException e) {
            Minehop.LOGGER.error("[Fabric registry sync] could not read the server's registry data", e);
            connection.disconnect(Component.literal("Minehop (Forge): could not read the server's Fabric registry sync data."));
            return;
        }

        List<String> problems = new ArrayList<>();
        int entries = 0;
        Map<String, String> modded = new TreeMap<>();
        for (Map.Entry<Identifier, Map<Identifier, Integer>> registryEntry : remote.entrySet()) {
            Identifier registryId = registryEntry.getKey();
            Registry<?> registry = BuiltInRegistries.REGISTRY.getValue(registryId);
            if (registry == null) {
                boolean optional = (attributes.getOrDefault(registryId, (byte) 0) & 0x1) != 0;
                if (!optional) {
                    problems.add("unknown registry " + registryId);
                }
                continue;
            }
            for (Map.Entry<Identifier, Integer> entry : registryEntry.getValue().entrySet()) {
                entries++;
                int local = localRawId(registry, entry.getKey());
                if (local < 0) {
                    problems.add(registryId + ": " + entry.getKey() + " is missing on this client");
                } else if (local != entry.getValue()) {
                    problems.add(registryId + ": " + entry.getKey() + " has raw id " + local + " here but " + entry.getValue() + " on the server");
                }
                if (!"minecraft".equals(entry.getKey().getNamespace())) {
                    modded.put(registryId + " " + entry.getKey(), entry.getValue() + (local == entry.getValue() ? "" : " (local " + local + ")"));
                }
            }
        }

        if (problems.isEmpty()) {
            Minehop.LOGGER.info("[Fabric registry sync] {} registries / {} entries from the server match this client: {}", remote.size(), entries, modded);
            channel.send(CompletePayload.INSTANCE, connection);
            return;
        }

        Minehop.LOGGER.error("[Fabric registry sync] {} of {} entries from the server do not match this client:", problems.size(), entries);
        for (String problem : problems) {
            Minehop.LOGGER.error("[Fabric registry sync]   {}", problem);
        }
        connection.disconnect(Component.literal("Minehop (Forge): this server's registries do not match your client ("
                + problems.size() + " differences, first: " + problems.get(0) + "). The server probably runs mods that are not installed"
                + " on your client; see the log for the full list."));
    }

    private static <T> int localRawId(Registry<T> registry, Identifier id) {
        if (!registry.containsKey(id)) {
            return -1;
        }
        T value = registry.getValue(id);
        return value == null ? -1 : registry.getId(value);
    }

    /**
     * Reads fabric-registry-sync-v0's {@code RegistrySyncPayload} format (the structure of 6.x's
     * {@code DirectRegistryPacketHandler}). Trailing bytes after the structure are ignored.
     */
    private static Map<Identifier, Map<Identifier, Integer>> parse(byte[] data, Map<Identifier, Byte> attributes) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(data));
        Map<Identifier, Map<Identifier, Integer>> result = new LinkedHashMap<>();
        int registryNamespaces = buf.readVarInt();
        for (int i = 0; i < registryNamespaces; i++) {
            String registryNamespace = namespace(buf.readUtf());
            int registries = buf.readVarInt();
            for (int j = 0; j < registries; j++) {
                String registryPath = buf.readUtf();
                byte attribute = buf.readByte();
                Map<Identifier, Integer> ids = new LinkedHashMap<>();
                int idNamespaces = buf.readVarInt();
                int lastBulkLastRawId = 0;
                for (int k = 0; k < idNamespaces; k++) {
                    String idNamespace = namespace(buf.readUtf());
                    int bulks = buf.readVarInt();
                    for (int l = 0; l < bulks; l++) {
                        int startDiff = buf.readVarInt();
                        int size = buf.readVarInt();
                        int rawId = lastBulkLastRawId + startDiff - 1;
                        for (int m = 0; m < size; m++) {
                            rawId++;
                            ids.put(Identifier.fromNamespaceAndPath(idNamespace, buf.readUtf()), rawId);
                        }
                        lastBulkLastRawId = rawId;
                    }
                }
                Identifier registryId = Identifier.fromNamespaceAndPath(registryNamespace, registryPath);
                result.put(registryId, ids);
                attributes.put(registryId, attribute);
            }
        }
        return result;
    }

    private static String namespace(String namespace) {
        return namespace.isEmpty() ? Identifier.DEFAULT_NAMESPACE : namespace;
    }

    /**
     * {@code fabric:registry/sync}: the registry structure as raw bytes, no length prefix.
     */
    private record SyncPayload(byte[] data) implements CustomPacketPayload {
        static final Type<SyncPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("fabric", "registry/sync"));
        static final StreamCodec<FriendlyByteBuf, SyncPayload> CODEC = StreamCodec.of(
                (buf, payload) -> buf.writeBytes(payload.data()),
                buf -> {
                    byte[] bytes = new byte[buf.readableBytes()];
                    buf.readBytes(bytes);
                    return new SyncPayload(bytes);
                });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * {@code fabric:registry/sync/complete}: empty.
     */
    private static final class CompletePayload implements CustomPacketPayload {
        static final CompletePayload INSTANCE = new CompletePayload();
        static final Type<CompletePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("fabric", "registry/sync/complete"));
        static final StreamCodec<FriendlyByteBuf, CompletePayload> CODEC = StreamCodec.unit(INSTANCE);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
