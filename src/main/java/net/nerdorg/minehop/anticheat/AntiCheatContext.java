package net.nerdorg.minehop.anticheat;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import net.nerdorg.minehop.config.MinehopConfig;

public final class AntiCheatContext {
    public final ServerPlayerEntity player;
    public final AntiCheatPlayerState state;
    public final MinehopConfig config;
    public final Vec3d preMovePosition;
    public final Vec3d postMovePosition;
    public final Vec3d preMoveVelocity;
    public final Vec3d postMoveVelocity;
    public final Vec3d movedDelta;
    public final boolean onGround;
    public final boolean wasOnGround;
    public final boolean climbing;
    public final boolean inFluid;
    public final boolean creativeOrSpectator;
    public final boolean surfing;
    public final boolean usingPlotCreative;
    public final boolean justTeleported;
    public final boolean jumpThisTick;
    public final double speedCap;
    public final long worldTick;

    public AntiCheatContext(
            ServerPlayerEntity player,
            AntiCheatPlayerState state,
            MinehopConfig config,
            Vec3d preMovePosition,
            Vec3d postMovePosition,
            Vec3d preMoveVelocity,
            Vec3d postMoveVelocity,
            boolean onGround,
            boolean wasOnGround,
            boolean climbing,
            boolean inFluid,
            boolean creativeOrSpectator,
            boolean surfing,
            boolean usingPlotCreative,
            boolean justTeleported,
            boolean jumpThisTick,
            double speedCap,
            long worldTick
    ) {
        this.player = player;
        this.state = state;
        this.config = config;
        this.preMovePosition = preMovePosition;
        this.postMovePosition = postMovePosition;
        this.preMoveVelocity = preMoveVelocity == null ? Vec3d.ZERO : preMoveVelocity;
        this.postMoveVelocity = postMoveVelocity == null ? Vec3d.ZERO : postMoveVelocity;
        this.movedDelta = postMovePosition.subtract(preMovePosition);
        this.onGround = onGround;
        this.wasOnGround = wasOnGround;
        this.climbing = climbing;
        this.inFluid = inFluid;
        this.creativeOrSpectator = creativeOrSpectator;
        this.surfing = surfing;
        this.usingPlotCreative = usingPlotCreative;
        this.justTeleported = justTeleported;
        this.jumpThisTick = jumpThisTick;
        this.speedCap = speedCap;
        this.worldTick = worldTick;
    }

    public double horizontalSpeed() {
        double dx = this.movedDelta.x;
        double dz = this.movedDelta.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    public double verticalDelta() {
        return this.movedDelta.y;
    }
}
