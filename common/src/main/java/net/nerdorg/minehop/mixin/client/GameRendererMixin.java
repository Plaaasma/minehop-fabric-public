package net.nerdorg.minehop.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.MinehopClient;
import net.nerdorg.minehop.config.ConfigWrapper;
import net.nerdorg.minehop.item.ModItems;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
    @Shadow @Final private Minecraft minecraft;

    /** Client replay playback: put its camera at the interpolated view of this frame before the level renders. */
    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void minehop$placeReplayCamera(net.minecraft.client.DeltaTracker deltaTracker, CallbackInfo ci) {
        net.nerdorg.minehop.client.replay.ReplayPlayback.onRenderFrame();
    }

    @Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
    private void onRenderHand(CallbackInfo ci) {
        if (net.nerdorg.minehop.client.ClientVisibility.hideSelf() && !this.minecraft.player.isHolding(ModItems.INSTAGIB_GUN.get())) {
            ci.cancel();
        }
    }
}