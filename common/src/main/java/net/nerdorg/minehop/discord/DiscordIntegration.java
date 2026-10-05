package net.nerdorg.minehop.discord;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.nerdorg.minehop.config.ConfigWrapper;
import net.nerdorg.minehop.config.MinehopConfig;

public class DiscordIntegration {
    public static void sendRecordToDiscord(String formattedRecord) {
        MinehopConfig config = ConfigWrapper.config;

        if (config != null) {
            if (!config.bot_token.equals("") && !config.record_channel.equals("")) {
                new Thread(() -> {
                    try {
                        JDABuilder builder = JDABuilder.createDefault(config.bot_token);
                        JDA jda = builder.build();
                        jda.awaitReady(); // Wait for the bot to be fully logged in.

                        // Assuming you know the channel ID you want to send a message to
                        TextChannel channel = jda.getTextChannelById(config.record_channel);
                        if (channel != null) {
                            channel.sendMessage(formattedRecord).queue();
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }).start();
            }
        }
    }
}
