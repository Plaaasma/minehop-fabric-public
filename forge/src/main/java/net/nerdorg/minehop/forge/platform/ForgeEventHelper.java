package net.nerdorg.minehop.forge.platform;

import net.nerdorg.minehop.platform.services.IEventHelper;

/**
 * PHASE 3 TODO: Forge implementation of {@link IEventHelper}. All events are on {@code MinecraftForge.EVENT_BUS}
 * (EventBus 6 in Forge 54).
 */
public class ForgeEventHelper implements IEventHelper {

    @Override
    public void onServerStarting(ServerListener listener) {
        // TODO(phase 3): net.minecraftforge.event.server.ServerStartingEvent -> listener.onServer(event.getServer())
        throw Todo.notImplemented("IEventHelper.onServerStarting");
    }

    @Override
    public void onServerStarted(ServerListener listener) {
        // TODO(phase 3): ServerStartedEvent
        throw Todo.notImplemented("IEventHelper.onServerStarted");
    }

    @Override
    public void onServerStopping(ServerListener listener) {
        // TODO(phase 3): ServerStoppingEvent
        throw Todo.notImplemented("IEventHelper.onServerStopping");
    }

    @Override
    public void onServerStopped(ServerListener listener) {
        // TODO(phase 3): ServerStoppedEvent
        throw Todo.notImplemented("IEventHelper.onServerStopped");
    }

    @Override
    public void onServerTickStart(ServerListener listener) {
        // TODO(phase 3): net.minecraftforge.event.TickEvent.ServerTickEvent.Pre -> event.getServer()
        throw Todo.notImplemented("IEventHelper.onServerTickStart");
    }

    @Override
    public void onServerTickEnd(ServerListener listener) {
        // TODO(phase 3): TickEvent.ServerTickEvent.Post -> event.getServer()
        throw Todo.notImplemented("IEventHelper.onServerTickEnd");
    }

    @Override
    public void onServerLevelLoad(ServerLevelListener listener) {
        // TODO(phase 3): net.minecraftforge.event.level.LevelEvent.Load, ServerLevel only -> (level.getServer(), level)
        throw Todo.notImplemented("IEventHelper.onServerLevelLoad");
    }

    @Override
    public void onServerLevelUnload(ServerLevelListener listener) {
        // TODO(phase 3): LevelEvent.Unload, ServerLevel only
        throw Todo.notImplemented("IEventHelper.onServerLevelUnload");
    }

    @Override
    public void onRegisterCommands(CommandRegistrationListener listener) {
        // TODO(phase 3): net.minecraftforge.event.RegisterCommandsEvent ->
        //  listener.register(event.getDispatcher(), event.getBuildContext(), event.getCommandSelection())
        throw Todo.notImplemented("IEventHelper.onRegisterCommands");
    }

    @Override
    public void onPlayerRespawn(PlayerRespawnListener listener) {
        // TODO(phase 3): PlayerEvent.Clone (remember getOriginal()) + PlayerEvent.PlayerRespawnEvent
        //  (alive = isEndConquered()), server side only - same as NeoForge.
        throw Todo.notImplemented("IEventHelper.onPlayerRespawn");
    }

    @Override
    public void onEntityLoad(EntityLoadListener listener) {
        // TODO(phase 3): net.minecraftforge.event.entity.EntityJoinLevelEvent, ServerLevel only
        throw Todo.notImplemented("IEventHelper.onEntityLoad");
    }

    @Override
    public void onAllowDamage(AllowDamageListener listener) {
        // TODO(phase 3): net.minecraftforge.event.entity.living.LivingAttackEvent (cancel when the listener returns false)
        throw Todo.notImplemented("IEventHelper.onAllowDamage");
    }

    @Override
    public void onBeforeBlockBreak(BeforeBlockBreakListener listener) {
        // TODO(phase 3): net.minecraftforge.event.level.BlockEvent.BreakEvent (cancel when false)
        throw Todo.notImplemented("IEventHelper.onBeforeBlockBreak");
    }

    @Override
    public void onUseBlock(UseBlockListener listener) {
        // TODO(phase 3): PlayerInteractEvent.RightClickBlock (non-PASS -> setCanceled(true) + setCancellationResult(result))
        throw Todo.notImplemented("IEventHelper.onUseBlock");
    }

    @Override
    public void onUseItem(UseItemListener listener) {
        // TODO(phase 3): PlayerInteractEvent.RightClickItem (same cancel pattern)
        throw Todo.notImplemented("IEventHelper.onUseItem");
    }

    @Override
    public void onUseEntity(UseEntityListener listener) {
        // TODO(phase 3): PlayerInteractEvent.EntityInteractSpecific / EntityInteract (same pattern; invoke once per click)
        throw Todo.notImplemented("IEventHelper.onUseEntity");
    }

    @Override
    public void onGameMessage(GameMessageListener listener) {
        // TODO(phase 3): no Forge event; forge-only mixin at the TAIL of
        //  PlayerList#broadcastSystemMessage(Component, Function<ServerPlayer, Component>, boolean).
        throw Todo.notImplemented("IEventHelper.onGameMessage");
    }
}
