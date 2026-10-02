package net.nerdorg.minehop.networking.payloads;

import net.minecraft.network.PacketByteBuf;
import net.nerdorg.minehop.networking.codec.PacketCodec;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.util.Identifier;
import net.nerdorg.minehop.Minehop;

public record OpenMapCreatorScreenPayload(
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
        double movementSpeedCap,
        boolean movementAutoStepUp,
        boolean movementCssCrouchJump,
        boolean movementDisableSprint,
        boolean movementFallDamage,
        int checkpointIndex
) implements FabricPacket {
    private static final int MAX_MAP_NAME_LENGTH = 128;
    public static final Identifier HANDSHAKE_ID = new Identifier(Minehop.MOD_ID, "open_map_creator_screen");
    public static final PacketType<OpenMapCreatorScreenPayload> ID = PacketType.create(HANDSHAKE_ID, buf -> OpenMapCreatorScreenPayload.CODEC.decode(buf));
    public static final PacketCodec<PacketByteBuf, OpenMapCreatorScreenPayload> CODEC = PacketCodec.of(
            (value, buf) -> {
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
                buf.writeDouble(value.movementSpeedCap);
                buf.writeBoolean(value.movementAutoStepUp);
                buf.writeBoolean(value.movementCssCrouchJump);
                buf.writeBoolean(value.movementDisableSprint);
                buf.writeBoolean(value.movementFallDamage);
                buf.writeInt(value.checkpointIndex);
            },
            buf -> new OpenMapCreatorScreenPayload(
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
                    buf.readDouble(),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readInt()
            )
    );

    @Override
    public void write(PacketByteBuf buf) {
        CODEC.encode(buf, this);
    }

    @Override
    public PacketType<?> getType() {
        return ID;
    }
}
