package net.nerdorg.minehop.forge.platform;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraftforge.common.util.Result;
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
import net.nerdorg.minehop.platform.services.IEventHelper;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Forge implementation of {@link IEventHelper}: every listener goes on the Forge event that fires where the
 * corresponding Fabric API event fires (EventBus 7, Forge 1.21.6+: each event class has its own static {@code BUS};
 * cancellable events are cancelled by returning {@code true} from the listener). The one exception is
 * {@link #onGameMessage} (no Forge event): {@code PlayerListMixin} in this module calls {@link #fireGameMessage}.
 */
public class ForgeEventHelper implements IEventHelper {
    private static final List<GameMessageListener> GAME_MESSAGE_LISTENERS = new CopyOnWriteArrayList<>();
    /** new player -> old player, from PlayerEvent.Clone until the PlayerRespawnEvent of the same respawn. */
    private static final Map<Player, Player> RESPAWN_ORIGINALS = new WeakHashMap<>();

    @Override
    public void onServerStarting(ServerListener listener) {
        ServerStartingEvent.BUS.addListener(event -> listener.onServer(event.getServer()));
    }

    @Override
    public void onServerStarted(ServerListener listener) {
        ServerStartedEvent.BUS.addListener(event -> listener.onServer(event.getServer()));
    }

    @Override
    public void onServerStopping(ServerListener listener) {
        ServerStoppingEvent.BUS.addListener(event -> listener.onServer(event.getServer()));
    }

    @Override
    public void onServerStopped(ServerListener listener) {
        ServerStoppedEvent.BUS.addListener(event -> listener.onServer(event.getServer()));
    }

    @Override
    public void onServerTickStart(ServerListener listener) {
        TickEvent.ServerTickEvent.Pre.BUS.addListener(event -> listener.onServer(event.server()));
    }

    @Override
    public void onServerTickEnd(ServerListener listener) {
        TickEvent.ServerTickEvent.Post.BUS.addListener(event -> listener.onServer(event.server()));
    }

    @Override
    public void onServerLevelLoad(ServerLevelListener listener) {
        LevelEvent.Load.BUS.addListener(event -> {
            if (event.getLevel() instanceof ServerLevel level) {
                listener.onLevel(level.getServer(), level);
            }
        });
    }

    @Override
    public void onServerLevelUnload(ServerLevelListener listener) {
        LevelEvent.Unload.BUS.addListener(event -> {
            if (event.getLevel() instanceof ServerLevel level) {
                listener.onLevel(level.getServer(), level);
            }
        });
    }

    @Override
    public void onRegisterCommands(CommandRegistrationListener listener) {
        RegisterCommandsEvent.BUS.addListener(event -> listener.register(event.getDispatcher(), event.getBuildContext(), event.getCommandSelection()));
    }

    @Override
    public void onPlayerRespawn(PlayerRespawnListener listener) {
        // Fabric's AFTER_RESPAWN hands over (old, new, alive). Forge fires PlayerEvent.Clone (new player + original)
        // and then PlayerRespawnEvent (new player, endConquered == alive) during the same PlayerList#respawn.
        PlayerEvent.Clone.BUS.addListener(event -> {
            if (event.getEntity() instanceof ServerPlayer) {
                synchronized (RESPAWN_ORIGINALS) {
                    RESPAWN_ORIGINALS.put(event.getEntity(), event.getOriginal());
                }
            }
        });
        PlayerEvent.PlayerRespawnEvent.BUS.addListener(event -> {
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
        EntityJoinLevelEvent.BUS.addListener((java.util.function.Consumer<EntityJoinLevelEvent>) event -> {
            if (event.getLevel() instanceof ServerLevel level) {
                listener.onLoad(event.getEntity(), level);
            }
        });
    }

    @Override
    public void onAllowDamage(AllowDamageListener listener) {
        LivingAttackEvent.BUS.addListener(event ->
                !event.getEntity().level().isClientSide() && !listener.allowDamage(event.getEntity(), event.getSource(), event.getAmount()));
    }

    @Override
    public void onBeforeBlockBreak(BeforeBlockBreakListener listener) {
        // Forge 61 decides the break from the event result (DENY), not from cancellation.
        BlockEvent.BreakEvent.BUS.addListener((java.util.function.Consumer<BlockEvent.BreakEvent>) event -> {
            if (event.getLevel() instanceof Level level
                    && !listener.beforeBlockBreak(level, event.getPlayer(), event.getPos(), event.getState(), level.getBlockEntity(event.getPos()))) {
                event.setResult(Result.DENY);
            }
        });
    }

    @Override
    public void onUseBlock(UseBlockListener listener) {
        PlayerInteractEvent.RightClickBlock.BUS.addListener(event -> {
            InteractionResult result = listener.interact(event.getEntity(), event.getLevel(), event.getHand(), event.getHitVec());
            if (result != InteractionResult.PASS) {
                event.setCancellationResult(result);
                return true;
            }
            return false;
        });
    }

    @Override
    public void onUseItem(UseItemListener listener) {
        PlayerInteractEvent.RightClickItem.BUS.addListener(event -> {
            InteractionResult result = listener.interact(event.getEntity(), event.getLevel(), event.getHand());
            if (result != InteractionResult.PASS) {
                event.setCancellationResult(result);
                return true;
            }
            return false;
        });
    }

    @Override
    public void onUseEntity(UseEntityListener listener) {
        // Fabric fires UseEntityCallback for both the "interact at" (with hit result) and the plain "interact" packet.
        PlayerInteractEvent.EntityInteractSpecific.BUS.addListener(event -> {
            EntityHitResult hit = new EntityHitResult(event.getTarget(), event.getLocalPos().add(event.getTarget().position()));
            InteractionResult result = listener.interact(event.getEntity(), event.getLevel(), event.getHand(), event.getTarget(), hit);
            if (result != InteractionResult.PASS) {
                event.setCancellationResult(result);
                return true;
            }
            return false;
        });
        PlayerInteractEvent.EntityInteract.BUS.addListener(event -> {
            InteractionResult result = listener.interact(event.getEntity(), event.getLevel(), event.getHand(), event.getTarget(), null);
            if (result != InteractionResult.PASS) {
                event.setCancellationResult(result);
                return true;
            }
            return false;
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
