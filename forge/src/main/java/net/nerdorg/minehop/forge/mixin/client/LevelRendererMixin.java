package net.nerdorg.minehop.forge.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.nerdorg.minehop.forge.platform.ForgeClientHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Forge 61 (1.21.11) has no {@code RenderLevelStageEvent}. Fires Minehop's END world-render hook once per rendered frame,
 * at the return of {@code renderLevel} (the frame graph, including the main pass where Fabric fires
 * {@code WorldRenderEvents.END_MAIN}, has executed by then). Minehop's END listener only measures frame times and updates
 * the run timer zones; it draws nothing. AFTER_ENTITIES is fired by {@link OutlineBufferSourceMixin}.
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    @Inject(method = "renderLevel", at = @At("RETURN"))
    private void minehop$end(CallbackInfo ci) {
        ForgeClientHelper.fireEnd(Minecraft.getInstance().gameRenderer.getMainCamera());
    }
}
