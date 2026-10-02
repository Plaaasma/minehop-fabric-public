package net.nerdorg.minehop.networking;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
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

public class HandshakeHandler {
    private static final HashMap<UUID, Integer> waitingForShake = new HashMap<>();
    private static boolean registered = false;
    public static void register() {
        if (registered){return;}
        registered = true;
        ServerTickEvents.END_SERVER_TICK.register(((server) -> {
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
                            serverPlayerEntity.connection.disconnect(Component.nullToEmpty("Please install/update to at least version " + Minehop.MOD_VERSION_STRING + " of the Minehop mod before joining this server."));
                        }
                    }
                }
            }
        }));

        ServerPlayConnectionEvents.JOIN.register(((networkHandler, sender, server) -> {
            waitingForShake.put(networkHandler.player.getUUID(), server.getTickCount());
        }));

        ServerPlayConnectionEvents.DISCONNECT.register(((networkHandler, server) -> {
            waitingForShake.remove(networkHandler.player.getUUID());
        }));

        registerReceivers();
    }

    private static void registerReceivers() {
        ServerPlayNetworking.registerGlobalReceiver(HandshakeIDPayload.ID, (payload, ctx) -> {
            int mod_version = payload.mod_version();
            ServerPlayer player = ctx.player();
            ctx.server().execute(() -> {
                if (player == null) {
                    return;
                }
                if (mod_version == Minehop.MOD_VERSION) {
                    waitingForShake.remove(player.getUUID());
                }
            });
        });
    }
}
