package net.nerdorg.minehop.replays;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.anticheat.AntiCheatManager;
import net.nerdorg.minehop.anticheat.stream.ClientTick;
import net.nerdorg.minehop.anticheat.stream.MovementValidator;
import net.nerdorg.minehop.data.DataManager;
import net.nerdorg.minehop.entity.custom.RunZones;
import net.nerdorg.minehop.entity.custom.StartEntity;
import net.nerdorg.minehop.platform.Services;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Starts and finishes runs from the client's tick stream, one client tick at a time, and timestamps them with the
 * time the tick arrived on the network thread ({@link ClientTick#arrivalNanos}).
 *
 * <p>The run-timer check (PacketHandler#handleMapCompletion) compares the client's own run time with the span between
 * the server's start and finish stamps. Those used to be taken on the server thread at the server tick that noticed the
 * launch or the end-zone entry, so any server-thread stall (a GC pause, chunk generation, a slow save) between the two
 * shifted one of them by the stall's length and a legit run was rejected. Here both stamps are network arrival times of
 * the very client ticks that launched and finished the run; a stall only delays when the ticks are processed (they
 * arrive in a burst afterwards, in order, each keeping its own arrival time), never the span.
 *
 * <p>The decisions mirror the client's own run timer (MinehopClient#updateRunTimerStartZones / finish sampling) tick
 * for tick: the run is armed by slowing below walk speed on the ground inside a start zone, held at zero while armed and
 * grounded there, and starts at the last such tick (the launch); it finishes in the first tick whose movement enters an
 * end zone of its map (also a pass through the zone within one tick). Ground is the client's own flag confirmed by the
 * server's blocks, speed the tick's real displacement (the server's own simulation of a player has no inputs and stays
 * near zero speed, so it can't tell a circle-bhop landing from standing still).
 *
 * <p>Players without a packet stream (fake players, test bots) are still timed per server tick by StartEntity and
 * EndEntity. Server thread only.
 */
public final class RunClock {
    /** What a client tick did to the player's run. */
    public enum Event {
        NONE,
        /** The run (re)starts at this tick: armed and on the ground in a start zone (the timer is held at 0). */
        START,
        /** This tick entered an end zone of the active run's map (first time this run). */
        FINISH
    }

    private static final boolean LOG = System.getProperty("minehop.replaytest") != null
            || Boolean.getBoolean("minehop.runlog");

    private static final Map<UUID, State> STATES = new HashMap<>();

    private static final class State {
        /** Slowed below walk on the ground in a start zone and not launched since. */
        boolean armed;
        /** Position at the end of the previous tick (null after a teleport). */
        Vec3 lastPos;
        /** Server-thread time the current run's start tick was handled (diagnostics: what the old clock measured). */
        long startHandledNanos = Long.MIN_VALUE;
    }

    private RunClock() {
    }

    /** True if the player is timed from its packet stream (every real client); false for fake players and bots. */
    public static boolean usesStream(ServerPlayer player) {
        return player != null && !Services.PLATFORM.isFakePlayer(player);
    }

    public static void forget(ServerPlayer player) {
        if (player != null) {
            STATES.remove(player.getUUID());
        }
    }

    public static void clear() {
        STATES.clear();
    }

    /** Server-thread time the player's current run start was handled, or Long.MIN_VALUE (diagnostics only). */
    public static long startHandledNanos(ServerPlayer player) {
        State st = player == null ? null : STATES.get(player.getUUID());
        return st == null ? Long.MIN_VALUE : st.startHandledNanos;
    }

    /** One client tick (see ReplayEvents#onClientTick). Returns what it did to the player's run. */
    static Event onClientTick(ServerPlayer player, ClientTick tick) {
        State st = STATES.computeIfAbsent(player.getUUID(), uuid -> new State());
        Vec3 pos = tick.position();
        Vec3 from = tick.discontinuity() ? null : st.lastPos;
        st.lastPos = pos;
        if (tick.awaitingTeleport()) {
            // The server is holding the player at a teleport target until the client confirms it; the client's own
            // timer only sees where it really is once it has.
            return Event.NONE;
        }
        if (player.isCreative() || player.isSpectator()) {
            st.armed = false;
            return Event.NONE;
        }
        ServerLevel level = player.serverLevel();
        StartEntity zone = RunZones.startZoneAt(level, pos);
        if (zone != null) {
            boolean grounded = tick.onGround() && MovementValidator.groundUnderFeet(player, pos);
            double speed = from == null ? 0.0D : Math.sqrt((pos.x - from.x) * (pos.x - from.x) + (pos.z - from.z) * (pos.z - from.z));
            if (grounded && speed < StartEntity.WALK_SPEED_BPT) {
                st.armed = true;
            }
            if (st.armed) {
                if (grounded) {
                    startRun(player, st, zone, tick);
                    return Event.START;
                }
                // Launched: the run started at the last armed tick on the ground.
                st.armed = false;
            }
        } else {
            st.armed = false;
        }
        return detectFinish(player, st, level, from, pos, tick) ? Event.FINISH : Event.NONE;
    }

    private static void startRun(ServerPlayer player, State st, StartEntity zone, ClientTick tick) {
        String playerName = player.getScoreboardName();
        String mapName = zone.getPairedMap();
        HashMap<String, Long> informationMap = new HashMap<>();
        informationMap.put(mapName, tick.arrivalNanos());
        Minehop.timerManager.put(playerName, informationMap);
        Minehop.finishTimeManager.remove(playerName);
        Minehop.runStartClientTicks.put(playerName, tick.index());
        Minehop.runFinishClientTicks.remove(playerName);
        // L6: the map's physics+geometry signature for this run.
        Minehop.runSignatureManager.put(playerName, DataManager.computeRunSignature(DataManager.getMap(mapName)));
        Minehop.playerMapLocation.put(player.getStringUUID(), zone);
        // Anticheat flags from here on are attached to this run.
        AntiCheatManager.onRunArmed(player);
        st.startHandledNanos = System.nanoTime();
    }

    private static boolean detectFinish(ServerPlayer player, State st, ServerLevel level, Vec3 from, Vec3 pos, ClientTick tick) {
        String playerName = player.getScoreboardName();
        HashMap<String, Long> timerMap = Minehop.timerManager.get(playerName);
        if (timerMap == null || timerMap.isEmpty()) {
            return false;
        }
        String mapName = timerMap.keySet().iterator().next();
        HashMap<String, Long> finishMap = Minehop.finishTimeManager.get(playerName);
        if (finishMap != null && finishMap.containsKey(mapName)) {
            return false; // first crossing only
        }
        double best = Double.NaN;
        for (AABB box : RunZones.endZones(level, mapName)) {
            double fraction = from == null ? (box.contains(pos) ? 1.0D : Double.NaN) : entryFraction(box, from, pos);
            if (Double.isFinite(fraction) && (Double.isNaN(best) || fraction < best)) {
                best = fraction;
            }
        }
        if (Double.isNaN(best)) {
            return false;
        }
        // The client samples the crossing while it renders this tick's movement, a fraction of a tick after the tick:
        // the stamp follows it (within one tick, far inside the check's tolerance either way).
        long nanosPerTick = player.getServer() == null ? 50_000_000L : player.getServer().tickRateManager().nanosecondsPerTick();
        long finishNanos = tick.arrivalNanos() + Math.round(best * nanosPerTick);
        Minehop.finishTimeManager.computeIfAbsent(playerName, k -> new HashMap<>()).put(mapName, finishNanos);
        Minehop.runFinishClientTicks.put(playerName, tick.index());
        if (LOG) {
            Long start = timerMap.get(mapName);
            Long startTicks = Minehop.runStartClientTicks.get(playerName);
            Minehop.LOGGER.info(String.format(Locale.ROOT,
                    "[RUN] %s entered the end zone of %s in client tick %d (%d ticks after the start): stream span %.4f s"
                            + " (networkTimed=%b), server-thread span %.4f s",
                    playerName, mapName, tick.index(), startTicks == null ? -1L : tick.index() - startTicks,
                    start == null ? Double.NaN : (finishNanos - start) / 1.0E9D, tick.networkTimed(),
                    st.startHandledNanos == Long.MIN_VALUE ? Double.NaN : (System.nanoTime() - st.startHandledNanos) / 1.0E9D));
        }
        return true;
    }

    /**
     * Where along the segment {@code from -> to} it first is inside {@code box}: 0 if it starts inside, NaN if it never
     * enters (slab intersection, like the client's finish sampling).
     */
    static double entryFraction(AABB box, Vec3 from, Vec3 to) {
        if (box.contains(from)) {
            return 0.0D;
        }
        double tMin = 0.0D;
        double tMax = 1.0D;
        double[] start = {from.x, from.y, from.z};
        double[] delta = {to.x - from.x, to.y - from.y, to.z - from.z};
        double[] min = {box.minX, box.minY, box.minZ};
        double[] max = {box.maxX, box.maxY, box.maxZ};
        for (int axis = 0; axis < 3; axis++) {
            if (Math.abs(delta[axis]) < 1.0E-12D) {
                if (start[axis] < min[axis] || start[axis] >= max[axis]) {
                    return Double.NaN;
                }
                continue;
            }
            double t1 = (min[axis] - start[axis]) / delta[axis];
            double t2 = (max[axis] - start[axis]) / delta[axis];
            if (t1 > t2) {
                double swap = t1;
                t1 = t2;
                t2 = swap;
            }
            tMin = Math.max(tMin, t1);
            tMax = Math.min(tMax, t2);
            if (tMin > tMax) {
                return Double.NaN;
            }
        }
        return tMin;
    }
}
