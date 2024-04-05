package net.nerdorg.minehop.antispam;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.network.message.SignedMessage;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class SpamManager {
    private static HashMap<String, List<SignedMessage>> messageMap = new HashMap<>();
    private static HashMap<String, List<SignedMessage>> commandMap = new HashMap<>();

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register((server -> {
            if (server.getTicks() % 20 == 0) {
                messageMap = new HashMap<>();
            }
        }));

        ServerMessageEvents.CHAT_MESSAGE.register((signedMessage, serverPlayerEntity, parameters) -> {
            if (messageMap.containsKey(serverPlayerEntity.getNameForScoreboard())) {
                List<SignedMessage> signedMessages = messageMap.get(serverPlayerEntity.getNameForScoreboard());
                signedMessages.add(signedMessage);
                messageMap.put(serverPlayerEntity.getNameForScoreboard(), signedMessages);
            }
            else {
                List<SignedMessage> signedMessages = new ArrayList<>();
                signedMessages.add(signedMessage);
                messageMap.put(serverPlayerEntity.getNameForScoreboard(), signedMessages);
            }

            List<SignedMessage> signedMessages = messageMap.get(serverPlayerEntity.getNameForScoreboard());
            if (signedMessages.size() > 3) {
                messageMap.remove(serverPlayerEntity.getNameForScoreboard());
                serverPlayerEntity.networkHandler.disconnect(Text.literal("Please do not send more than 3 messages per second."));
            }
        });

        ServerMessageEvents.COMMAND_MESSAGE.register((signedMessage, commandSource, parameters) -> {
            ServerPlayerEntity serverPlayerEntity = commandSource.getPlayer();
            if (serverPlayerEntity != null) {
                if (commandMap.containsKey(serverPlayerEntity.getNameForScoreboard())) {
                    List<SignedMessage> signedMessages = commandMap.get(serverPlayerEntity.getNameForScoreboard());
                    signedMessages.add(signedMessage);
                    commandMap.put(serverPlayerEntity.getNameForScoreboard(), signedMessages);
                } else {
                    List<SignedMessage> signedMessages = new ArrayList<>();
                    signedMessages.add(signedMessage);
                    commandMap.put(serverPlayerEntity.getNameForScoreboard(), signedMessages);
                }

                List<SignedMessage> signedMessages = commandMap.get(serverPlayerEntity.getNameForScoreboard());
                if (signedMessages.size() > 2) {
                    commandMap.remove(serverPlayerEntity.getNameForScoreboard());
                    serverPlayerEntity.networkHandler.disconnect(Text.literal("Please do not send more than 2 commands per second."));
                }
            }
        });
    }
}
