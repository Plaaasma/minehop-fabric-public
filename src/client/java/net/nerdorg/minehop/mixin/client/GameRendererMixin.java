package net.nerdorg.minehop.mixin.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.GameRenderer;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.MinehopClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
    @Inject(method = "renderHand", at = @At("HEAD"), cancellable = true)
    private void onRenderHand(CallbackInfo ci) {
        if (MinehopClient.hideSelf) {
            ci.cancel();
        }
    }
}