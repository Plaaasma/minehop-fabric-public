package net.nerdorg.minehop.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.MinehopClient;
import net.nerdorg.minehop.config.ConfigWrapper;
import net.nerdorg.minehop.config.MinehopConfig;

/**
 * Minehop's HUD (speedometer, jump HUD, spectators, replay bar), drawn after the vanilla HUD: from {@code InGameHudMixin} at the
 * tail of {@code Gui#render} (Fabric), and from {@code RenderGuiEvent.Post} on Forge 1.20.1, whose {@code ForgeGui}
 * renders the HUD through overlays and never calls {@code Gui#render}.
 */
public final class MinehopHudOverlay {
    private MinehopHudOverlay() {
    }

    public static void render(GuiGraphics context, float tickDelta) {
        // The HUD editor draws its own WYSIWYG previews; don't double-render the live HUD behind it.
        if (Minecraft.getInstance().screen instanceof net.nerdorg.minehop.client.HudEditorScreen) {
            return;
        }
        MinehopConfig config;
        if (Minehop.override_config) {
            config = new MinehopConfig();
            config.enabled = Minehop.o_enabled;
            config.fall_damage = Minehop.o_fall_damage;
            config.movement.sv_friction = Minehop.o_sv_friction;
            config.movement.sv_accelerate = Minehop.o_sv_accelerate;
            config.movement.sv_airaccelerate = Minehop.o_sv_airaccelerate;
            config.movement.sv_maxairspeed = Minehop.o_sv_maxairspeed;
            config.movement.speed_mul = Minehop.o_speed_mul;
            config.movement.sv_gravity = Minehop.o_sv_gravity;
            config.movement.sv_stopspeed = Minehop.o_sv_stopspeed;
            config.movement.speed_coefficient = Minehop.o_speed_coefficient;
            config.movement.auto_step_up = Minehop.o_auto_step_up;
            config.movement.css_crouch_jump = Minehop.o_css_crouch_jump;
            config.nulls = ConfigWrapper.config.nulls;
            config.jHud.ssjHud = ConfigWrapper.config.jHud.ssjHud;
            config.jHud.efficiencyHud = ConfigWrapper.config.jHud.efficiencyHud;
            config.jHud.speedHud = ConfigWrapper.config.jHud.speedHud;
            config.jHud.prespeedHud = ConfigWrapper.config.jHud.prespeedHud;
            config.jHud.gaugeHud = ConfigWrapper.config.jHud.gaugeHud;
            config.jHud.timerHud = ConfigWrapper.config.jHud.timerHud;
        }
        else {
            config = ConfigWrapper.config;
        }

        if (config.jHud.speedHud.show_current_speed && config.enabled) {
            MinehopClient.squeedometerHud.drawMain(context, tickDelta, config);
        }
        if (config.enabled) {
            MinehopClient.squeedometerHud.drawJHUD(context, config);
            if (!MinehopClient.spectatorList.isEmpty()) {
                MinehopClient.squeedometerHud.drawSpectators(context, tickDelta);
            }
        }
        net.nerdorg.minehop.client.replay.ReplayHud.render(context);
    }
}
