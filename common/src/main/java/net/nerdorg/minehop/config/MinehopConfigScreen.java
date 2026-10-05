package net.nerdorg.minehop.config;

import me.shedaniel.autoconfig.AutoConfigClient;
import net.minecraft.client.gui.screens.Screen;

/**
 * Config screen hook shared by all loaders (client only). The config itself is cloth-config AutoConfig
 * ({@code config/minehop.json5}), registered in {@code Minehop#onInitialize}.
 * <ul>
 *     <li>Fabric: ModMenu entrypoint ({@code ModMenuIntegration}, fabric module only).</li>
 *     <li>NeoForge: {@code ModContainer#registerExtensionPoint(IConfigScreenFactory.class, (container, parent) -> create(parent))}.</li>
 *     <li>Forge: {@code ModLoadingContext#registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class, ...)}.</li>
 * </ul>
 */
public final class MinehopConfigScreen {
    private MinehopConfigScreen() {
    }

    public static Screen create(Screen parent) {
        return AutoConfigClient.getConfigScreen(MinehopConfig.class, parent).get();
    }
}
