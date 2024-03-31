package net.nerdorg.minehop.event;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

import javax.swing.text.JTextComponent;

public class KeyInputHandler {
    public static final String KEY_CATEGORY_MINEHOP = "key.category.minehop";
    public static final String KEY_RESTART = "key.minehop.restart";

    public static KeyBinding restartKey;

    public static void registerKeyInputs() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (restartKey.wasPressed()) {
                client.getNetworkHandler().sendCommand("map restart");
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

        registerKeyInputs();
    }
}
