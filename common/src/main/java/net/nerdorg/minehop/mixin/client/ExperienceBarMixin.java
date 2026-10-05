package net.nerdorg.minehop.mixin.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.contextualbar.ExperienceBarRenderer;
import net.nerdorg.minehop.config.ConfigWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 1.21.6+ moved the experience bar out of InGameHud#renderExperienceBar into its own Bar class.
 * Same behaviour as the old InGameHudMixin hook: "hide self" hides the experience bar.
 */
@Mixin(ExperienceBarRenderer.class)
public abstract class ExperienceBarMixin {
    @Inject(method = "renderBackground", at = @At("HEAD"), cancellable = true)
    private void minehop$hideExperienceBar(GuiGraphics context, DeltaTracker tickCounter, CallbackInfo ci) {
        if (ConfigWrapper.config.hideSelf) {
            ci.cancel();
        }
    }
}
