package net.nerdorg.minehop.motd;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class MotdManager {
    private static List<String> randomMessages = new ArrayList<>(Arrays.asList("lolrow is a homosexual.",
            "Please submit maps to us in discord! (/discord)",
            "No you can't have admin.",
            "You're dumb.",
            "I love beans.",
            "Join our discord (/discord) or I'll find you and kill you.",
            "Please report any bugs in discord. (/discord)",
            "Tell all your friends about minehop..... or else.",
            "Thanks for playing on minehop!",
            "Brought to you by nerd org.",
            "How to make cookies: Preheat your oven to 375°F (190°C).\nMix the butter, sugars, and vanilla in a large bowl until creamy. Add the eggs, one at a time, beating well after each addition.\nCombine the flour, baking soda, and salt in a separate bowl. Gradually beat the dry ingredients into the butter mixture.\nStir in the chocolate chips and nuts (if using) by hand with a wooden spoon or spatula.\nDrop by rounded tablespoonfuls onto ungreased baking sheets, leaving enough space between each cookie for them to spread.\nBake for 9 to 11 minutes or until golden brown. Cool on baking sheets for 2 minutes; remove to wire racks to cool completely."));
    private static Random random = new Random();

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register((server -> {
            if (server.getTicks() % 200 == 0) {
                server.setMotd("§2NerdOrg §7(§d§oMinehop§7) §8-> §b" + randomMessages.get(random.nextInt(0, randomMessages.size() - 1)));
            }
        }));
    }
}
