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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SoundEngine.class)
public class SoundSystemMixin {
    @Inject(method = "play(Lnet/minecraft/client/resources/sounds/SoundInstance;)V", at = @At("HEAD"), cancellable = true)
    public void onPlaySound(SoundInstance soundInstance, CallbackInfo ci) {
        if (Minecraft.getInstance().player != null) {
            DataManager.MapData currentMap = ZoneUtil.getCurrentMap(Minecraft.getInstance().player);
            if (currentMap != null && currentMap.hns) {
                if (soundInstance.getLocation().equals(SoundEvents.PLAYER_ATTACK_NODAMAGE.getLocation())) {
                    ci.cancel();
                }
            }
        }
    }
}
