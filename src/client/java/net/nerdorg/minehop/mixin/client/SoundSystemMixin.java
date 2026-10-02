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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SoundSystem.class)
public class SoundSystemMixin {
    // 1.21.11: play(SoundInstance) returns a PlayResult; a suppressed sound reports NOT_STARTED.
    @Inject(method = "play(Lnet/minecraft/client/sound/SoundInstance;)Lnet/minecraft/client/sound/SoundSystem$PlayResult;", at = @At("HEAD"), cancellable = true)
    public void onPlaySound(SoundInstance soundInstance, CallbackInfoReturnable<SoundSystem.PlayResult> ci) {
        if (MinecraftClient.getInstance().player != null) {
            DataManager.MapData currentMap = ZoneUtil.getCurrentMap(MinecraftClient.getInstance().player);
            if (currentMap != null && currentMap.hns) {
                if (soundInstance.getId().equals(SoundEvents.ENTITY_PLAYER_ATTACK_NODAMAGE.id())) {
                    ci.setReturnValue(SoundSystem.PlayResult.NOT_STARTED);
                }
            }
        }
    }
}
