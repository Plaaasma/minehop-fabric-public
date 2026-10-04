package net.nerdorg.minehop.forge.platform;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.nerdorg.minehop.platform.services.INetworkHelper;

/**
 * PHASE 3 TODO: Forge implementation of {@link INetworkHelper}. Must stay wire-compatible with the Fabric server
 * (see docs/MULTILOADER.md, "Networking").
 */
public class ForgeNetworkHelper implements INetworkHelper {

    @Override
    public <T extends CustomPacketPayload> void registerPayloadS2C(CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
        // TODO(phase 3): record (type, codec, direction). After the common init, build ONE
        //  net.minecraftforge.network.ChannelBuilder.named(minehop:network).optional().payloadChannel().play()
        //  with .clientbound()/.serverbound()/.bidirectional() .add(type, codec, (payload, ctx) -> dispatch) for every
        //  recorded type. PayloadChannel sends each payload as a vanilla custom payload under its OWN id (type.id())
        //  with exactly the codec bytes (no discriminator), i.e. the Fabric wire format. Do NOT use SimpleChannel
        //  (it prefixes a discriminator varint). The codecs are StreamCodec<FriendlyByteBuf, T>: adapt them to the
        //  channel's RegistryFriendlyByteBuf type. Forge calls handlers on the netty thread: dispatch with
        //  ctx.enqueueWork(...) + ctx.setPacketHandled(true) (Fabric runs handlers on the main thread).
        throw Todo.notImplemented("INetworkHelper.registerPayloadS2C");
    }

    @Override
    public <T extends CustomPacketPayload> void registerPayloadC2S(CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
        // TODO(phase 3): see registerPayloadS2C.
        throw Todo.notImplemented("INetworkHelper.registerPayloadC2S");
    }

    @Override
    public <T extends CustomPacketPayload> void registerServerReceiver(CustomPacketPayload.Type<T> type, ServerPayloadHandler<T> handler) {
        // TODO(phase 3): putIfAbsent into the server handler map read by the channel's dispatcher (first wins).
        //  Context: player() = ctx.getSender(), server() = player.server.
        throw Todo.notImplemented("INetworkHelper.registerServerReceiver");
    }

    @Override
    public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        // TODO(phase 3): channel.send(payload, net.minecraftforge.network.PacketDistributor.PLAYER.with(player))
        throw Todo.notImplemented("INetworkHelper.sendToPlayer");
    }

    @Override
    public void onPlayConnectionInit(PlayConnectionListener listener) {
        // TODO(phase 3): fire from PlayerEvent.PlayerLoggedInEvent at EventPriority.HIGHEST (before the join listeners)
        throw Todo.notImplemented("INetworkHelper.onPlayConnectionInit");
    }

    @Override
    public void onPlayConnectionJoin(PlayConnectionListener listener) {
        // TODO(phase 3): MinecraftForge.EVENT_BUS PlayerEvent.PlayerLoggedInEvent -> (serverPlayer.connection, serverPlayer.server)
        throw Todo.notImplemented("INetworkHelper.onPlayConnectionJoin");
    }

    @Override
    public void onPlayConnectionDisconnect(PlayConnectionListener listener) {
        // TODO(phase 3): PlayerEvent.PlayerLoggedOutEvent -> (serverPlayer.connection, serverPlayer.server)
        throw Todo.notImplemented("INetworkHelper.onPlayConnectionDisconnect");
    }
}
