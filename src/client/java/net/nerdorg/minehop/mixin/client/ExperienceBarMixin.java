package net.nerdorg.minehop.mixin.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.bar.ExperienceBar;
import net.minecraft.client.render.RenderTickCounter;
import net.nerdorg.minehop.config.ConfigWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 1.21.6+ moved the experience bar out of InGameHud#renderExperienceBar into its own Bar class.
 * Same behaviour as the old InGameHudMixin hook: "hide self" hides the experience bar.
 */
@Mixin(ExperienceBar.class)
public abstract class ExperienceBarMixin {
    @Inject(method = "renderBar", at = @At("HEAD"), cancellable = true)
    private void minehop$hideExperienceBar(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (ConfigWrapper.config.hideSelf) {
            ci.cancel();
        }
    }
}
