package net.nerdorg.minehop.anticheat.stream;

import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.block.BedBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerAbilities;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.s2c.common.CustomPayloadS2CPacket;
import net.minecraft.network.packet.s2c.play.BundleS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.anticheat.AntiCheatManager;
import net.nerdorg.minehop.block.ModBlocks;
import net.nerdorg.minehop.config.ConfigWrapper;
import net.nerdorg.minehop.config.MinehopConfig;
import net.nerdorg.minehop.entity.custom.SurfRampEntity;
import net.nerdorg.minehop.networking.payloads.ResetVelocityCarryPayload;
import net.nerdorg.minehop.util.MovementUtil;

import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Validates movement from the client's own packet stream, one client tick at a time.
 *
 * <p>On a dedicated server the server never receives the client's strafe inputs as movement, so its
 * own simulation of the player is not a usable reference. Instead every client tick is checked against
 * hard limits derived from the mod's own physics, which the legit client runs exactly:
 * <ul>
 *   <li><b>Speed</b>: horizontal speed² can grow by at most {@code 7 * cap²} per tick in the air
 *       (≤7 substeps of canonical air-accelerate, each adding ≤cap²); after two ticks on the ground
 *       without jumping only Source friction + ground-accelerate apply.</li>
 *   <li><b>Fly</b>: while airborne, vertical motion must follow the ballistic arc (dy drops by exactly
 *       gravity per tick); only the crouch-jump offset may lift the player above it.</li>
 *   <li><b>Timer</b>: the client may not tick faster than real time (see {@link TimerBalance}).</li>
 *   <li><b>Strafe</b> (flag-only): replays the player's real keys and yaw through the air-accelerate
 *       code; gaining more than those inputs allow means tampered movement settings.</li>
 * </ul>
 * Movement the model can't predict exactly (teleports, server-sent velocity, fluids, ladders, bounce
 * blocks, boost pads, surf ramps, vehicles, elytra, effects) is a short "grace": only a hard cap on the
 * per-tick displacement applies and the baseline is rebuilt from what the client did. Server-sent
 * velocity is tracked with a ping/pong transaction, so latency never causes a false flag. Only blatant
 * violations lag the player back; everything subtler is flag-only evidence attached to the run.
 *
 * <p><b>1.21.1 port (pre-1.21.2 protocol).</b> The 1.21.4 build delimits client ticks with
 * {@code ClientTickEndC2SPacket} and reads the movement keys from the boolean
 * {@code PlayerInputC2SPacket}; neither exists before 1.21.2. Adaptations, all chosen so missing
 * information can only make the checks more lenient, never create a flag:
 * <ul>
 *   <li><b>Tick boundary = each move packet.</b> A 1.21.1 client sends at most one move packet per tick
 *       (from {@code sendMovementPackets}: position when it moved &gt; 2e-4 or every 20 ticks, look when
 *       it rotated, on-ground-only when that flag flipped) and nothing on a fully quiet tick. So every
 *       move packet the server processes finalizes one client tick ({@link #onMoveAccepted} /
 *       {@link #onMoveNotAccepted}); a quiet tick is merged into the next packet's tick. A quiet tick
 *       moved &lt; 2e-4 blocks, so the merge is the same sub-threshold effect a 1.21.4 tick without a
 *       position packet has. The teleport-confirm echo is still not a tick. {@link #clientTicks} can
 *       only under-count real ticks (quiet ticks), which keeps the run-timing check one-sided.</li>
 *   <li><b>Timer</b>: {@link TimerBalance} counts move packets as ticks (its "move without tick-end"
 *       path); quiet ticks build lag credit, bounded by its credit floor and decay.</li>
 *   <li><b>Keys</b>: only sneak/sprint are known, from {@code ClientCommandC2SPacket}
 *       ({@link #onClientCommand}). Jump is never known, so a jump is always treated as possible
 *       ({@link #JUMP_KEY_KNOWN}: no "two ground ticks without jumping = walking friction only"
 *       regime). W/A/S/D are never known, so the flag-only Strafe replay is disabled
 *       ({@link #MOVEMENT_KEYS_KNOWN}).</li>
 *   <li><b>Teleports</b>: {@code requestTeleport(x, y, z, yaw, pitch, flags)}; there is no
 *       {@code PlayerPosition} velocity. A relative X/Y/Z flag keeps the client's velocity on that axis
 *       (pre-1.21.2 equivalent of the DELTA_* flags), so it carries the last step's speed as grace.</li>
 *   <li>Move packets have no horizontal-collision flag (added in 1.21.2); the server player's own
 *       flag from vanilla's replay of the packet is used instead (it only gates the skipped Strafe
 *       replay).</li>
 *   <li>Explosion knockback is the packet's (always present, possibly zero) player velocity.</li>
 * </ul>
 */
public final class MovementValidator {
    public static final String CHECK_TIMER = "Timer";
    public static final String CHECK_SPEED = "Speed";
    public static final String CHECK_FLY = "Fly";
    public static final String CHECK_STRAFE = "Strafe";
    public static final String CHECK_PACKETS = "BadPackets";

    private static final int PING_TAG = 0x4D480000;
    private static final int PING_TAG_MASK = 0xFFFF0000;
    private static final AtomicInteger PING_SEQUENCE = new AtomicInteger();

    private static final double SOURCE_UNIT_TO_BLOCKS_PER_TICK = 1.0D / 800.0D;
    private static final double FRAME_TIME = 1.0D / 20.0D;
    private static final int MAX_AIR_SUBSTEPS = 7;
    private static final double SOURCE_MAX_AIR_YAW_DELTA = 90.0D;
    private static final double SUPPORT_PROBE_DEPTH = 0.26D;
    private static final double CLIENT_GROUND_DEPTH = 0.6D;
    // LivingEntityMixin's jump buffer fires only while vertical velocity <= 0.10 b/t.
    private static final double JUMP_BUFFER_MAX_VY = 0.10D;
    private static final double VANILLA_STEP_HEIGHT = 0.6D;
    private static final double AUTO_STEP_HEIGHT = 1.12D;
    private static final double SPRINT_MARGIN = 1.3D;
    private static final double ENTITY_PUSH_PER_TICK = 0.055D;
    private static final double MAX_ENVIRONMENT_SCAN_STEP = 8.0D;
    private static final double SURF_RAMP_PROXIMITY = 3.0D;

    private static final int TELEPORT_GRACE_TICKS = 3;
    private static final int VELOCITY_GRACE_TICKS = 3;
    private static final int ENVIRONMENT_GRACE_TICKS = 3;
    private static final int CHUNK_LOAD_WINDOW_TICKS = 200;
    private static final int JUMP_INPUT_WINDOW_TICKS = 10;
    private static final int CROUCH_RESTORE_WINDOW_TICKS = 20;
    /** How close dy must be to "arc minus crouch offset" to count as the delayed uncrouch drop. */
    private static final double UNCROUCH_DROP_MATCH = 0.005D;
    // Grace is not a free pass: the per-tick displacement may only grow so much beyond the fastest
    // legit source in play (last step, server-sent velocity, launch blocks).
    private static final double GRACE_GROWTH = 1.25D;
    private static final double GRACE_SLACK = 1.0D;
    private static final double BOUNCE_LAUNCH_SPEED = 13.0D;
    private static final double RIPTIDE_LAUNCH_SPEED = 6.0D;
    private static final long PING_TIMEOUT_MIN_NANOS = 400_000_000L;
    private static final long PING_TIMEOUT_MAX_NANOS = 2_000_000_000L;
    private static final long PING_TIMEOUT_SLACK_NANOS = 150_000_000L;

    private static final double HORIZONTAL_EPSILON2 = 1.0E-9D;
    private static final double VERTICAL_EPSILON = 1.0E-4D;
    // Lagback only when the movement is blatantly impossible; anything subtler is flag-only evidence.
    private static final double BLATANT_SPEED_RATIO = 1.2D;
    private static final double BLATANT_SPEED_EXCESS = 0.05D;
    private static final double SPEED_FLAG_MIN_UNITS = 0.5D;
    private static final double SPEED_BUFFER_LAGBACK = 200.0D;
    private static final double SPEED_BUFFER_DECAY = 1.0D;
    private static final double FLY_FLAG_MIN = 0.01D;
    private static final double BLATANT_FLY_EXCESS = 0.5D;
    private static final double FLY_BUFFER_LAGBACK = 1.0D;
    private static final double FLY_BUFFER_DECAY = 0.02D;
    private static final double STRAFE_FLAG_BUFFER = 20.0D;
    private static final double STRAFE_BUFFER_DECAY = 0.25D;
    private static final double STRAFE_EPSILON_RATIO = 1.0E-6D;

    /**
     * 1.21.1 port: pre-1.21.2 clients never send their jump key (no per-tick PlayerInputC2SPacket), so a
     * jump is always assumed possible: the ground "walking" regime (friction-only bound) never applies
     * and every takeoff may use the full jump impulse.
     */
    private static final boolean JUMP_KEY_KNOWN = false;
    /**
     * 1.21.1 port: pre-1.21.2 clients never send W/A/S/D, so the Strafe replay (which needs the real
     * keys) is skipped entirely. It was flag-only, so no lagback behaviour changes.
     */
    private static final boolean MOVEMENT_KEYS_KNOWN = false;

    private static final Map<UUID, StreamState> STATES = new ConcurrentHashMap<>();

    private MovementValidator() {
    }

    private static StreamState state(ServerPlayerEntity player) {
        return STATES.computeIfAbsent(player.getUuid(), uuid -> new StreamState());
    }

    public static void clear(UUID uuid) {
        if (uuid != null) {
            STATES.remove(uuid);
        }
    }

    /** Client ticks validated so far for this player, or -1 if none (used to check run timing). */
    public static long clientTicks(ServerPlayerEntity player) {
        StreamState st = player == null ? null : STATES.get(player.getUuid());
        return st == null ? -1L : st.clientTicks;
    }

    // ------------------------------------------------------------------------------------------
    // Packet entry points (called from the network handler mixins)
    // ------------------------------------------------------------------------------------------

    /**
     * Server thread. 1.21.1 port: replaces the 1.21.2+ {@code PlayerInputC2SPacket} hook. Sneak and
     * sprint changes arrive as client commands, sent right before that tick's move packet.
     */
    public static void onClientCommand(ServerPlayerEntity player, ClientCommandC2SPacket.Mode mode) {
        if (player == null || mode == null) {
            return;
        }
        StreamState st = state(player);
        switch (mode) {
            case PRESS_SHIFT_KEY -> st.input = st.input.withSneak(true);
            case RELEASE_SHIFT_KEY -> st.input = st.input.withSneak(false);
            case START_SPRINTING -> st.input = st.input.withSprint(true);
            case STOP_SPRINTING -> st.input = st.input.withSprint(false);
            default -> {
            }
        }
    }

    /**
     * Server thread, after vanilla accepted and applied a move packet. 1.21.1 port: the packet is also
     * the client's tick boundary, so the tick is finalized here (unless it is the teleport echo).
     */
    public static void onMoveAccepted(ServerPlayerEntity player, PlayerMoveC2SPacket packet) {
        if (player == null || packet == null) {
            return;
        }
        StreamState st = state(player);
        boolean echo = false;
        if (packet.changesPosition()) {
            Vec3d pos = player.getPos();
            if (st.teleportEchoPending) {
                st.teleportEchoPending = false;
                echo = st.lastPos != null && pos.squaredDistanceTo(st.lastPos) < 1.0E-12D;
            }
            if (!echo) {
                if (st.tickPos != null) {
                    // Two positional packets in one client tick: a vanilla client never does this.
                    // Validate the earlier one as its own tick (with its own yaw/ground flag first).
                    // (1.21.1: unreachable, every packet is finalized below; kept for parity.)
                    if (st.everSentTickEnd) {
                        AntiCheatManager.reportMovementViolation(player, CHECK_PACKETS, 1.0D,
                                "multipleMovesPerTick", false, null);
                    }
                    finalizeTick(player, st);
                }
                st.tickPos = pos;
                // 1.21.1: move packets carry no horizontalCollision flag (added in 1.21.2); use the
                // server player's flag from vanilla's move() replay of this packet just before.
                st.tickHorizontalCollision = player.horizontalCollision;
            }
        }
        st.clientOnGround = packet.isOnGround();
        if (packet.changesLook()) {
            // Raw (unwrapped) yaw exactly as the client's travel() used it.
            st.tickYaw = packet.getYaw(st.tickYaw);
            st.tickHasYaw = true;
        }
        if (!echo) {
            finalizeTick(player, st);
        }
    }

    /**
     * Server thread. 1.21.1 port: a move packet vanilla processed without applying it (awaiting a
     * teleport confirm, riding, sleeping, or rejected and teleported back) still marks one client tick,
     * like a 1.21.4 tick-end packet arriving without an accepted move.
     */
    public static void onMoveNotAccepted(ServerPlayerEntity player) {
        if (player == null) {
            return;
        }
        finalizeTick(player, state(player));
    }

    /** Netty thread: a move packet arrived (extra ticks without tick-end packets count too). */
    public static void onMovePacketNetwork(ServerPlayerEntity player) {
        MinecraftServer server = player == null ? null : player.getServer();
        if (server == null) {
            return;
        }
        handleTimer(player, server, state(player).timer.onMovePacket(System.nanoTime(), server.getTickManager().getNanosPerTick()));
    }

    /** Netty thread: the client confirmed a teleport; its next move packet is an echo, not a tick. */
    public static void onTeleportConfirmNetwork(ServerPlayerEntity player) {
        if (player != null) {
            state(player).timer.onTeleportConfirm();
        }
    }

    private static void handleTimer(ServerPlayerEntity player, MinecraftServer server, TimerBalance.Violation violation) {
        if (violation != null) {
            UUID uuid = player.getUuid();
            server.execute(() -> onTimerViolation(uuid, server, violation));
        }
    }

    /**
     * Server thread: vanilla is about to ignore moves until the client confirms this teleport.
     * 1.21.1 port: a teleport carries no velocity of its own (no {@code PlayerPosition}); the client
     * zeroes its velocity except on relatively-flagged X/Y/Z axes, where it keeps it.
     */
    public static void onTeleportRequested(ServerPlayerEntity player, Set<PositionFlag> flags) {
        if (player == null) {
            return;
        }
        StreamState st = state(player);
        st.awaitingTeleport = true;
        st.clearTickAccumulation();
        double speed = 0.0D;
        if (flags != null && (flags.contains(PositionFlag.X) || flags.contains(PositionFlag.Y)
                || flags.contains(PositionFlag.Z))) {
            speed += st.lastStep == null ? 0.0D : st.lastStep.length();
        }
        st.pendingTeleportSpeed = Math.max(st.pendingTeleportSpeed, speed);
    }

    /** Server thread: the client confirmed the pending teleport and now stands at the target. */
    public static void onTeleportConfirmed(ServerPlayerEntity player) {
        if (player == null) {
            return;
        }
        StreamState st = state(player);
        st.awaitingTeleport = false;
        st.teleportEchoPending = true;
        st.ticksSinceTeleport = 0;
        st.graceTicks = Math.max(st.graceTicks, TELEPORT_GRACE_TICKS);
        st.graceSpeed = Math.max(st.graceSpeed, st.pendingTeleportSpeed);
        st.pendingTeleportSpeed = 0.0D;
        st.horizontalBuffer = 0.0D;
        st.verticalBuffer = 0.0D;
        // A respawn or dimension change gives the client a new player entity without the crouch
        // offset; with sneak still held it lifts again.
        st.crouchLiftOwed = true;
        st.clearTickAccumulation();
        st.resetBaseline(player.getPos());
    }

    /** Called right before our own lagback teleport is requested. */
    public static void onLagbackIssued(ServerPlayerEntity player) {
        if (player == null) {
            return;
        }
        StreamState st = state(player);
        st.horizontalBuffer = 0.0D;
        st.verticalBuffer = 0.0D;
        st.strafeBuffer = 0.0D;
    }

    /**
     * Any thread: a packet is being sent to {@code player}. If it changes the player's own velocity,
     * returns the ping id that must be sent right after it (the client answers once it applied the
     * velocity), else -1.
     */
    public static int onPacketSent(ServerPlayerEntity player, Packet<?> packet) {
        if (player == null || packet == null) {
            return -1;
        }
        double[] velocity = selfVelocity(packet, player.getId());
        if (velocity == null) {
            return -1;
        }
        int id = PING_TAG | (PING_SEQUENCE.incrementAndGet() & 0xFFFF);
        state(player).pendingPings.put(id, new StreamState.PendingPing(System.nanoTime(), (int) velocity[0], velocity[1]));
        return id;
    }

    public static boolean isTransactionPing(int id) {
        return (id & PING_TAG_MASK) == PING_TAG;
    }

    /** Server thread (queued in packet order): the client has applied a server-sent velocity. */
    public static void onTransactionPong(UUID playerUuid, int id) {
        StreamState st = STATES.get(playerUuid);
        StreamState.PendingPing ping = st == null ? null : st.pendingPings.remove(id);
        if (ping == null) {
            return;
        }
        long rtt = System.nanoTime() - ping.sentNanos();
        st.transactionRttNanos = Math.max(rtt, (long) (st.transactionRttNanos * 0.9D));
        // The pong precedes the first move packet that used the new velocity; that tick and the
        // next (plus any reset-velocity carry ticks) are rebuilt from realized movement.
        st.graceTicks = Math.max(st.graceTicks, VELOCITY_GRACE_TICKS + ping.carryTicks());
        st.graceSpeed = Math.max(st.graceSpeed, ping.speed());
    }

    /** {carryTicks, speed} if the packet changes the player's own velocity, else null. */
    private static double[] selfVelocity(Packet<?> packet, int selfId) {
        if (packet instanceof EntityVelocityUpdateS2CPacket velocity) {
            if (velocity.getEntityId() != selfId) {
                return null;
            }
            return new double[]{0, new Vec3d(velocity.getVelocityX(), velocity.getVelocityY(), velocity.getVelocityZ()).length()};
        }
        if (packet instanceof ExplosionS2CPacket explosion) {
            // 1.21.1: the knockback is always in the packet (zero when the player wasn't affected) and
            // the client adds it to its velocity.
            Vec3d knock = new Vec3d(explosion.getPlayerVelocityX(), explosion.getPlayerVelocityY(), explosion.getPlayerVelocityZ());
            return knock.lengthSquared() > 0.0D ? new double[]{0, knock.length()} : null;
        }
        if (packet instanceof CustomPayloadS2CPacket custom
                && custom.payload() instanceof ResetVelocityCarryPayload carry) {
            return new double[]{Math.max(1, carry.ticks()), new Vec3d(carry.x(), carry.y(), carry.z()).length()};
        }
        if (packet instanceof BundleS2CPacket bundle) {
            double[] best = null;
            for (Packet<?> inner : bundle.getPackets()) {
                double[] candidate = selfVelocity(inner, selfId);
                if (candidate != null && (best == null || candidate[1] > best[1])) {
                    best = candidate;
                }
            }
            return best;
        }
        return null;
    }

    private static void onTimerViolation(UUID uuid, MinecraftServer server, TimerBalance.Violation violation) {
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
        if (player == null || isUnchecked(player)) {
            return;
        }
        StreamState st = state(player);
        // Right after a stall the "excess" may be the client catching up: evidence only.
        String details = String.format(Locale.ROOT, "aheadOfRealTime=%dms%s",
                violation.aheadNanos() / 1_000_000L, violation.afterLagBurst() ? " afterLagBurst" : "");
        AntiCheatManager.reportMovementViolation(player, CHECK_TIMER, 2.0D, details,
                !violation.afterLagBurst() && !st.awaitingTeleport, st.lastGoodPos);
    }

    // ------------------------------------------------------------------------------------------
    // Per-tick validation
    // ------------------------------------------------------------------------------------------

    private static boolean isUnchecked(ServerPlayerEntity player) {
        if (player instanceof FakePlayer || !AntiCheatManager.isEnabled() || AntiCheatManager.isExempt(player)) {
            return true;
        }
        MinecraftServer server = player.getServer();
        return server != null && server.isHost(player.getGameProfile());
    }

    private static int latencyTicks(ServerPlayerEntity player) {
        return player.networkHandler == null ? 0 : Math.max(0, player.networkHandler.getLatency()) / 50;
    }

    private static void finalizeTick(ServerPlayerEntity player, StreamState st) {
        st.clientTicks++;
        StreamState.Input input = st.input;
        // 1.21.1: the jump key is unknown, so a jump is always possible (see JUMP_KEY_KNOWN).
        st.ticksSinceJumpInput = (!JUMP_KEY_KNOWN || input.jump()) ? 0 : Math.min(st.ticksSinceJumpInput + 1, 1000);
        boolean sneakChanged = input.sneak() != st.lastTickInput.sneak();
        st.ticksSinceSneakChange = sneakChanged ? 0 : Math.min(st.ticksSinceSneakChange + 1, 1000);
        if (sneakChanged) {
            // Pressing sneak owes a css crouch lift (it waits for headroom); releasing cancels it.
            st.crouchLiftOwed = input.sneak();
        }
        st.lastTickInput = input;
        if (st.ticksSinceTeleport < 1000) {
            st.ticksSinceTeleport++;
        }
        if (st.holdTicks > 0) {
            st.holdTicks--;
        }
        expireStalePings(player, st);

        if (st.awaitingTeleport) {
            st.clearTickAccumulation();
            return;
        }
        ServerWorld world = player.getServerWorld();
        Vec3d pos = st.tickPos != null ? st.tickPos : (st.lastPos != null ? st.lastPos : player.getPos());
        float yaw = st.tickHasYaw ? st.tickYaw : (st.hasLastYaw ? st.lastYaw : player.getYaw());
        boolean horizontalCollision = st.tickHorizontalCollision;
        st.clearTickAccumulation();

        if (st.lastPos == null) {
            st.resetBaseline(pos);
            st.lastSupported = isSupported(world, player, pos);
            st.lastYaw = yaw;
            st.hasLastYaw = true;
            st.ticksSinceTeleport = 0;
            return;
        }

        MinehopConfig config = ConfigWrapper.getEffectiveConfig(player);
        Vec3d step = pos.subtract(st.lastPos);
        boolean supportedPrev = st.lastSupported;

        // Movement settings. A map change, an admin edit or an effect ending reaches the client a
        // round trip later, so the looser of the old and new values is kept until then.
        double airCap = config.movement.sv_maxairspeed * SOURCE_UNIT_TO_BLOCKS_PER_TICK
                * Math.max(config.movement.speed_coefficient, 0.0D);
        double gravity = config.movement.sv_gravity * FRAME_TIME * SOURCE_UNIT_TO_BLOCKS_PER_TICK;
        double jumpVy = config.movement.sv_jump_impulse * SOURCE_UNIT_TO_BLOCKS_PER_TICK + jumpBoost(player);
        if (!Double.isNaN(st.lastAirCap) && (Math.abs(airCap - st.lastAirCap) > 1.0E-9D
                || Math.abs(gravity - st.lastGravity) > 1.0E-9D || Math.abs(jumpVy - st.lastJumpVy) > 1.0E-9D)) {
            boolean holding = st.holdTicks > 0;
            st.heldAirCap = holding ? Math.max(st.heldAirCap, st.lastAirCap) : st.lastAirCap;
            st.heldGravity = holding ? Math.min(st.heldGravity, st.lastGravity) : st.lastGravity;
            st.heldJumpVy = holding ? Math.max(st.heldJumpVy, st.lastJumpVy) : st.lastJumpVy;
            st.holdTicks = 10 + 2 * latencyTicks(player);
        }
        st.lastAirCap = airCap;
        st.lastGravity = gravity;
        st.lastJumpVy = jumpVy;
        boolean holding = st.holdTicks > 0;
        double effectiveCap = holding ? Math.max(airCap, st.heldAirCap) : airCap;
        double effectiveGravity = holding ? Math.min(gravity, st.heldGravity) : gravity;
        double effectiveJumpVy = holding ? Math.max(jumpVy, st.heldJumpVy) : jumpVy;

        // Ground within reach only counts if the player isn't still rising fast past it (launched
        // alongside a ledge). Up to 0.10 b/t upward the mod's jump buffer may legitimately fire.
        boolean stillRising = st.inAir && !supportedPrev && step.y > 1.0E-3D
                && st.airVy - effectiveGravity > JUMP_BUFFER_MAX_VY + 1.0E-6D;
        // Minecraft resolves vertical collision BEFORE horizontal, so a landing on a block edge
        // happens at the previous tick's x/z even if the player then slides off it horizontally.
        // The client's own ground flag is only accepted with ground directly under its feet.
        boolean supportedNow = !stillRising && (isSupported(world, player, pos)
                || isSupported(world, player, new Vec3d(st.lastPos.x, pos.y, st.lastPos.z))
                || (st.clientOnGround && isGroundUnderFeet(world, player, pos)));

        String exemptReason = environmentExemption(player, world, st.lastPos, pos);
        if (exemptReason != null) {
            // Effects/blocks also change on the client a round trip later.
            st.graceTicks = Math.max(st.graceTicks, ENVIRONMENT_GRACE_TICKS + 2 * latencyTicks(player));
            if ("bounce".equals(exemptReason)) {
                st.graceSpeed = Math.max(st.graceSpeed, BOUNCE_LAUNCH_SPEED);
            } else if ("riptide".equals(exemptReason)) {
                st.graceSpeed = Math.max(st.graceSpeed, RIPTIDE_LAUNCH_SPEED);
            }
        }
        for (StreamState.PendingPing ping : st.pendingPings.values()) {
            st.graceSpeed = Math.max(st.graceSpeed, ping.speed());
        }

        double h2 = step.x * step.x + step.z * step.z;
        boolean sneakingNearGround = input.sneak() && (supportedPrev || supportedNow);
        double crouchOffset = config.movement.css_crouch_jump ? crouchDelta(player) : 0.0D;
        // The client lifts whenever sneak is held and its offset isn't applied, so the lift is owed
        // again (without any sneak change) once something made it drop the offset: climbing, fluids
        // and gliding (all exemptions), unchecked movement, or the crouch setting turning on.
        if (exemptReason != null || crouchOffset != st.lastCrouchOffset) {
            st.crouchLiftOwed = true;
        }
        st.lastCrouchOffset = crouchOffset;

        if (isUnchecked(player) || !config.enabled) {
            st.crouchLiftOwed = true;
            rebuildBaseline(st, step, h2, supportedNow, crouchOffset);
            commit(st, pos, step, supportedNow, horizontalCollision, yaw);
            st.lastGoodPos = pos;
            st.graceSpeed = 0.0D;
            debug(player, st, pos, step, "unchecked", Double.NaN, Double.NaN, supportedPrev, supportedNow);
            return;
        }

        if (st.graceTicks > 0 || !st.pendingPings.isEmpty()) {
            if (st.graceTicks > 0) {
                st.graceTicks--;
            }
            double base = Math.max(st.lastStep == null ? 0.0D : st.lastStep.length(), st.graceSpeed);
            double cap = base * GRACE_GROWTH + GRACE_SLACK;
            double stepLength = step.length();
            String reason = exemptReason != null ? exemptReason : (!st.pendingPings.isEmpty() ? "velocity" : "teleport");
            if (stepLength > cap) {
                String details = String.format(Locale.ROOT, "graceStep=%.3f cap=%.3f during=%s", stepLength, cap, reason);
                if (AntiCheatManager.reportMovementViolation(player, CHECK_SPEED, 2.0D, details, true, st.lastGoodPos)) {
                    debug(player, st, pos, step, "VIOLATION(grace)", Double.NaN, Double.NaN, supportedPrev, supportedNow);
                    return;
                }
            }
            rebuildBaseline(st, step, h2, supportedNow, crouchOffset);
            commit(st, pos, step, supportedNow, horizontalCollision, yaw);
            st.lastGoodPos = pos;
            if (st.graceTicks == 0 && st.pendingPings.isEmpty() && exemptReason == null) {
                st.graceSpeed = 0.0D;
            }
            debug(player, st, pos, step, "grace(" + reason + ")", cap * cap, Double.NaN, supportedPrev, supportedNow);
            decayBuffers(st);
            return;
        }

        boolean lagback = false;
        boolean violated = false;

        // The client drops its css crouch offset at the START of its tick, before its movement code
        // looks for ground: on the sneak-release tick, or delayed to the first airborne tick when sneak
        // was released while standing (the floor blocked it). If the lowered feet are within ground
        // reach, this tick moves like a ground tick (ground acceleration, jump buffer).
        // (1.21.1: sneak is the synthetic state from the PRESS/RELEASE_SHIFT_KEY client commands, sent
        // right before that tick's move packet.)
        boolean uncrouchDropNow = st.ticksSinceSneakChange <= 1
                || (st.inAir && isUncrouchDrop(input, step.y, st.airVy - effectiveGravity, crouchOffset));
        boolean groundBelowPrev = supportedPrev || (crouchOffset > 0.0D && !input.sneak() && uncrouchDropNow
                && isSupported(world, player, st.lastPos.add(0.0D, -crouchOffset, 0.0D)));

        // ---------------- horizontal: speed² gain bound ----------------
        double vRef2 = st.horizontalVelocityBound2;
        double airBound2 = vRef2 + MAX_AIR_SUBSTEPS * effectiveCap * effectiveCap;
        double groundBound2 = groundBound2(player, config, vRef2);
        // Two ticks on the ground without jumping = ground movement: friction always applies.
        boolean walking = supportedPrev && supportedNow && st.groundTicks >= 2 && st.ticksSinceJumpInput > 1;
        double bound2 = walking ? groundBound2
                : (groundBelowPrev || supportedNow) ? Math.max(airBound2, groundBound2) : airBound2;
        int pushers = countPushers(player, world, st.lastPos);
        if (pushers > 0) {
            double b = Math.sqrt(bound2) + ENTITY_PUSH_PER_TICK * pushers;
            bound2 = b * b;
        }
        // auto_step_up maps: the forced step nudges the player forward and restores its velocity.
        boolean autoStep = config.movement.auto_step_up && step.y > 0.05D && (supportedPrev || supportedNow);
        if (!autoStep && h2 > bound2 + HORIZONTAL_EPSILON2) {
            double unit2 = Math.max(effectiveCap * effectiveCap, 1.0E-6D);
            double excessUnits = (h2 - bound2) / unit2;
            double speed = Math.sqrt(h2);
            double limit = Math.sqrt(bound2);
            st.horizontalBuffer += excessUnits;
            if (excessUnits >= SPEED_FLAG_MIN_UNITS) {
                violated = true;
                boolean blatant = (limit <= 1.0E-9D || speed / limit >= BLATANT_SPEED_RATIO)
                        && speed - limit >= BLATANT_SPEED_EXCESS;
                boolean wantLagback = blatant || st.horizontalBuffer >= SPEED_BUFFER_LAGBACK;
                String details = String.format(Locale.ROOT,
                        "speed=%.4f limit=%.4f ratio=%.3f buffer=%.1f %s",
                        speed, limit, limit > 0 ? speed / limit : 999.0D, st.horizontalBuffer,
                        walking ? "walking" : (groundBelowPrev || supportedNow) ? "ground" : "air");
                if (AntiCheatManager.reportMovementViolation(player, CHECK_SPEED,
                        blatant ? 2.0D : 0.5D, details, wantLagback, st.lastGoodPos)) {
                    lagback = true;
                }
            }
        }
        // Upper bound on the true horizontal velocity entering next tick. A sneak edge-clip (or an
        // auto-step) can cut the displacement without cutting the velocity, so keep this tick's bound
        // there; on the ground that bound already includes friction, so it can't creep upward.
        double nextBound2 = (sneakingNearGround || autoStep) ? Math.max(h2, bound2) : h2;

        // ---------------- strafe: replay real inputs (flag-only) ----------------
        boolean airTick = !supportedPrev && !supportedNow;
        // 1.21.1: skipped, the client's W/A/S/D are unknown (see MOVEMENT_KEYS_KNOWN).
        if (MOVEMENT_KEYS_KNOWN && airTick && st.lastWasCheckedAir && st.lastStep != null && !st.lastHorizontalCollision
                && !horizontalCollision && st.hasLastYaw && pushers == 0) {
            double sI = (input.left() ? 1.0D : 0.0D) - (input.right() ? 1.0D : 0.0D);
            double fI = (input.forward() ? 1.0D : 0.0D) - (input.backward() ? 1.0D : 0.0D);
            Vec3d vPrev = new Vec3d(st.lastStep.x, 0.0D, st.lastStep.z);
            double predicted2 = maxAirStrafeSpeed2(vPrev, sI, fI, effectiveCap, st.lastYaw, yaw);
            if (!Double.isNaN(predicted2)) {
                double tolerance = predicted2 * STRAFE_EPSILON_RATIO + 1.0E-10D;
                if (h2 > predicted2 + tolerance) {
                    double unit2 = Math.max(effectiveCap * effectiveCap, 1.0E-6D);
                    st.strafeBuffer += (h2 - predicted2) / unit2;
                    if (st.strafeBuffer >= STRAFE_FLAG_BUFFER) {
                        String details = String.format(Locale.ROOT,
                                "gainBeyondInputs speed=%.4f inputLimit=%.4f buffer=%.1f keys=%s%s%s%s",
                                Math.sqrt(h2), Math.sqrt(predicted2), st.strafeBuffer,
                                input.forward() ? "W" : "", input.left() ? "A" : "",
                                input.backward() ? "S" : "", input.right() ? "D" : "");
                        AntiCheatManager.reportMovementViolation(player, CHECK_STRAFE, 1.0D, details, false, null);
                        st.strafeBuffer = 0.0D;
                    }
                }
            }
        }

        // ---------------- vertical: ballistic arc ----------------
        double dy = step.y;
        // Lift allowance: on a sneak change, or while a lift is still owed (delayed by a ceiling).
        double crouchNow = (st.ticksSinceSneakChange <= 1 || (input.sneak() && st.crouchLiftOwed)) ? crouchOffset : 0.0D;
        // Height above the arc the offset can account for: while crouched, and briefly after release.
        double crouchHeld = (input.sneak() || st.ticksSinceSneakChange <= CROUCH_RESTORE_WINDOW_TICKS) ? crouchOffset : 0.0D;
        double stepHeight = config.movement.auto_step_up ? AUTO_STEP_HEIGHT : VANILLA_STEP_HEIGHT;
        boolean jumpPossible = st.ticksSinceJumpInput <= JUMP_INPUT_WINDOW_TICKS;
        boolean liftOwedBefore = st.crouchLiftOwed;
        double verticalExcess = 0.0D;
        double expectedDy = Double.NaN;
        // Where dy would be without a crouch lift (NaN on takeoff ticks, where a jump looks alike).
        double liftReference = Double.NaN;
        // groundBelowPrev (see above): ground within reach of the dropped feet can also launch a jump.
        if (groundBelowPrev) {
            // Ground within reach can be reported a tick before the actual contact while the player
            // still carries upward speed, so that speed (minus gravity) is also a valid takeoff.
            double carriedVy = st.lastStep == null ? 0.0D : st.lastStep.y - effectiveGravity;
            double takeoffVy = Math.max(jumpPossible ? effectiveJumpVy : 0.0D, carriedVy);
            // A step-up only counts if the player ends the tick standing on the step.
            double allowed = Math.max(takeoffVy, supportedNow ? stepHeight : 0.0D) + crouchNow + VERTICAL_EPSILON;
            verticalExcess = dy - allowed;
            if (!supportedNow) {
                st.inAir = true;
                // A crouch drop on this tick moved the feet, not the velocity: continue from whichever
                // legal takeoff speed (jump or carried) it exactly matches.
                st.airVy = isUncrouchDrop(input, dy, takeoffVy, crouchOffset) ? takeoffVy
                        : isUncrouchDrop(input, dy, carriedVy, crouchOffset) ? carriedVy
                        : Math.min(dy + crouchNow, takeoffVy);
                st.airExcess = Math.max(0.0D, dy - st.airVy);
            } else {
                st.inAir = false;
                liftReference = 0.0D;
            }
        } else if (st.inAir) {
            expectedDy = st.airVy - effectiveGravity;
            if (Math.abs(dy) < 1.0E-9D && isChunkStillLoading(player, st, pos)) {
                // The client freezes vertically while the chunk it is in hasn't arrived yet.
                st.airVy = 0.0D;
                st.airExcess = 0.0D;
            } else if (supportedNow) {
                double allowed = Math.max(expectedDy, 0.0D) + stepHeight + crouchNow + VERTICAL_EPSILON;
                verticalExcess = dy - allowed;
                st.inAir = false;
            } else {
                liftReference = expectedDy;
                st.airExcess = Math.max(0.0D, st.airExcess + (dy - expectedDy));
                double perTick = dy - (expectedDy + crouchNow + VERTICAL_EPSILON);
                double cumulative = st.airExcess - (crouchHeld + VERTICAL_EPSILON);
                verticalExcess = Math.max(perTick, cumulative);
                st.airVy = isUncrouchDrop(input, dy, expectedDy, crouchOffset)
                        ? expectedDy
                        : Math.min(dy + crouchNow, expectedDy);
            }
        } else if (!supportedNow) {
            // Airborne without a known arc (first tick after a rebuilt baseline): start one here.
            st.inAir = true;
            st.airVy = dy + crouchNow;
            st.airExcess = 0.0D;
        }
        if (st.crouchLiftOwed && crouchOffset > 0.0D && !Double.isNaN(liftReference)
                && dy - liftReference >= crouchOffset * 0.5D) {
            st.crouchLiftOwed = false;
        }
        if (verticalExcess > FLY_FLAG_MIN) {
            violated = true;
            st.verticalBuffer += verticalExcess;
            boolean blatant = verticalExcess >= BLATANT_FLY_EXCESS;
            boolean wantLagback = blatant || st.verticalBuffer >= FLY_BUFFER_LAGBACK;
            // Enough context to tell a missed legit case from a cheat without /mdebug.
            String details = String.format(Locale.ROOT,
                    "dy=%.4f expected=%s excess=%.4f buffer=%.2f %s | prevDy=%s sneak=%s sinceSneak=%d liftOwed=%s"
                            + " sinceJump=%d sinceTp=%d cg=%s",
                    dy, Double.isNaN(expectedDy) ? "takeoff" : String.format(Locale.ROOT, "%.4f", expectedDy),
                    verticalExcess, st.verticalBuffer, groundBelowPrev ? "fromGround" : "air",
                    st.lastStep == null ? "none" : String.format(Locale.ROOT, "%.4f", st.lastStep.y),
                    input.sneak(), st.ticksSinceSneakChange, liftOwedBefore,
                    st.ticksSinceJumpInput, st.ticksSinceTeleport, st.clientOnGround);
            if (AntiCheatManager.reportMovementViolation(player, CHECK_FLY,
                    blatant ? 2.0D : 0.5D, details, wantLagback, st.lastGoodPos)) {
                lagback = true;
            }
            if (st.inAir) {
                // Count an unexplained impulse once: continue the arc from what actually happened.
                st.airVy = dy + crouchNow;
                st.airExcess = Math.min(st.airExcess, crouchHeld);
            }
        }

        debug(player, st, pos, step, violated ? "VIOLATION" : "ok", bound2, expectedDy, supportedPrev, supportedNow);
        if (lagback) {
            return; // baseline is rebuilt when the client confirms the lagback teleport
        }
        st.horizontalVelocityBound2 = nextBound2;
        st.lastWasCheckedAir = airTick;
        st.groundTicks = supportedNow ? st.groundTicks + 1 : 0;
        commit(st, pos, step, supportedNow, horizontalCollision, yaw);
        // Flag-only ticks still advance the anchor, so a later lagback only rubberbands one tick.
        st.lastGoodPos = pos;
        decayBuffers(st);
    }

    /**
     * True if dy is exactly the arc's {@code expected} minus the css crouch offset with sneak released:
     * the client's delayed uncrouch. Releasing sneak while standing can't lower the feet into the floor,
     * so the client drops the offset on its first tick with room below (usually one tick after a jump).
     * That is a one-off displacement, not lost upward speed, so the arc keeps its velocity; otherwise
     * the next tick would look 0.3 too high. Only an exact match counts, and keeping the unmodified
     * arc never allows more height than it already would.
     * (1.21.1: {@code input.sneak()} is the synthetic state from PRESS/RELEASE_SHIFT_KEY.)
     */
    private static boolean isUncrouchDrop(StreamState.Input input, double dy, double expected, double crouchOffset) {
        return crouchOffset > 0.0D && !input.sneak()
                && Math.abs(dy + crouchOffset - expected) <= UNCROUCH_DROP_MATCH;
    }

    /** After a grace or unchecked tick: take the realized movement as the new baseline. */
    private static void rebuildBaseline(StreamState st, Vec3d step, double h2, boolean supportedNow, double crouchOffset) {
        st.horizontalVelocityBound2 = h2;
        st.lastWasCheckedAir = false;
        st.groundTicks = supportedNow ? st.groundTicks + 1 : 0;
        if (supportedNow) {
            st.inAir = false;
        } else {
            st.inAir = true;
            st.airVy = step.y + crouchOffset;
            st.airExcess = 0.0D;
        }
    }

    private static void commit(StreamState st, Vec3d pos, Vec3d step, boolean supported, boolean horizontalCollision, float yaw) {
        st.lastPos = pos;
        st.lastStep = step;
        st.lastSupported = supported;
        st.lastHorizontalCollision = horizontalCollision;
        st.lastYaw = yaw;
        st.hasLastYaw = true;
    }

    private static void decayBuffers(StreamState st) {
        st.horizontalBuffer = Math.max(0.0D, st.horizontalBuffer - SPEED_BUFFER_DECAY);
        st.verticalBuffer = Math.max(0.0D, st.verticalBuffer - FLY_BUFFER_DECAY);
        st.strafeBuffer = Math.max(0.0D, st.strafeBuffer - STRAFE_BUFFER_DECAY);
    }

    /**
     * Unanswered velocity transactions expire after a server-measured round trip (the client can't
     * stretch it by delaying keepalives). The movement stays hard-capped meanwhile either way.
     */
    private static void expireStalePings(ServerPlayerEntity player, StreamState st) {
        if (st.pendingPings.isEmpty()) {
            return;
        }
        long timeout;
        if (st.transactionRttNanos > 0L) {
            timeout = 2L * st.transactionRttNanos + PING_TIMEOUT_SLACK_NANOS;
        } else {
            long latencyNanos = player.networkHandler == null ? 0L : Math.max(0, player.networkHandler.getLatency()) * 1_000_000L;
            timeout = 2L * latencyNanos + 250_000_000L;
        }
        timeout = Math.max(PING_TIMEOUT_MIN_NANOS, Math.min(PING_TIMEOUT_MAX_NANOS, timeout));
        long now = System.nanoTime();
        Iterator<Map.Entry<Integer, StreamState.PendingPing>> it = st.pendingPings.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, StreamState.PendingPing> entry = it.next();
            if (now - entry.getValue().sentNanos() > timeout) {
                it.remove();
                // It may still be applied late: keep a short, capped grace for it.
                st.graceTicks = Math.max(st.graceTicks, VELOCITY_GRACE_TICKS + entry.getValue().carryTicks());
                st.graceSpeed = Math.max(st.graceSpeed, entry.getValue().speed());
                AntiCheatManager.reportMovementViolation(player, CHECK_PACKETS, 1.0D,
                        "unansweredVelocityPing", false, null);
            }
        }
    }

    // ------------------------------------------------------------------------------------------
    // Physics limits
    // ------------------------------------------------------------------------------------------

    /** Upper bound on horizontal speed² after one ground tick (Source friction + ground accelerate). */
    private static double groundBound2(ServerPlayerEntity player, MinehopConfig config, double vRef2) {
        double wish = player.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED) * SPRINT_MARGIN
                * Math.max(config.movement.speed_mul, 0.0D);
        double speed = Math.sqrt(Math.max(vRef2, 0.0D));
        double stop = Math.max(config.movement.sv_stopspeed, 0.0D) * SOURCE_UNIT_TO_BLOCKS_PER_TICK;
        double drop = Math.max(speed, stop) * Math.max(config.movement.sv_friction, 0.0D) * FRAME_TIME;
        double afterFriction = Math.max(speed - drop, 0.0D);
        double accel = Math.min(Math.max(config.movement.sv_accelerate, 0.0D) * FRAME_TIME * wish, wish);
        // Projecting the accelerated component onto wishdir adds at most accel*(2*wish - accel).
        return afterFriction * afterFriction + accel * (2.0D * wish - accel);
    }

    /**
     * Highest horizontal speed² the player's actual keys and yaw could produce this air tick: the
     * mod's substepped air-accelerate (6 or 7 substeps, yaw lerped over at most 90°), with every
     * substep taking the full cap. NaN if a substep pushes against the velocity — then the real
     * acceleration (capped by sneaking/item use) is not bounded by this model, so the tick is skipped.
     */
    private static double maxAirStrafeSpeed2(Vec3d velocity, double sI, double fI, double cap, float startYaw, float endYaw) {
        if (sI == 0.0D && fI == 0.0D) {
            return velocity.x * velocity.x + velocity.z * velocity.z;
        }
        double best = 0.0D;
        for (int steps = 6; steps <= MAX_AIR_SUBSTEPS; steps++) {
            Vec3d result = simulateAirStrafe(velocity, sI, fI, cap, startYaw, endYaw, steps);
            if (result == null) {
                return Double.NaN;
            }
            best = Math.max(best, result.x * result.x + result.z * result.z);
        }
        return best;
    }

    private static Vec3d simulateAirStrafe(Vec3d velocity, double sI, double fI, double cap, float startYaw, float endYaw, int steps) {
        float yawDelta = MathHelper.wrapDegrees(endYaw - startYaw);
        if (yawDelta > SOURCE_MAX_AIR_YAW_DELTA) {
            yawDelta = (float) SOURCE_MAX_AIR_YAW_DELTA;
        } else if (yawDelta < -SOURCE_MAX_AIR_YAW_DELTA) {
            yawDelta = (float) -SOURCE_MAX_AIR_YAW_DELTA;
        }
        float effectiveStartYaw = endYaw - yawDelta;
        Vec3d hv = velocity;
        for (int i = 0; i < steps; i++) {
            float frac = (float) ((i + 1.0D) / steps);
            float yawI = MathHelper.lerpAngleDegrees(frac, effectiveStartYaw, endYaw);
            Vec3d wish = MovementUtil.movementInputToVelocity(new Vec3d(sI, 0.0D, fI), 1.0F, yawI);
            double length = wish.horizontalLength();
            if (length <= 0.0D) {
                continue;
            }
            Vec3d dir = new Vec3d(wish.x / length, 0.0D, wish.z / length);
            double along = hv.dotProduct(dir);
            if (along < 0.0D) {
                return null;
            }
            double add = cap - along;
            if (add > 0.0D) {
                hv = hv.add(dir.multiply(add));
            }
        }
        return hv;
    }

    /**
     * The client freezes vertically while the chunk it stands in hasn't arrived. That's only plausible
     * shortly after a teleport and while the server still has that chunk queued, or within a round
     * trip of the teleport — not for 10 s after every teleport (a hover cheat would hide in that).
     */
    private static boolean isChunkStillLoading(ServerPlayerEntity player, StreamState st, Vec3d pos) {
        if (st.ticksSinceTeleport > CHUNK_LOAD_WINDOW_TICKS || player.networkHandler == null) {
            return false;
        }
        long chunk = ChunkPos.toLong(MathHelper.floor(pos.x) >> 4, MathHelper.floor(pos.z) >> 4);
        if (player.networkHandler.chunkDataSender.isInNextBatch(chunk)) {
            return true;
        }
        return st.ticksSinceTeleport <= 10 + 2 * latencyTicks(player);
    }

    private static double jumpBoost(ServerPlayerEntity player) {
        if (!player.hasStatusEffect(StatusEffects.JUMP_BOOST)) {
            return 0.0D;
        }
        var effect = player.getStatusEffect(StatusEffects.JUMP_BOOST);
        return effect == null ? 0.0D : 0.1F * (effect.getAmplifier() + 1);
    }

    private static double crouchDelta(ServerPlayerEntity player) {
        double standing = player.getDimensions(EntityPose.STANDING).height();
        double crouching = player.getDimensions(EntityPose.CROUCHING).height();
        return Math.max(standing - crouching, 0.0D);
    }

    // ------------------------------------------------------------------------------------------
    // Environment
    // ------------------------------------------------------------------------------------------

    /** Solid ground (or a collidable entity) within reach below the feet at {@code pos}. */
    private static boolean isSupported(ServerWorld world, ServerPlayerEntity player, Vec3d pos) {
        Box box = player.getDimensions(EntityPose.STANDING).getBoxAt(pos);
        Box below = new Box(box.minX, box.minY - SUPPORT_PROBE_DEPTH, box.minZ, box.maxX, box.minY - 1.0E-6D, box.maxZ);
        return !world.isSpaceEmpty(player, below);
    }

    /**
     * Confirms the client's own on-ground claim: ground somewhere directly under its footprint (not
     * beside it — a wall must not count as a floor).
     */
    private static boolean isGroundUnderFeet(ServerWorld world, ServerPlayerEntity player, Vec3d pos) {
        Box box = player.getDimensions(EntityPose.STANDING).getBoxAt(pos);
        Box below = new Box(box.minX, box.minY - CLIENT_GROUND_DEPTH, box.minZ, box.maxX, box.minY + 1.0E-3D, box.maxZ);
        return !world.isSpaceEmpty(player, below);
    }

    /** Why this tick's movement can't be modeled (null if it can). */
    private static String environmentExemption(ServerPlayerEntity player, ServerWorld world, Vec3d from, Vec3d to) {
        if (player.hasVehicle()) {
            return "vehicle";
        }
        if (player.isFallFlying()) {
            return "glide";
        }
        if (player.isUsingRiptide()) {
            return "riptide";
        }
        if (player.isSleeping() || !player.isAlive()) {
            return "inactive";
        }
        PlayerAbilities abilities = player.getAbilities();
        if (abilities.flying || abilities.allowFlying) {
            return "flight";
        }
        if (player.hasStatusEffect(StatusEffects.LEVITATION) || player.hasStatusEffect(StatusEffects.SLOW_FALLING)) {
            return "effect";
        }
        EntityDimensions dims = player.getDimensions(EntityPose.STANDING);
        Box toBox = dims.getBoxAt(to);
        Box body = from.squaredDistanceTo(to) <= MAX_ENVIRONMENT_SCAN_STEP * MAX_ENVIRONMENT_SCAN_STEP
                ? dims.getBoxAt(from).union(toBox)
                : toBox;
        if (SurfRampEntity.hasNearbyRamp(world, body, SURF_RAMP_PROXIMITY)) {
            return "surf";
        }
        Box bodyScan = body.expand(0.1D);
        BlockPos.Mutable mutable = new BlockPos.Mutable();
        int minX = MathHelper.floor(bodyScan.minX);
        int maxX = MathHelper.floor(bodyScan.maxX);
        int minY = MathHelper.floor(bodyScan.minY - 0.6D);
        int maxY = MathHelper.floor(bodyScan.maxY);
        int minZ = MathHelper.floor(bodyScan.minZ);
        int maxZ = MathHelper.floor(bodyScan.maxZ);
        int bodyMinY = MathHelper.floor(bodyScan.minY);
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    mutable.set(x, y, z);
                    BlockState state = world.getBlockState(mutable);
                    if (state.isAir()) {
                        continue;
                    }
                    // Under the feet: blocks that launch or redirect the player.
                    if (state.isOf(Blocks.SLIME_BLOCK) || state.isOf(Blocks.HONEY_BLOCK)
                            || state.getBlock() instanceof BedBlock || state.isOf(ModBlocks.BOOSTER_BLOCK)) {
                        return "bounce";
                    }
                    if (y < bodyMinY) {
                        continue;
                    }
                    // In the body: fluids, climbables and blocks with their own movement physics.
                    if (!state.getFluidState().isEmpty()) {
                        return "fluid";
                    }
                    if (state.isIn(BlockTags.CLIMBABLE) || state.isOf(Blocks.SCAFFOLDING)
                            || state.isOf(Blocks.POWDER_SNOW) || state.isOf(Blocks.COBWEB)
                            || state.isOf(Blocks.SWEET_BERRY_BUSH) || state.isOf(Blocks.BUBBLE_COLUMN)) {
                        return "block";
                    }
                    if (state.isOf(Blocks.MOVING_PISTON) || state.isOf(Blocks.PISTON_HEAD)) {
                        return "piston";
                    }
                }
            }
        }
        return null;
    }

    /**
     * Entities that can push the player this tick (each push adds ≤0.05 blocks/tick). Other players
     * count too: on HNS maps the clients push each other even though the server treats players as
     * not pushable.
     */
    private static int countPushers(ServerPlayerEntity player, ServerWorld world, Vec3d pos) {
        Box box = player.getDimensions(EntityPose.STANDING).getBoxAt(pos).expand(0.3D);
        return world.getOtherEntities(player, box,
                entity -> !entity.isSpectator() && (entity.isPushable() || entity instanceof PlayerEntity)).size();
    }

    // ------------------------------------------------------------------------------------------
    // Debug
    // ------------------------------------------------------------------------------------------

    private static void debug(ServerPlayerEntity player, StreamState st, Vec3d pos, Vec3d step, String status,
                              double bound2, double expectedDy, boolean supportedPrev, boolean supportedNow) {
        if (Minehop.surfDebugPlayers.isEmpty() || !Minehop.surfDebugPlayers.contains(player.getUuid())) {
            return;
        }
        Minehop.LOGGER.info(String.format(Locale.ROOT,
                "[STREAM] %s %s pos=(%.4f,%.4f,%.4f) cg=%b h=%.4f limit=%s dy=%.4f expDy=%s sup=%s>%s air=%b airVy=%.4f keys=%s%s%s%s%s%s",
                player.getNameForScoreboard(), status, pos.x, pos.y, pos.z, st.clientOnGround,
                Math.sqrt(step.x * step.x + step.z * step.z),
                Double.isNaN(bound2) ? "-" : String.format(Locale.ROOT, "%.4f", Math.sqrt(bound2)),
                step.y,
                Double.isNaN(expectedDy) ? "-" : String.format(Locale.ROOT, "%.4f", expectedDy),
                supportedPrev ? "G" : "A", supportedNow ? "G" : "A", st.inAir, st.airVy,
                st.input.forward() ? "W" : "", st.input.left() ? "A" : "", st.input.backward() ? "S" : "",
                st.input.right() ? "D" : "", st.input.jump() ? "J" : "", st.input.sneak() ? "C" : ""));
    }

    /** True if {@code entity} is a server player whose stream is tracked (for diagnostics). */
    public static boolean isTracked(Entity entity) {
        return entity instanceof ServerPlayerEntity player && STATES.containsKey(player.getUuid());
    }
}
