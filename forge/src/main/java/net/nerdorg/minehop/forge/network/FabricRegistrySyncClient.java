package net.nerdorg.minehop.forge.network;

import io.netty.buffer.Unpooled;
import io.netty.util.AttributeKey;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.event.EventNetworkChannel;
import net.nerdorg.minehop.Minehop;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Client side of Fabric API's registry sync on 1.20.1, so a Forge client can tell whether it may play on a Fabric server
 * (the production server).
 *
 * <p>Fabric API 0.92.x (fabric-registry-sync-v0 2.4.x) syncs registries in the PLAY phase: when a player joins
 * ({@code ServerPlayConnectionEvents.JOIN}) a Fabric server whose synced registries contain modded entries (Minehop's
 * items, blocks, entity types, ...) sends them as {@code fabric:registry/sync/direct} play payloads, unconditionally (no
 * channel check), and expects no answer: there is no {@code complete} packet and no configuration phase before 1.20.2,
 * so a client that ignores the payloads is not kicked. A Fabric client would remap its registries to the server's raw
 * ids; this client cannot, so it checks them: the payloads are raw slices of one buffer and an empty payload ends it.
 * The buffer lists, per registry, every (id, raw id) of the server. When every entry exists here with the same raw id
 * (true when both sides run vanilla + Minehop, which registers in the same order on every loader) it logs that and
 * carries on; otherwise it disconnects with an explanation instead of playing with mismatched ids.</p>
 */
public final class FabricRegistrySyncClient {
    public static final ResourceLocation DIRECT = new ResourceLocation("fabric", "registry/sync/direct");
    private static final AttributeKey<ByteArrayOutputStream> STATE = AttributeKey.valueOf("minehop:fabric_registry_sync");
    private static EventNetworkChannel channel;

    private FabricRegistrySyncClient() {
    }

    /**
     * Registers the channel. Physical client only, during mod construction. It accepts any (or no) remote version, so it
     * never affects which servers the client may join.
     */
    public static synchronized void register() {
        if (channel != null) {
            return;
        }
        channel = NetworkRegistry.ChannelBuilder.named(DIRECT)
                .networkProtocolVersion(() -> "1")
                .clientAcceptedVersions(version -> true)
                .serverAcceptedVersions(version -> true)
                .eventNetworkChannel();
        // ServerCustomPayloadEvent = a payload sent by the server (Forge 47 names these events after their origin).
        channel.addListener((NetworkEvent.ServerCustomPayloadEvent event) -> onDirect(event));
    }

    /**
     * Netty thread, play phase.
     */
    private static void onDirect(NetworkEvent.ServerCustomPayloadEvent event) {
        NetworkEvent.Context context = event.getSource().get();
        context.setPacketHandled(true);
        Connection connection = context.getNetworkManager();
        ByteArrayOutputStream buffer = context.attr(STATE).get();
        if (buffer == null) {
            buffer = new ByteArrayOutputStream();
            context.attr(STATE).set(buffer);
        }
        FriendlyByteBuf payload = event.getPayload();
        int length = payload.readableBytes();
        if (length != 0) {
            byte[] slice = new byte[length];
            payload.readBytes(slice);
            buffer.write(slice, 0, length);
            return;
        }
        byte[] combined = buffer.toByteArray();
        buffer.reset();
        // Compare on the client thread (registries are only ever changed there).
        context.enqueueWork(() -> verify(connection, combined));
    }

    private static void verify(Connection connection, byte[] data) {
        Map<ResourceLocation, Map<ResourceLocation, Integer>> remote;
        try {
            remote = parse(data);
        } catch (RuntimeException e) {
            Minehop.LOGGER.error("[Fabric registry sync] could not read the server's registry data", e);
            connection.disconnect(Component.literal("Minehop (Forge): could not read the server's Fabric registry sync data."));
            return;
        }

        List<String> problems = new ArrayList<>();
        int entries = 0;
        Map<String, String> modded = new TreeMap<>();
        for (Map.Entry<ResourceLocation, Map<ResourceLocation, Integer>> registryEntry : remote.entrySet()) {
            ResourceLocation registryId = registryEntry.getKey();
            Registry<?> registry = BuiltInRegistries.REGISTRY.get(registryId);
            if (registry == null) {
                problems.add("unknown registry " + registryId);
                continue;
            }
            for (Map.Entry<ResourceLocation, Integer> entry : registryEntry.getValue().entrySet()) {
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
            Minehop.LOGGER.info("[Fabric registry sync] {} registries / {} entries from the server match this client (raw ids identical): {}",
                    remote.size(), entries, modded);
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

    private static <T> int localRawId(Registry<T> registry, ResourceLocation id) {
        if (!registry.containsKey(id)) {
            return -1;
        }
        T value = registry.get(id);
        return value == null ? -1 : registry.getId(value);
    }

    /**
     * Reads fabric-registry-sync-v0 2.4.x's {@code DirectRegistryPacketHandler} format (1.20.1: no per-registry
     * attribute byte, unlike later versions). Trailing bytes after the structure are ignored.
     */
    private static Map<ResourceLocation, Map<ResourceLocation, Integer>> parse(byte[] data) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(data));
        Map<ResourceLocation, Map<ResourceLocation, Integer>> result = new LinkedHashMap<>();
        int registryNamespaces = buf.readVarInt();
        for (int i = 0; i < registryNamespaces; i++) {
            String registryNamespace = namespace(buf.readUtf());
            int registries = buf.readVarInt();
            for (int j = 0; j < registries; j++) {
                String registryPath = buf.readUtf();
                Map<ResourceLocation, Integer> ids = new LinkedHashMap<>();
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
                            ids.put(new ResourceLocation(idNamespace, buf.readUtf()), rawId);
                        }
                        lastBulkLastRawId = rawId;
                    }
                }
                result.put(new ResourceLocation(registryNamespace, registryPath), ids);
            }
        }
        return result;
    }

    private static String namespace(String namespace) {
        return namespace.isEmpty() ? ResourceLocation.DEFAULT_NAMESPACE : namespace;
    }
}
