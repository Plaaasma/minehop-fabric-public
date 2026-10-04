package net.nerdorg.minehop.fabric.platform;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.nerdorg.minehop.platform.services.IEventHelper;

/**
 * Each method registers the listener on the Fabric API event the common code used to register on directly.
 */
public class FabricEventHelper implements IEventHelper {

    @Override
    public void onServerStarting(ServerListener listener) {
        ServerLifecycleEvents.SERVER_STARTING.register(listener::onServer);
    }

    @Override
    public void onServerStarted(ServerListener listener) {
        ServerLifecycleEvents.SERVER_STARTED.register(listener::onServer);
    }

    @Override
    public void onServerStopping(ServerListener listener) {
        ServerLifecycleEvents.SERVER_STOPPING.register(listener::onServer);
    }

    @Override
    public void onServerStopped(ServerListener listener) {
        ServerLifecycleEvents.SERVER_STOPPED.register(listener::onServer);
    }

    @Override
    public void onServerTickStart(ServerListener listener) {
        ServerTickEvents.START_SERVER_TICK.register(listener::onServer);
    }

    @Override
    public void onServerTickEnd(ServerListener listener) {
        ServerTickEvents.END_SERVER_TICK.register(listener::onServer);
    }

    @Override
    public void onServerLevelLoad(ServerLevelListener listener) {
        ServerWorldEvents.LOAD.register(listener::onLevel);
    }

    @Override
    public void onServerLevelUnload(ServerLevelListener listener) {
        ServerWorldEvents.UNLOAD.register(listener::onLevel);
    }

    @Override
    public void onRegisterCommands(CommandRegistrationListener listener) {
        CommandRegistrationCallback.EVENT.register(listener::register);
    }

    @Override
    public void onPlayerRespawn(PlayerRespawnListener listener) {
        ServerPlayerEvents.AFTER_RESPAWN.register(listener::afterRespawn);
    }

    @Override
    public void onEntityLoad(EntityLoadListener listener) {
        ServerEntityEvents.ENTITY_LOAD.register(listener::onLoad);
    }

    @Override
    public void onAllowDamage(AllowDamageListener listener) {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register(listener::allowDamage);
    }

    @Override
    public void onBeforeBlockBreak(BeforeBlockBreakListener listener) {
        PlayerBlockBreakEvents.BEFORE.register(listener::beforeBlockBreak);
    }

    @Override
    public void onUseBlock(UseBlockListener listener) {
        UseBlockCallback.EVENT.register(listener::interact);
    }

    @Override
    public void onUseItem(UseItemListener listener) {
        UseItemCallback.EVENT.register(listener::interact);
    }

    @Override
    public void onUseEntity(UseEntityListener listener) {
        UseEntityCallback.EVENT.register(listener::interact);
    }

    @Override
    public void onGameMessage(GameMessageListener listener) {
        ServerMessageEvents.GAME_MESSAGE.register(listener::onGameMessage);
    }
}
