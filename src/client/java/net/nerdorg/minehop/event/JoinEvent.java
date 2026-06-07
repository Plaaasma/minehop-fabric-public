package net.nerdorg.minehop.event;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.MinehopClient;
import net.nerdorg.minehop.networking.ClientPacketHandler;
import net.nerdorg.minehop.util.Logger;
import org.apache.commons.logging.Log;

public class JoinEvent {
    public static void register() {
        ClientPlayConnectionEvents.JOIN.register((networkHandler, sender, client) -> {
            Minehop.receivedConfig = false;
            Logger.logSuccess(client.player, "Use /hide self to toggle showing your hotbar and hand while still showing other HUD elements.");
            ClientPacketHandler.sendHandshake();
        });
    }
}
