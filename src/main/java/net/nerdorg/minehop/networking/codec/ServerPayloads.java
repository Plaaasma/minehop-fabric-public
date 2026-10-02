package net.nerdorg.minehop.networking.codec;

import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * 1.20.1 backport shim for the 1.20.5+ {@code ServerPlayNetworking.PlayPayloadHandler} /
 * {@code Context} API, so the receivers keep their {@code (payload, ctx) -> ctx.player()/ctx.server()}
 * shape. Like the 1.21.4 payload handlers, Fabric runs {@code FabricPacket} handlers on the server
 * thread.
 */
public final class ServerPayloads {
    private ServerPayloads() {
    }

    public record Context(ServerPlayerEntity player, MinecraftServer server) {
    }

    @FunctionalInterface
    public interface Handler<T extends FabricPacket> {
        void receive(T payload, Context context);
    }

    public static <T extends FabricPacket> boolean registerGlobalReceiver(PacketType<T> type, Handler<T> handler) {
        return ServerPlayNetworking.registerGlobalReceiver(type,
                (packet, player, responseSender) -> handler.receive(packet, new Context(player, player.getServer())));
    }
}
