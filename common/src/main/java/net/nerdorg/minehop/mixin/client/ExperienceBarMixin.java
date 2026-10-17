package net.nerdorg.minehop.mixin.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.contextualbar.ExperienceBar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 1.21.6+ moved the experience bar out of InGameHud#renderExperienceBar into its own Bar class (26.2: ExperienceBar).
 * Same behaviour as the old InGameHudMixin hook: "hide self" hides the experience bar.
 */
@Mixin(ExperienceBar.class)
public abstract class ExperienceBarMixin {
    @Inject(method = "extractBackground", at = @At("HEAD"), cancellable = true)
    private void minehop$hideExperienceBar(GuiGraphicsExtractor context, DeltaTracker tickCounter, CallbackInfo ci) {
        if (net.nerdorg.minehop.client.ClientVisibility.hideSelf()) {
            ci.cancel();
        }
    }
}
