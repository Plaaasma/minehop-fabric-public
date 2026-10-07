package net.nerdorg.minehop.mixin;

import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.nerdorg.minehop.anticheat.stream.MovePacketArrival;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * 1.20.1: lets a move packet carry the time it arrived on the network thread to its server-thread pass (see
 * {@link MovePacketArrival}).
 */
@Mixin(ServerboundMovePlayerPacket.class)
public abstract class ServerboundMovePlayerPacketMixin implements MovePacketArrival {
    @Unique
    private volatile long minehop$arrivalNanos;

    @Override
    public long minehop$arrivalNanos() {
        return this.minehop$arrivalNanos;
    }

    @Override
    public void minehop$setArrivalNanos(long nanos) {
        this.minehop$arrivalNanos = nanos;
    }
}
