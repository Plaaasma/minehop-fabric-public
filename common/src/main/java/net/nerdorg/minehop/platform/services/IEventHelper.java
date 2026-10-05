package net.nerdorg.minehop.platform.services;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Server lifecycle, tick, level, command and gameplay events (logical server side unless stated otherwise).
 *
 * <p>Each method mirrors one Fabric API event (named in its doc), which is what the Fabric implementation registers
 * to; the NeoForge/Forge equivalents are listed for phase 3 (all on the game event bus unless stated otherwise).</p>
 */
public interface IEventHelper {

    // ---------------------------------------------------------------------------------------------
    // Server lifecycle (Fabric ServerLifecycleEvents; NeoForge/Forge ServerStartingEvent, ServerStartedEvent,
    // ServerStoppingEvent, ServerStoppedEvent)
    // ---------------------------------------------------------------------------------------------

    void onServerStarting(ServerListener listener);

    void onServerStarted(ServerListener listener);

    void onServerStopping(ServerListener listener);

    void onServerStopped(ServerListener listener);

    // ---------------------------------------------------------------------------------------------
    // Ticks (Fabric ServerTickEvents.START/END_SERVER_TICK; NeoForge ServerTickEvent.Pre/Post;
    // Forge TickEvent.ServerTickEvent.Pre/Post)
    // ---------------------------------------------------------------------------------------------

    void onServerTickStart(ServerListener listener);

    void onServerTickEnd(ServerListener listener);

    // ---------------------------------------------------------------------------------------------
    // Levels (Fabric ServerWorldEvents.LOAD/UNLOAD; NeoForge/Forge LevelEvent.Load/Unload filtered to ServerLevel).
    // Fired once per dimension.
    // ---------------------------------------------------------------------------------------------

    void onServerLevelLoad(ServerLevelListener listener);

    void onServerLevelUnload(ServerLevelListener listener);

    // ---------------------------------------------------------------------------------------------
    // Commands (Fabric CommandRegistrationCallback; NeoForge/Forge RegisterCommandsEvent)
    // ---------------------------------------------------------------------------------------------

    void onRegisterCommands(CommandRegistrationListener listener);

    // ---------------------------------------------------------------------------------------------
    // Players and entities
    // ---------------------------------------------------------------------------------------------

    /**
     * After a player respawned (death or leaving the End) and was placed in the world.
     * Fabric {@code ServerPlayerEvents.AFTER_RESPAWN}; NeoForge/Forge {@code PlayerEvent.PlayerRespawnEvent}
     * ({@code alive} = {@code isEndConquered()}; the old player instance is available from {@code PlayerEvent.Clone},
     * which fires just before in the same respawn).
     */
    void onPlayerRespawn(PlayerRespawnListener listener);

    /**
     * An entity was loaded into a server level (spawned or loaded from disk).
     * Fabric {@code ServerEntityEvents.ENTITY_LOAD}; NeoForge/Forge {@code EntityJoinLevelEvent} (server levels only).
     */
    void onEntityLoad(EntityLoadListener listener);

    /**
     * Whether a living entity may take damage; returning false cancels the damage.
     * Fabric {@code ServerLivingEntityEvents.ALLOW_DAMAGE}; NeoForge {@code LivingIncomingDamageEvent} (cancel);
     * Forge {@code LivingAttackEvent} (cancel).
     */
    void onAllowDamage(AllowDamageListener listener);

    /**
     * Before a player breaks a block (server side); returning false cancels the break.
     * Fabric {@code PlayerBlockBreakEvents.BEFORE}; NeoForge/Forge {@code BlockEvent.BreakEvent} (cancel).
     */
    void onBeforeBlockBreak(BeforeBlockBreakListener listener);

    /**
     * A player right-clicks a block. Fired on BOTH logical sides (like Fabric's callback); any result other than
     * {@code PASS} cancels vanilla handling and is returned to the caller.
     * Fabric {@code UseBlockCallback}; NeoForge/Forge {@code PlayerInteractEvent.RightClickBlock}.
     */
    void onUseBlock(UseBlockListener listener);

    /**
     * A player uses (right-clicks) the item in a hand. Both logical sides; non-{@code PASS} cancels.
     * Fabric {@code UseItemCallback}; NeoForge/Forge {@code PlayerInteractEvent.RightClickItem}.
     */
    void onUseItem(UseItemListener listener);

    /**
     * A player right-clicks an entity. Both logical sides; non-{@code PASS} cancels.
     * Fabric {@code UseEntityCallback}; NeoForge/Forge {@code PlayerInteractEvent.EntityInteractSpecific} (with hit
     * result) and {@code PlayerInteractEvent.EntityInteract} (hitResult = null).
     */
    void onUseEntity(UseEntityListener listener);

    // ---------------------------------------------------------------------------------------------
    // Chat
    // ---------------------------------------------------------------------------------------------

    /**
     * The server broadcast a system/game message (join/leave/death messages, /say ...).
     * Fabric {@code ServerMessageEvents.GAME_MESSAGE}. NeoForge/Forge have no event for it: implement with a loader-side
     * mixin at the tail of {@code PlayerList#broadcastSystemMessage(Component, Function, boolean)}.
     */
    void onGameMessage(GameMessageListener listener);

    // ---------------------------------------------------------------------------------------------
    // Listener types
    // ---------------------------------------------------------------------------------------------

    @FunctionalInterface
    interface ServerListener {
        void onServer(MinecraftServer server);
    }

    @FunctionalInterface
    interface ServerLevelListener {
        void onLevel(MinecraftServer server, ServerLevel level);
    }

    @FunctionalInterface
    interface CommandRegistrationListener {
        void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext registryAccess, Commands.CommandSelection environment);
    }

    @FunctionalInterface
    interface PlayerRespawnListener {
        void afterRespawn(ServerPlayer oldPlayer, ServerPlayer newPlayer, boolean alive);
    }

    @FunctionalInterface
    interface EntityLoadListener {
        void onLoad(Entity entity, ServerLevel level);
    }

    @FunctionalInterface
    interface AllowDamageListener {
        boolean allowDamage(LivingEntity entity, DamageSource source, float amount);
    }

    @FunctionalInterface
    interface BeforeBlockBreakListener {
        boolean beforeBlockBreak(Level level, Player player, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity);
    }

    @FunctionalInterface
    interface UseBlockListener {
        InteractionResult interact(Player player, Level level, InteractionHand hand, BlockHitResult hitResult);
    }

    /**
     * 1.20.1: like Fabric's {@code UseItemCallback} of that version, the result carries the item stack
     * ({@code InteractionResultHolder}); its {@code getResult()} has the same PASS/non-PASS meaning.
     */
    @FunctionalInterface
    interface UseItemListener {
        InteractionResultHolder<ItemStack> interact(Player player, Level level, InteractionHand hand);
    }

    @FunctionalInterface
    interface UseEntityListener {
        InteractionResult interact(Player player, Level level, InteractionHand hand, Entity entity, @Nullable EntityHitResult hitResult);
    }

    @FunctionalInterface
    interface GameMessageListener {
        void onGameMessage(MinecraftServer server, Component message, boolean overlay);
    }
}
