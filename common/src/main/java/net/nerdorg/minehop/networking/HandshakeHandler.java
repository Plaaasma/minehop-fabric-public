package net.nerdorg.minehop.networking;

import net.nerdorg.minehop.platform.Services;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.config.ConfigWrapper;
import net.nerdorg.minehop.config.MinehopConfig;
import net.nerdorg.minehop.networking.payloads.HandshakeIDPayload;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class HandshakeHandler {
    private static final HashMap<UUID, Integer> waitingForShake = new HashMap<>();
    /**
     * The Minehop version each connected client announced in its handshake (MOD_VERSION, e.g. 11700), until it
     * disconnects. A player without an entry has not sent one (yet): treat them as the oldest accepted client.
     */
    private static final Map<UUID, Integer> CLIENT_VERSIONS = new ConcurrentHashMap<>();

    /** The Minehop version the player's client announced, or 0 if it hasn't (yet). */
    public static int clientVersion(ServerPlayer player) {
        if (player == null) {
            return 0;
        }
        Integer version = CLIENT_VERSIONS.get(player.getUUID());
        return version == null ? 0 : version;
    }

    /**
     * True if the player's client plays replays itself (1.1.7+): only then may it be sent the replay streaming
     * payloads, which older clients don't know. Old clients keep watching the server's ghost entities.
     */
    public static boolean supportsClientReplays(ServerPlayer player) {
        return clientVersion(player) >= Minehop.CLIENT_REPLAY_MOD_VERSION;
    }

    private static boolean registered = false;
    public static void register() {
        if (registered){return;}
        registered = true;
        Services.EVENTS.onServerTickEnd(((server) -> {
            MinehopConfig config = ConfigWrapper.config;
            if (config != null) {
                if (config.client_validation) {
                    // Collect players to disconnect and do it AFTER the loop. disconnect() fires the
                    // DISCONNECT event synchronously, whose handler removes from waitingForShake —
                    // mutating the map mid-iteration would throw ConcurrentModificationException and
                    // crash the server tick. Remove via the iterator here; disconnect outside.
                    List<ServerPlayer> toDisconnect = null;
                    Iterator<Map.Entry<UUID, Integer>> iterator = waitingForShake.entrySet().iterator();
                    while (iterator.hasNext()) {
                        Map.Entry<UUID, Integer> entry = iterator.next();
                        if (entry == null) {
                            iterator.remove();
                            continue;
                        }
                        UUID playerUuid = entry.getKey();
                        Integer joinTick = entry.getValue();
                        ServerPlayer serverPlayerEntity = server.getPlayerList().getPlayer(playerUuid);
                        if (serverPlayerEntity == null) {
                            iterator.remove();
                            continue;
                        }
                        if (joinTick != null && server.getTickCount() > joinTick + 60) {
                            iterator.remove();
                            if (toDisconnect == null) {
                                toDisconnect = new ArrayList<>();
                            }
                            toDisconnect.add(serverPlayerEntity);
                        }
                    }
                    if (toDisconnect != null) {
                        for (ServerPlayer serverPlayerEntity : toDisconnect) {
                            serverPlayerEntity.connection.disconnect(Component.nullToEmpty("Please install/update to at least version " + Minehop.MIN_CLIENT_MOD_VERSION_STRING + " of the Minehop mod before joining this server."));
                        }
                    }
                }
            }
        }));

        Services.NETWORK.onPlayConnectionJoin(((networkHandler, server) -> {
            waitingForShake.put(networkHandler.player.getUUID(), server.getTickCount());
        }));

        Services.NETWORK.onPlayConnectionDisconnect(((networkHandler, server) -> {
            waitingForShake.remove(networkHandler.player.getUUID());
            CLIENT_VERSIONS.remove(networkHandler.player.getUUID());
        }));

        registerReceivers();
    }

    private static void registerReceivers() {
        Services.NETWORK.registerServerReceiver(HandshakeIDPayload.ID, (payload, ctx) -> {
            int mod_version = payload.mod_version();
            ServerPlayer player = ctx.player();
            ctx.server().execute(() -> {
                if (player == null) {
                    return;
                }
                if (player.connection == null || player.hasDisconnected()) {
                    return;
                }
                // Kept even below the minimum (client_validation off): gating only ever looks for newer versions.
                Integer previous = CLIENT_VERSIONS.put(player.getUUID(), mod_version);
                if (mod_version >= Minehop.MIN_CLIENT_MOD_VERSION) {
                    waitingForShake.remove(player.getUUID());
                }
                if (previous == null && supportsClientReplays(player)) {
                    net.nerdorg.minehop.replays.ReplayStreaming.onClientReady(player);
                }
            });
        });
    }
}
