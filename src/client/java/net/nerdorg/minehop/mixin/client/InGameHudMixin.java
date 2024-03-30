package net.nerdorg.minehop.mixin.client;

import net.nerdorg.minehop.MinehopClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.gui.DrawContext;

@Mixin(InGameHud.class)
public class InGameHudMixin {
    @Inject(at = @At("TAIL"), method = "render(Lnet/minecraft/client/gui/DrawContext;F)V")
    private void renderSqueedometerHud(DrawContext context, float tickDelta, CallbackInfo info) {
        MinehopClient.squeedometerHud.drawMain(context, tickDelta);
        MinehopClient.squeedometerHud.drawSSJ(context, tickDelta);
    }
}