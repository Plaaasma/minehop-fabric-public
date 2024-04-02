package net.nerdorg.minehop.motd;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class MotdManager {
    private static List<String> randomMessages = new ArrayList<>(Arrays.asList("lolrow is a homosexual.", "Please submit maps to us in discord!", "No you can't have admin.", "Raw accel runs will show up on the cheaterboard.", "You're dumb.", "Nobody likes you."));
    private static Random random = new Random();

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register((server -> {
            if (server.getTicks() % 200 == 0) {
                server.setMotd("&2NerdOrg &7(&d&oMinehop&7) &8-> &b" + randomMessages.get(random.nextInt(0, randomMessages.size() - 1)));
            }
        }));
    }
}
