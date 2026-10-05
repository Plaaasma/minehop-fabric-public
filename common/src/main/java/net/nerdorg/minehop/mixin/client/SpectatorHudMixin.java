package net.nerdorg.minehop.mixin.client;

import net.minecraft.client.gui.components.spectator.SpectatorGui;
import net.nerdorg.minehop.MinehopClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SpectatorGui.class)
public class SpectatorHudMixin {
    @Inject(method = "onHotbarSelected", at = @At("HEAD"), cancellable = true)
    private void onSelectSlot(CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(method = "renderAction", at = @At("HEAD"), cancellable = true)
    private void onRender(CallbackInfo ci) {
        ci.cancel();
    }
}