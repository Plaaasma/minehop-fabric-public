package net.nerdorg.minehop.anticheat.stream;

/**
 * 1.21.1 port: stand-in for 1.21.2+'s {@code net.minecraft.world.entity.player.Input} (same accessor names, so the
 * checks and the tick stream ({@link ClientTick#input}) read the same as on 1.21.4). Pre-1.21.2 clients never report
 * their movement keys: {@code ServerboundPlayerInputPacket} is vehicle-only (sent only while riding) and there is no
 * per-tick input packet. Only sneak and sprint are known, from {@code ServerboundPlayerCommandPacket}
 * (PRESS/RELEASE_SHIFT_KEY, START/STOP_SPRINTING), which the client sends right before that tick's move packet.
 * W/A/S/D and jump stay {@code false} here and every check that would need them is gated on
 * {@link MovementValidator}'s *_KNOWN flags, so the unknown keys can never create a flag; replay frames record them as
 * not pressed.
 */
public record Input(boolean forward, boolean backward, boolean left, boolean right, boolean jump, boolean shift,
                    boolean sprint) {
    public static final Input EMPTY = new Input(false, false, false, false, false, false, false);

    Input withShift(boolean shift) {
        return new Input(this.forward, this.backward, this.left, this.right, this.jump, shift, this.sprint);
    }

    Input withSprint(boolean sprint) {
        return new Input(this.forward, this.backward, this.left, this.right, this.jump, this.shift, sprint);
    }
}
