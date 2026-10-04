package net.nerdorg.minehop.forge.platform;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.fml.loading.FMLPaths;
import net.nerdorg.minehop.platform.services.IPlatformHelper;

import java.nio.file.Path;

/**
 * Forge implementation of {@link IPlatformHelper}.
 */
public class ForgePlatformHelper implements IPlatformHelper {

    @Override
    public String getPlatformName() {
        return "Forge";
    }

    @Override
    public boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    @Override
    public boolean isDevelopmentEnvironment() {
        return !FMLLoader.isProduction();
    }

    @Override
    public boolean isPhysicalClient() {
        return FMLEnvironment.dist == Dist.CLIENT;
    }

    @Override
    public Path getGameDirectory() {
        return FMLPaths.GAMEDIR.get();
    }

    @Override
    public Path getConfigDirectory() {
        return FMLPaths.CONFIGDIR.get();
    }

    /**
     * Forge 54 (1.21.4) ships no FakePlayer, so Minehop brings its own (modelled on Fabric API's), cached per
     * level + profile like Fabric's {@code FakePlayer.get}.
     */
    @Override
    public ServerPlayer createFakePlayer(ServerLevel level, GameProfile profile) {
        return MinehopFakePlayer.get(level, profile);
    }

    @Override
    public boolean isFakePlayer(Player player) {
        return player instanceof MinehopFakePlayer;
    }
}
