package net.nerdorg.minehop.neoforge.platform;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.nerdorg.minehop.platform.services.IPlatformHelper;

import java.nio.file.Path;

/**
 * PHASE 3 TODO: NeoForge implementation of {@link IPlatformHelper}.
 */
public class NeoForgePlatformHelper implements IPlatformHelper {

    @Override
    public String getPlatformName() {
        return "NeoForge";
    }

    @Override
    public boolean isModLoaded(String modId) {
        // TODO(phase 3): net.neoforged.fml.ModList.get().isLoaded(modId)
        throw Todo.notImplemented("IPlatformHelper.isModLoaded");
    }

    @Override
    public boolean isDevelopmentEnvironment() {
        // TODO(phase 3): !net.neoforged.fml.loading.FMLLoader.isProduction()
        throw Todo.notImplemented("IPlatformHelper.isDevelopmentEnvironment");
    }

    @Override
    public boolean isPhysicalClient() {
        // TODO(phase 3): net.neoforged.fml.loading.FMLEnvironment.dist == Dist.CLIENT
        throw Todo.notImplemented("IPlatformHelper.isPhysicalClient");
    }

    @Override
    public Path getGameDirectory() {
        // TODO(phase 3): net.neoforged.fml.loading.FMLPaths.GAMEDIR.get()
        throw Todo.notImplemented("IPlatformHelper.getGameDirectory");
    }

    @Override
    public Path getConfigDirectory() {
        // TODO(phase 3): net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get()
        throw Todo.notImplemented("IPlatformHelper.getConfigDirectory");
    }

    @Override
    public ServerPlayer createFakePlayer(ServerLevel level, GameProfile profile) {
        // TODO(phase 3): net.neoforged.neoforge.common.util.FakePlayerFactory.get(level, profile)
        //  (re-run the movement harness on NeoForge: its output must match the Fabric baseline)
        throw Todo.notImplemented("IPlatformHelper.createFakePlayer");
    }

    @Override
    public boolean isFakePlayer(Player player) {
        // TODO(phase 3): player instanceof net.neoforged.neoforge.common.util.FakePlayer
        //  (or player.isFakePlayer(), the NeoForge IPlayerExtension method)
        throw Todo.notImplemented("IPlatformHelper.isFakePlayer");
    }
}
