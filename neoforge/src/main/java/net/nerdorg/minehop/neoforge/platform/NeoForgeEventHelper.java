package net.nerdorg.minehop.neoforge.platform;

import net.nerdorg.minehop.platform.services.IEventHelper;

/**
 * PHASE 3 TODO: NeoForge implementation of {@link IEventHelper}. All events are on {@code NeoForge.EVENT_BUS}.
 */
public class NeoForgeEventHelper implements IEventHelper {

    @Override
    public void onServerStarting(ServerListener listener) {
        // TODO(phase 3): net.neoforged.neoforge.event.server.ServerStartingEvent -> listener.onServer(event.getServer())
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
        // TODO(phase 3): net.neoforged.neoforge.event.tick.ServerTickEvent.Pre -> event.getServer()
        throw Todo.notImplemented("IEventHelper.onServerTickStart");
    }

    @Override
    public void onServerTickEnd(ServerListener listener) {
        // TODO(phase 3): ServerTickEvent.Post -> event.getServer()
        throw Todo.notImplemented("IEventHelper.onServerTickEnd");
    }

    @Override
    public void onServerLevelLoad(ServerLevelListener listener) {
        // TODO(phase 3): net.neoforged.neoforge.event.level.LevelEvent.Load; only if event.getLevel() is a ServerLevel:
        //  listener.onLevel(level.getServer(), level)
        throw Todo.notImplemented("IEventHelper.onServerLevelLoad");
    }

    @Override
    public void onServerLevelUnload(ServerLevelListener listener) {
        // TODO(phase 3): LevelEvent.Unload, ServerLevel only (see onServerLevelLoad)
        throw Todo.notImplemented("IEventHelper.onServerLevelUnload");
    }

    @Override
    public void onRegisterCommands(CommandRegistrationListener listener) {
        // TODO(phase 3): net.neoforged.neoforge.event.RegisterCommandsEvent ->
        //  listener.register(event.getDispatcher(), event.getBuildContext(), event.getCommandSelection())
        throw Todo.notImplemented("IEventHelper.onRegisterCommands");
    }

    @Override
    public void onPlayerRespawn(PlayerRespawnListener listener) {
        // TODO(phase 3): PlayerEvent.Clone (fires inside PlayerList#respawn) -> remember event.getOriginal() for the new
        //  player; PlayerEvent.PlayerRespawnEvent -> listener.afterRespawn(old, (ServerPlayer) event.getEntity(),
        //  event.isEndConquered()). Server side only.
        throw Todo.notImplemented("IEventHelper.onPlayerRespawn");
    }

    @Override
    public void onEntityLoad(EntityLoadListener listener) {
        // TODO(phase 3): net.neoforged.neoforge.event.entity.EntityJoinLevelEvent, ServerLevel only
        //  (fires before the entity is added; Fabric's ENTITY_LOAD fires after - nothing in Minehop depends on it yet)
        throw Todo.notImplemented("IEventHelper.onEntityLoad");
    }

    @Override
    public void onAllowDamage(AllowDamageListener listener) {
        // TODO(phase 3): net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent:
        //  if (!listener.allowDamage(event.getEntity(), event.getSource(), event.getAmount())) event.setCanceled(true)
        //  (server side only, like Fabric's ServerLivingEntityEvents)
        throw Todo.notImplemented("IEventHelper.onAllowDamage");
    }

    @Override
    public void onBeforeBlockBreak(BeforeBlockBreakListener listener) {
        // TODO(phase 3): net.neoforged.neoforge.event.level.BlockEvent.BreakEvent: if (!listener.beforeBlockBreak(
        //  (Level) event.getLevel(), event.getPlayer(), event.getPos(), event.getState(),
        //  event.getLevel().getBlockEntity(event.getPos()))) event.setCanceled(true)
        throw Todo.notImplemented("IEventHelper.onBeforeBlockBreak");
    }

    @Override
    public void onUseBlock(UseBlockListener listener) {
        // TODO(phase 3): PlayerInteractEvent.RightClickBlock: result = listener.interact(event.getEntity(),
        //  event.getLevel(), event.getHand(), event.getHitVec()); if (result != InteractionResult.PASS) {
        //  event.setCanceled(true); event.setCancellationResult(result); }
        throw Todo.notImplemented("IEventHelper.onUseBlock");
    }

    @Override
    public void onUseItem(UseItemListener listener) {
        // TODO(phase 3): PlayerInteractEvent.RightClickItem, same cancel pattern as onUseBlock
        throw Todo.notImplemented("IEventHelper.onUseItem");
    }

    @Override
    public void onUseEntity(UseEntityListener listener) {
        // TODO(phase 3): PlayerInteractEvent.EntityInteractSpecific (hit result = new EntityHitResult(target,
        //  event.getLocalPos().add(target.position()))) and PlayerInteractEvent.EntityInteract (hit result null); same
        //  cancel pattern as onUseBlock. Fabric calls the callback once per interaction: avoid double invocation.
        throw Todo.notImplemented("IEventHelper.onUseEntity");
    }

    @Override
    public void onGameMessage(GameMessageListener listener) {
        // TODO(phase 3): no NeoForge event. Add a NeoForge-only mixin (minehop.neoforge.mixins.json) injecting at the
        //  TAIL of PlayerList#broadcastSystemMessage(Component, Function<ServerPlayer, Component>, boolean) that calls
        //  the listeners with (server, message, overlay). (Minehop's only listener is currently empty.)
        throw Todo.notImplemented("IEventHelper.onGameMessage");
    }
}
