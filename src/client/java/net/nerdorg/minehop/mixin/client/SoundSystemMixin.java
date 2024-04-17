package net.nerdorg.minehop.mixin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundSystem;
import net.minecraft.sound.SoundEvents;
import net.nerdorg.minehop.data.DataManager;
import net.nerdorg.minehop.util.ZoneUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SoundSystem.class)
public class SoundSystemMixin {
    @Inject(method = "play(Lnet/minecraft/client/sound/SoundInstance;)V", at = @At("HEAD"), cancellable = true)
    public void onPlaySound(SoundInstance soundInstance, CallbackInfo ci) {
        if (MinecraftClient.getInstance().player != null) {
            DataManager.MapData currentMap = ZoneUtil.getCurrentMap(MinecraftClient.getInstance().player);
            if (currentMap != null && currentMap.hns) {
                if (soundInstance.getId().equals(SoundEvents.ENTITY_PLAYER_ATTACK_NODAMAGE.getId())) {
                    ci.cancel();
                }
            }
        }
    }
}
