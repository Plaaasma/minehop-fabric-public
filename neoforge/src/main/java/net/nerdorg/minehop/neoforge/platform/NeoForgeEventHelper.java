package net.nerdorg.minehop.neoforge.platform;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.nerdorg.minehop.platform.services.IEventHelper;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * NeoForge implementation of {@link IEventHelper}. Every listener is added to {@link NeoForge#EVENT_BUS} at the default
 * priority, so listeners of one event run in registration order (Fabric's semantics).
 */
public class NeoForgeEventHelper implements IEventHelper {
    /** {@code PlayerEvent.Clone} (old player) of the respawn in progress, keyed by the new player. Server thread only. */
    private static final Map<Player, Player> RESPAWN_ORIGINALS = new WeakHashMap<>();
    private static final List<GameMessageListener> GAME_MESSAGE_LISTENERS = new CopyOnWriteArrayList<>();
    private static boolean respawnCloneListenerAdded;

    @Override
    public void onServerStarting(ServerListener listener) {
        // Fabric's SERVER_STARTING fires before the levels are loaded (start of MinecraftServer#runServer); NeoForge's
        // ServerStartingEvent fires after. ServerAboutToStartEvent is the equivalent point.
        NeoForge.EVENT_BUS.addListener(ServerAboutToStartEvent.class, event -> listener.onServer(event.getServer()));
    }

    @Override
    public void onServerStarted(ServerListener listener) {
        NeoForge.EVENT_BUS.addListener(ServerStartedEvent.class, event -> listener.onServer(event.getServer()));
    }

    @Override
    public void onServerStopping(ServerListener listener) {
        NeoForge.EVENT_BUS.addListener(ServerStoppingEvent.class, event -> listener.onServer(event.getServer()));
    }

    @Override
    public void onServerStopped(ServerListener listener) {
        NeoForge.EVENT_BUS.addListener(ServerStoppedEvent.class, event -> listener.onServer(event.getServer()));
    }

    @Override
    public void onServerTickStart(ServerListener listener) {
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Pre.class, event -> listener.onServer(event.getServer()));
    }

    @Override
    public void onServerTickEnd(ServerListener listener) {
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, event -> listener.onServer(event.getServer()));
    }

    @Override
    public void onServerLevelLoad(ServerLevelListener listener) {
        NeoForge.EVENT_BUS.addListener(LevelEvent.Load.class, event -> {
            if (event.getLevel() instanceof ServerLevel level) {
                listener.onLevel(level.getServer(), level);
            }
        });
    }

    @Override
    public void onServerLevelUnload(ServerLevelListener listener) {
        NeoForge.EVENT_BUS.addListener(LevelEvent.Unload.class, event -> {
            if (event.getLevel() instanceof ServerLevel level) {
                listener.onLevel(level.getServer(), level);
            }
        });
    }

    @Override
    public void onRegisterCommands(CommandRegistrationListener listener) {
        NeoForge.EVENT_BUS.addListener(RegisterCommandsEvent.class,
                event -> listener.register(event.getDispatcher(), event.getBuildContext(), event.getCommandSelection()));
    }

    @Override
    public void onPlayerRespawn(PlayerRespawnListener listener) {
        synchronized (NeoForgeEventHelper.class) {
            if (!respawnCloneListenerAdded) {
                respawnCloneListenerAdded = true;
                // Clone fires inside PlayerList#respawn (ServerPlayer#restoreFrom), before PlayerRespawnEvent.
                NeoForge.EVENT_BUS.addListener(PlayerEvent.Clone.class, event -> {
                    if (event.getEntity() instanceof ServerPlayer) {
                        RESPAWN_ORIGINALS.put(event.getEntity(), event.getOriginal());
                    }
                });
                // Drop the entry once every respawn listener ran (lowest priority, registered once).
                NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST, PlayerEvent.PlayerRespawnEvent.class,
                        event -> RESPAWN_ORIGINALS.remove(event.getEntity()));
            }
        }
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerRespawnEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayer newPlayer) {
                Player original = RESPAWN_ORIGINALS.get(newPlayer);
                ServerPlayer oldPlayer = original instanceof ServerPlayer serverPlayer ? serverPlayer : null;
                // Fabric's "alive" = the player left the End (not a death respawn).
                listener.afterRespawn(oldPlayer, newPlayer, event.isEndConquered());
            }
        });
    }

    @Override
    public void onEntityLoad(EntityLoadListener listener) {
        NeoForge.EVENT_BUS.addListener(EntityJoinLevelEvent.class, event -> {
            if (event.getLevel() instanceof ServerLevel level) {
                listener.onLoad(event.getEntity(), level);
            }
        });
    }

    @Override
    public void onAllowDamage(AllowDamageListener listener) {
        // Fired from LivingEntity#hurtServer after the invulnerability checks, exactly where Fabric's ALLOW_DAMAGE is;
        // cancelling makes hurtServer return false.
        NeoForge.EVENT_BUS.addListener(LivingIncomingDamageEvent.class, event -> {
            if (!event.isCanceled() && !listener.allowDamage(event.getEntity(), event.getSource(), event.getAmount())) {
                event.setCanceled(true);
            }
        });
    }

    @Override
    public void onBeforeBlockBreak(BeforeBlockBreakListener listener) {
        // 26.1: BreakBlockEvent fires on both sides; Fabric's BEFORE is server-only, so only the server asks the listener
        // (and tells the client when it cancels).
        NeoForge.EVENT_BUS.addListener(BreakBlockEvent.class, event -> {
            if (event.isCanceled() || !(event.getLevel() instanceof Level level) || level.isClientSide()) {
                return;
            }
            if (!listener.beforeBlockBreak(level, event.getPlayer(), event.getPos(), event.getState(), level.getBlockEntity(event.getPos()))) {
                event.setCanceled(true);
                event.setNotifyClient(true);
            }
        });
    }

    @Override
    public void onUseBlock(UseBlockListener listener) {
        NeoForge.EVENT_BUS.addListener(PlayerInteractEvent.RightClickBlock.class, event -> {
            if (event.isCanceled()) {
                return;
            }
            InteractionResult result = listener.interact(event.getEntity(), event.getLevel(), event.getHand(), event.getHitVec());
            if (result != InteractionResult.PASS) {
                event.setCancellationResult(result);
                event.setCanceled(true);
            }
        });
    }

    @Override
    public void onUseItem(UseItemListener listener) {
        NeoForge.EVENT_BUS.addListener(PlayerInteractEvent.RightClickItem.class, event -> {
            if (event.isCanceled()) {
                return;
            }
            InteractionResult result = listener.interact(event.getEntity(), event.getLevel(), event.getHand());
            if (result != InteractionResult.PASS) {
                event.setCancellationResult(result);
                event.setCanceled(true);
            }
        });
    }

    @Override
    public void onUseEntity(UseEntityListener listener) {
        // Fabric calls UseEntityCallback from both entity interaction packets (INTERACT_AT with the hit position, then
        // INTERACT without); NeoForge fires EntityInteractSpecific and EntityInteract at the same two points.
        NeoForge.EVENT_BUS.addListener(PlayerInteractEvent.EntityInteractSpecific.class, event -> {
            if (event.isCanceled()) {
                return;
            }
            Entity target = event.getTarget();
            EntityHitResult hitResult = new EntityHitResult(target, event.getLocalPos().add(target.getX(), target.getY(), target.getZ()));
            InteractionResult result = listener.interact(event.getEntity(), event.getLevel(), event.getHand(), target, hitResult);
            if (result != InteractionResult.PASS) {
                event.setCancellationResult(result);
                event.setCanceled(true);
            }
        });
        NeoForge.EVENT_BUS.addListener(PlayerInteractEvent.EntityInteract.class, event -> {
            if (event.isCanceled()) {
                return;
            }
            InteractionResult result = listener.interact(event.getEntity(), event.getLevel(), event.getHand(), event.getTarget(), null);
            if (result != InteractionResult.PASS) {
                event.setCancellationResult(result);
                event.setCanceled(true);
            }
        });
    }

    @Override
    public void onGameMessage(GameMessageListener listener) {
        // No NeoForge event: fired by the NeoForge-only PlayerListMixin (tail of PlayerList#broadcastSystemMessage,
        // where Fabric fires ServerMessageEvents.GAME_MESSAGE).
        GAME_MESSAGE_LISTENERS.add(listener);
    }

    /**
     * Called by {@code net.nerdorg.minehop.neoforge.mixin.PlayerListMixin}.
     */
    public static void fireGameMessage(net.minecraft.server.MinecraftServer server, net.minecraft.network.chat.Component message, boolean overlay) {
        for (GameMessageListener listener : GAME_MESSAGE_LISTENERS) {
            listener.onGameMessage(server, message, overlay);
        }
    }
}
