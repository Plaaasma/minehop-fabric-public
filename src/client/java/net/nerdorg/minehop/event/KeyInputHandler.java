package net.nerdorg.minehop.event;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.nerdorg.minehop.client.HudEditorScreen;
import org.lwjgl.glfw.GLFW;

public class KeyInputHandler {
    public static final String KEY_CATEGORY_MINEHOP = "key.category.minehop";
    public static final String KEY_RESTART = "key.minehop.restart";
    public static final String KEY_HUD_EDITOR = "key.minehop.hud_editor";

    public static KeyBinding restartKey;
    public static KeyBinding hudEditorKey;

    public static void registerKeyInputs() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (restartKey.wasPressed() && client.getNetworkHandler() != null) {
                client.getNetworkHandler().sendCommand("map restart");
            }
            while (hudEditorKey.wasPressed()) {
                if (client.currentScreen == null) {
                    client.setScreen(new HudEditorScreen());
                }
            }
        });
    }

    public static void register() {
        restartKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
           KEY_RESTART,
           InputUtil.Type.KEYSYM,
           GLFW.GLFW_KEY_R,
           KEY_CATEGORY_MINEHOP
        ));

        hudEditorKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
           KEY_HUD_EDITOR,
           InputUtil.Type.KEYSYM,
           GLFW.GLFW_KEY_RIGHT_BRACKET,
           KEY_CATEGORY_MINEHOP
        ));

        registerKeyInputs();
    }
}
