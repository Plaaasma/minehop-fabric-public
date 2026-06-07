package net.nerdorg.minehop.networking;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.config.ConfigWrapper;
import net.nerdorg.minehop.config.MinehopConfig;
import net.nerdorg.minehop.networking.payloads.HandshakeIDPayload;

import java.util.HashMap;
import java.util.Iterator;
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
                    Iterator<Map.Entry<UUID, Integer>> iterator = waitingForShake.entrySet().iterator();
                    while (iterator.hasNext()) {
                        Map.Entry<UUID, Integer> entry = iterator.next();
                        if (entry == null) {
                            iterator.remove();
                            continue;
                        }
                        UUID playerUuid = entry.getKey();
                        Integer joinTick = entry.getValue();
                        ServerPlayerEntity serverPlayerEntity = server.getPlayerManager().getPlayer(playerUuid);
                        if (serverPlayerEntity == null) {
                            iterator.remove();
                            continue;
                        }
                        if (joinTick != null && server.getTicks() > joinTick + 60) {
                            serverPlayerEntity.networkHandler.disconnect(Text.of("Please install/update to at least version " + Minehop.MOD_VERSION_STRING + " of the Minehop mod before joining this server."));
                            iterator.remove();
                        }
                    }
                }
            }
        }));

        ServerPlayConnectionEvents.JOIN.register(((networkHandler, sender, server) -> {
            waitingForShake.put(networkHandler.player.getUuid(), server.getTicks());
        }));

        ServerPlayConnectionEvents.DISCONNECT.register(((networkHandler, server) -> {
            waitingForShake.remove(networkHandler.player.getUuid());
        }));

        registerReceivers();
    }

    private static void registerReceivers() {
        ServerPlayNetworking.registerGlobalReceiver(HandshakeIDPayload.ID, (payload, ctx) -> {
            int mod_version = payload.mod_version();
            ServerPlayerEntity player = ctx.player();
            ctx.server().execute(() -> {
                if (player == null) {
                    return;
                }
                if (mod_version == Minehop.MOD_VERSION) {
                    waitingForShake.remove(player.getUuid());
                }
            });
        });
    }
}
