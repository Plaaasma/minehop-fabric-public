package net.nerdorg.minehop.event;

import com.mojang.blaze3d.platform.InputConstants;
import net.nerdorg.minehop.platform.ClientServices;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.client.HudEditorScreen;

public class KeyInputHandler {
    public static final String KEY_CATEGORY_MINEHOP = "key.category.minehop";
    // 1.21.9+: key categories are registered objects; label key = key.category.minehop.main (lang).
    public static final KeyMapping.Category MINEHOP_CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(Minehop.MOD_ID, "main"));
    public static final String KEY_RESTART = "key.minehop.restart";
    public static final String KEY_HUD_EDITOR = "key.minehop.hud_editor";

    public static KeyMapping restartKey;
    public static KeyMapping hudEditorKey;

    public static void registerKeyInputs() {
        ClientServices.CLIENT.onClientTickEnd(client -> {
            if (restartKey.consumeClick() && client.getConnection() != null) {
                client.getConnection().sendCommand("map restart");
            }
            while (hudEditorKey.consumeClick()) {
                if (client.gui.screen() == null) {
                    client.gui.setScreen(new HudEditorScreen());
                }
            }
        });
    }

    public static void register() {
        restartKey = ClientServices.CLIENT.registerKeyMapping(new KeyMapping(
           KEY_RESTART,
           InputConstants.Type.KEYBOARD,
           InputConstants.KEY_R,
           MINEHOP_CATEGORY
        ));

        hudEditorKey = ClientServices.CLIENT.registerKeyMapping(new KeyMapping(
           KEY_HUD_EDITOR,
           InputConstants.Type.KEYBOARD,
           InputConstants.KEY_RBRACKET,
           MINEHOP_CATEGORY
        ));

        registerKeyInputs();
    }
}
