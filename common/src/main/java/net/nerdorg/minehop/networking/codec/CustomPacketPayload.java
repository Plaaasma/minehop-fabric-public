package net.nerdorg.minehop.networking.codec;

import net.minecraft.resources.ResourceLocation;

/**
 * 1.20.1 backport shim of the 1.20.2+ {@code net.minecraft.network.protocol.common.custom.CustomPacketPayload}: a typed
 * Minehop payload. Minecraft 1.20.1's custom payload packets only carry an id and raw bytes, so on the wire every payload
 * is still exactly {@code ClientboundCustomPayloadPacket(type().id(), <bytes written by its codec>)} (and the serverbound
 * equivalent), byte-identical to the 1.20.5+ versions of Minehop. The platform network helpers turn payload objects into
 * those packets (see docs/MULTILOADER.md).
 */
public interface CustomPacketPayload {
    Type<? extends CustomPacketPayload> type();

    /**
     * The payload's channel id, {@code minehop:<name>}.
     */
    record Type<T extends CustomPacketPayload>(ResourceLocation id) {
    }
}
