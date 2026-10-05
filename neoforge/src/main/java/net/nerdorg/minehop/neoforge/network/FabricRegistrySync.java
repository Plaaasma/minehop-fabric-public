package net.nerdorg.minehop.neoforge.network;

import io.netty.buffer.Unpooled;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.registries.RegistryManager;
import net.neoforged.neoforge.registries.RegistrySnapshot;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.neoforge.platform.NeoForgeNetworkHelper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Client side of Fabric API's registry sync (fabric-registry-sync-v0 6.2.x for 1.21.11, {@code RegistrySyncPayload}),
 * so that a NeoForge client can join a Fabric server.
 *
 * <p>A Fabric server whose synced registries contain modded entries (Minehop's blocks, items, entity types, ...) runs a
 * configuration task right at the start of the configuration phase: it sends the raw ids of those registries as one
 * {@code fabric:registry/sync} payload and waits for {@code fabric:registry/sync/complete}. (Up to 1.21.4 Fabric sent
 * them as {@code fabric:registry/sync/direct} slices ended by an empty slice.) A client that did not announce
 * {@code fabric:registry/sync} in its {@code minecraft:register} packet is disconnected ("This server requires Fabric
 * Loader and Fabric API installed on your client!"); see {@code ClientConfigurationPacketListenerImplMixin} for the
 * announcement. Fabric registers the payload as "large": above vanilla's 1 MiB payload limit it would be split into
 * {@code fabric:split} packets, which this client does not reassemble (vanilla + Minehop is far below that limit).</p>
 *
 * <p>Like Fabric's client, this checks that every entry the server sent exists here (otherwise it disconnects with an
 * explanation) and makes the raw ids match the server's: when they already match (same mods, same registration order:
 * the normal case for Minehop) nothing changes; otherwise the registries are remapped with NeoForge's own registry
 * snapshot mechanism (entries this client has and the server does not are moved after the server's ids, as Fabric
 * does). NeoForge reverts the registries to their frozen state when the client disconnects.</p>
 *
 * <p>Wire format (DirectRegistryPacketHandler, fabric-registry-sync-v0 6.x): VarInt registry-namespace group count;
 * per group: String namespace ({@code ""} = minecraft), VarInt registry count; per registry: String path, byte
 * attributes (bit 0 = optional registry), VarInt id-namespace group count; per group: String namespace, VarInt bulk
 * count; per bulk: VarInt raw-id delta (first raw id = previous bulk's last raw id + delta), VarInt size, then
 * {@code size} String paths with consecutive raw ids. Fabric may pad a payload with trailing zero bytes (it sends the
 * whole backing array of its buffer), so trailing bytes after the structure are ignored.</p>
 */
public final class FabricRegistrySync {
    public static final Identifier SYNC_ID = Identifier.fromNamespaceAndPath("fabric", "registry/sync");
    public static final Identifier COMPLETE_ID = Identifier.fromNamespaceAndPath("fabric", "registry/sync/complete");

    private FabricRegistrySync() {
    }

    /**
     * Registers the two configuration payloads (physical client only: a NeoForge server never receives them).
     */
    public static void registerPayloads(PayloadRegistrar optionalRegistrar) {
        if (!FMLEnvironment.getDist().isClient()) {
            return;
        }
        optionalRegistrar.configurationToClient(SyncPayload.TYPE, SyncPayload.STREAM_CODEC, FabricRegistrySync::onSyncPayload);
        optionalRegistrar.configurationToServer(CompletePayload.TYPE, CompletePayload.STREAM_CODEC, (payload, context) -> {
        });
    }

    /**
     * Client thread (registrar default). The payload carries the whole registry map.
     */
    private static void onSyncPayload(SyncPayload payload, IPayloadContext context) {
        byte[] combined = payload.data();
        Map<Identifier, SyncedRegistry> synced;
        try {
            synced = decode(combined);
        } catch (RuntimeException e) {
            Minehop.LOGGER.error("Could not read the Fabric registry sync data ({} bytes)", combined.length, e);
            context.disconnect(Component.literal("Minehop (NeoForge): could not read the server's Fabric registry sync data: " + e));
            return;
        }

        Component failure = apply(synced);
        if (failure != null) {
            context.disconnect(failure);
            return;
        }
        NeoForgeNetworkHelper.ensureChannel(context.listener(), COMPLETE_ID);
        context.reply(CompletePayload.INSTANCE);
    }

    static Map<Identifier, SyncedRegistry> decode(byte[] data) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(data));
        Map<Identifier, SyncedRegistry> result = new LinkedHashMap<>();
        int registryNamespaceGroups = buf.readVarInt();
        for (int i = 0; i < registryNamespaceGroups; i++) {
            String registryNamespace = expandNamespace(buf.readUtf());
            int registryCount = buf.readVarInt();
            for (int j = 0; j < registryCount; j++) {
                String registryPath = buf.readUtf();
                boolean optional = (buf.readByte() & 0x1) != 0;
                Map<Identifier, Integer> ids = new LinkedHashMap<>();
                int idNamespaceGroups = buf.readVarInt();
                int lastBulkLastRawId = 0;
                for (int k = 0; k < idNamespaceGroups; k++) {
                    String idNamespace = expandNamespace(buf.readUtf());
                    int bulkCount = buf.readVarInt();
                    for (int l = 0; l < bulkCount; l++) {
                        int startDelta = buf.readVarInt();
                        int bulkSize = buf.readVarInt();
                        int rawId = lastBulkLastRawId + startDelta - 1;
                        for (int m = 0; m < bulkSize; m++) {
                            rawId++;
                            ids.put(Identifier.fromNamespaceAndPath(idNamespace, buf.readUtf()), rawId);
                        }
                        lastBulkLastRawId = rawId;
                    }
                }
                Identifier registryId = Identifier.fromNamespaceAndPath(registryNamespace, registryPath);
                result.put(registryId, new SyncedRegistry(registryId, optional, ids));
            }
        }
        return result;
    }

    private static String expandNamespace(String namespace) {
        return namespace.isEmpty() ? Identifier.DEFAULT_NAMESPACE : namespace;
    }

    /**
     * @return null on success, else the disconnect reason
     */
    static Component apply(Map<Identifier, SyncedRegistry> synced) {
        List<Identifier> missingRegistries = new ArrayList<>();
        Map<Identifier, List<Identifier>> missingEntries = new TreeMap<>();
        Map<Identifier, RegistrySnapshot> remaps = new LinkedHashMap<>();
        int checkedEntries = 0;

        for (SyncedRegistry syncedRegistry : synced.values()) {
            Registry<?> registry = BuiltInRegistries.REGISTRY.getValue(syncedRegistry.id());
            if (registry == null) {
                if (syncedRegistry.optional()) {
                    Minehop.LOGGER.info("Fabric registry sync: ignoring unknown optional registry {}", syncedRegistry.id());
                } else {
                    missingRegistries.add(syncedRegistry.id());
                }
                continue;
            }
            boolean mismatch = false;
            for (Map.Entry<Identifier, Integer> entry : syncedRegistry.ids().entrySet()) {
                checkedEntries++;
                if (!registry.containsKey(entry.getKey())) {
                    missingEntries.computeIfAbsent(syncedRegistry.id(), k -> new ArrayList<>()).add(entry.getKey());
                } else if (rawIdOf(registry, entry.getKey()) != entry.getValue()) {
                    mismatch = true;
                }
            }
            if (mismatch) {
                remaps.put(syncedRegistry.id(), remapSnapshot(registry, syncedRegistry.ids()));
            }
        }

        if (!missingRegistries.isEmpty() || !missingEntries.isEmpty()) {
            missingRegistries.forEach(id -> Minehop.LOGGER.error("Fabric registry sync: the server has registry {}, this client does not", id));
            missingEntries.forEach((registry, ids) -> ids.forEach(id -> Minehop.LOGGER.error("Fabric registry sync: the server has {} in {}, this client does not", id, registry)));
            return missingContentMessage(missingRegistries, missingEntries);
        }

        if (remaps.isEmpty()) {
            Minehop.LOGGER.info("Fabric registry sync: {} registries, {} entries, raw ids identical to this client ({})",
                    synced.size(), checkedEntries, synced.keySet());
            return null;
        }

        Minehop.LOGGER.warn("Fabric registry sync: raw ids differ from the server in {}; remapping this client's registries to the server's ids", remaps.keySet());
        try {
            Set<ResourceKey<?>> unknown = RegistryManager.applySnapshot(remaps, false);
            if (!unknown.isEmpty()) {
                // Cannot happen: every synced entry was checked above.
                return Component.literal("Minehop (NeoForge): registry remapping failed, unknown entries " + unknown);
            }
        } catch (RuntimeException e) {
            Minehop.LOGGER.error("Fabric registry sync: remapping failed", e);
            return Component.literal("Minehop (NeoForge): registry remapping failed: " + e);
        }
        Minehop.LOGGER.info("Fabric registry sync: {} registries, {} entries, remapped {}", synced.size(), checkedEntries, remaps.keySet());
        return null;
    }

    private static <T> int rawIdOf(Registry<T> registry, Identifier id) {
        return registry.getId(registry.getValue(id));
    }

    /**
     * Builds a NeoForge registry snapshot holding the server's raw ids, followed by the entries only this client has
     * (in their current order), and the registry's current aliases.
     */
    private static <T> RegistrySnapshot remapSnapshot(Registry<T> registry, Map<Identifier, Integer> serverIds) {
        TreeMap<Integer, Identifier> ids = new TreeMap<>();
        int nextId = 0;
        for (Map.Entry<Identifier, Integer> entry : serverIds.entrySet()) {
            ids.put(entry.getValue(), entry.getKey());
            nextId = Math.max(nextId, entry.getValue() + 1);
        }
        for (T value : registry) {
            Identifier key = registry.getKey(value);
            if (key != null && !serverIds.containsKey(key)) {
                ids.put(nextId++, key);
            }
        }
        Map<Identifier, Identifier> aliases = new RegistrySnapshot(registry, false).getAliases();

        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            // RegistrySnapshot.STREAM_CODEC: map<VarInt, Identifier> ids, map<Identifier, Identifier> aliases
            buf.writeVarInt(ids.size());
            ids.forEach((rawId, key) -> {
                buf.writeVarInt(rawId);
                buf.writeIdentifier(key);
            });
            buf.writeVarInt(aliases.size());
            aliases.forEach((from, to) -> {
                buf.writeIdentifier(from);
                buf.writeIdentifier(to);
            });
            return RegistrySnapshot.STREAM_CODEC.decode(buf);
        } finally {
            buf.release();
        }
    }

    private static Component missingContentMessage(List<Identifier> missingRegistries, Map<Identifier, List<Identifier>> missingEntries) {
        Set<String> namespaces = new TreeSet<>();
        missingRegistries.forEach(id -> namespaces.add(id.getNamespace()));
        missingEntries.values().forEach(ids -> ids.forEach(id -> namespaces.add(id.getNamespace())));
        int entryCount = missingEntries.values().stream().mapToInt(List::size).sum();

        MutableComponent text = Component.literal("This server has content that this client does not have:\n");
        if (!missingRegistries.isEmpty()) {
            text.append(Component.literal(missingRegistries.size() + " unknown registr" + (missingRegistries.size() == 1 ? "y" : "ies") + "\n"));
        }
        if (entryCount > 0) {
            text.append(Component.literal(entryCount + " unknown registry entr" + (entryCount == 1 ? "y" : "ies") + "\n"));
        }
        text.append(Component.literal("\nMods (namespaces) involved:\n"));
        int shown = 0;
        for (String namespace : namespaces) {
            if (shown++ == 4) {
                text.append(Component.literal("and " + (namespaces.size() - 4) + " more\n"));
                break;
            }
            text.append(Component.literal(namespace + "\n").withStyle(ChatFormatting.YELLOW));
        }
        text.append(Component.literal("\nInstall the same mods as the server (NeoForge versions) to join.").withStyle(ChatFormatting.GOLD));
        return text;
    }

    record SyncedRegistry(Identifier id, boolean optional, Map<Identifier, Integer> ids) {
    }

    /**
     * {@code fabric:registry/sync}: the whole registry map, no length prefix (the payload is the rest of the packet).
     */
    public record SyncPayload(byte[] data) implements CustomPacketPayload {
        public static final Type<SyncPayload> TYPE = new Type<>(SYNC_ID);
        public static final StreamCodec<FriendlyByteBuf, SyncPayload> STREAM_CODEC = StreamCodec.of(
                (buf, payload) -> buf.writeBytes(payload.data()),
                buf -> {
                    byte[] bytes = new byte[buf.readableBytes()];
                    buf.readBytes(bytes);
                    return new SyncPayload(bytes);
                });

        @Override
        public Type<SyncPayload> type() {
            return TYPE;
        }
    }

    /**
     * {@code fabric:registry/sync/complete}: empty.
     */
    public enum CompletePayload implements CustomPacketPayload {
        INSTANCE;

        public static final Type<CompletePayload> TYPE = new Type<>(COMPLETE_ID);
        public static final StreamCodec<FriendlyByteBuf, CompletePayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

        @Override
        public Type<CompletePayload> type() {
            return TYPE;
        }
    }
}
