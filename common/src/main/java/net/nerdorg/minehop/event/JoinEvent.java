package net.nerdorg.minehop.event;

import net.nerdorg.minehop.platform.ClientServices;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.MinehopClient;
import net.nerdorg.minehop.networking.ClientPacketHandler;
import net.nerdorg.minehop.util.Logger;

public class JoinEvent {
    public static void register() {
        ClientServices.NETWORK.onConnectionJoin((networkHandler, client) -> {
            Minehop.receivedConfig = false;
            Logger.logSuccess(client.player, "Use /hide self to toggle showing your hotbar and hand while still showing other HUD elements.");
            ClientPacketHandler.sendHandshake();
        });
    }
}
