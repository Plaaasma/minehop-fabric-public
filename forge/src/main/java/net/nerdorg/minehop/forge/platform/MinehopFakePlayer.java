package net.nerdorg.minehop.forge.platform;

import com.google.common.collect.MapMaker;
import com.mojang.authlib.GameProfile;
import io.netty.channel.ChannelFutureListener;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.stats.Stat;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.scores.PlayerTeam;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;

/**
 * A server player without a client, for the movement test harness and {@code /test}. Forge (54 / 61) ships no
 * FakePlayer, so this mirrors Fabric API's {@code net.fabricmc.fabric.api.entity.FakePlayer} (what Minehop uses on
 * Fabric) member for member: default client information, a packet listener over a dummy connection that drops every
 * packet, no ticking, no stats, invulnerable, no team, no riding/sleeping/menus. Instances are cached per level +
 * profile (weak values), like {@code FakePlayer.get}.
 */
public class MinehopFakePlayer extends ServerPlayer {
    private record Key(ServerLevel level, GameProfile profile) {
    }

    private static final Map<Key, MinehopFakePlayer> FAKE_PLAYERS = new MapMaker().weakValues().makeMap();

    public static MinehopFakePlayer get(ServerLevel level, GameProfile profile) {
        Objects.requireNonNull(level, "Level may not be null.");
        Objects.requireNonNull(profile, "Game profile may not be null.");
        return FAKE_PLAYERS.computeIfAbsent(new Key(level, profile), key -> new MinehopFakePlayer(key.level(), key.profile()));
    }

    protected MinehopFakePlayer(ServerLevel level, GameProfile profile) {
        super(level.getServer(), level, profile, ClientInformation.createDefault());
        this.connection = new FakePacketListener(this);
    }

    @Override
    public void tick() {
    }

    @Override
    public void updateOptions(ClientInformation information) {
    }

    @Override
    public void awardStat(Stat<?> stat, int amount) {
    }

    @Override
    public void resetStat(Stat<?> stat) {
    }

    @Override
    public boolean isInvulnerableTo(ServerLevel level, DamageSource source) {
        return true;
    }

    @Nullable
    @Override
    public PlayerTeam getTeam() {
        // The team is looked up by the profile name by default, which a fake player must not inherit.
        return null;
    }

    @Override
    public void startSleeping(BlockPos pos) {
        // Do not lock a bed forever.
    }

    @Override
    public boolean startRiding(Entity entity, boolean force, boolean emitEvent) {
        return false;
    }

    @Override
    public void openTextEdit(SignBlockEntity sign, boolean front) {
    }

    @Override
    public OptionalInt openMenu(@Nullable MenuProvider menu) {
        return OptionalInt.empty();
    }

    @Override
    public void openHorseInventory(AbstractHorse horse, Container inventory) {
    }

    /**
     * Play packet listener of a fake player: drops every packet (Fabric API's {@code FakePlayerNetworkHandler}).
     */
    private static final class FakePacketListener extends ServerGamePacketListenerImpl {
        // Fully qualified: inside a ServerPlayer subclass the simple name Connection is WaypointTransmitter.Connection.
        private static final net.minecraft.network.Connection FAKE_CONNECTION = new FakeConnection();

        private FakePacketListener(ServerPlayer player) {
            super(player.level().getServer(), FAKE_CONNECTION, player, CommonListenerCookie.createInitial(player.getGameProfile(), false));
        }

        @Override
        public void send(Packet<?> packet, @Nullable ChannelFutureListener listener) {
        }
    }

    /** 1.21.11: {@code Connection} is abstract (Fabric API's {@code FakeClientConnection} does the same). */
    private static final class FakeConnection extends net.minecraft.network.Connection {
        private FakeConnection() {
            super(PacketFlow.CLIENTBOUND);
        }
    }
}
