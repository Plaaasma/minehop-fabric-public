package net.nerdorg.minehop.event;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.nerdorg.minehop.networking.ClientPacketHandler;

public class JoinEvent {
    public static void register() {
        ClientPlayConnectionEvents.JOIN.register((networkHandler, sender, client) -> {
            ClientPacketHandler.sendHandshake();
        });
    }
}
