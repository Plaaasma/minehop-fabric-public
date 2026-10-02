package net.nerdorg.minehop.networking;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

/**
 * 1.20.1 backport shim for the 1.20.5+ {@code ClientPlayNetworking.PlayPayloadHandler} / {@code Context}
 * API, so the receivers keep their {@code (payload, ctx) -> ctx.client()} shape. Like the 1.21.4
 * payload handlers, Fabric runs {@code FabricPacket} handlers on the client thread.
 */
public final class ClientPayloads {
    private ClientPayloads() {
    }

    public record Context(MinecraftClient client, ClientPlayerEntity player) {
    }

    @FunctionalInterface
    public interface Handler<T extends FabricPacket> {
        void receive(T payload, Context context);
    }

    public static <T extends FabricPacket> boolean registerGlobalReceiver(PacketType<T> type, Handler<T> handler) {
        return ClientPlayNetworking.registerGlobalReceiver(type,
                (packet, player, responseSender) -> handler.receive(packet, new Context(MinecraftClient.getInstance(), player)));
    }
}
