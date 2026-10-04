package net.nerdorg.minehop.anticheat;

import com.google.gson.reflect.TypeToken;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.util.JsonStorage;

import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Records WHY an abnormal-velocity tick was permitted by the anticheat (boost, surf contact,
 * teleport grace, fluid, climb, vehicle, external knockback). The audit found exemptions were silent
 * early-returns with no record of what allowed fast movement, so an admin could never tell a real
 * surf cap-suspension from a cheat slipping through a fluid/teleport exemption.
 *
 * <p>Bounded in-memory ring (last {@value #MAX_ENTRIES}), throttled per player, persisted via the
 * crash-safe {@link JsonStorage}. A debug flag mirrors each record to the server log.
 */
public final class AntiCheatAllowLog {
    public enum Reason {
        BOOST, SURF_CONTACT, TELEPORT, FLUID, CLIMB, VEHICLE, EXTERNAL
    }

    public static final class Entry {
        public long timeMs;
        public String player;
        public String reason;
        public double speed;
        public double cap;
        public double x;
        public double y;
        public double z;

        public Entry() {
        }

        public Entry(long timeMs, String player, String reason, double speed, double cap, double x, double y, double z) {
            this.timeMs = timeMs;
            this.player = player;
            this.reason = reason;
            this.speed = speed;
            this.cap = cap;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    private static final int MAX_ENTRIES = 256;
    private static final int THROTTLE_TICKS = 20;
    private static final int SCHEMA_VERSION = 1;
    private static final String FILE = "MineHop_Data/anticheat_allows.json";

    private static final Deque<Entry> RING = new ConcurrentLinkedDeque<>();
    private static final Map<UUID, Long> LAST_LOG_TICK = new ConcurrentHashMap<>();
    private static volatile boolean debug = false;

    private AntiCheatAllowLog() {
    }

    public static void setDebug(boolean value) {
        debug = value;
    }

    public static boolean isDebug() {
        return debug;
    }

    /** Record an allow-reason for a player, at most once per {@value #THROTTLE_TICKS} ticks each. */
    public static void record(ServerPlayer player, Reason reason, double speed, double cap, Vec3 pos, long tick) {
        if (player == null || reason == null || pos == null) {
            return;
        }
        Long last = LAST_LOG_TICK.get(player.getUUID());
        if (last != null && tick - last < THROTTLE_TICKS) {
            return;
        }
        LAST_LOG_TICK.put(player.getUUID(), tick);

        Entry entry = new Entry(System.currentTimeMillis(), player.getScoreboardName(), reason.name(),
                speed, cap, pos.x, pos.y, pos.z);
        RING.addLast(entry);
        while (RING.size() > MAX_ENTRIES) {
            RING.pollFirst();
        }
        if (debug) {
            Minehop.LOGGER.info(String.format(Locale.ROOT,
                    "[AC-ALLOW] %s reason=%s speed=%.3f cap=%.3f pos=(%.1f,%.1f,%.1f)",
                    entry.player, entry.reason, speed, cap, pos.x, pos.y, pos.z));
        }
    }

    public static List<Entry> recent() {
        return new ArrayList<>(RING);
    }

    public static void clearForDisconnect(UUID uuid) {
        if (uuid != null) {
            LAST_LOG_TICK.remove(uuid);
        }
    }

    public static void save(MinecraftServer server) {
        if (server == null) {
            return;
        }
        Path path = server.getWorldPath(LevelResource.ROOT).resolve(FILE);
        JsonStorage.writeAtomic(path, SCHEMA_VERSION, new ArrayList<>(RING));
    }

    public static void load(MinecraftServer server) {
        if (server == null) {
            return;
        }
        Path path = server.getWorldPath(LevelResource.ROOT).resolve(FILE);
        Type type = new TypeToken<List<Entry>>() {}.getType();
        List<Entry> loaded = JsonStorage.readData(path, type);
        if (loaded != null) {
            RING.clear();
            RING.addAll(loaded);
            while (RING.size() > MAX_ENTRIES) {
                RING.pollFirst();
            }
        }
    }
}
