package net.nerdorg.minehop.networking;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.world.World;
import net.minecraft.world.dimension.DimensionOptions;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.data.DataManager;

import java.util.ArrayList;
import java.util.List;

public class JoinLeaveManager {
    public static void register() {
        ServerMessageEvents.GAME_MESSAGE.register(((server, message, overlay) -> {

        }));

        ServerPlayConnectionEvents.JOIN.register(((networkHandler, sender, server) -> {
            DataManager.MapData mapData = DataManager.getMap("spawn");
            if (mapData != null) {
                networkHandler.player.teleport(
                        server.getOverworld(),
                        mapData.x,
                        mapData.y,
                        mapData.z,
                        (float) mapData.yrot,
                        (float) mapData.xrot
                );
            }
        }));
    }
}
