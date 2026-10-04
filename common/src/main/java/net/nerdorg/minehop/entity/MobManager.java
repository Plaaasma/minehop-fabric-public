package net.nerdorg.minehop.entity;

import me.shedaniel.autoconfig.AutoConfig;
import net.nerdorg.minehop.platform.Services;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ambient.Bat;
import net.nerdorg.minehop.config.MinehopConfig;
import net.nerdorg.minehop.networking.PacketHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class MobManager {
    public static void register() {
        Services.EVENTS.onServerTickEnd((server) -> {
            for (Entity entity : server.overworld().getAllEntities()) {
                if (entity instanceof Bat) {
                    entity.kill(server.overworld());
                }
            }
        });
    }
}
