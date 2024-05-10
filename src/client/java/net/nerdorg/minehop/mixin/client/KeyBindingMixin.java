package net.nerdorg.minehop.mixin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.InputUtil;
import net.minecraft.server.world.ServerWorld;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.MinehopClient;
import net.nerdorg.minehop.config.ConfigWrapper;
import net.nerdorg.minehop.config.MinehopConfig;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.List;

@Mixin(KeyBinding.class)
public abstract class KeyBindingMixin {
    @Shadow @Final private InputUtil.Key boundKey;

    @Shadow private boolean pressed;
    private static KeyBinding leftKey;
    private static KeyBinding rightKey;
    private static KeyBinding sneakKey;

    private static boolean isLeftKeyPressed = false;
    private static boolean isRightKeyPressed = false;

    private static final int NONE = 0;
    private static final int LEFT = 1;
    private static final int RIGHT = 2;
    private static int lastKeyPressed = NONE;

    @Inject(method = "<init>*", at = @At("RETURN"))
    private void onInit(String translationKey, int code, String category, CallbackInfo ci) {
        if (translationKey.equals("key.left")) {
            leftKey = (KeyBinding)(Object)this;
        } else if (translationKey.equals("key.right")) {
            rightKey = (KeyBinding)(Object)this;
        } else if (translationKey.equals("key.sneak")) {
            sneakKey = (KeyBinding)(Object)this;
        }
    }

    @Inject(method = "setPressed", at = @At("HEAD"))
    private void onSetPressed(boolean value, CallbackInfo ci) {
        if ((this.boundKey.getTranslationKey().equals(leftKey.getBoundKeyTranslationKey()))) {
            if (value) {
                lastKeyPressed = LEFT;
                isLeftKeyPressed = true;
            }
            else {
                lastKeyPressed = RIGHT;
                isLeftKeyPressed = false;
            }
        } else if (this.boundKey.getTranslationKey().equals(rightKey.getBoundKeyTranslationKey())) {
            if (value) {
                lastKeyPressed = RIGHT;
                isRightKeyPressed = true;
            }
            else {
                lastKeyPressed = LEFT;
                isRightKeyPressed = false;
            }
        }
    }

    @Inject(method = "isPressed", at = @At("HEAD"), cancellable = true)
    private void isPressed(CallbackInfoReturnable<Boolean> cir) {
        MinehopConfig config;
        if (Minehop.override_config) {
            config = new MinehopConfig();
            config.movement.sv_friction = Minehop.o_sv_friction;
            config.movement.sv_accelerate = Minehop.o_sv_accelerate;
            config.movement.sv_airaccelerate = Minehop.o_sv_airaccelerate;
            config.movement.sv_maxairspeed = Minehop.o_sv_maxairspeed;
            config.movement.speed_mul = Minehop.o_speed_mul;
            config.movement.sv_gravity = Minehop.o_sv_gravity;
            config.nulls = ConfigWrapper.config.nulls;
            config.jHud.ssjHud = ConfigWrapper.config.jHud.ssjHud;
            config.jHud.efficiencyHud = ConfigWrapper.config.jHud.efficiencyHud;
            config.jHud.speedHud = ConfigWrapper.config.jHud.speedHud;
            config.jHud.prespeedHud = ConfigWrapper.config.jHud.prespeedHud;
            config.jHud.gaugeHud = ConfigWrapper.config.jHud.gaugeHud;
        }
        else {
            config = ConfigWrapper.config;
        }

        if (sneakKey != null && this.boundKey.getTranslationKey().equals(sneakKey.getBoundKeyTranslationKey())) {
            if (MinecraftClient.getInstance().player != null) {
                if (MinecraftClient.getInstance().player.isSpectator()) {
                    cir.setReturnValue(false);
                    return;
                }
            }
        }

        if (config.nulls) {
            if (this.boundKey.getTranslationKey().equals(leftKey.getBoundKeyTranslationKey())) {
                if (lastKeyPressed == LEFT && isLeftKeyPressed) {
                    cir.setReturnValue(true);
                } else {
                    cir.setReturnValue(false);
                }
            } else if (this.boundKey.getTranslationKey().equals(rightKey.getBoundKeyTranslationKey())) {
                if (lastKeyPressed == RIGHT && isRightKeyPressed) {
                    cir.setReturnValue(true);
                } else {
                    cir.setReturnValue(false);
                }
            }
        }
    }
}