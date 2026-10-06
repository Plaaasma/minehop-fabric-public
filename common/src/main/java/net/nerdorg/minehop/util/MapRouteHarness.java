package net.nerdorg.minehop.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.entity.custom.StartEntity;
import net.nerdorg.minehop.entity.custom.SurfRampEntity;
import net.nerdorg.minehop.platform.Services;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Dev-only bot harness for verifying converted surf maps on the REAL travel() path (same idea as
 * {@link MovementTestHarness}). Enabled with {@code -Dminehop.maptest=<request.json>}; no-op otherwise.
 *
 * <p>Every second it looks for the request file. A request lists rollouts: a fake player is placed at a position with a
 * velocity and driven with simple inputs (hold a strafe key while on the launch ramp, then turn at a constant rate
 * while air-strafing) until it touches another ramp face, enters a zone, lands on blocks, or times out. Results are
 * appended as JSON lines to the request's "out" file; the request file is renamed to *.done afterwards.
 */
public final class MapRouteHarness {
    private static final String REQUEST = System.getProperty("minehop.maptest", "");
    private static final boolean ENABLED = !REQUEST.isBlank();
    private static final double UPS = 800.0D;
    private static final int TRAVEL_BUDGET_PER_TICK = 1500;

    private static int tickCounter;
    private static Job job;

    private MapRouteHarness() {
    }

    public static void register() {
        if (!ENABLED) {
            return;
        }
        Services.EVENTS.onServerTickEnd(MapRouteHarness::tick);
        Minehop.LOGGER.info("[MAPTEST] map route harness enabled, request file {}", REQUEST);
    }

    private static void tick(MinecraftServer server) {
        if (job == null) {
            if (++tickCounter % 20 != 0) {
                return;
            }
            Path path = Paths.get(REQUEST);
            if (!Files.exists(path)) {
                return;
            }
            try {
                job = Job.load(server, path);
                Minehop.LOGGER.info("[MAPTEST] loaded request: {} rollouts in {}", job.rollouts.size(), job.level.dimension().identifier());
            } catch (Exception exception) {
                Minehop.LOGGER.error("[MAPTEST] bad request", exception);
                try {
                    Files.move(path, Paths.get(REQUEST + ".bad"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException ignored) {
                }
            }
            return;
        }
        int budget = TRAVEL_BUDGET_PER_TICK;
        while (budget > 0 && job != null) {
            budget -= job.step(budget);
            if (job.finished()) {
                job.close();
                job = null;
            }
        }
    }

    private static final class Zone {
        final String kind;
        final int index;
        final AABB box;

        Zone(String kind, int index, AABB box) {
            this.kind = kind;
            this.index = index;
            this.box = box;
        }
    }

    private static final class Rollout {
        String id;
        Vec3 pos;
        Vec3 vel;
        float yaw;
        int sourceFace = -1;
        double holdSide;
        double holdFwd;
        boolean yawFollowVel = true;
        int minPhase1 = 0;
        double omega;
        double airSide;
        double airFwd;
        boolean jumpOnGround;
        boolean stopOnGround = true;
        boolean probe;
        int maxTicks = 200;
        boolean trace;
        Vec3 aim;          // air phase: steer toward this point by air-strafing (overrides omega)
        double aimLead = 3.0D;
    }

    private static final class Job {
        ServerLevel level;
        ServerPlayer bot;
        List<Zone> zones = new ArrayList<>();
        List<Rollout> rollouts = new ArrayList<>();
        Path requestPath;
        Writer out;
        int index = -1;
        int t;
        boolean leftSource;
        int noSourceTicks;
        int groundTicks;
        // probe statistics
        double maxPen;
        int contactTicks;
        int groundOnRampTicks;
        Rollout cur;
        double jumpImpulse = 290.0D / UPS;
        Vec3 contactNormal;
        int lastFace = -1;
        int curFace = -1;      // face being ridden (route rollouts)
        int airTicks;          // consecutive ticks without any ramp contact
        int pendingLanding = -1;

        static Job load(MinecraftServer server, Path path) throws IOException {
            JsonObject root = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
            Job job = new Job();
            job.requestPath = path;
            ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, Identifier.parse(root.get("dimension").getAsString()));
            job.level = server.getLevel(key);
            if (job.level == null) {
                throw new IOException("no such dimension " + key);
            }
            if (root.has("jumpImpulseUps")) {
                job.jumpImpulse = root.get("jumpImpulseUps").getAsDouble() / UPS;
            }
            JsonArray zones = root.getAsJsonArray("zones");
            for (int i = 0; zones != null && i < zones.size(); i++) {
                JsonObject z = zones.get(i).getAsJsonObject();
                JsonArray a = z.getAsJsonArray("min");
                JsonArray b = z.getAsJsonArray("max");
                job.zones.add(new Zone(z.get("kind").getAsString(), i, new AABB(
                        a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble(),
                        b.get(0).getAsDouble(), b.get(1).getAsDouble(), b.get(2).getAsDouble())));
            }
            for (JsonElement element : root.getAsJsonArray("rollouts")) {
                JsonObject r = element.getAsJsonObject();
                Rollout ro = new Rollout();
                ro.id = r.get("id").getAsString();
                ro.pos = vec(r.getAsJsonArray("pos"));
                ro.vel = vec(r.getAsJsonArray("vel"));
                ro.yaw = r.get("yaw").getAsFloat();
                if (r.has("sourceFace")) ro.sourceFace = r.get("sourceFace").getAsInt();
                if (r.has("holdSide")) ro.holdSide = r.get("holdSide").getAsDouble();
                if (r.has("holdFwd")) ro.holdFwd = r.get("holdFwd").getAsDouble();
                if (r.has("yawFollowVel")) ro.yawFollowVel = r.get("yawFollowVel").getAsBoolean();
                if (r.has("minPhase1")) ro.minPhase1 = r.get("minPhase1").getAsInt();
                if (r.has("omega")) ro.omega = r.get("omega").getAsDouble();
                if (r.has("airSide")) ro.airSide = r.get("airSide").getAsDouble();
                if (r.has("airFwd")) ro.airFwd = r.get("airFwd").getAsDouble();
                if (r.has("jumpOnGround")) ro.jumpOnGround = r.get("jumpOnGround").getAsBoolean();
                if (r.has("stopOnGround")) ro.stopOnGround = r.get("stopOnGround").getAsBoolean();
                if (r.has("probe")) ro.probe = r.get("probe").getAsBoolean();
                if (r.has("maxTicks")) ro.maxTicks = r.get("maxTicks").getAsInt();
                if (r.has("trace")) ro.trace = r.get("trace").getAsBoolean();
                if (r.has("aim")) ro.aim = vec(r.getAsJsonArray("aim"));
                if (r.has("aimLead")) ro.aimLead = r.get("aimLead").getAsDouble();
                job.rollouts.add(ro);
            }
            Path outPath = Paths.get(root.get("out").getAsString());
            job.out = Files.newBufferedWriter(outPath, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            job.bot = Services.PLATFORM.createFakePlayer(job.level, new com.mojang.authlib.GameProfile(UUID.randomUUID(), "MapTestBot"));
            job.bot.setGameMode(GameType.ADVENTURE);
            // Same effective movement config as a real runner of this map: runners get the map's override once a
            // start zone of the map registers them (Minehop.playerMapLocation).
            String map = root.has("map") ? root.get("map").getAsString() : "";
            for (Entity entity : job.level.getAllEntities()) {
                if (entity instanceof StartEntity start && start.getPairedMap().equals(map)) {
                    Minehop.playerMapLocation.put(job.bot.getStringUUID(), start);
                    break;
                }
            }
            Minehop.LOGGER.info("[MAPTEST] bot map binding: {}", Minehop.playerMapLocation.containsKey(job.bot.getStringUUID()) ? map : "NONE (global config)");
            return job;
        }

        private static Vec3 vec(JsonArray a) {
            return new Vec3(a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble());
        }

        boolean finished() {
            return this.index >= this.rollouts.size();
        }

        void close() {
            try {
                this.out.write("{\"done\":true}\n");
                this.out.close();
                Files.move(this.requestPath, Paths.get(this.requestPath + ".done"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException exception) {
                Minehop.LOGGER.error("[MAPTEST] close failed", exception);
            }
            Minehop.playerMapLocation.remove(this.bot.getStringUUID());
            Minehop.LOGGER.info("[MAPTEST] request finished");
        }

        private void begin(Rollout r) {
            this.cur = r;
            this.t = 0;
            this.leftSource = r.sourceFace < 0;
            this.noSourceTicks = 0;
            this.groundTicks = 0;
            this.maxPen = -1e9;
            this.contactTicks = 0;
            this.groundOnRampTicks = 0;
            this.bot.snapTo(r.pos.x, r.pos.y, r.pos.z, r.yaw, 0.0F);
            this.bot.setDeltaMovement(r.vel);
            this.bot.setYRot(r.yaw);
            this.bot.yRotO = r.yaw;
            this.bot.setOnGround(false);
            this.bot.fallDistance = 0.0F;
            this.bot.setSpeed(0.1F);
            this.lastFace = r.sourceFace;
            this.contactNormal = null;
            this.curFace = r.sourceFace;
            this.airTicks = r.sourceFace >= 0 ? 0 : 99;
            this.pendingLanding = -1;
        }

        /** Runs travel() calls for the current/next rollouts; returns the number of calls used. */
        int step(int budget) {
            int used = 0;
            while (used < budget && !this.finished()) {
                if (this.cur == null) {
                    this.index++;
                    if (this.finished()) {
                        break;
                    }
                    this.begin(this.rollouts.get(this.index));
                }
                String result = this.tickOnce();
                used++;
                if (result != null) {
                    this.emit(result);
                    this.cur = null;
                }
            }
            return Math.max(used, 1);
        }

        private int faceOf(SurfRampEntity ramp) {
            for (String tag : ramp.getTags()) {
                if (tag.startsWith("mhr_")) {
                    String[] parts = tag.split("_");
                    try {
                        return Integer.parseInt(parts[1]);
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            return -2;
        }

        /** Face id of a ramp in contact with the bot's feet (or -1), and the deepest penetration into it. */
        private double[] contact() {
            AABB box = this.bot.getBoundingBox();
            double feet = box.minY;
            double cx = this.bot.getX();
            double cz = this.bot.getZ();
            double[] best = {-1, -1e9};
            this.contactNormal = null;
            for (SurfRampEntity ramp : SurfRampEntity.collectNearbyRamps(this.level, box, 1.0D)) {
                int face = this.faceOf(ramp);
                double pen = -1e9;
                Vec3 normal = null;
                for (int i = 0; i < 5; i++) {
                    // centre + the four corners of the 0.6x0.6 hull (the hull rests on its up-slope corner)
                    double sx = cx + (i == 0 ? 0.0 : (i == 1 || i == 2 ? 0.3 : -0.3));
                    double sz = cz + (i == 0 ? 0.0 : (i == 1 || i == 3 ? 0.3 : -0.3));
                    var c = ramp.sampleSurface(sx, sz, feet, 2.0D, 0.6D);
                    if (c != null && c.surfaceY() - feet > pen) {
                        pen = c.surfaceY() - feet;
                        normal = c.normal();
                    }
                }
                if (pen > -0.35D && pen > best[1]) {
                    best[0] = face;
                    best[1] = pen;
                    this.contactNormal = normal;
                }
            }
            return best;
        }

        private String tickOnce() {
            Rollout r = this.cur;
            Vec3 v = this.bot.getDeltaMovement();
            double side;
            double fwd;
            float yaw = this.bot.getYRot();
            boolean riding = r.probe ? !this.leftSource : (this.curFace >= 0 && this.airTicks < 2);
            if (riding || this.t < r.minPhase1) {
                side = r.holdSide;
                fwd = r.holdFwd;
                if (r.yawFollowVel && v.horizontalDistanceSqr() > 1.0E-6D) {
                    yaw = (float) (Math.toDegrees(Math.atan2(-v.x, v.z)));
                }
                if (!r.probe && this.lastFace >= 0 && this.contactNormal != null) {
                    // ride like a surfer: look ALONG the ramp (horizontal direction in its plane) in the direction of
                    // travel, not down the fall line
                    double tx = -this.contactNormal.z, tz = this.contactNormal.x;
                    double tl = Math.sqrt(tx * tx + tz * tz);
                    if (tl > 1.0E-6D) {
                        tx /= tl;
                        tz /= tl;
                        double fx0 = -Math.sin(Math.toRadians(this.bot.getYRot())), fz0 = Math.cos(Math.toRadians(this.bot.getYRot()));
                        double refx = v.horizontalDistanceSqr() > 1.0E-4D ? v.x : fx0;
                        double refz = v.horizontalDistanceSqr() > 1.0E-4D ? v.z : fz0;
                        if (tx * refx + tz * refz < 0.0D) {
                            tx = -tx;
                            tz = -tz;
                        }
                        yaw = (float) Math.toDegrees(Math.atan2(-tx, tz));
                    }
                }
                if (this.lastFace >= 0 && this.contactNormal != null && side != 0.0D) {
                    // hold the strafe key that pushes into the ramp we are riding (sign follows the view)
                    double yr = Math.toRadians(yaw);
                    double into = -(Math.cos(yr) * this.contactNormal.x + Math.sin(yr) * this.contactNormal.z);
                    side = into >= 0.0D ? 1.0D : -1.0D;
                }
            } else if (r.aim != null && v.horizontalDistanceSqr() > 1.0E-6D) {
                // Air-strafe toward the aim point like a player: view on the velocity heading (+/- a small lead),
                // strafe key toward the side the target is on (A = left, D = right); nothing when aligned.
                double headingDeg = Math.toDegrees(Math.atan2(-v.x, v.z));
                double hr = Math.toRadians(headingDeg);
                double fx = -Math.sin(hr), fz = Math.cos(hr);
                double lx = Math.cos(hr), lz = Math.sin(hr);
                double dx = r.aim.x - this.bot.getX(), dz = r.aim.z - this.bot.getZ();
                double dist = Math.sqrt(dx * dx + dz * dz);
                double ang = Math.toDegrees(Math.atan2(dx * lx + dz * lz, dx * fx + dz * fz));
                fwd = 0.0D;
                if (dist < 2.0D || Math.abs(ang) < 2.0D || Math.abs(ang) > 150.0D) {
                    side = 0.0D;
                    yaw = (float) headingDeg;
                } else if (ang > 0.0D) {
                    side = 1.0D;
                    yaw = (float) (headingDeg - r.aimLead);
                } else {
                    side = -1.0D;
                    yaw = (float) (headingDeg + r.aimLead);
                }
            } else {
                side = r.airSide;
                fwd = r.airFwd;
                yaw = (float) (yaw + r.omega);
            }
            if (r.jumpOnGround && this.bot.onGround()) {
                this.bot.setDeltaMovement(v.x, this.jumpImpulse, v.z);
            }
            this.bot.yRotO = this.bot.getYRot();
            this.bot.setYRot(yaw);
            this.bot.setSpeed(0.1F);
            this.bot.travel(new Vec3(side, 0.0D, fwd));
            this.t++;

            double[] c = this.contact();
            int face = (int) c[0];
            this.lastFace = face;
            Vec3 p = this.bot.position();
            boolean ground = this.bot.onGround();
            if (r.trace) {
                Vec3 nv = this.bot.getDeltaMovement();
                Minehop.LOGGER.info(String.format(Locale.ROOT, "[MAPTEST] %s t=%d pos=(%.2f,%.2f,%.2f) v=(%.3f,%.3f,%.3f) h=%.0f ground=%b face=%d pen=%.2f",
                        r.id, this.t, p.x, p.y, p.z, nv.x, nv.y, nv.z, Math.sqrt(nv.x * nv.x + nv.z * nv.z) * UPS, ground, face, c[1]));
            }
            if (r.probe) {
                if (face == r.sourceFace) {
                    this.contactTicks++;
                    this.maxPen = Math.max(this.maxPen, c[1]);
                    double frac = p.y - Math.floor(p.y);
                    if (ground && frac > 0.02D && frac < 0.98D) {
                        // grounded but not on a block top: standing on the ramp itself
                        this.groundOnRampTicks++;
                    }
                }
                return this.t >= r.maxTicks ? "probe" : null;
            }
            // A rollout rides across seams / overlapping faces while in contact and only ends on a LANDING: touching a
            // face other than the one ridden after at least 3 airborne ticks.
            if (face >= 0) {
                if (face != this.curFace && this.airTicks >= 3) {
                    this.pendingLanding = face;
                }
                this.curFace = face;
                this.airTicks = 0;
            } else if (this.airTicks < 1000) {
                this.airTicks++;
            }
            for (Zone z : this.zones) {
                if (z.box.contains(p)) {
                    if (z.kind.equals("end")) {
                        return "end";
                    }
                    if (z.kind.equals("reset")) {
                        return "fail:" + z.index;
                    }
                }
            }
            if (this.pendingLanding >= 0) {
                int landed = this.pendingLanding;
                this.pendingLanding = -1;
                return "face:" + landed;
            }
            if (ground && face < 0) {
                if (++this.groundTicks >= 3 && r.stopOnGround) {
                    return "ground";
                }
            } else {
                this.groundTicks = 0;
            }
            if (p.y < this.level.getMinY() + 2) {
                return "void";
            }
            return this.t >= r.maxTicks ? "timeout" : null;
        }

        private void emit(String result) {
            Vec3 p = this.bot.position();
            Vec3 v = this.bot.getDeltaMovement();
            JsonObject o = new JsonObject();
            o.addProperty("id", this.cur.id);
            o.addProperty("result", result);
            o.addProperty("ticks", this.t);
            JsonArray pos = new JsonArray();
            pos.add(round(p.x));
            pos.add(round(p.y));
            pos.add(round(p.z));
            o.add("pos", pos);
            o.addProperty("speed", Math.round(Math.sqrt(v.x * v.x + v.z * v.z) * UPS));
            JsonArray vel = new JsonArray();
            vel.add(round(v.x));
            vel.add(round(v.y));
            vel.add(round(v.z));
            o.add("vel", vel);
            o.addProperty("yaw", round(this.bot.getYRot()));
            o.addProperty("vy", Math.round(v.y * UPS));
            if (this.cur.probe) {
                o.addProperty("contactTicks", this.contactTicks);
                o.addProperty("maxPen", round(this.maxPen));
                o.addProperty("groundOnRampTicks", this.groundOnRampTicks);
            }
            try {
                this.out.write(o.toString());
                this.out.write("\n");
            } catch (IOException exception) {
                Minehop.LOGGER.error("[MAPTEST] write failed", exception);
            }
        }

        private static double round(double value) {
            return Math.round(value * 1000.0D) / 1000.0D;
        }
    }
}
