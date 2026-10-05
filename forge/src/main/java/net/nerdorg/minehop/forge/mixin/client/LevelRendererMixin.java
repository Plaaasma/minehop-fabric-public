package net.nerdorg.minehop.forge.mixin.client;

import net.minecraft.client.renderer.LevelRenderer;
import net.nerdorg.minehop.forge.platform.ForgeClientHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Forge 64 (26.1) has no {@code RenderLevelStageEvent}. Fires Minehop's world-render hooks where Fabric API fires its
 * {@code LevelRenderEvents}, inside the main frame pass ({@code LevelRenderer#addMainPass}'s lambda, matched by a regex
 * selector so a renumbered lambda still matches):
 * <ul>
 *     <li>AFTER_TRANSLUCENT_FEATURES: right after {@code FeatureRenderDispatcher#renderTranslucentFeatures()}, before the
 *     buffer source is flushed.</li>
 *     <li>END_MAIN: return of the main pass.</li>
 * </ul>
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    @Inject(method = "/^lambda\\$addMainPass\\$\\d+$/", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher;renderTranslucentFeatures()V", shift = At.Shift.AFTER))
    private void minehop$afterTranslucentFeatures(CallbackInfo ci) {
        ForgeClientHelper.fireAfterTranslucentFeatures();
    }

    @Inject(method = "/^lambda\\$addMainPass\\$\\d+$/", at = @At("RETURN"))
    private void minehop$endMain(CallbackInfo ci) {
        ForgeClientHelper.fireEndMain();
    }
}
