package net.nerdorg.minehop.forge.platform;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.nerdorg.minehop.platform.services.IPlatformHelper;

import java.nio.file.Path;

/**
 * PHASE 3 TODO: Forge implementation of {@link IPlatformHelper}.
 */
public class ForgePlatformHelper implements IPlatformHelper {

    @Override
    public String getPlatformName() {
        return "Forge";
    }

    @Override
    public boolean isModLoaded(String modId) {
        // TODO(phase 3): net.minecraftforge.fml.ModList.get().isLoaded(modId)
        throw Todo.notImplemented("IPlatformHelper.isModLoaded");
    }

    @Override
    public boolean isDevelopmentEnvironment() {
        // TODO(phase 3): !net.minecraftforge.fml.loading.FMLLoader.isProduction()
        throw Todo.notImplemented("IPlatformHelper.isDevelopmentEnvironment");
    }

    @Override
    public boolean isPhysicalClient() {
        // TODO(phase 3): net.minecraftforge.fml.loading.FMLEnvironment.dist == Dist.CLIENT
        throw Todo.notImplemented("IPlatformHelper.isPhysicalClient");
    }

    @Override
    public Path getGameDirectory() {
        // TODO(phase 3): net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get()
        throw Todo.notImplemented("IPlatformHelper.getGameDirectory");
    }

    @Override
    public Path getConfigDirectory() {
        // TODO(phase 3): net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get()
        throw Todo.notImplemented("IPlatformHelper.getConfigDirectory");
    }

    @Override
    public ServerPlayer createFakePlayer(ServerLevel level, GameProfile profile) {
        // TODO(phase 3): Forge 54 (1.21.4) ships NO FakePlayer/FakePlayerFactory. Add a forge-module class
        //  `MinehopFakePlayer extends ServerPlayer` modelled on Fabric's FakePlayer (ClientInformation.createDefault(),
        //  a no-op ServerGamePacketListenerImpl over an embedded Connection, no stats/advancements, isSpectator()=false),
        //  cached per (level, profile) like Fabric's FakePlayer.get. The movement harness output must match the baseline.
        throw Todo.notImplemented("IPlatformHelper.createFakePlayer");
    }

    @Override
    public boolean isFakePlayer(Player player) {
        // TODO(phase 3): player instanceof MinehopFakePlayer (see createFakePlayer)
        throw Todo.notImplemented("IPlatformHelper.isFakePlayer");
    }
}
