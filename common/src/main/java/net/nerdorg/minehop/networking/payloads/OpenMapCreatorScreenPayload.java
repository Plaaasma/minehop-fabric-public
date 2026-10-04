package net.nerdorg.minehop.networking.payloads;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
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
) implements CustomPacketPayload {
    private static final int MAX_MAP_NAME_LENGTH = 128;
    public static final ResourceLocation HANDSHAKE_ID = ResourceLocation.fromNamespaceAndPath(Minehop.MOD_ID, "open_map_creator_screen");
    public static final Type<OpenMapCreatorScreenPayload> ID = new Type<>(HANDSHAKE_ID);
    public static final StreamCodec<FriendlyByteBuf, OpenMapCreatorScreenPayload> CODEC = StreamCodec.ofMember(
            (value, buf) -> {
                buf.writeUtf(value.mapName == null ? "" : value.mapName, MAX_MAP_NAME_LENGTH);
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
                    buf.readUtf(MAX_MAP_NAME_LENGTH),
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
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
