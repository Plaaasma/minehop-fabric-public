package net.nerdorg.minehop.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.sounds.SoundEvents;
import net.nerdorg.minehop.data.DataManager;
import net.nerdorg.minehop.util.ZoneUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SoundEngine.class)
public class SoundSystemMixin {
    // 1.21.11: play(SoundInstance) returns a PlayResult; a suppressed sound reports NOT_STARTED.
    @Inject(method = "play(Lnet/minecraft/client/resources/sounds/SoundInstance;)Lnet/minecraft/client/sounds/SoundEngine$PlayResult;", at = @At("HEAD"), cancellable = true)
    public void onPlaySound(SoundInstance soundInstance, CallbackInfoReturnable<SoundEngine.PlayResult> ci) {
        if (Minecraft.getInstance().player != null) {
            DataManager.MapData currentMap = ZoneUtil.getCurrentMap(Minecraft.getInstance().player);
            if (currentMap != null && currentMap.hns) {
                if (soundInstance.getIdentifier().equals(SoundEvents.PLAYER_ATTACK_NODAMAGE.location())) {
                    ci.setReturnValue(SoundEngine.PlayResult.NOT_STARTED);
                }
            }
        }
    }
}
