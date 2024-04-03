package net.nerdorg.minehop.anticheat;

import net.minecraft.server.network.ServerPlayerEntity;

import java.util.HashMap;

public class AutoDisconnect {

    public static HashMap<ServerPlayerEntity, CancelableTimer> PlayerDisconnectTimers = new HashMap<>();

    public static void startPlayerTimer(ServerPlayerEntity player) {
        CancelableTimer timer = new CancelableTimer(player);
        timer.start(5000, 1);
        PlayerDisconnectTimers.put(player, timer);
    }
    public static void stopPlayerTimer(ServerPlayerEntity player) {
        PlayerDisconnectTimers.get(player).cancel();
        PlayerDisconnectTimers.remove(player);
    }
}
