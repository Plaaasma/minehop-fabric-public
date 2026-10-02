package net.nerdorg.minehop.entity;

import me.shedaniel.autoconfig.AutoConfig;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ambient.Bat;
import net.nerdorg.minehop.config.MinehopConfig;
import net.nerdorg.minehop.networking.PacketHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class MobManager {
    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register((server) -> {
            for (Entity entity : server.overworld().getAllEntities()) {
                if (entity instanceof Bat) {
                    entity.kill(server.overworld());
                }
            }
        });
    }
}
