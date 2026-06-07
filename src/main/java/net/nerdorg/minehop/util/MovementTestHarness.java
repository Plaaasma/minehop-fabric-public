package net.nerdorg.minehop.util;

import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.entity.ModEntities;
import net.nerdorg.minehop.entity.custom.SurfRampEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class MovementTestHarness {
    private static final boolean ENABLED = Boolean.getBoolean("minehop.movementtest");

    private static final int WARMUP_TICKS = 40;
    private static final int SCENARIO_TICKS = 60;
    private static final double TEST_Y = 200.0D;
    private static final double BLOCKS_PER_TICK_TO_UPS = 800.0D;

    private static final double RAMP_X = 200.0D;
    private static final double RAMP_Y = 120.0D;
    private static final double RAMP_Z = 200.0D;
    private static final double RAMP_LENGTH = 60.0D;
    private static final double RAMP_DROP = 12.0D;
    private static final double RAMP_WIDTH = 18.0D;

    private static int phaseTick = 0;
    private static int scenarioIndex = -1;
    private static int scenarioTick = 0;
    private static final double CURVE_X = 300.0D;
    private static final double CURVE_Y = 120.0D;
    private static final double CURVE_Z = 300.0D;
    private static final double FLOOR_TOP_Y = RAMP_Y; // ramp low-edge surfaceY == baseY == floor top
    // Length-declining ramp: centerline drops along travel (+X). Repro for the periodic-bump report.
    private static final double DECL_X = 400.0D;
    private static final double DECL_Y_TOP = 140.0D;
    private static final double DECL_Y_BOT = 120.0D;
    private static final double DECL_Z = 200.0D;
    private static final double DECL_LEN = 60.0D;
    private static final double DECL_DROP = 3.0D;
    private static final double DECL_WIDTH = 18.0D;
    // Diagonal declining ramp: centerline along +X+Z and dropping (non-axis tangent test).
    private static final double DIAG_X = 500.0D;
    private static final double DIAG_Y_TOP = 135.0D;
    private static final double DIAG_Z = 500.0D;
    private static final double DIAG_LEN = 40.0D; // per-axis; true length ~56.6
    private static final double DIAG_Y_BOT = 120.0D;
    private static final double DIAG_DROP = 3.0D;
    private static final double DIAG_WIDTH = 16.0D;
    // Steep ramp: big lateral drop over narrow width (~63 deg tilt). Clip/snap-cap stress.
    private static final double STEEP_X = 600.0D;
    private static final double STEEP_Y = 140.0D;
    private static final double STEEP_Z = 600.0D;
    private static final double STEEP_LEN = 50.0D;
    private static final double STEEP_DROP = 16.0D;
    private static final double STEEP_WIDTH = 8.0D;

    private static FakePlayer fakePlayer;
    private static ServerWorld testWorld;
    private static SurfRampEntity testRamp;
    private static SurfRampEntity testRampCurved;
    private static SurfRampEntity testRampDecline;
    private static SurfRampEntity testRampDiag;
    private static SurfRampEntity testRampSteep;
    // Built the in-game way (densified centerline path, split into linked segments) to repro placement.
    private static List<SurfRampEntity> testRampBigStraight;
    private static List<SurfRampEntity> testRampBigCurve;
    private static final List<Scenario> SCENARIOS = new ArrayList<>();
    private static final List<SurfScenario> SURF_SCENARIOS = new ArrayList<>();
    private static boolean inSurfPhase = false;
    private static int surfIndex = -1;
    private static int surfTick = 0;

    private MovementTestHarness() {
    }

    public static void register() {
        if (!ENABLED) {
            return;
        }
        buildScenarios();
        buildSurfScenarios();
        ServerTickEvents.END_SERVER_TICK.register(MovementTestHarness::tick);
        Minehop.LOGGER.info("[MTEST] Movement test harness ENABLED. air={} surf={}.", SCENARIOS.size(), SURF_SCENARIOS.size());
    }

    private static void buildScenarios() {
        SCENARIOS.add(new Scenario("hold-A_no-mouse_air", 1.0, 0.0, 0.0, true, 0.0));
        SCENARIOS.add(new Scenario("gainA_05deg_v400", 1.0, 0.0, -5.0, true, 400.0));
        SCENARIOS.add(new Scenario("gainA_18deg_v400", 1.0, 0.0, -18.0, true, 400.0));
        SCENARIOS.add(new Scenario("gainA_42deg_v400", 1.0, 0.0, -42.0, true, 400.0));
    }

    private static void buildSurfScenarios() {
        // surface Y over the ramp at lateral L (Z - RAMP_Z) = RAMP_Y + RAMP_DROP*(1 - clamp(L/RAMP_WIDTH))
        // at Z=RAMP_Z+2 (L=2): surfaceY = 120 + 6*(1-0.333) = 124.0
        double feetOn = surfaceYAt(RAMP_Z + 2.0D) + 0.05D; // = ~124.05
        // B3: steady surf along +X at various speeds. >0.32 b/t = snap gated off (suspected sink-through).
        SURF_SCENARIOS.add(new SurfScenario("surf_slow_0.25", RAMP_X + 10, feetOn, RAMP_Z + 2.0D, 0.25, 0.0, 0.04, 55));
        SURF_SCENARIOS.add(new SurfScenario("surf_med_0.50", RAMP_X + 10, feetOn, RAMP_Z + 2.0D, 0.50, 0.0, 0.04, 55));
        SURF_SCENARIOS.add(new SurfScenario("surf_fast_0.75", RAMP_X + 10, feetOn, RAMP_Z + 2.0D, 0.75, 0.0, 0.04, 55));
        SURF_SCENARIOS.add(new SurfScenario("surf_vfast_1.00", RAMP_X + 8, feetOn, RAMP_Z + 2.0D, 1.00, 0.0, 0.04, 50));
        // B2: approach/getting-on from above, fast steep descent.
        SURF_SCENARIOS.add(new SurfScenario("geton_steep", RAMP_X + 12, RAMP_Y + 10.0D, RAMP_Z + 2.0D, 0.45, -0.9, 0.15, 30));
        SURF_SCENARIOS.add(new SurfScenario("geton_fast_flat", RAMP_X + 6, RAMP_Y + 14.0D, RAMP_Z + 2.0D, 0.85, -0.4, 0.2, 30));
        // B1: surf toward the +X end endpoint and off it.
        SURF_SCENARIOS.add(new SurfScenario("flickoff_end", RAMP_X + RAMP_LENGTH - 12, feetOn, RAMP_Z + 2.0D, 0.75, 0.0, 0.04, 30));
        // B1b: ride up toward the high edge (toward Z=RAMP_Z) and off the top edge.
        SURF_SCENARIOS.add(new SurfScenario("flickoff_topedge", RAMP_X + 15, surfaceYAt(RAMP_Z + 4.0D) + 0.05D, RAMP_Z + 4.0D, 0.4, 0.05, -0.45, 35));
        // B4: surf along +X then AIM INTO the ramp (wishdir -Z = uphill, into the surface) holding W. Must stay solid, not pass through.
        double feetOn8 = surfaceYAt(RAMP_Z + 8.0D) + 0.05D;
        SURF_SCENARIOS.add(new SurfScenario("aim_into_W", RAMP_X + 15, feetOn8, RAMP_Z + 8.0D, 0.50, 0.0, 0.0, 45, 0.0, 1.0, 180.0F));
        SURF_SCENARIOS.add(new SurfScenario("aim_into_W_fast", RAMP_X + 12, feetOn8, RAMP_Z + 8.0D, 0.90, 0.0, 0.0, 45, 0.0, 1.0, 180.0F));
        // B4b: aim into via strafe (sideways toward -Z): yaw -90, sideways 1 -> wishdir -Z.
        SURF_SCENARIOS.add(new SurfScenario("aim_into_strafe", RAMP_X + 15, feetOn8, RAMP_Z + 8.0D, 0.60, 0.0, 0.0, 45, 1.0, 0.0, -90.0F));
        // C1: land on a CURVED ramp from above (curve midpoint ~ (CURVE_X+10, CURVE_Z+30)). Watch for a bounce (vy -, then +, then -) before settling.
        SURF_SCENARIOS.add(new SurfScenario("curved_land", CURVE_X + 10, CURVE_Y + 13.0D, CURVE_Z + 30.0D, 0.03, -0.55, 0.03, 30, 0.0, 0.0, -90.0F, true));
        SURF_SCENARIOS.add(new SurfScenario("curved_land_fast", CURVE_X + 10, CURVE_Y + 16.0D, CURVE_Z + 30.0D, 0.10, -0.95, 0.10, 30, 0.0, 0.0, -90.0F, true));
        SURF_SCENARIOS.add(new SurfScenario("curved_land_lat", CURVE_X + 10, CURVE_Y + 13.0D, CURVE_Z + 33.0D, 0.03, -0.55, 0.03, 30, 0.0, 0.0, -90.0F, true));
        // C1c: surf ALONG the curve spawned exactly on its real surface at the sharp middle (t=0.5). Watch for bounce / velocity reversal / fast height change / phase-through.
        SURF_SCENARIOS.add(new SurfScenario("curveT_mid_05", 0, 0, 0, 0.70, -0.10, 0.0, 45, 0.0, 0.0, -90.0F, true, false, 0.50, 3.0));
        SURF_SCENARIOS.add(new SurfScenario("curveT_mid_10", 0, 0, 0, 1.00, -0.10, 0.0, 45, 0.0, 0.0, -90.0F, true, false, 0.50, 4.0));
        SURF_SCENARIOS.add(new SurfScenario("curveT_early_035", 0, 0, 0, 0.80, -0.10, 0.0, 45, 0.0, 0.0, -90.0F, true, false, 0.35, 3.0));
        SURF_SCENARIOS.add(new SurfScenario("curveT_mid_drop", 0, 0, 0, 0.70, -0.50, 0.0, 45, 0.0, 0.0, -90.0F, true, false, 0.50, 2.0));
        // C2: surf the straight ramp down to the floor at the low edge; must transition to ground, not sink/lagback.
        double feetMid = surfaceYAt(RAMP_Z + 6.0D) + 0.05D;
        SURF_SCENARIOS.add(new SurfScenario("surf_to_floor", RAMP_X + 15, feetMid, RAMP_Z + 6.0D, 0.30, 0.0, 0.10, 45));
        // C2b: same but HOLDING SPACE (bhop jump on contact) — must bhop/ground, not float away or crash.
        SURF_SCENARIOS.add(new SurfScenario("surf_to_floor_jump", RAMP_X + 15, feetMid, RAMP_Z + 6.0D, 0.30, 0.0, 0.10, 50, 0.0, 0.0, -90.0F, false, true));
        // C3: steady surf ALONG a length-declining ramp (centerline drops along +X). Watch for a
        // periodic vertical bump (float->snap sawtooth). vy seeded tangent to the slope so it
        // starts in steady state. slope = (DECL_Y_BOT-DECL_Y_TOP)/DECL_LEN per +X block.
        double declSlope = (DECL_Y_BOT - DECL_Y_TOP) / DECL_LEN;
        double declLat = 2.0D;
        double declFeet = declineSurfaceYAt(DECL_X + 8.0D, declLat) + 0.05D;
        SURF_SCENARIOS.add(new SurfScenario("decline_surf_03", DECL_X + 8.0D, declFeet, DECL_Z + declLat, 0.30, 0.30 * declSlope, 0.0, 60));
        SURF_SCENARIOS.add(new SurfScenario("decline_surf_06", DECL_X + 8.0D, declFeet, DECL_Z + declLat, 0.60, 0.60 * declSlope, 0.0, 45));
        // C3b: decline near the up-slope lateral EDGE (binding-foot = high edge, combined decline+tilt).
        double declFeetHi = declineSurfaceYAt(DECL_X + 8.0D, DECL_WIDTH - 2.0D) + 0.05D;
        SURF_SCENARIOS.add(new SurfScenario("decline_lat_high", DECL_X + 8.0D, declFeetHi, DECL_Z + DECL_WIDTH - 2.0D, 0.45, 0.45 * declSlope, 0.0, 50));
        // C3c: UPHILL — spawn near bottom moving -X (gravity opposes); must decelerate/detach, not glitch.
        double declFeetBot = declineSurfaceYAt(DECL_X + DECL_LEN - 8.0D, declLat) + 0.05D;
        SURF_SCENARIOS.add(new SurfScenario("decline_uphill", DECL_X + DECL_LEN - 8.0D, declFeetBot, DECL_Z + declLat, -0.50, 0.0, 0.0, 40));
        // C4: DIAGONAL declining ramp (non-axis tangent). Spawn ON its surface via samplers in
        // beginSurfScenario; vx = along-tangent speed, vy seeded ~tangent to the slope.
        SURF_SCENARIOS.add(new SurfScenario("diag_surf_04", DIAG_X, DIAG_Y_TOP, DIAG_Z, 0.50, -0.13, 0.0, 55));
        SURF_SCENARIOS.add(new SurfScenario("diag_surf_07", DIAG_X, DIAG_Y_TOP, DIAG_Z, 0.80, -0.21, 0.0, 45));
        // C5: STEEP lateral ramp (~63 deg). Spawn above near high edge; must clip & slide, not pop/tunnel.
        SURF_SCENARIOS.add(new SurfScenario("steep_surf_05", STEEP_X + 10.0D, STEEP_Y + 14.0D, STEEP_Z + 1.5D, 0.50, -0.10, 0.0, 50));
        SURF_SCENARIOS.add(new SurfScenario("steep_land", STEEP_X + 10.0D, STEEP_Y + 16.0D, STEEP_Z + 2.0D, 0.10, -0.80, 0.0, 40));
        // C6: BIG ramps (in-game path build, width6 drop6). _surf snaps onto surface; _land drops on top.
        SURF_SCENARIOS.add(new SurfScenario("bigstr_surf", 708.0D, 134.0D, 702.0D, 0.60, 0.0, 0.0, 45));
        SURF_SCENARIOS.add(new SurfScenario("bigstr_land_top", 712.0D, 140.0D, 700.6D, 0.05, -0.60, 0.0, 40));
        SURF_SCENARIOS.add(new SurfScenario("bigcrv_surf", 805.0D, 134.0D, 705.0D, 0.60, 0.0, 0.30, 45));
        // C7: land on the HIGH edge of a normal-size ramp (the "snaps you into the ramp" report).
        SURF_SCENARIOS.add(new SurfScenario("land_top_edge", RAMP_X + 20.0D, RAMP_Y + RAMP_DROP + 4.0D, RAMP_Z + 0.6D, 0.05, -0.55, 0.0, 40));
    }

    private static double declineSurfaceYAt(double x, double lateralL) {
        double t = Math.max(0.0D, Math.min(1.0D, (x - DECL_X) / DECL_LEN));
        double baseY = DECL_Y_TOP + (DECL_Y_BOT - DECL_Y_TOP) * t;
        double normLat = Math.max(0.0D, Math.min(1.0D, lateralL / DECL_WIDTH));
        return baseY + DECL_DROP * (1.0D - normLat);
    }

    private static double surfaceYAt(double z) {
        double lateral = z - RAMP_Z;
        double normLat = Math.max(0.0D, Math.min(1.0D, lateral / RAMP_WIDTH));
        return RAMP_Y + RAMP_DROP * (1.0D - normLat);
    }

    private static void tick(MinecraftServer server) {
        if (!ENABLED) {
            return;
        }
        phaseTick++;
        if (phaseTick < WARMUP_TICKS) {
            return;
        }
        if (testWorld == null) {
            testWorld = server.getOverworld();
            fakePlayer = FakePlayer.get(testWorld, new com.mojang.authlib.GameProfile(UUID.randomUUID(), "MTestBot"));
            Minehop.surfDebugPlayers.add(fakePlayer.getUuid());
            spawnRamp();
            Minehop.LOGGER.info("[MTEST] === harness ready, beginning scenarios ===");
        }

        if (!inSurfPhase) {
            if (scenarioIndex < 0 || scenarioTick >= SCENARIO_TICKS) {
                scenarioIndex++;
                scenarioTick = 0;
                if (scenarioIndex >= SCENARIOS.size()) {
                    inSurfPhase = true;
                    Minehop.LOGGER.info("[MTEST] === AIR DONE, beginning SURF scenarios ===");
                } else {
                    beginScenario(SCENARIOS.get(scenarioIndex));
                    runScenarioTick(SCENARIOS.get(scenarioIndex));
                    scenarioTick++;
                    return;
                }
            } else {
                runScenarioTick(SCENARIOS.get(scenarioIndex));
                scenarioTick++;
                return;
            }
        }

        SurfScenario current = surfIndex >= 0 && surfIndex < SURF_SCENARIOS.size() ? SURF_SCENARIOS.get(surfIndex) : null;
        if (surfIndex < 0 || current == null || surfTick >= current.ticks) {
            surfIndex++;
            surfTick = 0;
            if (surfIndex >= SURF_SCENARIOS.size()) {
                Minehop.LOGGER.info("[MTEST] === ALL SCENARIOS DONE ===");
                server.stop(false);
                return;
            }
            beginSurfScenario(SURF_SCENARIOS.get(surfIndex));
        }
        runSurfScenarioTick(SURF_SCENARIOS.get(surfIndex));
        surfTick++;
    }

    private static void spawnRamp() {
        int cxLo = ((int) Math.floor(RAMP_X)) >> 4;
        int cxHi = ((int) Math.floor(RAMP_X + RAMP_LENGTH)) >> 4;
        int czMid = ((int) Math.floor(RAMP_Z)) >> 4;
        for (int cx = cxLo - 1; cx <= cxHi + 1; cx++) {
            for (int cz = czMid - 1; cz <= czMid + 1; cz++) {
                testWorld.setChunkForced(cx, cz, true);
            }
        }
        testRamp = new SurfRampEntity(ModEntities.SURF_RAMP_ENTITY, testWorld);
        testRamp.setGeometry(
                new Vec3d(RAMP_X, RAMP_Y, RAMP_Z),
                new Vec3d(RAMP_X + RAMP_LENGTH, RAMP_Y, RAMP_Z),
                RAMP_DROP,
                RAMP_WIDTH,
                false,
                1
        );
        testWorld.spawnEntity(testRamp);
        Minehop.LOGGER.info("[MTEST] spawned ramp start=({}, {}, {}) len={} drop={} width={}",
                RAMP_X, RAMP_Y, RAMP_Z, RAMP_LENGTH, RAMP_DROP, RAMP_WIDTH);

        // C2 floor: solid blocks under the straight ramp's low edge (top at FLOOR_TOP_Y).
        int floorY = (int) Math.floor(FLOOR_TOP_Y) - 1;
        for (int bx = (int) RAMP_X - 2; bx <= (int) (RAMP_X + RAMP_LENGTH) + 2; bx++) {
            for (int bz = (int) (RAMP_Z + RAMP_WIDTH) - 4; bz <= (int) (RAMP_Z + RAMP_WIDTH) + 12; bz++) {
                testWorld.setBlockState(new BlockPos(bx, floorY, bz), Blocks.STONE.getDefaultState());
            }
        }

        // C1 curved ramp (bezier: start/end differ in both x and z => isCurved).
        int ccxLo = ((int) Math.floor(CURVE_X)) >> 4;
        int ccxHi = ((int) Math.floor(CURVE_X + 40.0D)) >> 4;
        int cczLo = ((int) Math.floor(CURVE_Z)) >> 4;
        int cczHi = ((int) Math.floor(CURVE_Z + 40.0D)) >> 4;
        for (int cx = ccxLo - 1; cx <= ccxHi + 1; cx++) {
            for (int cz = cczLo - 1; cz <= cczHi + 1; cz++) {
                testWorld.setChunkForced(cx, cz, true);
            }
        }
        testRampCurved = new SurfRampEntity(ModEntities.SURF_RAMP_ENTITY, testWorld);
        testRampCurved.setGeometry(
                new Vec3d(CURVE_X, CURVE_Y, CURVE_Z),
                new Vec3d(CURVE_X + 40.0D, CURVE_Y, CURVE_Z + 40.0D),
                10.0D,
                14.0D,
                false,
                1
        );
        testWorld.spawnEntity(testRampCurved);
        Minehop.LOGGER.info("[MTEST] spawned CURVED ramp start=({}, {}, {}) end=(+40,+0,+40)",
                CURVE_X, CURVE_Y, CURVE_Z);

        // C3 length-declining ramp.
        int dcxLo = ((int) Math.floor(DECL_X)) >> 4;
        int dcxHi = ((int) Math.floor(DECL_X + DECL_LEN)) >> 4;
        int dczMid = ((int) Math.floor(DECL_Z)) >> 4;
        for (int cx = dcxLo - 1; cx <= dcxHi + 1; cx++) {
            for (int cz = dczMid - 1; cz <= dczMid + 1; cz++) {
                testWorld.setChunkForced(cx, cz, true);
            }
        }
        testRampDecline = new SurfRampEntity(ModEntities.SURF_RAMP_ENTITY, testWorld);
        testRampDecline.setGeometry(
                new Vec3d(DECL_X, DECL_Y_TOP, DECL_Z),
                new Vec3d(DECL_X + DECL_LEN, DECL_Y_BOT, DECL_Z),
                DECL_DROP,
                DECL_WIDTH,
                false,
                1
        );
        testWorld.spawnEntity(testRampDecline);
        Minehop.LOGGER.info("[MTEST] spawned DECLINE ramp start=({}, {}, {}) end=(+{}, {}, +0) drop={} width={}",
                DECL_X, DECL_Y_TOP, DECL_Z, DECL_LEN, DECL_Y_BOT, DECL_DROP, DECL_WIDTH);

        // C4 diagonal declining ramp.
        forceChunksAround(DIAG_X, DIAG_Z, DIAG_LEN, DIAG_LEN);
        testRampDiag = new SurfRampEntity(ModEntities.SURF_RAMP_ENTITY, testWorld);
        testRampDiag.setGeometry(
                new Vec3d(DIAG_X, DIAG_Y_TOP, DIAG_Z),
                new Vec3d(DIAG_X + DIAG_LEN, DIAG_Y_BOT, DIAG_Z + DIAG_LEN),
                DIAG_DROP,
                DIAG_WIDTH,
                false,
                1
        );
        testWorld.spawnEntity(testRampDiag);
        Minehop.LOGGER.info("[MTEST] spawned DIAG ramp start=({}, {}, {}) end=(+{},{},+{})",
                DIAG_X, DIAG_Y_TOP, DIAG_Z, DIAG_LEN, DIAG_Y_BOT, DIAG_LEN);

        // C5 steep ramp.
        forceChunksAround(STEEP_X, STEEP_Z, STEEP_LEN, 0.0D);
        testRampSteep = new SurfRampEntity(ModEntities.SURF_RAMP_ENTITY, testWorld);
        testRampSteep.setGeometry(
                new Vec3d(STEEP_X, STEEP_Y, STEEP_Z),
                new Vec3d(STEEP_X + STEEP_LEN, STEEP_Y, STEEP_Z),
                STEEP_DROP,
                STEEP_WIDTH,
                false,
                1
        );
        testWorld.spawnEntity(testRampSteep);
        Minehop.LOGGER.info("[MTEST] spawned STEEP ramp start=({}, {}, {}) drop={} width={}",
                STEEP_X, STEEP_Y, STEEP_Z, STEEP_DROP, STEEP_WIDTH);

        // BIG ramps built the in-game way (densified path points), width/drop > 3 — repro for the
        // "big straight ramp = fall straight through" report (curved control should work).
        java.util.List<Vec3d> straightPts = java.util.List.of(
                new Vec3d(700.0D, 130.0D, 700.0D),
                new Vec3d(730.0D, 130.0D, 700.0D));
        testRampBigStraight = spawnPathRamp(straightPts, 6.0D, 6.0D, 1);
        java.util.List<Vec3d> curvePts = java.util.List.of(
                new Vec3d(800.0D, 130.0D, 700.0D),
                new Vec3d(810.0D, 130.0D, 703.0D),
                new Vec3d(820.0D, 130.0D, 710.0D),
                new Vec3d(830.0D, 130.0D, 721.0D));
        testRampBigCurve = spawnPathRamp(curvePts, 6.0D, 6.0D, 1);
    }

    // Build a ramp the same way SurfRampPlacementManager does: densify the centerline, SPLIT it into
    // <=5-length LINKED segments (each a separate SurfRampEntity sharing the full path with a sub
    // pathT range + linked seams), so the real multi-segment/seam code runs. Returns all segments.
    private static final double SPLIT_MAX_LEN = 5.0D;
    private static List<SurfRampEntity> spawnPathRamp(List<Vec3d> points, double drop, double width, int sideSign) {
        List<Vec3d> dense = new ArrayList<>();
        dense.add(points.get(0));
        for (int i = 1; i < points.size(); i++) {
            Vec3d a = points.get(i - 1), b = points.get(i);
            double dist = Math.hypot(b.x - a.x, b.z - a.z);
            int slices = Math.max(1, (int) Math.ceil(dist / 0.4D));
            for (int s = 1; s <= slices; s++) {
                dense.add(a.lerp(b, (double) s / slices));
            }
        }
        double minX = 1e9, maxX = -1e9, minZ = 1e9, maxZ = -1e9;
        for (Vec3d p : dense) { minX = Math.min(minX, p.x); maxX = Math.max(maxX, p.x); minZ = Math.min(minZ, p.z); maxZ = Math.max(maxZ, p.z); }
        forceChunksAround(minX - width - 2, minZ - width - 2, (maxX - minX) + 2 * width + 4, (maxZ - minZ) + 2 * width + 4);

        // Split into index ranges of <=SPLIT_MAX_LEN horizontal length (share the boundary point).
        List<int[]> ranges = new ArrayList<>();
        int startIdx = 0;
        while (startIdx < dense.size() - 1) {
            int endIdx = startIdx + 1;
            double len = 0.0D;
            while (endIdx < dense.size() - 1) {
                double seg = Math.hypot(dense.get(endIdx).x - dense.get(endIdx - 1).x, dense.get(endIdx).z - dense.get(endIdx - 1).z);
                if (len + seg > SPLIT_MAX_LEN) break;
                len += seg; endIdx++;
            }
            ranges.add(new int[]{startIdx, endIdx});
            startIdx = endIdx;
        }

        List<Vec3d> shared = List.copyOf(dense);
        int span = Math.max(dense.size() - 1, 1);
        List<SurfRampEntity> segs = new ArrayList<>();
        for (int r = 0; r < ranges.size(); r++) {
            int s0 = ranges.get(r)[0], s1 = ranges.get(r)[1];
            Vec3d segStart = dense.get(s0), segEnd = dense.get(s1);
            SurfRampEntity ramp = new SurfRampEntity(ModEntities.SURF_RAMP_ENTITY, testWorld);
            ramp.setGeometry(segStart, segEnd, drop, width, false, sideSign);
            ramp.setCenterlinePoints(shared, (double) s0 / span, (double) s1 / span);
            ramp.setChainId("mtest-chain-" + Math.round(points.get(0).x));
            ramp.setLinkedSeams(r > 0, r < ranges.size() - 1);
            testWorld.spawnEntity(ramp);
            segs.add(ramp);
        }
        Minehop.LOGGER.info("[MTEST] spawned PATH ramp pts={} dense={} SEGMENTS={} start=({},{},{}) drop={} width={}",
                points.size(), dense.size(), segs.size(), dense.get(0).x, dense.get(0).y, dense.get(0).z, drop, width);
        return segs;
    }

    private static SurfRampEntity nearestSeg(List<SurfRampEntity> segs, double x, double z, double feetY) {
        SurfRampEntity best = null; double bestPen = 1e9;
        for (SurfRampEntity r : segs) {
            var ct = r.sampleNearestContact(x, z, feetY, 24.0D);
            if (ct != null) { double d = Math.abs(ct.surfaceY() - feetY); if (d < bestPen) { bestPen = d; best = r; } }
        }
        return best != null ? best : (segs.isEmpty() ? null : segs.get(0));
    }

    private static void forceChunksAround(double x, double z, double extentX, double extentZ) {
        int cxLo = ((int) Math.floor(x)) >> 4;
        int cxHi = ((int) Math.floor(x + extentX)) >> 4;
        int czLo = ((int) Math.floor(z)) >> 4;
        int czHi = ((int) Math.floor(z + extentZ)) >> 4;
        for (int cx = cxLo - 1; cx <= cxHi + 1; cx++) {
            for (int cz = czLo - 1; cz <= czHi + 1; cz++) {
                testWorld.setChunkForced(cx, cz, true);
            }
        }
    }

    private static void beginScenario(Scenario scenario) {
        double spawnX = 8.0D;
        double spawnZ = 8.0D;
        fakePlayer.refreshPositionAndAngles(spawnX, TEST_Y, spawnZ, 0.0F, 0.0F);
        double initBpt = scenario.initSpeedUps / BLOCKS_PER_TICK_TO_UPS;
        fakePlayer.setVelocity(new Vec3d(0.0D, 0.0D, initBpt));
        fakePlayer.setYaw(0.0F);
        fakePlayer.prevYaw = 0.0F;
        fakePlayer.setOnGround(!scenario.airborne);
        fakePlayer.changeGameMode(GameMode.SURVIVAL);
        Minehop.LOGGER.info("[MTEST] --- air '{}' start ---", scenario.name);
    }

    private static void runScenarioTick(Scenario scenario) {
        float prevYaw = fakePlayer.getYaw();
        float newYaw = prevYaw + (float) scenario.yawDeltaPerTick;
        fakePlayer.prevYaw = prevYaw;
        fakePlayer.setYaw(newYaw);
        fakePlayer.setMovementSpeed(0.1F);
        if (!scenario.airborne) {
            fakePlayer.setOnGround(true);
        }

        Vec3d before = fakePlayer.getVelocity();
        double beforeUps = horizontalUps(before);

        fakePlayer.travel(new Vec3d(scenario.sideways, 0.0D, scenario.forward));

        Vec3d after = fakePlayer.getVelocity();
        double afterUps = horizontalUps(after);
        double velHeading = afterUps > 1.0E-6D ? Math.toDegrees(Math.atan2(after.x, after.z)) : 0.0D;

        net.nerdorg.minehop.util.StrafeStats st = Minehop.strafeStatsMap.get(fakePlayer.getNameForScoreboard());
        String statStr = st != null
                ? String.format(Locale.ROOT, " eff=%.1f sync=%.1f gauge=%.1f", st.liveEfficiency, st.liveSync, st.liveGauge)
                : " eff=- sync=- gauge=-";
        Minehop.LOGGER.info(String.format(Locale.ROOT,
                "[MTEST] %s t=%02d yaw=%.1f velHead=%.1f onGround=%b beforeH=%.1f afterH=%.1f dH=%+.2f vy=%.3f%s",
                scenario.name, scenarioTick, newYaw, velHeading, fakePlayer.isOnGround(),
                beforeUps, afterUps, afterUps - beforeUps, after.y, statStr));
    }

    private static void beginSurfScenario(SurfScenario s) {
        double sx = s.x, sy = s.y, sz = s.z, svx = s.vx, svy = s.vy, svz = s.vz;
        if (s.curveSpawnT >= 0.0D && testRampCurved != null) {
            Vec3d center = testRampCurved.sampleCenterline(s.curveSpawnT);
            Vec3d left = testRampCurved.sampleLeft(s.curveSpawnT);
            int side = testRampCurved.getSideSign();
            double width = 14.0D;
            double drop = 10.0D;
            double worldLateral = s.curveSpawnLat * side;
            double normLat = Math.max(0.0D, Math.min(1.0D, s.curveSpawnLat / width));
            double surfY = testRampCurved.sampleBaseY(s.curveSpawnT) + drop * (1.0D - normLat);
            sx = center.x + left.x * worldLateral;
            sz = center.z + left.z * worldLateral;
            sy = surfY + 0.05D;
            // tangent perpendicular to left = (left.z, -left.x)
            double tx = left.z, tz = -left.x;
            double tl = Math.hypot(tx, tz);
            if (tl > 1.0E-6D) { tx /= tl; tz /= tl; }
            svx = tx * s.vx;
            svz = tz * s.vx;
            Minehop.LOGGER.info(String.format(Locale.ROOT, "[MTEST] curveSpawn t=%.2f -> pos=(%.2f,%.3f,%.2f) tan=(%.2f,%.2f) surfY=%.3f",
                    s.curveSpawnT, sx, sy, sz, tx, tz, surfY));
        }
        if (s.name.startsWith("diag_") && testRampDiag != null) {
            double t = 0.18D;
            Vec3d center = testRampDiag.sampleCenterline(t);
            Vec3d left = testRampDiag.sampleLeft(t);
            int side = testRampDiag.getSideSign();
            double lat = 5.0D;
            double normLat = Math.max(0.0D, Math.min(1.0D, lat / DIAG_WIDTH));
            double surfY = testRampDiag.sampleBaseY(t) + DIAG_DROP * (1.0D - normLat);
            sx = center.x + left.x * lat * side;
            sz = center.z + left.z * lat * side;
            sy = surfY + 0.05D;
            double tx = left.z, tz = -left.x; // tangent perpendicular to left
            double tl = Math.hypot(tx, tz);
            if (tl > 1.0E-6D) { tx /= tl; tz /= tl; }
            svx = tx * s.vx;
            svz = tz * s.vx;
            svy = s.vy;
            Minehop.LOGGER.info(String.format(Locale.ROOT, "[MTEST] diagSpawn '%s' pos=(%.2f,%.3f,%.2f) tan=(%.2f,%.2f) surfY=%.3f",
                    s.name, sx, sy, sz, tx, tz, surfY));
        } else if (s.curveSpawnT < 0.0D && s.name.startsWith("steep_") && testRampSteep != null) {
            var ct = testRampSteep.sampleNearestContact(sx, sz, sy, 24.0D);
            if (ct != null) {
                sy = ct.surfaceY() + 0.05D;
                Minehop.LOGGER.info(String.format(Locale.ROOT, "[MTEST] snapSpawn '%s' surfaceY=%.3f -> feetY=%.3f", s.name, ct.surfaceY(), sy));
            }
        } else if (s.name.startsWith("bigstr_surf") || s.name.startsWith("bigcrv_surf")) {
            List<SurfRampEntity> segs = s.name.startsWith("bigstr_") ? testRampBigStraight : testRampBigCurve;
            if (segs != null) {
                SurfRampEntity r = nearestSeg(segs, sx, sz, sy);
                var ct = r != null ? r.sampleNearestContact(sx, sz, sy, 24.0D) : null;
                if (ct != null) {
                    sy = ct.surfaceY() + 0.05D;
                    Minehop.LOGGER.info(String.format(Locale.ROOT, "[MTEST] snapSpawn '%s' surfaceY=%.3f -> feetY=%.3f", s.name, ct.surfaceY(), sy));
                } else {
                    Minehop.LOGGER.info(String.format(Locale.ROOT, "[MTEST] snapSpawn '%s' NO CONTACT at (%.2f,%.2f) feetY=%.2f -> ramp not detected!", s.name, sx, sz, sy));
                }
            }
        }
        fakePlayer.refreshPositionAndAngles(sx, sy, sz, s.yawDeg, 0.0F);
        fakePlayer.setVelocity(new Vec3d(svx, svy, svz));
        fakePlayer.setYaw(s.yawDeg);
        fakePlayer.prevYaw = s.yawDeg;
        fakePlayer.setOnGround(false);
        fakePlayer.changeGameMode(GameMode.SURVIVAL);
        fakePlayer.setMovementSpeed(0.1F);
        Minehop.LOGGER.info(String.format(Locale.ROOT,
                "[MTEST] --- surf '%s' start pos=(%.2f,%.3f,%.2f) vel=(%.3f,%.3f,%.3f) in=(%.1f,%.1f) yaw=%.0f ---",
                s.name, s.x, s.y, s.z, s.vx, s.vy, s.vz, s.inSideways, s.inForward, s.yawDeg));
    }

    private static void runSurfScenarioTick(SurfScenario s) {
        fakePlayer.setYaw(s.yawDeg);
        fakePlayer.prevYaw = s.yawDeg;
        fakePlayer.setMovementSpeed(0.1F);

        if (s.holdSpace && fakePlayer.isOnGround()) {
            Vec3d jv = fakePlayer.getVelocity();
            fakePlayer.setVelocity(jv.x, 300.0D / BLOCKS_PER_TICK_TO_UPS, jv.z);
        }

        fakePlayer.travel(new Vec3d(s.inSideways, 0.0D, s.inForward));

        double feetY = fakePlayer.getBoundingBox().minY;
        double x = fakePlayer.getX();
        double z = fakePlayer.getZ();
        SurfRampEntity r;
        if (s.name.startsWith("decline_")) {
            r = testRampDecline;
        } else if (s.name.startsWith("diag_")) {
            r = testRampDiag;
        } else if (s.name.startsWith("steep_")) {
            r = testRampSteep;
        } else if (s.name.startsWith("bigstr_")) {
            r = nearestSeg(testRampBigStraight, x, z, feetY);
        } else if (s.name.startsWith("bigcrv_")) {
            r = nearestSeg(testRampBigCurve, x, z, feetY);
        } else {
            r = s.curved ? testRampCurved : testRamp;
        }
        Vec3d v = fakePlayer.getVelocity();
        boolean bypass = Minehop.surfCollisionBypassEntities.contains(fakePlayer.getId());
        boolean onGround = fakePlayer.isOnGround();
        var contact = r.sampleNearestContact(x, z, feetY, 12.0D);
        String contactStr;
        if (contact == null) {
            contactStr = "NOCONTACT";
        } else {
            double pen = contact.surfaceY() - feetY; // >0 = feet BELOW surface (sinking through)
            contactStr = String.format(Locale.ROOT, "surfY=%.3f pen=%+.3f ny=%.3f hard=%b",
                    contact.surfaceY(), pen, contact.normal().y, contact.hardEndpoint());
        }
        Minehop.LOGGER.info(String.format(Locale.ROOT,
                "[MTEST] %s t=%02d pos=(%.2f,%.3f,%.2f) vel=(%.3f,%.3f,%.3f) bypass=%b onGround=%b %s",
                s.name, surfTick, x, feetY, z, v.x, v.y, v.z, bypass, onGround, contactStr));
    }

    private static double horizontalUps(Vec3d v) {
        return Math.sqrt(v.x * v.x + v.z * v.z) * BLOCKS_PER_TICK_TO_UPS;
    }

    private static final class Scenario {
        final String name;
        final double sideways;
        final double forward;
        final double yawDeltaPerTick;
        final boolean airborne;
        final double initSpeedUps;

        Scenario(String name, double sideways, double forward, double yawDeltaPerTick, boolean airborne, double initSpeedUps) {
            this.name = name;
            this.sideways = sideways;
            this.forward = forward;
            this.yawDeltaPerTick = yawDeltaPerTick;
            this.airborne = airborne;
            this.initSpeedUps = initSpeedUps;
        }
    }

    private static final class SurfScenario {
        final String name;
        final double x;
        final double y;
        final double z;
        final double vx;
        final double vy;
        final double vz;
        final int ticks;
        final double inSideways;
        final double inForward;
        final float yawDeg;
        final boolean curved;
        final boolean holdSpace;
        final double curveSpawnT;   // >=0: spawn on the curved ramp's true surface at this t, moving along the tangent at speed |vx|
        final double curveSpawnLat;

        SurfScenario(String name, double x, double y, double z, double vx, double vy, double vz, int ticks) {
            this(name, x, y, z, vx, vy, vz, ticks, 0.0D, 0.0D, -90.0F, false, false, -1.0D, 0.0D);
        }

        SurfScenario(String name, double x, double y, double z, double vx, double vy, double vz, int ticks,
                     double inSideways, double inForward, float yawDeg) {
            this(name, x, y, z, vx, vy, vz, ticks, inSideways, inForward, yawDeg, false, false, -1.0D, 0.0D);
        }

        SurfScenario(String name, double x, double y, double z, double vx, double vy, double vz, int ticks,
                     double inSideways, double inForward, float yawDeg, boolean curved) {
            this(name, x, y, z, vx, vy, vz, ticks, inSideways, inForward, yawDeg, curved, false, -1.0D, 0.0D);
        }

        SurfScenario(String name, double x, double y, double z, double vx, double vy, double vz, int ticks,
                     double inSideways, double inForward, float yawDeg, boolean curved, boolean holdSpace) {
            this(name, x, y, z, vx, vy, vz, ticks, inSideways, inForward, yawDeg, curved, holdSpace, -1.0D, 0.0D);
        }

        SurfScenario(String name, double x, double y, double z, double vx, double vy, double vz, int ticks,
                     double inSideways, double inForward, float yawDeg, boolean curved, boolean holdSpace,
                     double curveSpawnT, double curveSpawnLat) {
            this.name = name;
            this.x = x;
            this.y = y;
            this.z = z;
            this.vx = vx;
            this.vy = vy;
            this.vz = vz;
            this.ticks = ticks;
            this.inSideways = inSideways;
            this.inForward = inForward;
            this.yawDeg = yawDeg;
            this.curved = curved;
            this.holdSpace = holdSpace;
            this.curveSpawnT = curveSpawnT;
            this.curveSpawnLat = curveSpawnLat;
        }
    }
}
