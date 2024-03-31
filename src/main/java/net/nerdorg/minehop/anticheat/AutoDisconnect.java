package net.nerdorg.minehop.anticheat;

import net.minecraft.server.network.ServerPlayerEntity;

import java.util.HashMap;

public class AutoDisconnect {

    public static HashMap<ServerPlayerEntity, CancelableTimer> PlayerDisconnectTimers = new HashMap<>();

    public static void startPlayerTimer(ServerPlayerEntity player) {
        PlayerDisconnectTimers.put(player, new CancelableTimer(player));
    }
    public static void stopPlayerTimer(ServerPlayerEntity player) {
        PlayerDisconnectTimers.get(player).cancel();
        PlayerDisconnectTimers.remove(player);
    }
}
