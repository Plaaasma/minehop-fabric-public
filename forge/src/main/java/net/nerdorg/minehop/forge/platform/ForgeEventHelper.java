package net.nerdorg.minehop.forge.platform;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.nerdorg.minehop.platform.services.IEventHelper;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Forge implementation of {@link IEventHelper}: every listener goes on {@code MinecraftForge.EVENT_BUS} (EventBus 6),
 * at the Forge event that fires where the corresponding Fabric API event fires. The one exception is
 * {@link #onGameMessage} (no Forge event): {@code PlayerListMixin} in this module calls {@link #fireGameMessage}.
 */
public class ForgeEventHelper implements IEventHelper {
    private static final List<GameMessageListener> GAME_MESSAGE_LISTENERS = new CopyOnWriteArrayList<>();
    /** new player -> old player, from PlayerEvent.Clone until the PlayerRespawnEvent of the same respawn. */
    private static final Map<Player, Player> RESPAWN_ORIGINALS = new WeakHashMap<>();

    private static <T extends Event> void listen(Class<T> type, Consumer<T> listener) {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, type, listener);
    }

    @Override
    public void onServerStarting(ServerListener listener) {
        listen(ServerStartingEvent.class, event -> listener.onServer(event.getServer()));
    }

    @Override
    public void onServerStarted(ServerListener listener) {
        listen(ServerStartedEvent.class, event -> listener.onServer(event.getServer()));
    }

    @Override
    public void onServerStopping(ServerListener listener) {
        listen(ServerStoppingEvent.class, event -> listener.onServer(event.getServer()));
    }

    @Override
    public void onServerStopped(ServerListener listener) {
        listen(ServerStoppedEvent.class, event -> listener.onServer(event.getServer()));
    }

    @Override
    public void onServerTickStart(ServerListener listener) {
        listen(TickEvent.ServerTickEvent.Pre.class, event -> listener.onServer(event.getServer()));
    }

    @Override
    public void onServerTickEnd(ServerListener listener) {
        listen(TickEvent.ServerTickEvent.Post.class, event -> listener.onServer(event.getServer()));
    }

    @Override
    public void onServerLevelLoad(ServerLevelListener listener) {
        listen(LevelEvent.Load.class, event -> {
            if (event.getLevel() instanceof ServerLevel level) {
                listener.onLevel(level.getServer(), level);
            }
        });
    }

    @Override
    public void onServerLevelUnload(ServerLevelListener listener) {
        listen(LevelEvent.Unload.class, event -> {
            if (event.getLevel() instanceof ServerLevel level) {
                listener.onLevel(level.getServer(), level);
            }
        });
    }

    @Override
    public void onRegisterCommands(CommandRegistrationListener listener) {
        listen(RegisterCommandsEvent.class, event -> listener.register(event.getDispatcher(), event.getBuildContext(), event.getCommandSelection()));
    }

    @Override
    public void onPlayerRespawn(PlayerRespawnListener listener) {
        // Fabric's AFTER_RESPAWN hands over (old, new, alive). Forge fires PlayerEvent.Clone (new player + original)
        // and then PlayerRespawnEvent (new player, endConquered == alive) during the same PlayerList#respawn.
        listen(PlayerEvent.Clone.class, event -> {
            if (event.getEntity() instanceof ServerPlayer) {
                synchronized (RESPAWN_ORIGINALS) {
                    RESPAWN_ORIGINALS.put(event.getEntity(), event.getOriginal());
                }
            }
        });
        listen(PlayerEvent.PlayerRespawnEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayer newPlayer) {
                Player original;
                synchronized (RESPAWN_ORIGINALS) {
                    original = RESPAWN_ORIGINALS.remove(newPlayer);
                }
                listener.afterRespawn(original instanceof ServerPlayer oldPlayer ? oldPlayer : null, newPlayer, event.isEndConquered());
            }
        });
    }

    @Override
    public void onEntityLoad(EntityLoadListener listener) {
        listen(EntityJoinLevelEvent.class, event -> {
            if (event.getLevel() instanceof ServerLevel level) {
                listener.onLoad(event.getEntity(), level);
            }
        });
    }

    @Override
    public void onAllowDamage(AllowDamageListener listener) {
        listen(LivingAttackEvent.class, event -> {
            if (!event.getEntity().level().isClientSide() && !listener.allowDamage(event.getEntity(), event.getSource(), event.getAmount())) {
                event.setCanceled(true);
            }
        });
    }

    @Override
    public void onBeforeBlockBreak(BeforeBlockBreakListener listener) {
        listen(BlockEvent.BreakEvent.class, event -> {
            if (event.getLevel() instanceof Level level
                    && !listener.beforeBlockBreak(level, event.getPlayer(), event.getPos(), event.getState(), level.getBlockEntity(event.getPos()))) {
                event.setCanceled(true);
            }
        });
    }

    @Override
    public void onUseBlock(UseBlockListener listener) {
        listen(PlayerInteractEvent.RightClickBlock.class, event -> {
            InteractionResult result = listener.interact(event.getEntity(), event.getLevel(), event.getHand(), event.getHitVec());
            if (result != InteractionResult.PASS) {
                event.setCanceled(true);
                event.setCancellationResult(result);
            }
        });
    }

    @Override
    public void onUseItem(UseItemListener listener) {
        listen(PlayerInteractEvent.RightClickItem.class, event -> {
            // 1.21.1: the listener returns Fabric's InteractionResultHolder; the event takes its result.
            InteractionResult result = listener.interact(event.getEntity(), event.getLevel(), event.getHand()).getResult();
            if (result != InteractionResult.PASS) {
                event.setCanceled(true);
                event.setCancellationResult(result);
            }
        });
    }

    @Override
    public void onUseEntity(UseEntityListener listener) {
        // Fabric fires UseEntityCallback for both the "interact at" (with hit result) and the plain "interact" packet.
        listen(PlayerInteractEvent.EntityInteractSpecific.class, event -> {
            EntityHitResult hit = new EntityHitResult(event.getTarget(), event.getLocalPos().add(event.getTarget().position()));
            InteractionResult result = listener.interact(event.getEntity(), event.getLevel(), event.getHand(), event.getTarget(), hit);
            if (result != InteractionResult.PASS) {
                event.setCanceled(true);
                event.setCancellationResult(result);
            }
        });
        listen(PlayerInteractEvent.EntityInteract.class, event -> {
            InteractionResult result = listener.interact(event.getEntity(), event.getLevel(), event.getHand(), event.getTarget(), null);
            if (result != InteractionResult.PASS) {
                event.setCanceled(true);
                event.setCancellationResult(result);
            }
        });
    }

    @Override
    public void onGameMessage(GameMessageListener listener) {
        GAME_MESSAGE_LISTENERS.add(listener);
    }

    /**
     * Called by {@code PlayerListMixin} at the tail of {@code PlayerList#broadcastSystemMessage(Component, Function,
     * boolean)}, where Fabric fires {@code ServerMessageEvents.GAME_MESSAGE}.
     */
    public static void fireGameMessage(MinecraftServer server, Component message, boolean overlay) {
        for (GameMessageListener listener : GAME_MESSAGE_LISTENERS) {
            listener.onGameMessage(server, message, overlay);
        }
    }
}
