package net.nerdorg.minehop.util;

import io.netty.buffer.Unpooled;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.entity.ModEntities;
import net.nerdorg.minehop.entity.custom.SurfRampEntity;
import net.nerdorg.minehop.platform.Services;

import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Dev-only server stress harness for surf ramps (no-op unless {@code -Dminehop.surfstress=true}).
 *
 * <p>Spawns fake players that surf ramps through the real {@code travel()} pipeline every tick, optionally
 * spawns extra big/curved/linked ramps, measures the server tick time (MSPT percentiles), the server thread's
 * allocation rate, the entity-id consumption (and which entity types were added to the world), and the
 * tracked-data size of the ramps, then stops the server.
 *
 * <p>Properties ({@code minehop.surfstress.*}): {@code bots} (fake players, default 0), {@code freshRamps} (extra
 * ramp groups to spawn, default 0), {@code ride} ({@code fresh} = bots ride the spawned ramps, {@code world} =
 * the ramps already in the world), {@code loadRampChunks} (force-load every chunk listed in
 * {@code minehop.surfstress.rampChunks}, a file of "cx cz n" lines), {@code warmup} (ticks, default 300),
 * {@code measure} (ticks, default 1200), {@code report} (ticks per window line, default 200).
 */
public final class SurfStressHarness {
    private static final boolean ENABLED = Boolean.getBoolean("minehop.surfstress");
    private static final int BOTS = Integer.getInteger("minehop.surfstress.bots", 0);
    private static final int FRESH_RAMPS = Integer.getInteger("minehop.surfstress.freshRamps", 0);
    private static final String RIDE = System.getProperty("minehop.surfstress.ride", "fresh");
    private static final String RAMP_CHUNKS = System.getProperty("minehop.surfstress.rampChunks", "");
    private static final double RIDE_MIN_X = Double.parseDouble(System.getProperty("minehop.surfstress.rideMinX", "-1.0E9"));
    private static final boolean NO_MOBS = Boolean.getBoolean("minehop.surfstress.noMobs");
    private static final boolean DRIVE = !"false".equals(System.getProperty("minehop.surfstress.drive", "true"));
    private static final int WARMUP = Integer.getInteger("minehop.surfstress.warmup", 300);
    private static final int MEASURE = Integer.getInteger("minehop.surfstress.measure", 1200);
    private static final int REPORT = Integer.getInteger("minehop.surfstress.report", 200);
    private static final int START_DELAY = 40;

    private static final double FRESH_ORIGIN_X = 3000.0D;
    private static final double FRESH_ORIGIN_Z = 3000.0D;
    private static final double FRESH_Y = 150.0D;

    private static int tick = 0;
    private static long tickStartNanos;
    private static long tickStartAlloc;
    private static boolean setupDone = false;
    private static final List<Bot> BOTS_LIST = new ArrayList<>();
    private static final List<List<SurfRampEntity>> LANES = new ArrayList<>();
    private static long[] measuredNanos;
    private static long[] measuredAlloc;
    private static int measuredCount = 0;
    private static int windowStart = 0;
    private static long botOnRampTicks = 0;
    private static long botTicks = 0;
    private static long botRespawns = 0;
    private static long travelNanos = 0;
    private static int counterAtMeasureStart = -1;
    private static int maxSeenId = -1;
    private static final Map<String, Integer> NEW_ENTITY_TYPES = new HashMap<>();
    private static AtomicInteger entityCounter;
    private static boolean measuring = false;
    private static int lastActiveCount = -1;
    private static int stableSince = 0;
    private static int setupTick = 0;

    private static long mix(long hash, long value) {
        return (hash ^ value) * 1099511628211L;
    }
    private static com.sun.management.ThreadMXBean threadBean;

    private SurfStressHarness() {
    }

    public static void register() {
        if (!ENABLED) {
            return;
        }
        Services.EVENTS.onServerTickStart(SurfStressHarness::onTickStart);
        Services.EVENTS.onServerTickEnd(SurfStressHarness::onTickEnd);
        measuredNanos = new long[MEASURE];
        measuredAlloc = new long[MEASURE];
        if (ManagementFactory.getThreadMXBean() instanceof com.sun.management.ThreadMXBean bean) {
            threadBean = bean;
            threadBean.setThreadAllocatedMemoryEnabled(true);
        }
        entityCounter = findEntityCounter();
        Minehop.LOGGER.info("[SSTRESS] enabled bots={} freshRamps={} ride={} warmup={} measure={} counter={}",
                BOTS, FRESH_RAMPS, RIDE, WARMUP, MEASURE, entityCounter != null);
    }

    private static AtomicInteger findEntityCounter() {
        for (Field field : Entity.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) && field.getType() == AtomicInteger.class) {
                try {
                    field.setAccessible(true);
                    return (AtomicInteger) field.get(null);
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    private static long allocatedBytes() {
        return threadBean != null ? threadBean.getCurrentThreadAllocatedBytes() : 0L;
    }

    private static void onTickStart(MinecraftServer server) {
        tickStartNanos = System.nanoTime();
        tickStartAlloc = allocatedBytes();
    }

    private static void onTickEnd(MinecraftServer server) {
        tick++;
        ServerLevel level = server.overworld();
        if (tick == START_DELAY) {
            forceRampChunks(level);
        }
        if (!setupDone && tick >= START_DELAY) {
            // Wait until the ramps loaded from disk have all registered (entity sections load asynchronously), so
            // the lanes (and the bots' trajectories) are the same on every run.
            int active = SurfRampEntity.collectAllActiveRamps(level).size();
            if (active != lastActiveCount) {
                lastActiveCount = active;
                stableSince = tick;
            }
            if ((tick - stableSince >= 100 && (active > 0 || RAMP_CHUNKS.isEmpty())) || tick >= START_DELAY + 1200) {
                setup(server, level);
                setupDone = true;
                setupTick = tick;
            }
        }
        if (setupDone) {
            long t0 = System.nanoTime();
            driveBots(level);
            travelNanos += System.nanoTime() - t0;
            probeNewEntities(server);
        }
        long elapsed = System.nanoTime() - tickStartNanos;
        long alloc = allocatedBytes() - tickStartAlloc;

        int measureStart = setupDone ? setupTick + WARMUP : Integer.MAX_VALUE;
        if (tick == measureStart) {
            measuring = true;
            counterAtMeasureStart = entityCounter != null ? entityCounter.get() : -1;
            NEW_ENTITY_TYPES.clear();
            botOnRampTicks = 0;
            botTicks = 0;
            botRespawns = 0;
            travelNanos = 0;
            windowStart = 0;
            logSyncSizes(server, level);
        }
        if (tick > measureStart && measuredCount < MEASURE) {
            measuredNanos[measuredCount] = elapsed;
            measuredAlloc[measuredCount] = alloc;
            measuredCount++;
            if (measuredCount - windowStart >= REPORT) {
                logStats("window", windowStart, measuredCount);
                windowStart = measuredCount;
            }
            if (measuredCount == MEASURE) {
                logStats("TOTAL", 0, measuredCount);
                int counterNow = entityCounter != null ? entityCounter.get() : -1;
                double minutes = MEASURE / 20.0D / 60.0D;
                Minehop.LOGGER.info(String.format(Locale.ROOT,
                        "[SSTRESS] entityIds consumed=%d (%.0f/min at 20 tps) addedToWorld=%s botOnRamp=%.1f%% respawns=%d travelMs/tick=%.3f",
                        counterNow - counterAtMeasureStart,
                        (counterNow - counterAtMeasureStart) / minutes,
                        NEW_ENTITY_TYPES,
                        botTicks > 0 ? 100.0D * botOnRampTicks / botTicks : 0.0D,
                        botRespawns,
                        travelNanos / 1.0e6D / MEASURE));
                StringBuilder sums = new StringBuilder();
                for (Bot bot : BOTS_LIST) {
                    sums.append(String.format(Locale.ROOT, " %s:%016x", bot.player.getScoreboardName(), bot.checksum));
                }
                Minehop.LOGGER.info("[SSTRESS] trajectory checksums{}", sums);
                Minehop.LOGGER.info("[SSTRESS] === DONE ===");
                server.halt(false);
            }
        }
    }

    private static void logStats(String label, int from, int to) {
        int n = to - from;
        long[] sorted = Arrays.copyOfRange(measuredNanos, from, to);
        Arrays.sort(sorted);
        long sum = 0;
        long allocSum = 0;
        for (int i = from; i < to; i++) {
            sum += measuredNanos[i];
            allocSum += measuredAlloc[i];
        }
        Minehop.LOGGER.info(String.format(Locale.ROOT,
                "[SSTRESS] %s ticks=%d mspt mean=%.3f p50=%.3f p90=%.3f p99=%.3f max=%.3f alloc/tick=%.1fKB",
                label, n, sum / 1.0e6D / n,
                sorted[n / 2] / 1.0e6D,
                sorted[Math.min(n - 1, (int) (n * 0.90D))] / 1.0e6D,
                sorted[Math.min(n - 1, (int) (n * 0.99D))] / 1.0e6D,
                sorted[n - 1] / 1.0e6D,
                allocSum / 1024.0D / n));
    }

    private static void probeNewEntities(MinecraftServer server) {
        int newMax = maxSeenId;
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                int id = entity.getId();
                if (id > maxSeenId) {
                    if (maxSeenId >= 0) {
                        String key = EntityType.getKey(entity.getType()).toString();
                        NEW_ENTITY_TYPES.merge(key, 1, Integer::sum);
                    }
                    if (id > newMax) {
                        newMax = id;
                    }
                }
            }
        }
        maxSeenId = newMax;
    }

    private static void logSyncSizes(MinecraftServer server, ServerLevel level) {
        List<SurfRampEntity> ramps = SurfRampEntity.collectAllActiveRamps(level);
        long dataBytes = 0;
        long attrBytes = 0;
        int maxData = 0;
        long pathChars = 0;
        for (SurfRampEntity ramp : ramps) {
            List<SynchedEntityData.DataValue<?>> values = ramp.getEntityData().getNonDefaultValues();
            if (values != null) {
                RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), server.registryAccess());
                ClientboundSetEntityDataPacket.STREAM_CODEC.encode(buf, new ClientboundSetEntityDataPacket(ramp.getId(), values));
                dataBytes += buf.readableBytes();
                maxData = Math.max(maxData, buf.readableBytes());
                buf.release();
            }
            RegistryFriendlyByteBuf abuf = new RegistryFriendlyByteBuf(Unpooled.buffer(), server.registryAccess());
            ClientboundUpdateAttributesPacket.STREAM_CODEC.encode(abuf,
                    new ClientboundUpdateAttributesPacket(ramp.getId(), ramp.getAttributes().getSyncableAttributes()));
            attrBytes += abuf.readableBytes();
            abuf.release();
            pathChars += ramp.getPathPointsEncoded().length();
        }
        Minehop.LOGGER.info(String.format(Locale.ROOT,
                "[SSTRESS] activeRamps=%d entityDataBytes total=%d max=%d avg=%.0f attributeBytes total=%d pathChars=%d entitiesInOverworld=%d",
                ramps.size(), dataBytes, maxData, ramps.isEmpty() ? 0.0D : (double) dataBytes / ramps.size(), attrBytes, pathChars,
                countEntities(level)));
    }

    private static int countEntities(ServerLevel level) {
        int n = 0;
        for (Entity ignored : level.getAllEntities()) {
            n++;
        }
        return n;
    }

    private static void forceRampChunks(ServerLevel level) {
        if (NO_MOBS) {
            level.getGameRules().set(net.minecraft.world.level.gamerules.GameRules.SPAWN_MOBS, false, level.getServer());
        }
        if (!RAMP_CHUNKS.isEmpty()) {
            try {
                int forced = 0;
                for (String line : java.nio.file.Files.readAllLines(java.nio.file.Path.of(RAMP_CHUNKS))) {
                    String[] parts = line.trim().split("\\s+");
                    if (parts.length < 2) {
                        continue;
                    }
                    level.setChunkForced(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), true);
                    forced++;
                }
                Minehop.LOGGER.info("[SSTRESS] force-loaded {} ramp chunks", forced);
            } catch (Exception e) {
                Minehop.LOGGER.error("[SSTRESS] could not read ramp chunks", e);
            }
        }
    }

    private static void setup(MinecraftServer server, ServerLevel level) {
        for (int i = 0; i < FRESH_RAMPS; i++) {
            spawnFreshGroup(level, i);
        }
        if ("world".equals(RIDE)) {
            // Lanes = every distinct ramp group already in the world (chains by id, single ramps alone).
            Map<String, List<SurfRampEntity>> chains = new HashMap<>();
            for (SurfRampEntity ramp : SurfRampEntity.collectAllActiveRamps(level)) {
                if (ramp.getStart().x < RIDE_MIN_X) {
                    continue;
                }
                String chain = ramp.getChainId();
                String key = chain.isEmpty()
                        ? String.format(Locale.ROOT, "single-%.2f,%.2f,%.2f", ramp.getStart().x, ramp.getStart().y, ramp.getStart().z)
                        : chain;
                chains.computeIfAbsent(key, k -> new ArrayList<>()).add(ramp);
            }
            List<String> keys = new ArrayList<>(chains.keySet());
            keys.sort(String::compareTo);
            for (String key : keys) {
                List<SurfRampEntity> lane = chains.get(key);
                // Deterministic order (registry order follows entity ids, which vary between runs).
                lane.sort(java.util.Comparator.<SurfRampEntity>comparingDouble(r -> r.getStart().x)
                        .thenComparingDouble(r -> r.getStart().z)
                        .thenComparingDouble(r -> r.getStart().y)
                        .thenComparingDouble(r -> r.getEnd().x)
                        .thenComparingDouble(r -> r.getEnd().z));
                LANES.add(lane);
            }
        }
        Minehop.LOGGER.info("[SSTRESS] lanes={} activeRamps={}", LANES.size(), SurfRampEntity.collectAllActiveRamps(level).size());
        if (LANES.isEmpty() && BOTS > 0) {
            Minehop.LOGGER.warn("[SSTRESS] no lanes to ride, bots disabled");
            return;
        }
        for (int i = 0; i < BOTS; i++) {
            ServerPlayer player = Services.PLATFORM.createFakePlayer(level,
                    new com.mojang.authlib.GameProfile(UUID.nameUUIDFromBytes(("SurfBot" + i).getBytes()), "SurfBot" + i));
            player.setGameMode(GameType.SURVIVAL);
            Bot bot = new Bot(player, LANES.get(i % LANES.size()), i);
            BOTS_LIST.add(bot);
            bot.respawn();
            level.addFreshEntity(player);
        }
    }

    // One group = a 60-block straight ramp, a 40x40 bezier curve, and a linked 6-point curved chain (built like
    // SurfRampPlacementManager does: densified path, <=5-length linked segments sharing the full path).
    private static void spawnFreshGroup(ServerLevel level, int index) {
        double ox = FRESH_ORIGIN_X + (index % 4) * 160.0D;
        double oz = FRESH_ORIGIN_Z + (index / 4) * 160.0D;
        forceChunks(level, ox - 30, oz - 30, ox + 150, oz + 150);

        SurfRampEntity straight = new SurfRampEntity(ModEntities.SURF_RAMP_ENTITY.get(), level);
        straight.setGeometry(new Vec3(ox, FRESH_Y, oz), new Vec3(ox + 60.0D, FRESH_Y - 8.0D, oz), 12.0D, 18.0D, false, 1);
        level.addFreshEntity(straight);
        LANES.add(List.of(straight));

        SurfRampEntity curved = new SurfRampEntity(ModEntities.SURF_RAMP_ENTITY.get(), level);
        curved.setGeometry(new Vec3(ox, FRESH_Y, oz + 40.0D), new Vec3(ox + 60.0D, FRESH_Y - 6.0D, oz + 100.0D), 10.0D, 14.0D, false, 1);
        level.addFreshEntity(curved);
        LANES.add(List.of(curved));

        List<Vec3> points = List.of(
                new Vec3(ox + 80.0D, FRESH_Y, oz),
                new Vec3(ox + 95.0D, FRESH_Y - 1.0D, oz + 6.0D),
                new Vec3(ox + 108.0D, FRESH_Y - 2.0D, oz + 18.0D),
                new Vec3(ox + 116.0D, FRESH_Y - 3.0D, oz + 34.0D),
                new Vec3(ox + 118.0D, FRESH_Y - 4.0D, oz + 52.0D),
                new Vec3(ox + 114.0D, FRESH_Y - 5.0D, oz + 70.0D));
        LANES.add(spawnPathRamp(level, points, 8.0D, 10.0D, 1, "sstress-" + index));
    }

    private static List<SurfRampEntity> spawnPathRamp(ServerLevel level, List<Vec3> points, double drop, double width, int sideSign, String chainId) {
        List<Vec3> dense = new ArrayList<>();
        dense.add(points.get(0));
        for (int i = 1; i < points.size(); i++) {
            Vec3 a = points.get(i - 1);
            Vec3 b = points.get(i);
            double dist = Math.hypot(b.x - a.x, b.z - a.z);
            int slices = Math.max(1, (int) Math.ceil(dist / 0.4D));
            for (int s = 1; s <= slices; s++) {
                dense.add(a.lerp(b, (double) s / slices));
            }
        }
        List<int[]> ranges = new ArrayList<>();
        int startIdx = 0;
        while (startIdx < dense.size() - 1) {
            int endIdx = startIdx + 1;
            double len = 0.0D;
            while (endIdx < dense.size() - 1) {
                double seg = Math.hypot(dense.get(endIdx).x - dense.get(endIdx - 1).x, dense.get(endIdx).z - dense.get(endIdx - 1).z);
                if (len + seg > 5.0D) {
                    break;
                }
                len += seg;
                endIdx++;
            }
            ranges.add(new int[]{startIdx, endIdx});
            startIdx = endIdx;
        }
        List<Vec3> shared = List.copyOf(dense.size() > 1024 ? dense.subList(0, 1024) : dense);
        int span = Math.max(shared.size() - 1, 1);
        List<SurfRampEntity> segments = new ArrayList<>();
        for (int r = 0; r < ranges.size(); r++) {
            int s0 = Math.min(ranges.get(r)[0], span);
            int s1 = Math.min(ranges.get(r)[1], span);
            SurfRampEntity ramp = new SurfRampEntity(ModEntities.SURF_RAMP_ENTITY.get(), level);
            ramp.setGeometry(shared.get(s0), shared.get(s1), drop, width, false, sideSign);
            ramp.setCenterlinePoints(shared, (double) s0 / span, (double) s1 / span);
            ramp.setChainId(chainId);
            ramp.setLinkedSeams(r > 0, r < ranges.size() - 1);
            level.addFreshEntity(ramp);
            segments.add(ramp);
        }
        return segments;
    }

    private static void forceChunks(ServerLevel level, double minX, double minZ, double maxX, double maxZ) {
        for (int cx = ((int) Math.floor(minX)) >> 4; cx <= ((int) Math.floor(maxX)) >> 4; cx++) {
            for (int cz = ((int) Math.floor(minZ)) >> 4; cz <= ((int) Math.floor(maxZ)) >> 4; cz++) {
                level.setChunkForced(cx, cz, true);
            }
        }
    }

    private static void driveBots(ServerLevel level) {
        for (Bot bot : BOTS_LIST) {
            bot.tick(level);
        }
    }

    private static final class Bot {
        private final ServerPlayer player;
        private final List<SurfRampEntity> lane;
        private final int index;
        private int age;
        private int offRampTicks;
        private int spawnCount;
        private float yaw;
        private double sideways;
        private long checksum = 1469598103934665603L;

        private Bot(ServerPlayer player, List<SurfRampEntity> lane, int index) {
            this.player = player;
            this.lane = lane;
            this.index = index;
        }

        private void respawn() {
            SurfRampEntity ramp = this.lane.get((this.spawnCount * 3 + this.index) % Math.max(1, Math.min(this.lane.size(), 4)));
            this.spawnCount++;
            double t = 0.08D + 0.07D * ((this.index + this.spawnCount) % 4);
            Vec3 center = ramp.sampleCenterline(t);
            Vec3 left = ramp.sampleLeft(t);
            int side = ramp.getSideSign();
            double width = ramp.getRampWidth();
            double lateral = width * 0.35D;
            double surfaceY = ramp.sampleBaseY(t) + ramp.getDrop() * (1.0D - 0.35D);
            double x = center.x + left.x * lateral * side;
            double z = center.z + left.z * lateral * side;
            double fx = left.z;
            double fz = -left.x;
            double fl = Math.hypot(fx, fz);
            if (fl > 1.0E-6D) {
                fx /= fl;
                fz /= fl;
            }
            this.yaw = (float) Math.toDegrees(Math.atan2(-fx, fz));
            // Strafe toward the high edge (the centerline): uphill = -left * side.
            double ux = -left.x * side;
            double uz = -left.z * side;
            double leftOfFacingX = fz;
            double leftOfFacingZ = -fx;
            this.sideways = (ux * leftOfFacingX + uz * leftOfFacingZ) >= 0.0D ? 1.0D : -1.0D;
            double speed = 0.7D + 0.05D * (this.index % 5);
            this.player.snapTo(x, surfaceY + 0.05D, z, this.yaw, 0.0F);
            this.player.setDeltaMovement(fx * speed, -0.05D, fz * speed);
            this.player.setOnGround(false);
            this.age = 0;
            this.offRampTicks = 0;
        }

        private void tick(ServerLevel level) {
            if (this.player.isRemoved() || !DRIVE) {
                return;
            }
            this.player.setYRot(this.yaw);
            this.player.yRotO = this.yaw;
            this.player.setSpeed(0.1F);
            this.player.travel(new Vec3(this.sideways, 0.0D, 0.0D));
            level.getChunkSource().move(this.player);
            if (measuring) {
                Vec3 v = this.player.getDeltaMovement();
                this.checksum = mix(this.checksum, Double.doubleToRawLongBits(this.player.getX()));
                this.checksum = mix(this.checksum, Double.doubleToRawLongBits(this.player.getY()));
                this.checksum = mix(this.checksum, Double.doubleToRawLongBits(this.player.getZ()));
                this.checksum = mix(this.checksum, Double.doubleToRawLongBits(v.x));
                this.checksum = mix(this.checksum, Double.doubleToRawLongBits(v.y));
                this.checksum = mix(this.checksum, Double.doubleToRawLongBits(v.z));
                this.checksum = mix(this.checksum, this.player.onGround() ? 1L : 0L);
            }
            this.age++;
            botTicks++;
            boolean onRamp = false;
            double feetY = this.player.getBoundingBox().minY;
            for (SurfRampEntity ramp : this.lane) {
                SurfContact contact = ramp.sampleNearestContact(this.player.getX(), this.player.getZ(), feetY, 0.6D);
                if (contact != null) {
                    onRamp = true;
                    break;
                }
            }
            if (onRamp) {
                botOnRampTicks++;
                this.offRampTicks = 0;
            } else {
                this.offRampTicks++;
            }
            if (this.age > 160 || this.offRampTicks > 12) {
                botRespawns++;
                this.respawn();
                level.getChunkSource().move(this.player);
            }
        }
    }
}
