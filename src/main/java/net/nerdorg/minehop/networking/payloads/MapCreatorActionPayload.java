package net.nerdorg.minehop.networking.payloads;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.nerdorg.minehop.Minehop;

public record MapCreatorActionPayload(
        String action,
        String mapName,
        int difficulty,
        boolean arena,
        boolean hns,
        boolean surf,
        boolean kz,
        boolean movementOverride,
        double movementSvFriction,
        double movementSvAccelerate,
        double movementSvAiraccelerate,
        double movementSvMaxairspeed,
        double movementSvJumpImpulse,
        double movementSpeedMul,
        double movementSvGravity,
        double movementSvStopspeed,
        double movementSpeedCoefficient,
        boolean movementAutoStepUp,
        boolean movementCssCrouchJump,
        boolean movementFallDamage,
        int checkpointIndex
) implements CustomPayload {
    public static final String ACTION_CREATE_OR_UPDATE = "create_or_update";
    public static final String ACTION_SET_SPAWN = "set_spawn";
    public static final String ACTION_ADD_CHECKPOINT = "add_checkpoint";
    public static final String ACTION_ADD_START_ZONE = "add_start_zone";
    public static final String ACTION_ADD_END_ZONE = "add_end_zone";
    public static final String ACTION_ADD_RESET_ZONE = "add_reset_zone";

    private static final int MAX_ACTION_LENGTH = 48;
    private static final int MAX_MAP_NAME_LENGTH = 128;
    public static final Identifier HANDSHAKE_ID = Identifier.of(Minehop.MOD_ID, "map_creator_action");
    public static final Id<MapCreatorActionPayload> ID = new Id<>(HANDSHAKE_ID);
    public static final PacketCodec<PacketByteBuf, MapCreatorActionPayload> CODEC = PacketCodec.of(
            (value, buf) -> {
                buf.writeString(value.action == null ? "" : value.action, MAX_ACTION_LENGTH);
                buf.writeString(value.mapName == null ? "" : value.mapName, MAX_MAP_NAME_LENGTH);
                buf.writeInt(value.difficulty);
                buf.writeBoolean(value.arena);
                buf.writeBoolean(value.hns);
                buf.writeBoolean(value.surf);
                buf.writeBoolean(value.kz);
                buf.writeBoolean(value.movementOverride);
                buf.writeDouble(value.movementSvFriction);
                buf.writeDouble(value.movementSvAccelerate);
                buf.writeDouble(value.movementSvAiraccelerate);
                buf.writeDouble(value.movementSvMaxairspeed);
                buf.writeDouble(value.movementSvJumpImpulse);
                buf.writeDouble(value.movementSpeedMul);
                buf.writeDouble(value.movementSvGravity);
                buf.writeDouble(value.movementSvStopspeed);
                buf.writeDouble(value.movementSpeedCoefficient);
                buf.writeBoolean(value.movementAutoStepUp);
                buf.writeBoolean(value.movementCssCrouchJump);
                buf.writeBoolean(value.movementFallDamage);
                buf.writeInt(value.checkpointIndex);
            },
            buf -> new MapCreatorActionPayload(
                    buf.readString(MAX_ACTION_LENGTH),
                    buf.readString(MAX_MAP_NAME_LENGTH),
                    buf.readInt(),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readInt()
            )
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
