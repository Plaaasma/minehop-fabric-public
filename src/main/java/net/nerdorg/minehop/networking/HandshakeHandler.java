package net.nerdorg.minehop.networking;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.anticheat.AutoDisconnect;
import net.nerdorg.minehop.commands.SpectateCommands;
import net.nerdorg.minehop.data.DataManager;

import java.util.HashMap;
import java.util.List;

public class HandshakeHandler {
    private static HashMap<String, Integer> waitingForShake = new HashMap<>();

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(((server) -> {
            for (String playerName : waitingForShake.keySet()) {
                if (server.getTicks() > waitingForShake.get(playerName) + 60) {
                    ServerPlayerEntity serverPlayerEntity = server.getPlayerManager().getPlayer(playerName);
                    if (serverPlayerEntity != null) {
                        serverPlayerEntity.networkHandler.disconnect(Text.of("Please install/update version " + Minehop.MOD_VERSION_STRING + " of the Minehop mod before joining this server."));
                    }
                }
            }
        }));

        ServerPlayConnectionEvents.JOIN.register(((networkHandler, sender, server) -> {
            waitingForShake.put(networkHandler.player.getNameForScoreboard(), server.getTicks());
        }));

        registerReceivers();
    }

    private static void registerReceivers() {
        ServerPlayNetworking.registerGlobalReceiver(ModMessages.HANDSHAKE_ID, (server, player, handler, buf, responseSender) -> {
            int mod_version = buf.readInt();
            if (mod_version == Minehop.MOD_VERSION) {
                System.out.println("Validated " + player.getNameForScoreboard());
                waitingForShake.remove(player.getNameForScoreboard());
            }
        });
    }
}
