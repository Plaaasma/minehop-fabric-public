package net.nerdorg.minehop.anticheat.stream;

/**
 * 1.20.1: the network-thread arrival time a move packet carries to its server-thread pass (implemented on
 * {@code ServerboundMovePlayerPacket} by ServerboundMovePlayerPacketMixin). Before 1.21.2 the move packet is the
 * client tick, so its arrival is the tick's arrival (see ClientTick#arrivalNanos). Both passes handle the same packet
 * object, so the time can't be paired with the wrong packet, also when a handler cancels a packet's server-thread pass
 * (a spectate session ignores the viewer's moves) before Minehop's stream hooks see it.
 */
public interface MovePacketArrival {
    /** {@code System.nanoTime()} on the network thread when the packet arrived, or 0 if not stamped. */
    long minehop$arrivalNanos();

    void minehop$setArrivalNanos(long nanos);
}
