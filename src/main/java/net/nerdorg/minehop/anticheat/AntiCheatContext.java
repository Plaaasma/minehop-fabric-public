package net.nerdorg.minehop.anticheat;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.nerdorg.minehop.config.MinehopConfig;

public final class AntiCheatContext {
    public final ServerPlayer player;
    public final AntiCheatPlayerState state;
    public final MinehopConfig config;
    public final Vec3 preMovePosition;
    public final Vec3 postMovePosition;
    public final Vec3 preMoveVelocity;
    public final Vec3 postMoveVelocity;
    public final Vec3 movedDelta;
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
            ServerPlayer player,
            AntiCheatPlayerState state,
            MinehopConfig config,
            Vec3 preMovePosition,
            Vec3 postMovePosition,
            Vec3 preMoveVelocity,
            Vec3 postMoveVelocity,
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
        this.preMoveVelocity = preMoveVelocity == null ? Vec3.ZERO : preMoveVelocity;
        this.postMoveVelocity = postMoveVelocity == null ? Vec3.ZERO : postMoveVelocity;
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
