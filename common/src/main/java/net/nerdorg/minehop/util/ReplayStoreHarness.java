package net.nerdorg.minehop.util;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.data.DataManager;
import net.nerdorg.minehop.platform.Services;
import net.nerdorg.minehop.replays.ReplayManager;
import net.nerdorg.minehop.replays.storage.CodecSelfTest;
import net.nerdorg.minehop.replays.storage.LegacyMigration;
import net.nerdorg.minehop.replays.storage.ReplayStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * DEV ONLY (system property {@code minehop.replaytest}, set by {@code gradlew :fabric:runServer -Preplaytest[=selftest]}):
 * the replay store's codec self-test and operator commands to inspect and stress the store (status, heap, verify every
 * file, load a run, synthetic finishes, injected write failures). With {@code selftest} the codec self-test runs once
 * the server has started and the server stops afterwards. Not registered otherwise.
 *
 * <p>Recording and run-timer tests: {@code stall <ms>} blocks the server thread (like a GC pause, chunk generation or a
 * slow save) while clients keep sending; {@code frames <id|latest>} reports a stored run's layout (pre/post frames,
 * ticks vs time, teleports) and {@code dump <id|latest>} writes its frames to a CSV next to the server.
 */
public final class ReplayStoreHarness {
    private ReplayStoreHarness() {
    }

    public static void register() {
        String mode = System.getProperty("minehop.replaytest");
        if (mode == null || mode.isBlank() || "false".equalsIgnoreCase(mode)) {
            return;
        }
        Minehop.LOGGER.warn("[RTEST] replay store test commands ENABLED (dev only)");
        if ("selftest".equalsIgnoreCase(mode)) {
            Services.EVENTS.onServerStarted(server -> {
                List<String> failures = CodecSelfTest.run(Long.getLong("minehop.replaytest.seed", 20261007L),
                        line -> Minehop.LOGGER.info("[RTEST] {}", line));
                for (String failure : failures) {
                    Minehop.LOGGER.error("[RTEST] FAIL {}", failure);
                }
                Minehop.LOGGER.info("[RTEST] codec self-test {}", failures.isEmpty() ? "PASSED" : "FAILED");
                server.halt(false);
            });
        }
        Services.EVENTS.onRegisterCommands((dispatcher, registryAccess, environment) -> dispatcher.register(
                LiteralArgumentBuilder.<CommandSourceStack>literal("replaytest")
                        .requires(source -> PermissionUtil.hasLevel(source, 4))
                        .then(LiteralArgumentBuilder.<CommandSourceStack>literal("selftest")
                                .executes(context -> selfTest(context, 20261007L))
                                .then(RequiredArgumentBuilder.<CommandSourceStack, Long>argument("seed", LongArgumentType.longArg())
                                        .executes(context -> selfTest(context, LongArgumentType.getLong(context, "seed")))))
                        .then(LiteralArgumentBuilder.<CommandSourceStack>literal("status")
                                .executes(context -> reply(context, status())))
                        .then(LiteralArgumentBuilder.<CommandSourceStack>literal("heap")
                                .executes(context -> reply(context, heap())))
                        .then(LiteralArgumentBuilder.<CommandSourceStack>literal("census")
                                .executes(context -> reply(context, census())))
                        .then(LiteralArgumentBuilder.<CommandSourceStack>literal("verify")
                                .executes(ReplayStoreHarness::verify))
                        .then(LiteralArgumentBuilder.<CommandSourceStack>literal("clearcache")
                                .executes(context -> {
                                    ReplayStore store = ReplayManager.store();
                                    if (store != null) {
                                        store.clearCache();
                                    }
                                    return reply(context, "frame cache cleared");
                                }))
                        .then(LiteralArgumentBuilder.<CommandSourceStack>literal("failwrites")
                                .then(RequiredArgumentBuilder.<CommandSourceStack, Integer>argument("count", IntegerArgumentType.integer(0, 1000))
                                        .executes(context -> {
                                            int count = IntegerArgumentType.getInteger(context, "count");
                                            ReplayStore.INJECT_WRITE_FAILURES.set(count);
                                            return reply(context, "the next " + count + " replay store file write(s) will fail");
                                        })))
                        .then(LiteralArgumentBuilder.<CommandSourceStack>literal("load")
                                .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("id", StringArgumentType.word())
                                        .executes(context -> load(context, StringArgumentType.getString(context, "id")))))
                        .then(LiteralArgumentBuilder.<CommandSourceStack>literal("loadall")
                                .executes(ReplayStoreHarness::loadAll))
                        .then(LiteralArgumentBuilder.<CommandSourceStack>literal("stall")
                                .then(RequiredArgumentBuilder.<CommandSourceStack, Integer>argument("ms", IntegerArgumentType.integer(0, 60000))
                                        .executes(context -> stall(context, IntegerArgumentType.getInteger(context, "ms")))))
                        .then(LiteralArgumentBuilder.<CommandSourceStack>literal("frames")
                                .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("id", StringArgumentType.word())
                                        .executes(context -> frames(context, StringArgumentType.getString(context, "id"), false))))
                        .then(LiteralArgumentBuilder.<CommandSourceStack>literal("dump")
                                .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("id", StringArgumentType.word())
                                        .executes(context -> frames(context, StringArgumentType.getString(context, "id"), true))))
                        .then(LiteralArgumentBuilder.<CommandSourceStack>literal("synth")
                                .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("map", StringArgumentType.string())
                                        .then(RequiredArgumentBuilder.<CommandSourceStack, Integer>argument("frames", IntegerArgumentType.integer(1, 200000))
                                                .executes(context -> synth(context, StringArgumentType.getString(context, "map"),
                                                        IntegerArgumentType.getInteger(context, "frames"))))))
        ));
    }

    private static int selfTest(CommandContext<CommandSourceStack> context, long seed) {
        List<String> lines = new ArrayList<>();
        List<String> failures = CodecSelfTest.run(seed, lines::add);
        lines.addAll(failures.stream().map(f -> "FAIL " + f).toList());
        lines.add(failures.isEmpty() ? "PASSED" : "FAILED");
        return reply(context, String.join("\n", lines));
    }

    private static String status() {
        StringBuilder text = new StringBuilder("store=").append(ReplayManager.storeMode())
                .append(" runs=").append(Minehop.replayList == null ? 0 : Minehop.replayList.size())
                .append(" ").append(LegacyMigration.status());
        ReplayStore store = ReplayManager.store();
        if (store != null) {
            ReplayStore.Stats stats = store.stats();
            text.append(String.format(Locale.ROOT,
                    "\nwritten=%d (%d bytes) writeFailures=%d pendingWrites=%d loads=%d loadFailures=%d cacheHits=%d quarantined=%d"
                            + "\ncache=%d runs / %.2f MB unavailable=%d lastFinish=%.3f ms maxFinish=%.3f ms",
                    stats.runsWritten.get(), stats.bytesWritten.get(), stats.writeFailures.get(), store.pendingWrites(), stats.loads.get(),
                    stats.loadFailures.get(), stats.cacheHits.get(), stats.quarantined.get(), store.cachedRuns(),
                    store.cacheBytes() / 1048576.0D, store.unavailableRuns().size(), stats.lastFinishNanos / 1.0E6D,
                    stats.maxFinishNanos / 1.0E6D));
            for (Map.Entry<String, String> unavailable : store.unavailableRuns().entrySet()) {
                text.append("\n unavailable ").append(unavailable.getKey()).append(": ").append(unavailable.getValue());
            }
        }
        return text.toString();
    }

    /** Heap in use after a full GC (what the replay metadata and caches really cost). */
    private static String heap() {
        Runtime runtime = Runtime.getRuntime();
        for (int i = 0; i < 3; i++) {
            System.gc();
        }
        long used = runtime.totalMemory() - runtime.freeMemory();
        return String.format(Locale.ROOT, "heap used after GC: %.1f MB (committed %.1f MB, max %.1f MB); store=%s runs=%d",
                used / 1048576.0D, runtime.totalMemory() / 1048576.0D, runtime.maxMemory() / 1048576.0D,
                ReplayManager.storeMode(), Minehop.replayList == null ? 0 : Minehop.replayList.size());
    }

    /** Run statuses and how many PB/WR rows resolve to a run (the same lookups the commands use). */
    private static String census() {
        long start = System.nanoTime();
        LegacyMigration.Census census = LegacyMigration.census(Minehop.replayList);
        Map<String, Integer> statuses = census.statuses();
        int pbRows = Minehop.personalRecordList == null ? 0 : Minehop.personalRecordList.size();
        long pbResolved = census.rows().entrySet().stream().filter(row -> row.getKey().startsWith("PB ") && !row.getValue().isEmpty()).count();
        int wrMaps = ReplayManager.worldRecordReplays().size();
        long frames = 0L;
        int flagged = 0;
        for (ReplayManager.Replay replay : Minehop.replayList) {
            frames += ReplayManager.frameCount(replay);
            if (replay.ac_flags != null && !replay.ac_flags.isBlank()) {
                flagged++;
            }
        }
        return String.format(Locale.ROOT, "runs=%d frames=%d flagged=%d statuses=%s PB rows=%d backed=%d WR replays=%d (%.1f ms)",
                Minehop.replayList.size(), frames, flagged, statuses, pbRows, pbResolved, wrMaps, (System.nanoTime() - start) / 1.0E6D);
    }

    private static int verify(CommandContext<CommandSourceStack> context) {
        ReplayStore store = ReplayManager.store();
        if (store == null) {
            return reply(context, "no v2 store");
        }
        store.verifyAll(text -> reply(context, text));
        return 1;
    }

    /** Blocks the server thread, as a GC pause, chunk generation or a slow save would. */
    private static int stall(CommandContext<CommandSourceStack> context, int millis) {
        Minehop.LOGGER.warn("[RTEST] stalling the server thread for {} ms", millis);
        long start = System.nanoTime();
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return reply(context, String.format(Locale.ROOT, "server thread stalled for %.1f ms", (System.nanoTime() - start) / 1.0E6D));
    }

    /** A stored run's layout (and with {@code dump} its frames as CSV in the server directory). */
    private static int frames(CommandContext<CommandSourceStack> context, String prefix, boolean dump) {
        ReplayManager.Replay replay = "latest".equals(prefix) && !Minehop.replayList.isEmpty()
                ? Minehop.replayList.get(Minehop.replayList.size() - 1) : find(prefix);
        if (replay == null) {
            return reply(context, "no run with id " + prefix + "...");
        }
        ReplayManager.loadFrames(replay, frames -> {
            if (frames == null) {
                reply(context, replay.replay_id + ": frames unavailable");
                return;
            }
            List<Integer> teleports = new ArrayList<>();
            for (int i = 0; i < frames.size(); i++) {
                if ((frames.flags(i) & net.nerdorg.minehop.replays.storage.ReplayFrames.FLAG_DISCONTINUITY) != 0) {
                    teleports.add(i);
                }
            }
            int runTicks = frames.runEnd() - frames.runStart() - 1;
            reply(context, String.format(Locale.ROOT,
                    "%s %s %s time=%.5fs: %d frames = %d pre + %d run + %d post, tickStream=%b; run frames-1 = %d ticks = %.3fs"
                            + " (time/ticks ratio %.4f); teleport frames %s",
                    replay.replay_id, replay.map_name, replay.player_name, replay.time, frames.size(), frames.preFrames(),
                    frames.runEnd() - frames.runStart(), frames.postFrames(), frames.tickStream(), runTicks, runTicks * 0.05D,
                    runTicks > 0 ? replay.time / (runTicks * 0.05D) : Double.NaN,
                    teleports.size() > 20 ? teleports.subList(0, 20) + "..." : teleports));
            if (dump) {
                java.nio.file.Path file = context.getSource().getServer().getServerDirectory()
                        .resolve("replaytest_dump_" + replay.replay_id + ".csv");
                StringBuilder csv = new StringBuilder("i,x,y,z,yaw,pitch,jumps,speed,efficiency,flags\n");
                for (int i = 0; i < frames.size(); i++) {
                    csv.append(String.format(Locale.ROOT, "%d,%.6f,%.6f,%.6f,%.4f,%.4f,%d,%.5f,%.3f,%d%n", i, frames.x(i), frames.y(i),
                            frames.z(i), frames.yaw(i), frames.pitch(i), frames.jumpCount(i), frames.lastJumpSpeed(i),
                            frames.efficiency(i), frames.flags(i)));
                }
                try {
                    java.nio.file.Files.writeString(file, csv.toString());
                    reply(context, "wrote " + file.toAbsolutePath());
                } catch (java.io.IOException e) {
                    reply(context, "could not write " + file + ": " + e);
                }
            }
        });
        return 1;
    }

    private static ReplayManager.Replay find(String prefix) {
        for (ReplayManager.Replay replay : Minehop.replayList) {
            if (replay.replay_id != null && replay.replay_id.startsWith(prefix)) {
                return replay;
            }
        }
        return null;
    }

    private static int load(CommandContext<CommandSourceStack> context, String prefix) {
        ReplayManager.Replay replay = find(prefix);
        if (replay == null) {
            return reply(context, "no run with id " + prefix + "...");
        }
        long start = System.nanoTime();
        boolean[] synchronous = {true};
        ReplayManager.loadFrames(replay, frames -> reply(context, String.format(Locale.ROOT,
                "%s %s %s %.3fs: %s after %.2f ms (%s)", replay.replay_id, replay.map_name, replay.player_name, replay.time,
                frames == null ? "UNAVAILABLE (" + ReplayManager.unavailableReason(replay) + ")" : frames.size() + " frames",
                (System.nanoTime() - start) / 1.0E6D, synchronous[0] ? "in memory" : "read from disk")));
        synchronous[0] = false;
        return 1;
    }

    /** Loads every playable run's frames through the store (cache churn and reader throughput). */
    private static int loadAll(CommandContext<CommandSourceStack> context) {
        List<ReplayManager.Replay> runs = new ArrayList<>(Minehop.replayList);
        int[] done = {0, 0};
        long[] frames = {0L};
        long start = System.nanoTime();
        for (ReplayManager.Replay replay : runs) {
            ReplayManager.loadFrames(replay, loaded -> {
                done[0]++;
                if (loaded == null) {
                    done[1]++;
                } else {
                    frames[0] += loaded.size();
                }
                if (done[0] == runs.size()) {
                    ReplayStore store = ReplayManager.store();
                    reply(context, String.format(Locale.ROOT, "loaded %d runs (%d unavailable), %d frames in %.1f s; cache %d runs / %.1f MB",
                            done[0], done[1], frames[0], (System.nanoTime() - start) / 1.0E9D,
                            store == null ? 0 : store.cachedRuns(), store == null ? 0.0D : store.cacheBytes() / 1048576.0D));
                }
            });
        }
        return 1;
    }

    /**
     * A synthetic finished run (a random walk from the map's spawn) stored exactly like a real finish's replay; reports
     * the server thread's cost. It doesn't touch the PB/WR lists, so it is never watchable.
     */
    private static int synth(CommandContext<CommandSourceStack> context, String mapName, int frameCount) {
        DataManager.MapData map = DataManager.getMap(mapName);
        double x = map == null ? 0.0D : map.x;
        double y = map == null ? 64.0D : map.y;
        double z = map == null ? 0.0D : map.z;
        Random random = new Random();
        List<ReplayManager.ReplayEntry> entries = new ArrayList<>(frameCount);
        float yaw = 0.0F;
        for (int i = 0; i < frameCount; i++) {
            x += random.nextGaussian() * 0.5D;
            y += random.nextGaussian() * 0.1D;
            z += random.nextGaussian() * 0.5D;
            yaw += (float) random.nextGaussian() * 3.0F;
            entries.add(new ReplayManager.ReplayEntry(x, y, z, random.nextGaussian() * 10.0D, yaw, i / 20, 0.5D, 75.0D));
        }
        ReplayManager.Replay replay = new ReplayManager.Replay(map == null ? mapName : map.name, "synthetic_runner",
                "00000000-0000-0000-0000-000000000000", frameCount / 20.0D + 0.123D, entries);
        MinecraftServer server = context.getSource().getServer();
        long start = System.nanoTime();
        ReplayManager.saveReplay(server.overworld(), replay, frameCount / 20.0D + 0.12D, frameCount);
        double ms = (System.nanoTime() - start) / 1.0E6D;
        return reply(context, String.format(Locale.ROOT, "stored synthetic run %s (%d frames) on %s: %.3f ms on the server thread",
                Minehop.replayList.get(Minehop.replayList.size() - 1).replay_id, frameCount, mapName, ms));
    }

    private static int reply(CommandContext<CommandSourceStack> context, String message) {
        for (String line : message.split("\n")) {
            Minehop.LOGGER.info("[RTEST] {}", line);
        }
        context.getSource().sendSuccess(() -> Component.literal(message), false);
        return 1;
    }
}
