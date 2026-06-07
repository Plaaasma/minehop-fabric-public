package net.nerdorg.minehop.anticheat;

import net.minecraft.util.math.Vec3d;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class AntiCheatPlayerState {
    public static final int RECENT_FLAG_HISTORY = 32;
    public static final int POSITION_HISTORY = 8;

    private final UUID playerUuid;
    private String lastKnownName = "";

    private Vec3d lastVerifiedPos;
    private Vec3d lastReportedPos;
    private Vec3d lastTickVelocity = Vec3d.ZERO;
    private double topRecentHorizontalSpeed;
    private int airborneTicks;
    private int ticksSinceMoved;
    private int ticksSinceJump = Integer.MAX_VALUE / 2;
    private long lastTickTime = Long.MIN_VALUE;
    private long lastAuthorizedTeleportTick = Long.MIN_VALUE;
    private long lastLagbackTick = Long.MIN_VALUE;
    private int consecutiveLagbacks;

    private final Map<String, Double> violationLevels = new HashMap<>();
    private final Deque<AntiCheatFlag> recentFlags = new ArrayDeque<>();
    private int totalFlagCount;

    public AntiCheatPlayerState(UUID playerUuid) {
        this.playerUuid = playerUuid;
    }

    public UUID playerUuid() {
        return this.playerUuid;
    }

    public String lastKnownName() {
        return this.lastKnownName == null ? "" : this.lastKnownName;
    }

    public void setLastKnownName(String name) {
        if (name != null) {
            this.lastKnownName = name;
        }
    }

    public Vec3d lastVerifiedPos() {
        return this.lastVerifiedPos;
    }

    public void setLastVerifiedPos(Vec3d pos) {
        this.lastVerifiedPos = pos;
    }

    public Vec3d lastReportedPos() {
        return this.lastReportedPos;
    }

    public void setLastReportedPos(Vec3d pos) {
        this.lastReportedPos = pos;
    }

    public Vec3d lastTickVelocity() {
        return this.lastTickVelocity == null ? Vec3d.ZERO : this.lastTickVelocity;
    }

    public void setLastTickVelocity(Vec3d velocity) {
        this.lastTickVelocity = velocity == null ? Vec3d.ZERO : velocity;
    }

    public double topRecentHorizontalSpeed() {
        return this.topRecentHorizontalSpeed;
    }

    public void updateTopRecentHorizontalSpeed(double speed) {
        if (speed > this.topRecentHorizontalSpeed) {
            this.topRecentHorizontalSpeed = speed;
        } else {
            this.topRecentHorizontalSpeed *= 0.985D;
            if (this.topRecentHorizontalSpeed < 0.001D) {
                this.topRecentHorizontalSpeed = 0.0D;
            }
        }
    }

    public int airborneTicks() {
        return this.airborneTicks;
    }

    public void incrementAirborneTicks() {
        if (this.airborneTicks < Integer.MAX_VALUE - 1) {
            this.airborneTicks++;
        }
    }

    public void resetAirborneTicks() {
        this.airborneTicks = 0;
    }

    public int ticksSinceMoved() {
        return this.ticksSinceMoved;
    }

    public void incrementTicksSinceMoved() {
        if (this.ticksSinceMoved < Integer.MAX_VALUE - 1) {
            this.ticksSinceMoved++;
        }
    }

    public void resetTicksSinceMoved() {
        this.ticksSinceMoved = 0;
    }

    public int ticksSinceJump() {
        return this.ticksSinceJump;
    }

    public void markJumpThisTick() {
        this.ticksSinceJump = 0;
    }

    public void incrementTicksSinceJump() {
        if (this.ticksSinceJump < Integer.MAX_VALUE - 1) {
            this.ticksSinceJump++;
        }
    }

    public long lastTickTime() {
        return this.lastTickTime;
    }

    public void setLastTickTime(long tick) {
        this.lastTickTime = tick;
    }

    public long lastAuthorizedTeleportTick() {
        return this.lastAuthorizedTeleportTick;
    }

    public void markAuthorizedTeleport(long tick) {
        this.lastAuthorizedTeleportTick = tick;
        this.consecutiveLagbacks = 0;
    }

    public long lastLagbackTick() {
        return this.lastLagbackTick;
    }

    public void markLagback(long tick) {
        this.lastLagbackTick = tick;
        this.consecutiveLagbacks++;
    }

    public int consecutiveLagbacks() {
        return this.consecutiveLagbacks;
    }

    public void resetConsecutiveLagbacks() {
        this.consecutiveLagbacks = 0;
    }

    public double getViolationLevel(String checkName) {
        if (checkName == null) {
            return 0.0D;
        }
        return this.violationLevels.getOrDefault(checkName, 0.0D);
    }

    public double addViolation(String checkName, double increment) {
        if (checkName == null) {
            return 0.0D;
        }
        double next = this.violationLevels.getOrDefault(checkName, 0.0D) + increment;
        if (next < 0.0D) {
            next = 0.0D;
        }
        this.violationLevels.put(checkName, next);
        return next;
    }

    public void decayAllViolations(double decayRate) {
        if (this.violationLevels.isEmpty()) {
            return;
        }
        this.violationLevels.replaceAll((k, v) -> {
            double next = v - decayRate;
            return next < 0.0D ? 0.0D : next;
        });
    }

    public void clearViolations() {
        this.violationLevels.clear();
    }

    public int totalFlagCount() {
        return this.totalFlagCount;
    }

    public Deque<AntiCheatFlag> recentFlags() {
        return this.recentFlags;
    }

    public void addFlag(AntiCheatFlag flag) {
        if (flag == null) {
            return;
        }
        this.recentFlags.addLast(flag);
        while (this.recentFlags.size() > RECENT_FLAG_HISTORY) {
            this.recentFlags.pollFirst();
        }
        this.totalFlagCount++;
    }

    public void clearRecentFlags() {
        this.recentFlags.clear();
        this.violationLevels.clear();
    }
}
