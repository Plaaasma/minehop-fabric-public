package net.nerdorg.minehop.platform.services;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.nio.file.Path;

/**
 * Loader and environment information, plus fake players.
 */
public interface IPlatformHelper {

    /**
     * @return the name of the running mod loader ("Fabric", "NeoForge" or "Forge").
     */
    String getPlatformName();

    /**
     * @return whether a mod with this id is loaded.
     */
    boolean isModLoaded(String modId);

    /**
     * @return whether the game runs in a development environment (gradle run task / IDE).
     */
    boolean isDevelopmentEnvironment();

    /**
     * @return whether this is the physical client (true also while hosting an integrated server).
     */
    boolean isPhysicalClient();

    /**
     * @return the game (run) directory.
     */
    Path getGameDirectory();

    /**
     * @return the loader's config directory ({@code <game dir>/config} on every loader).
     */
    Path getConfigDirectory();

    default String getEnvironmentName() {
        return isDevelopmentEnvironment() ? "development" : "production";
    }

    // ---------------------------------------------------------------------------------------------
    // Fake players (server-driven ServerPlayer without a client: movement test harness, /test command)
    // Fabric: net.fabricmc.fabric.api.entity.FakePlayer; NeoForge: net.neoforged.neoforge.common.util.FakePlayer(Factory);
    // Forge: net.minecraftforge.common.util.FakePlayer(Factory).
    // ---------------------------------------------------------------------------------------------

    /**
     * Returns the loader's fake player for this level and profile (cached per level + profile by every loader).
     */
    ServerPlayer createFakePlayer(ServerLevel level, GameProfile profile);

    /**
     * @return whether {@code player} is a loader fake player (as created by {@link #createFakePlayer}, or by another mod).
     */
    boolean isFakePlayer(Player player);
}
