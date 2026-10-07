package net.nerdorg.minehop.anticheat.stream;

import net.nerdorg.minehop.platform.Services;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.network.protocol.game.ClientboundPingPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
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
 * own simulation of the player is not a usable reference. Instead every client tick (delimited by the
 * vanilla {@code ClientTickEndC2SPacket}) is checked against hard limits derived from the mod's own
 * physics, which the legit client runs exactly:
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
 * <p><b>1.20.1 backport (pre-1.21.2 protocol).</b> There is no {@code ClientTickEndC2SPacket} and no
 * key-state {@code PlayerInputC2SPacket}, so:
 * <ul>
 *   <li>Tick boundary = each move packet the server processes. A pre-1.21.2 client sends one move
 *       packet per tick in which it moved (&gt;2e-4 blocks), turned or changed its ground flag, and a
 *       forced position packet every 20 idle ticks; idle ticks are folded into the next packet (they
 *       carry no movement, so the per-tick limits still hold). {@link TimerBalance} counts each move
 *       packet as one tick, so idling only builds credit, bounded by its credit floor.</li>
 *   <li>Sneak and sprint come from {@code ClientCommandC2SPacket} press/release commands. Movement
 *       keys and jump are unknown: jump is always treated as possible (no "walking friction-only"
 *       regime) and the flag-only Strafe replay is skipped. Missing input never creates a flag.</li>
 *   <li>Velocity transactions use {@code PlayPingS2CPacket}/{@code PlayPongC2SPacket}; the chunk-stall
 *       exemption falls back to "chunk not loaded on the server" + the post-teleport window (no
 *       {@code ChunkDataSender} before 1.20.2).</li>
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
    /** Timer heartbeat pings (see TimerBalance): their own tag so they never start velocity grace. */
    private static final int HEARTBEAT_TAG = 0x4D490000;
    private static final int HEARTBEAT_INTERVAL_TICKS = 5;
    /** Withheld-tick evidence is not reported this soon after a teleport/respawn (world switching). */
    private static final long WITHHELD_REPORT_QUIET_NANOS = 15_000_000_000L;
    private static final AtomicInteger PING_SEQUENCE = new AtomicInteger();

    private static final double SOURCE_UNIT_TO_BLOCKS_PER_TICK = 1.0D / 800.0D;
    private static final double FRAME_TIME = 1.0D / 20.0D;
    private static final int MAX_AIR_SUBSTEPS = 7;
    private static final double SOURCE_MAX_AIR_YAW_DELTA = 90.0D;
    private static final double SUPPORT_PROBE_DEPTH = 0.26D;
    private static final double CLIENT_GROUND_DEPTH = 0.6D;
    /** The client's friction ground test (LivingEntityMixin#minehop$hasRealGroundBelow). */
    private static final double FRICTION_GROUND_DEPTH = 0.20D;
    private static final double FRICTION_GROUND_INSET = 0.001D;
    // LivingEntityMixin's jump buffer fires only while vertical velocity <= 0.10 b/t.
    private static final double JUMP_BUFFER_MAX_VY = 0.10D;
    private static final double VANILLA_STEP_HEIGHT = 0.6D;
    private static final double AUTO_STEP_HEIGHT = 1.12D;
    /** Player#maybeBackOffFromEdge shortens the displacement in steps of this size. */
    private static final double SNEAK_BACK_OFF_STEP = 0.05D;
    /**
     * Consecutive non-walking ticks a sneak clip may carry the speed bound without re-anchoring it to the
     * realized movement. A legit clip lasts the few ticks the client descends within step height of a
     * ledge plus its landing (under 12 even with auto-step); walking carries include friction and
     * can't grow the bound, so they aren't capped (nor counted).
     */
    private static final int MAX_SNEAK_CARRY_TICKS = 12;
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
    /** Slack when matching a dy deficit against the crouch offset still pending a drop. */
    private static final double UNCROUCH_DROP_MATCH = 0.005D;
    /** Smallest dy deficit treated as an uncrouch drop (anything less is float noise). */
    private static final double UNCROUCH_DROP_MIN = 0.005D;
    /** A client stays frozen (dy == 0) until the chunk it was teleported into arrives and is built. */
    private static final int FROZEN_AFTER_TELEPORT_TICKS = 40;
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
    // 1.20.1: the client doesn't report movement keys, so the key replay (flag-only) can't run.
    private static final boolean MOVEMENT_KEYS_KNOWN = false;
    // 1.20.1 has no tick-rate manager: a server tick is always 50 ms.
    private static final long NANOS_PER_TICK = 50_000_000L;

    private static final Map<UUID, StreamState> STATES = new ConcurrentHashMap<>();

    private MovementValidator() {
    }

    private static StreamState state(ServerPlayer player) {
        return STATES.computeIfAbsent(player.getUUID(), uuid -> new StreamState());
    }

    public static void clear(UUID uuid) {
        if (uuid != null) {
            STATES.remove(uuid);
        }
    }

    /** 1.20.1: client ticks handed on so far, inferred idle ticks included (ClientTick#streamIndex); -1 if none. */
    public static long streamTicks(ServerPlayer player) {
        StreamState st = player == null ? null : STATES.get(player.getUUID());
        return st == null ? -1L : st.streamTicks;
    }

    /** Client ticks validated so far for this player, or -1 if none (used to check run timing). */
    public static long clientTicks(ServerPlayer player) {
        StreamState st = player == null ? null : STATES.get(player.getUUID());
        return st == null ? -1L : st.clientTicks;
    }

    /**
     * Jump stats for spectators and replays, derived here from the movement the server accepted instead of taken
     * from the client: the same rule as the client's own SSJ counter (MinehopClient), i.e. an upward take-off while
     * jump is held counts a jump and records the horizontal speed, and releasing jump ends the chain. Not used by
     * any check.
     *
     * <p>1.20.1: the client doesn't report the jump key (there is no key-state input packet before 1.21.2). Holding
     * jump makes the client take off on its first tick back on the ground, so the chain goes on while every landing
     * is followed by a take-off within one ground tick; a second client tick on the ground means jump was released
     * and ends the chain (the client's counter resets on the release itself, i.e. at most a jump's air time earlier).
     */
    private static void trackJumps(StreamState st, Vec3 step, double jumpVy) {
        double previousVy = st.lastStep == null ? 0.0D : st.lastStep.y;
        if (previousVy <= 0.05D && step.y >= Math.max(0.05D, jumpVy * 0.6D)) {
            st.jumpCount++;
            st.lastJumpSpeed = Math.sqrt(step.x * step.x + step.z * step.z);
            st.jumpGroundTicks = 0;
            return;
        }
        if (!st.clientOnGround) {
            st.jumpGroundTicks = 0;
        } else if (++st.jumpGroundTicks >= 2) {
            st.jumpCount = 0;
            st.lastJumpSpeed = 0.0D;
        }
    }

    /** Consecutive jumps (bhop chain) while jump is held, as seen in the client's accepted movement; 0 if none. */
    public static int jumpCount(ServerPlayer player) {
        StreamState st = player == null ? null : STATES.get(player.getUUID());
        return st == null ? 0 : st.jumpCount;
    }

    /** Horizontal speed (blocks/tick) at the last take-off of the current jump chain; 0 if none. */
    public static double lastJumpSpeed(ServerPlayer player) {
        StreamState st = player == null ? null : STATES.get(player.getUUID());
        return st == null ? 0.0D : st.lastJumpSpeed;
    }

    /**
     * The player's realized movement over its last client tick (null if unknown, e.g. right after a
     * teleport). Exactly one client tick, unlike a server-tick position delta, which covers however many
     * of the client's move packets the server handled during that tick.
     */
    public static Vec3 lastClientStep(ServerPlayer player) {
        StreamState st = player == null ? null : STATES.get(player.getUUID());
        return st == null ? null : st.lastStep;
    }

    // ------------------------------------------------------------------------------------------
    // Packet entry points (called from the network handler mixins)
    // ------------------------------------------------------------------------------------------

    /**
     * Server thread. 1.20.1: sneak/sprint key changes arrive as client commands, sent right before the
     * move packet of the tick they happened in.
     */
    public static void onClientCommand(ServerPlayer player, ServerboundPlayerCommandPacket.Action mode) {
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
     * Server thread, after vanilla processed a move packet. 1.20.1: each move packet ends one client
     * tick (there is no tick-end packet), so the tick is finalized here. {@code accepted} = vanilla
     * applied the client's position (otherwise only look/ground data is used, like a 1.21.2+ tick
     * whose move was rejected).
     */
    public static void onMovePacketProcessed(ServerPlayer player, ServerboundMovePlayerPacket packet, boolean accepted) {
        if (player == null || packet == null) {
            return;
        }
        StreamState st = state(player);
        // The tick's arrival on the network thread, carried by the packet itself (see onMovePacketNetwork).
        long stamp = packet instanceof MovePacketArrival arrival ? arrival.minehop$arrivalNanos() : 0L;
        boolean networkTimed = stamp != 0L;
        long arrivalNanos = networkTimed ? stamp : System.nanoTime();
        if (accepted && packet.hasPosition()) {
            Vec3 pos = player.position();
            if (st.teleportEchoPending) {
                st.teleportEchoPending = false;
                if (st.lastPos != null && pos.distanceToSqr(st.lastPos) < 1.0E-12D) {
                    // The Full packet a client echoes after confirming a teleport is not a tick.
                    return;
                }
            }
            st.tickPos = pos;
            // 1.20.1 move packets carry no horizontal-collision flag (added in 1.21.2); it is only
            // used to skip the Strafe replay, which can't run on 1.20.1 anyway.
            st.tickHorizontalCollision = false;
        }
        int idleTicks = idleTicksBefore(st, packet, arrivalNanos, networkTimed);
        st.clientOnGround = packet.isOnGround();
        if (packet.hasRotation()) {
            // Raw (unwrapped) yaw exactly as the client's travel() used it.
            st.tickYaw = packet.getYRot(st.tickYaw);
            st.tickPitch = packet.getXRot(st.tickPitch);
            st.tickHasYaw = true;
        }
        finalizeTick(player, st, arrivalNanos, networkTimed, idleTicks);
    }

    /** A pre-1.21.2 client sends a position at least every this many of its ticks (LocalPlayer's position reminder). */
    private static final int POSITION_REMINDER_TICKS = 20;
    /** Below this a client's movement counts as noise (LocalPlayer only sends a position that moved more). */
    private static final double CLIENT_POSITION_EPSILON = 2.0E-4D;
    /** Horizontal step (blocks/tick) on the ground below which the client may come to rest on its next tick. */
    private static final double RESTING_STEP = 0.03D;

    /**
     * 1.20.1: how many client ticks passed without a packet right before this move packet (see ClientTick, "1.20.1").
     * A pre-1.21.2 client sends a move packet only in a tick in which it moved more than 2e-4 blocks since its last
     * sent position, turned, or changed its ground flag, and sends its position anyway once 20 ticks have passed since
     * the last one. So a position packet that didn't move ends exactly 20 ticks since the previous position packet,
     * and the ticks in between that sent nothing were idle (exact). Any other packet after a gap is the end of an idle
     * stretch only if the client was at rest before it (on the ground, slower than {@link #RESTING_STEP}); its length
     * is then taken from the arrival times, one tick per 50 ms, never more than the gap or the 20-tick bound allows. A
     * moving client's packets that were merely delayed by the network are therefore never padded. Not used by any
     * check: only the tick stream handed on (replays, run timing) sees these ticks.
     */
    private static int idleTicksBefore(StreamState st, ServerboundMovePlayerPacket packet, long arrivalNanos, boolean networkTimed) {
        boolean positional = packet.hasPosition();
        Vec3 packetPos = positional ? new Vec3(packet.getX(0.0D), packet.getY(0.0D), packet.getZ(0.0D)) : null;
        int bound = Math.max(0, POSITION_REMINDER_TICKS - 1 - st.ticksSincePositional);
        int idle = 0;
        if (st.lastFramePos != null) {
            boolean reminder = positional && st.lastPacketPos != null
                    && packetPos.distanceToSqr(st.lastPacketPos) <= CLIENT_POSITION_EPSILON * CLIENT_POSITION_EPSILON;
            if (reminder) {
                idle = bound;
            } else if (st.lastTickResting && networkTimed && st.lastTickNetworkTimed) {
                long gapTicks = Math.round((arrivalNanos - st.lastTickArrivalNanos) / (double) NANOS_PER_TICK);
                idle = (int) Math.max(0L, Math.min(gapTicks - 1L, bound));
            }
        }
        // Bookkeeping for the next packet.
        if (positional) {
            double dx = st.lastPacketPos == null ? 0.0D : packetPos.x - st.lastPacketPos.x;
            double dz = st.lastPacketPos == null ? 0.0D : packetPos.z - st.lastPacketPos.z;
            st.lastTickResting = packet.isOnGround() && dx * dx + dz * dz <= RESTING_STEP * RESTING_STEP;
            st.lastPacketPos = packetPos;
            st.ticksSincePositional = 0;
        } else {
            // A look or ground-flag change without moving: the client didn't move this tick.
            st.lastTickResting = packet.isOnGround();
            st.ticksSincePositional += idle + 1;
        }
        st.lastTickArrivalNanos = arrivalNanos;
        st.lastTickNetworkTimed = networkTimed;
        return idle;
    }

    /**
     * Netty thread: a move packet arrived; on 1.20.1 every move packet is one client tick. The arrival time goes with
     * the packet to its server-thread pass (the tick's arrival, see ClientTick#arrivalNanos).
     */
    public static void onMovePacketNetwork(ServerPlayer player, ServerboundMovePlayerPacket packet) {
        MinecraftServer server = player == null ? null : player.getServer();
        if (server == null) {
            return;
        }
        long now = System.nanoTime();
        if (packet instanceof MovePacketArrival arrival) {
            arrival.minehop$setArrivalNanos(now);
        }
        handleTimer(player, server, state(player).timer.onMovePacket(now, NANOS_PER_TICK));
    }

    /** Netty thread: the client confirmed a teleport; its next move packet is an echo, not a tick. */
    public static void onTeleportConfirmNetwork(ServerPlayer player) {
        if (player != null) {
            state(player).timer.onTeleportConfirm();
        }
    }

    private static void handleTimer(ServerPlayer player, MinecraftServer server, TimerBalance.Violation violation) {
        if (violation != null) {
            UUID uuid = player.getUUID();
            server.execute(() -> onTimerViolation(uuid, server, violation));
        }
    }

    /**
     * Server thread: vanilla is about to ignore moves until the client confirms this teleport.
     * 1.20.1: a teleport carries no velocity; the client keeps its velocity only on axes sent as
     * relative (X/Y/Z flags) and zeroes it otherwise.
     */
    public static void onTeleportRequested(ServerPlayer player, Set<RelativeMovement> flags) {
        if (player == null) {
            return;
        }
        StreamState st = state(player);
        st.awaitingTeleport = true;
        st.lastTeleportNanos = System.nanoTime();
        st.frameDiscontinuity = true;
        st.clearTickAccumulation();
        double speed = 0.0D;
        if (flags != null && (flags.contains(RelativeMovement.X) || flags.contains(RelativeMovement.Y)
                || flags.contains(RelativeMovement.Z))) {
            speed += st.lastStep == null ? 0.0D : st.lastStep.length();
        }
        st.pendingTeleportSpeed = Math.max(st.pendingTeleportSpeed, speed);
    }

    /** Server thread: the client confirmed the pending teleport and now stands at the target. */
    public static void onTeleportConfirmed(ServerPlayer player) {
        if (player == null) {
            return;
        }
        StreamState st = state(player);
        st.awaitingTeleport = false;
        st.lastTeleportNanos = System.nanoTime();
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
        st.resetBaseline(player.position());
    }

    /** Called right before our own lagback teleport is requested. */
    public static void onLagbackIssued(ServerPlayer player) {
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
    public static int onPacketSent(ServerPlayer player, Packet<?> packet) {
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

    public static boolean isHeartbeatPing(int id) {
        return (id & PING_TAG_MASK) == HEARTBEAT_TAG;
    }

    /** Netty thread, in packet order: a timer heartbeat was answered. */
    public static void onHeartbeatPongNetwork(ServerPlayer player, int id) {
        if (player != null) {
            state(player).timer.onHeartbeatPong(id, System.nanoTime());
        }
    }

    private static int heartbeatCountdown;

    /**
     * Server thread, every server tick: send the timer heartbeat pings and report clients that keep
     * answering them while their ticks stop (evidence of withheld ticks, flag only).
     *
     * <p>1.20.1 (pre-1.21.2 tick model): the heartbeats are play-phase {@code PlayPingS2CPacket}s. An
     * idle player sends no move packets (its ticks) while it keeps answering heartbeats, so a tick gap
     * with pongs inside is normal here and "ticksWithheldWhileAnswering" is never reported.
     * "heartbeatsUnanswered" is still reported: TimerBalance only raises it from a tick, i.e. while
     * move packets keep arriving and the heartbeats sent alongside them go unanswered.
     */
    public static void onServerTick(MinecraftServer server) {
        if (--heartbeatCountdown > 0) {
            return;
        }
        heartbeatCountdown = HEARTBEAT_INTERVAL_TICKS;
        long now = System.nanoTime();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.connection == null) {
                continue;
            }
            StreamState st = state(player);
            int id = HEARTBEAT_TAG | (PING_SEQUENCE.incrementAndGet() & 0xFFFF);
            st.timer.onHeartbeatSent(id, now);
            player.connection.send(new ClientboundPingPacket(id));
            st.timer.takeWithheldGaps(); // 1.20.1: an idle player's gaps; not evidence (see above)
            boolean unanswered = st.timer.takeWithheldHeartbeats();
            if (unanswered && !isUnchecked(player) && !st.awaitingTeleport
                    && now - st.lastTeleportNanos > WITHHELD_REPORT_QUIET_NANOS) {
                AntiCheatManager.reportMovementViolation(player, CHECK_PACKETS, 1.0D,
                        "heartbeatsUnanswered", false, null);
            }
        }
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
        if (packet instanceof ClientboundSetEntityMotionPacket velocity) {
            if (velocity.getId() != selfId) {
                return null;
            }
            // 1.20.1 exposes the raw wire values (velocity * 8000).
            return new double[]{0, new Vec3(velocity.getXa() / 8000.0D, velocity.getYa() / 8000.0D,
                    velocity.getZa() / 8000.0D).length()};
        }
        if (packet instanceof ClientboundExplodePacket explosion) {
            // 1.20.1 always sends the knockback (zero when the player isn't affected).
            double knock = new Vec3(explosion.getKnockbackX(), explosion.getKnockbackY(),
                    explosion.getKnockbackZ()).length();
            return knock > 0.0D ? new double[]{0, knock} : null;
        }
        if (packet instanceof ClientboundCustomPayloadPacket custom
                && ResetVelocityCarryPayload.HANDSHAKE_ID.equals(custom.getIdentifier())) {
            // 1.20.1 custom payloads are already serialized: decode the bytes (getData() is a copy).
            ResetVelocityCarryPayload carry;
            try {
                carry = ResetVelocityCarryPayload.CODEC.decode(custom.getData());
            } catch (RuntimeException e) {
                return null;
            }
            return new double[]{Math.max(1, carry.ticks()), new Vec3(carry.x(), carry.y(), carry.z()).length()};
        }
        if (packet instanceof ClientboundBundlePacket bundle) {
            double[] best = null;
            for (Packet<?> inner : bundle.subPackets()) {
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
        ServerPlayer player = server.getPlayerList().getPlayer(uuid);
        if (player == null || isUnchecked(player)) {
            return;
        }
        StreamState st = state(player);
        // Right after a stall the "excess" may be the client catching up, and a single violation may
        // be a one-off network anomaly: both are evidence only. A client that keeps getting ahead of
        // real time is running a timer and gets lagged back.
        String details = String.format(Locale.ROOT, "aheadOfRealTime=%dms%s%s",
                violation.aheadNanos() / 1_000_000L, violation.afterLagBurst() ? " afterLagBurst" : "",
                violation.sustained() ? " sustained" : "");
        AntiCheatManager.reportMovementViolation(player, CHECK_TIMER, 2.0D, details,
                violation.sustained() && !violation.afterLagBurst() && !st.awaitingTeleport, st.lastGoodPos);
    }

    // ------------------------------------------------------------------------------------------
    // Per-tick validation
    // ------------------------------------------------------------------------------------------

    private static boolean isUnchecked(ServerPlayer player) {
        if (Services.PLATFORM.isFakePlayer(player) || !AntiCheatManager.isEnabled() || AntiCheatManager.isExempt(player)) {
            return true;
        }
        MinecraftServer server = player.getServer();
        return server != null && server.isSingleplayerOwner(player.getGameProfile());
    }

    private static int latencyTicks(ServerPlayer player) {
        return player.connection == null ? 0 : Math.max(0, player.latency) / 50;
    }

    /**
     * One client tick is complete: validate it, then hand it on as a {@link ClientTick} (replay recording, run timing
     * and stats, see ReplayEvents#onClientTick). {@code arrivalNanos}: when its last packet arrived on the network
     * thread ({@code networkTimed}), else the server-thread time now. 1.20.1: {@code idleTicks} idle ticks the client
     * sent nothing for are handed on first (see idleTicksBefore).
     */
    private static void finalizeTick(ServerPlayer player, StreamState st, long arrivalNanos, boolean networkTimed, int idleTicks) {
        // The client's view at the end of this tick, taken before validation consumes the tick's packets. Until the
        // client confirms a teleport the server ignores its moves and holds it at the target.
        boolean awaitingTeleport = st.awaitingTeleport;
        if (idleTicks > 0 && st.lastFramePos != null && !st.frameDiscontinuity && !awaitingTeleport) {
            // The client stood where the last tick left it, looking the same way, on the same ground flag.
            float idleYaw = st.frameHasRotation ? st.frameYaw : player.getYRot();
            float idlePitch = st.frameHasRotation ? st.framePitch : player.getXRot();
            ClientTick.Input idleInput = clientInput(st.lastFrameInput);
            for (int i = idleTicks; i >= 1; i--) {
                if (st.lastFrameOnGround && ++st.jumpGroundTicks >= 2) {
                    // Standing still on the ground: jump isn't held (see trackJumps).
                    st.jumpCount = 0;
                    st.lastJumpSpeed = 0.0D;
                }
                handOn(player, new ClientTick(st.clientTicks, arrivalNanos - i * NANOS_PER_TICK, networkTimed,
                        st.lastFramePos, idleYaw, idlePitch, idleInput, st.lastFrameOnGround, false, false, st.jumpCount,
                        st.lastJumpSpeed, false, ++st.streamTicks, true));
            }
        }
        Vec3 framePos = awaitingTeleport ? player.position()
                : st.tickPos != null ? st.tickPos : (st.lastPos != null ? st.lastPos : player.position());
        if (st.tickHasYaw) {
            st.frameYaw = st.tickYaw;
            st.framePitch = st.tickPitch;
            st.frameHasRotation = true;
        }
        int jumpsBefore = st.jumpCount;
        validateTick(player, st);
        boolean discontinuity = st.frameDiscontinuity;
        st.frameDiscontinuity = false;
        st.lastFramePos = framePos;
        st.lastFrameOnGround = st.clientOnGround;
        st.lastFrameInput = st.input;
        handOn(player, new ClientTick(st.clientTicks, arrivalNanos, networkTimed, framePos,
                st.frameHasRotation ? st.frameYaw : player.getYRot(), st.frameHasRotation ? st.framePitch : player.getXRot(),
                clientInput(st.input), st.clientOnGround, discontinuity, awaitingTeleport, st.jumpCount, st.lastJumpSpeed,
                st.jumpCount > jumpsBefore, ++st.streamTicks, false));
    }

    /** 1.20.1: the keys a client reports (sneak, sprint) as a ClientTick input; the others are unknown. */
    private static ClientTick.Input clientInput(StreamState.StreamInput input) {
        return new ClientTick.Input(false, false, false, false, false, input.sneak(), input.sprint());
    }

    private static void handOn(ServerPlayer player, ClientTick tick) {
        try {
            net.nerdorg.minehop.replays.ReplayEvents.onClientTick(player, tick);
        } catch (RuntimeException e) {
            // Recording must never break the anticheat stream.
            if (!tickListenerFailed) {
                tickListenerFailed = true;
                Minehop.LOGGER.error("Client tick listener failed (logged once)", e);
            }
        }
    }

    private static boolean tickListenerFailed;

    private static void validateTick(ServerPlayer player, StreamState st) {
        st.clientTicks++;
        StreamState.StreamInput input = st.input;
        st.ticksSinceJumpInput = input.jump() ? 0 : Math.min(st.ticksSinceJumpInput + 1, 1000);
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
        ServerLevel world = player.serverLevel();
        Vec3 pos = st.tickPos != null ? st.tickPos : (st.lastPos != null ? st.lastPos : player.position());
        float yaw = st.tickHasYaw ? st.tickYaw : (st.hasLastYaw ? st.lastYaw : player.getYRot());
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
        Vec3 step = pos.subtract(st.lastPos);
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
        trackJumps(st, step, jumpVy);

        // Ground within reach only counts if the player isn't still rising fast past it (launched
        // alongside a ledge). Up to 0.10 b/t upward the mod's jump buffer may legitimately fire.
        boolean stillRising = st.inAir && !supportedPrev && step.y > 1.0E-3D
                && st.airVy - effectiveGravity > JUMP_BUFFER_MAX_VY + 1.0E-6D;
        // Minecraft resolves vertical collision BEFORE horizontal, so a landing on a block edge
        // happens at the previous tick's x/z even if the player then slides off it horizontally.
        // The client's own ground flag is only accepted with ground directly under its feet.
        boolean supportedNow = !stillRising && (isSupported(world, player, pos)
                || isSupported(world, player, new Vec3(st.lastPos.x, pos.y, st.lastPos.z))
                || (st.clientOnGround && isGroundUnderFeet(world, player, pos)));
        // Ground friction: the client applies it only after two ticks that START with ground within 0.20
        // under its box (LivingEntityMixin#minehop$hasRealGroundBelow), a narrower test than the 0.26
        // support reach, so a tick ending 0.20-0.26 above ground leaves the next one frictionless.
        // Hovering in that band isn't legit though (a ballistic arc crosses it within a few ticks), so a
        // longer stay there counts as ground.
        boolean realGroundNow = hasFrictionGround(world, player, pos, FRICTION_GROUND_INSET);
        boolean hoverNow = !realGroundNow && isSupported(world, player, pos)
                && !hasFrictionGround(world, player, pos, 0.0D);
        st.bandHoverTicks = hoverNow ? st.bandHoverTicks + 1 : 0;
        boolean frictionGroundNow = realGroundNow
                || (hoverNow && st.bandHoverTicks > maxBandDwellTicks(effectiveGravity));

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
        // While sneak is held the client has (or is owed) its crouch offset; after release it drops
        // up to that much, possibly ticks later and in pieces (see uncrouchDrop).
        if (input.sneak()) {
            st.pendingCrouchDrop = crouchOffset;
        }

        if (isUnchecked(player) || !config.enabled) {
            st.crouchLiftOwed = true;
            rebuildBaseline(st, step, h2, supportedNow, frictionGroundNow, crouchOffset);
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
            rebuildBaseline(st, step, h2, supportedNow, frictionGroundNow, crouchOffset);
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
        // (1.20.1: sneak state comes from the RELEASE_SHIFT_KEY command the client sends right before
        // this tick's move packet, so ticksSinceSneakChange means the same as with the 1.21.2+ input.)
        double dropNow = 0.0D;
        if (crouchOffset > 0.0D && !input.sneak()) {
            dropNow = st.ticksSinceSneakChange <= 1
                    ? crouchOffset
                    : st.inAir ? uncrouchDrop(st, step.y, st.airVy - effectiveGravity) : 0.0D;
        }
        boolean groundBelowPrev = supportedPrev || (dropNow > 0.0D
                && isSupported(world, player, st.lastPos.add(0.0D, -dropNow, 0.0D)));
        // Lift allowance: on a sneak change, or while a lift is still owed (delayed by a ceiling).
        double crouchNow = (st.ticksSinceSneakChange <= 1 || (input.sneak() && st.crouchLiftOwed)) ? crouchOffset : 0.0D;

        // ---------------- horizontal: speed² gain bound ----------------
        double vRef2 = st.horizontalVelocityBound2;
        double airBound2 = vRef2 + MAX_AIR_SUBSTEPS * effectiveCap * effectiveCap;
        double groundBound2 = groundBound2(player, config, vRef2);
        // Two ticks starting on the client's friction ground (groundTicks) without jumping = ground
        // movement: friction always applies.
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
        // there; on the ground that bound already includes friction, so it can't creep upward. The
        // clip also happens in the air up to step height above a ledge (isSneakEdgeClip). Without
        // friction a carried bound still grows by a tick of air-accelerate, so that carry is capped:
        // it can defer a few ticks of legit gain, never bank speed.
        // 1.20.1: no STEP_HEIGHT attribute (1.20.5+); the client's edge back-off reads maxUpStep() directly.
        double backOffReach = Math.max(config.movement.auto_step_up ? AUTO_STEP_HEIGHT : VANILLA_STEP_HEIGHT,
                player.maxUpStep());
        boolean sneakClip = (sneakingNearGround
                || (input.sneak() && isSneakEdgeClip(world, player, st.lastPos, step, backOffReach, crouchNow)))
                && (walking || st.sneakCarryTicks < MAX_SNEAK_CARRY_TICKS);
        double nextBound2 = (sneakClip || autoStep) ? Math.max(h2, bound2) : h2;

        // ---------------- strafe: replay real inputs (flag-only) ----------------
        boolean airTick = !supportedPrev && !supportedNow;
        if (MOVEMENT_KEYS_KNOWN && airTick && st.lastWasCheckedAir && st.lastStep != null && !st.lastHorizontalCollision
                && !horizontalCollision && st.hasLastYaw && pushers == 0) {
            // (unreachable on 1.20.1: movement keys are unknown, see MOVEMENT_KEYS_KNOWN)
            double sI = 0.0D;
            double fI = 0.0D;
            Vec3 vPrev = new Vec3(st.lastStep.x, 0.0D, st.lastStep.z);
            double predicted2 = maxAirStrafeSpeed2(vPrev, sI, fI, effectiveCap, st.lastYaw, yaw);
            if (!Double.isNaN(predicted2)) {
                double tolerance = predicted2 * STRAFE_EPSILON_RATIO + 1.0E-10D;
                if (h2 > predicted2 + tolerance) {
                    double unit2 = Math.max(effectiveCap * effectiveCap, 1.0E-6D);
                    st.strafeBuffer += (h2 - predicted2) / unit2;
                    if (st.strafeBuffer >= STRAFE_FLAG_BUFFER) {
                        String details = String.format(Locale.ROOT,
                                "gainBeyondInputs speed=%.4f inputLimit=%.4f buffer=%.1f",
                                Math.sqrt(h2), Math.sqrt(predicted2), st.strafeBuffer);
                        AntiCheatManager.reportMovementViolation(player, CHECK_STRAFE, 1.0D, details, false, null);
                        st.strafeBuffer = 0.0D;
                    }
                }
            }
        }

        // ---------------- vertical: ballistic arc ----------------
        double dy = step.y;
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
                // Already airborne (e.g. the rest of a split crouch drop the tick after takeoff): the
                // arc's own continuation is a legal reference speed too.
                double arcVy = st.inAir ? st.airVy - effectiveGravity : Double.NaN;
                st.inAir = true;
                // A crouch drop on this tick moved the feet, not the velocity: continue from the
                // fastest legal speed (jump, arc, carried) it accounts for.
                double drop = 0.0D;
                double dropFrom = takeoffVy;
                if (!input.sneak()) {
                    for (double ref : new double[]{takeoffVy, arcVy, carriedVy}) {
                        if (!Double.isNaN(ref) && (drop = uncrouchDrop(st, dy, ref)) > 0.0D) {
                            dropFrom = ref;
                            break;
                        }
                    }
                }
                st.airVy = drop > 0.0D ? dropFrom : Math.min(dy + crouchNow, takeoffVy);
                st.pendingCrouchDrop = Math.max(0.0D, st.pendingCrouchDrop - drop);
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
                double drop = input.sneak() ? 0.0D : uncrouchDrop(st, dy, expectedDy);
                st.airVy = drop > 0.0D ? expectedDy : Math.min(dy + crouchNow, expectedDy);
                st.pendingCrouchDrop = Math.max(0.0D, st.pendingCrouchDrop - drop);
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
                            + " sinceJump=%d sinceTp=%d cg=%s pendingDrop=%.3f",
                    dy, Double.isNaN(expectedDy) ? "takeoff" : String.format(Locale.ROOT, "%.4f", expectedDy),
                    verticalExcess, st.verticalBuffer, groundBelowPrev ? "fromGround" : "air",
                    st.lastStep == null ? "none" : String.format(Locale.ROOT, "%.4f", st.lastStep.y),
                    input.sneak(), st.ticksSinceSneakChange, liftOwedBefore,
                    st.ticksSinceJumpInput, st.ticksSinceTeleport, st.clientOnGround, st.pendingCrouchDrop);
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
        // Walking carries keep the count (friction only shrinks a bank); realizing the bound ends it.
        st.sneakCarryTicks = !sneakClip || bound2 <= h2 ? 0 : walking ? st.sneakCarryTicks : st.sneakCarryTicks + 1;
        st.lastWasCheckedAir = airTick;
        st.groundTicks = frictionGroundNow ? st.groundTicks + 1 : 0;
        commit(st, pos, step, supportedNow, horizontalCollision, yaw);
        // Flag-only ticks still advance the anchor, so a later lagback only rubberbands one tick.
        st.lastGoodPos = pos;
        decayBuffers(st);
    }

    /**
     * How far dy falls short of the arc's {@code expected}, if that can be the client's css uncrouch
     * drop (0 if not). Released sneak lowers the feet by whatever is left of the crouch offset at the
     * START of the client's first tick with room below: usually one tick after a jump when it was
     * released while standing (the floor blocked it), and only partly when the floor caught it first
     * (e.g. a quick crouch tap right before jumping). That moves the feet, not the velocity, so the arc
     * keeps its speed. Only what is still pending counts, and keeping the unmodified arc never allows
     * more height than it already would. Callers require sneak to be released. (1.20.1: the sneak
     * state comes from the client's PRESS/RELEASE_SHIFT_KEY commands.)
     */
    private static double uncrouchDrop(StreamState st, double dy, double expected) {
        double deficit = expected - dy;
        return deficit >= UNCROUCH_DROP_MIN && deficit <= st.pendingCrouchDrop + UNCROUCH_DROP_MATCH ? deficit : 0.0D;
    }

    /** After a grace or unchecked tick: take the realized movement as the new baseline. */
    private static void rebuildBaseline(StreamState st, Vec3 step, double h2, boolean supportedNow,
                                        boolean frictionGroundNow, double crouchOffset) {
        st.horizontalVelocityBound2 = h2;
        st.sneakCarryTicks = 0;
        st.lastWasCheckedAir = false;
        st.groundTicks = frictionGroundNow ? st.groundTicks + 1 : 0;
        if (supportedNow) {
            st.inAir = false;
        } else {
            st.inAir = true;
            st.airVy = step.y + crouchOffset;
            st.airExcess = 0.0D;
        }
    }

    private static void commit(StreamState st, Vec3 pos, Vec3 step, boolean supported, boolean horizontalCollision, float yaw) {
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
    private static void expireStalePings(ServerPlayer player, StreamState st) {
        if (st.pendingPings.isEmpty()) {
            return;
        }
        long timeout;
        if (st.transactionRttNanos > 0L) {
            timeout = 2L * st.transactionRttNanos + PING_TIMEOUT_SLACK_NANOS;
        } else {
            long latencyNanos = player.connection == null ? 0L : Math.max(0, player.latency) * 1_000_000L;
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
    private static double groundBound2(ServerPlayer player, MinehopConfig config, double vRef2) {
        double wish = player.getAttributeValue(Attributes.MOVEMENT_SPEED) * SPRINT_MARGIN
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
    private static double maxAirStrafeSpeed2(Vec3 velocity, double sI, double fI, double cap, float startYaw, float endYaw) {
        if (sI == 0.0D && fI == 0.0D) {
            return velocity.x * velocity.x + velocity.z * velocity.z;
        }
        double best = 0.0D;
        for (int steps = 6; steps <= MAX_AIR_SUBSTEPS; steps++) {
            Vec3 result = simulateAirStrafe(velocity, sI, fI, cap, startYaw, endYaw, steps);
            if (result == null) {
                return Double.NaN;
            }
            best = Math.max(best, result.x * result.x + result.z * result.z);
        }
        return best;
    }

    private static Vec3 simulateAirStrafe(Vec3 velocity, double sI, double fI, double cap, float startYaw, float endYaw, int steps) {
        float yawDelta = Mth.wrapDegrees(endYaw - startYaw);
        if (yawDelta > SOURCE_MAX_AIR_YAW_DELTA) {
            yawDelta = (float) SOURCE_MAX_AIR_YAW_DELTA;
        } else if (yawDelta < -SOURCE_MAX_AIR_YAW_DELTA) {
            yawDelta = (float) -SOURCE_MAX_AIR_YAW_DELTA;
        }
        float effectiveStartYaw = endYaw - yawDelta;
        Vec3 hv = velocity;
        for (int i = 0; i < steps; i++) {
            float frac = (float) ((i + 1.0D) / steps);
            float yawI = Mth.rotLerp(frac, effectiveStartYaw, endYaw);
            Vec3 wish = MovementUtil.movementInputToVelocity(new Vec3(sI, 0.0D, fI), 1.0F, yawI);
            double length = wish.horizontalDistance();
            if (length <= 0.0D) {
                continue;
            }
            Vec3 dir = new Vec3(wish.x / length, 0.0D, wish.z / length);
            double along = hv.dot(dir);
            if (along < 0.0D) {
                return null;
            }
            double add = cap - along;
            if (add > 0.0D) {
                hv = hv.add(dir.scale(add));
            }
        }
        return hv;
    }

    /**
     * The client freezes vertically while the chunk it stands in hasn't arrived. That's only plausible
     * shortly after a teleport and while the server still has that chunk queued, or within a round
     * trip of the teleport — not for 10 s after every teleport (a hover cheat would hide in that).
     */
    private static boolean isChunkStillLoading(ServerPlayer player, StreamState st, Vec3 pos) {
        if (st.ticksSinceTeleport > CHUNK_LOAD_WINDOW_TICKS || player.connection == null) {
            return false;
        }
        long chunk = ChunkPos.asLong(Mth.floor(pos.x) >> 4, Mth.floor(pos.z) >> 4);
        // 1.20.1 has no ChunkDataSender (1.20.2+): chunks are sent as soon as the server has them
        // loaded, so a chunk the server hasn't loaded yet can't have reached the client.
        if (!player.serverLevel().getChunkSource().hasChunk(ChunkPos.getX(chunk), ChunkPos.getZ(chunk))) {
            return true;
        }
        // The chunk was sent but the client may still be decoding/building it (seen in production
        // 11 ticks after a join teleport, before the first latency measurement).
        return st.ticksSinceTeleport <= FROZEN_AFTER_TELEPORT_TICKS + 2 * latencyTicks(player);
    }

    private static double jumpBoost(ServerPlayer player) {
        if (!player.hasEffect(MobEffects.JUMP)) {
            return 0.0D;
        }
        var effect = player.getEffect(MobEffects.JUMP);
        return effect == null ? 0.0D : 0.1F * (effect.getAmplifier() + 1);
    }

    private static double crouchDelta(ServerPlayer player) {
        double standing = player.getDimensions(Pose.STANDING).height;
        double crouching = player.getDimensions(Pose.CROUCHING).height;
        return Math.max(standing - crouching, 0.0D);
    }

    // ------------------------------------------------------------------------------------------
    // Environment
    // ------------------------------------------------------------------------------------------

    /** Solid ground (or a collidable entity) within reach below the feet at {@code pos}. */
    private static boolean isSupported(ServerLevel world, ServerPlayer player, Vec3 pos) {
        AABB box = player.getDimensions(Pose.STANDING).makeBoundingBox(pos);
        AABB below = new AABB(box.minX, box.minY - SUPPORT_PROBE_DEPTH, box.minZ, box.maxX, box.minY - 1.0E-6D, box.maxZ);
        return !world.noCollision(player, below);
    }

    /**
     * Whether vanilla's sneak back-off ({@code Player#maybeBackOffFromEdge}) can have cut this tick's
     * displacement short. The client backs off whenever it sneaks, isn't rising and has ground within
     * its step height below ({@code isAboveGround}; the client never accumulates fall distance, so that
     * holds up to the step height above a ledge, not only on it). The velocity is left untouched, so the
     * next unclipped tick (e.g. the jump off the ledge) moves at full speed again. Checked with the
     * server's own blocks the way the client tests them, from the start height: ground within reach
     * below the start box and below the realized end box, and a drop one back-off step beyond the end
     * (the shortening is real: the box stopped at a ledge). {@code lift} is a css crouch lift the client
     * may have applied before moving.
     */
    private static boolean isSneakEdgeClip(ServerLevel world, ServerPlayer player, Vec3 from, Vec3 step,
                                           double reach, double lift) {
        AABB base = player.getDimensions(Pose.STANDING).makeBoundingBox(from);
        double[] lifts = lift > 0.0D ? new double[]{0.0D, lift} : new double[]{0.0D};
        double[] xSteps = backOffSteps(step.x);
        double[] zSteps = backOffSteps(step.z);
        for (double l : lifts) {
            if (step.y - l > VERTICAL_EPSILON) {
                continue; // rising: no back-off
            }
            AABB start = base.move(0.0D, l, 0.0D);
            if (canFall(world, player, start, 0.0D, 0.0D, reach) || canFall(world, player, start, step.x, step.z, reach)) {
                continue;
            }
            // The back-off tries each axis alone, then both together (Player#maybeBackOffFromEdge).
            for (double ex : xSteps) {
                if (canFall(world, player, start, step.x + ex, 0.0D, reach)
                        || canFall(world, player, start, step.x + ex, step.z, reach)) {
                    return true;
                }
                for (double ez : zSteps) {
                    if (canFall(world, player, start, step.x + ex, step.z + ez, reach)) {
                        return true;
                    }
                }
            }
            for (double ez : zSteps) {
                if (canFall(world, player, start, 0.0D, step.z + ez, reach)
                        || canFall(world, player, start, step.x, step.z + ez, reach)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** One back-off step further along a realized displacement component (either way if it is zero). */
    private static double[] backOffSteps(double d) {
        if (Math.abs(d) > 1.0E-7D) {
            return new double[]{Math.signum(d) * SNEAK_BACK_OFF_STEP};
        }
        return new double[]{SNEAK_BACK_OFF_STEP, -SNEAK_BACK_OFF_STEP};
    }

    /**
     * The 1.20.1 client's fall test in {@code Player#maybeBackOffFromEdge} / {@code isAboveGround}: the whole box
     * moved by (dx, -depth, dz) collides with nothing. (1.21.2+ tests only the slab {@code depth} below the feet,
     * {@code Player#canFallAtLeast}.)
     */
    private static boolean canFall(ServerLevel world, ServerPlayer player, AABB box, double dx, double dz, double depth) {
        return world.noCollision(player, box.move(dx, -depth, dz));
    }

    /**
     * The client's friction ground test ({@code LivingEntityMixin#minehop$hasRealGroundBelow}): ground
     * within 0.20 under the box, which it insets by 0.001 ({@code inset} 0 tells a real gap below from
     * a box resting on a sliver of a block edge).
     */
    private static boolean hasFrictionGround(ServerLevel world, ServerPlayer player, Vec3 pos, double inset) {
        AABB box = player.getDimensions(Pose.STANDING).makeBoundingBox(pos);
        AABB below = new AABB(box.minX + inset, box.minY - FRICTION_GROUND_DEPTH, box.minZ + inset,
                box.maxX - inset, box.minY - FRICTION_GROUND_INSET, box.maxZ - inset);
        return !world.noCollision(player, below);
    }

    /**
     * Most consecutive tick ends a ballistic arc can spend 0.20-0.26 above ground (around its apex);
     * staying there longer is hovering. Without gravity hovering is legit.
     */
    private static int maxBandDwellTicks(double gravity) {
        if (gravity <= 1.0E-6D) {
            return Integer.MAX_VALUE;
        }
        double band = SUPPORT_PROBE_DEPTH - FRICTION_GROUND_DEPTH;
        return (int) Math.floor(2.0D * Math.sqrt(2.0D * band / gravity)) + 2;
    }

    /**
     * Confirms the client's own on-ground claim: ground somewhere directly under its footprint (not
     * beside it — a wall must not count as a floor).
     */
    private static boolean isGroundUnderFeet(ServerLevel world, ServerPlayer player, Vec3 pos) {
        AABB box = player.getDimensions(Pose.STANDING).makeBoundingBox(pos);
        AABB below = new AABB(box.minX, box.minY - CLIENT_GROUND_DEPTH, box.minZ, box.maxX, box.minY + 1.0E-3D, box.maxZ);
        return !world.noCollision(player, below);
    }

    /** Why this tick's movement can't be modeled (null if it can). */
    private static String environmentExemption(ServerPlayer player, ServerLevel world, Vec3 from, Vec3 to) {
        if (player.isPassenger()) {
            return "vehicle";
        }
        if (player.isFallFlying()) {
            return "glide";
        }
        if (player.isAutoSpinAttack()) {
            return "riptide";
        }
        if (player.isSleeping() || !player.isAlive()) {
            return "inactive";
        }
        Abilities abilities = player.getAbilities();
        if (abilities.flying || abilities.mayfly) {
            return "flight";
        }
        if (player.hasEffect(MobEffects.LEVITATION) || player.hasEffect(MobEffects.SLOW_FALLING)) {
            return "effect";
        }
        EntityDimensions dims = player.getDimensions(Pose.STANDING);
        AABB toBox = dims.makeBoundingBox(to);
        AABB body = from.distanceToSqr(to) <= MAX_ENVIRONMENT_SCAN_STEP * MAX_ENVIRONMENT_SCAN_STEP
                ? dims.makeBoundingBox(from).minmax(toBox)
                : toBox;
        if (SurfRampEntity.hasNearbyRamp(world, body, SURF_RAMP_PROXIMITY)) {
            return "surf";
        }
        AABB bodyScan = body.inflate(0.1D);
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        int minX = Mth.floor(bodyScan.minX);
        int maxX = Mth.floor(bodyScan.maxX);
        int minY = Mth.floor(bodyScan.minY - 0.6D);
        int maxY = Mth.floor(bodyScan.maxY);
        int minZ = Mth.floor(bodyScan.minZ);
        int maxZ = Mth.floor(bodyScan.maxZ);
        int bodyMinY = Mth.floor(bodyScan.minY);
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    mutable.set(x, y, z);
                    BlockState state = world.getBlockState(mutable);
                    if (state.isAir()) {
                        continue;
                    }
                    // Under the feet: blocks that launch or redirect the player.
                    if (state.is(Blocks.SLIME_BLOCK) || state.is(Blocks.HONEY_BLOCK)
                            || state.getBlock() instanceof BedBlock || state.is(ModBlocks.BOOSTER_BLOCK.get())) {
                        return "bounce";
                    }
                    if (y < bodyMinY) {
                        continue;
                    }
                    // In the body: fluids, climbables and blocks with their own movement physics.
                    if (!state.getFluidState().isEmpty()) {
                        return "fluid";
                    }
                    if (state.is(BlockTags.CLIMBABLE) || state.is(Blocks.SCAFFOLDING)
                            || state.is(Blocks.POWDER_SNOW) || state.is(Blocks.COBWEB)
                            || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.BUBBLE_COLUMN)) {
                        return "block";
                    }
                    if (state.is(Blocks.MOVING_PISTON) || state.is(Blocks.PISTON_HEAD)) {
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
    private static int countPushers(ServerPlayer player, ServerLevel world, Vec3 pos) {
        AABB box = player.getDimensions(Pose.STANDING).makeBoundingBox(pos).inflate(0.3D);
        return world.getEntities(player, box,
                entity -> !entity.isSpectator() && (entity.isPushable() || entity instanceof Player)).size();
    }

    // ------------------------------------------------------------------------------------------
    // Debug
    // ------------------------------------------------------------------------------------------

    private static void debug(ServerPlayer player, StreamState st, Vec3 pos, Vec3 step, String status,
                              double bound2, double expectedDy, boolean supportedPrev, boolean supportedNow) {
        if (Minehop.surfDebugPlayers.isEmpty() || !Minehop.surfDebugPlayers.contains(player.getUUID())) {
            return;
        }
        Minehop.LOGGER.info(String.format(Locale.ROOT,
                "[STREAM] %s %s pos=(%.4f,%.4f,%.4f) cg=%b h=%.4f limit=%s dy=%.4f expDy=%s sup=%s>%s air=%b airVy=%.4f keys=%s%s",
                player.getScoreboardName(), status, pos.x, pos.y, pos.z, st.clientOnGround,
                Math.sqrt(step.x * step.x + step.z * step.z),
                Double.isNaN(bound2) ? "-" : String.format(Locale.ROOT, "%.4f", Math.sqrt(bound2)),
                step.y,
                Double.isNaN(expectedDy) ? "-" : String.format(Locale.ROOT, "%.4f", expectedDy),
                supportedPrev ? "G" : "A", supportedNow ? "G" : "A", st.inAir, st.airVy,
                st.input.sneak() ? "C" : "", st.input.sprint() ? "R" : ""));
    }

    /**
     * Ground directly under the footprint of a player standing at {@code pos} (within 0.6 below, not beside it): what
     * confirms the client's own on-ground flag.
     */
    public static boolean groundUnderFeet(ServerPlayer player, Vec3 pos) {
        return isGroundUnderFeet(player.serverLevel(), player, pos);
    }

    /** The client's friction ground test at {@code pos} (ground within 0.20 under the box, see LivingEntityMixin). */
    public static boolean frictionGround(ServerPlayer player, Vec3 pos) {
        return hasFrictionGround(player.serverLevel(), player, pos, FRICTION_GROUND_INSET);
    }

    /** True if {@code entity} is a server player whose stream is tracked (for diagnostics). */
    public static boolean isTracked(Entity entity) {
        return entity instanceof ServerPlayer player && STATES.containsKey(player.getUUID());
    }
}
