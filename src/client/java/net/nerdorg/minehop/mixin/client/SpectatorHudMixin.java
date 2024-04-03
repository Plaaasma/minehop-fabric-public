package net.nerdorg.minehop.mixin.client;

import net.minecraft.client.gui.hud.SpectatorHud;
import net.minecraft.client.render.GameRenderer;
import net.nerdorg.minehop.MinehopClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SpectatorHud.class)
public class SpectatorHudMixin {
    @Inject(method = "selectSlot", at = @At("HEAD"), cancellable = true)
    private void onSelectSlot(CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void onRender(CallbackInfo ci) {
        ci.cancel();
    }
}