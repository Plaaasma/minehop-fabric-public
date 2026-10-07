package net.nerdorg.minehop.mixin.client;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
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
import com.mojang.blaze3d.platform.InputConstants;
import java.util.HashMap;
import java.util.List;

@Mixin(KeyMapping.class)
public abstract class KeyBindingMixin {
    @Shadow @Final private InputConstants.Key key;

    @Shadow private boolean isDown;
    private static KeyMapping leftKey;
    private static KeyMapping rightKey;
    private static KeyMapping sneakKey;

    private static boolean isLeftKeyPressed = false;
    private static boolean isRightKeyPressed = false;

    private static final int NONE = 0;
    private static final int LEFT = 1;
    private static final int RIGHT = 2;
    private static int lastKeyPressed = NONE;

    // 1.21.9+ constructors take a KeyBinding.Category instead of a String; the binding id is read back
    // from the constructed instance so one handler fits every constructor.
    @Inject(method = "<init>*", at = @At("RETURN"))
    private void onInit(CallbackInfo ci) {
        String translationKey = ((KeyMapping) (Object) this).getName();
        if (translationKey.equals("key.left")) {
            leftKey = (KeyMapping)(Object)this;
        } else if (translationKey.equals("key.right")) {
            rightKey = (KeyMapping)(Object)this;
        } else if (translationKey.equals("key.sneak")) {
            sneakKey = (KeyMapping)(Object)this;
        }
    }

    @Inject(method = "setDown", at = @At("HEAD"))
    private void onSetPressed(boolean value, CallbackInfo ci) {
        if ((this.key.getName().equals(leftKey.saveString()))) {
            if (value) {
                lastKeyPressed = LEFT;
                isLeftKeyPressed = true;
            }
            else {
                lastKeyPressed = RIGHT;
                isLeftKeyPressed = false;
            }
        } else if (this.key.getName().equals(rightKey.saveString())) {
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

    @Inject(method = "isDown", at = @At("HEAD"), cancellable = true)
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
            config.movement.sv_stopspeed = Minehop.o_sv_stopspeed;
            config.movement.auto_step_up = Minehop.o_auto_step_up;
            config.movement.css_crouch_jump = Minehop.o_css_crouch_jump;
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

        if (sneakKey != null && this.key.getName().equals(sneakKey.saveString())) {
            if (Minecraft.getInstance().player != null) {
                // While watching someone, sneak would ask the server to drop the camera (a server with spectate
                // sessions ends the session for it; an older one lets the camera go). Free spectator flight
                // (operators) keeps sneak to fly down.
                if (Minecraft.getInstance().player.isSpectator()
                        && Minecraft.getInstance().getCameraEntity() != Minecraft.getInstance().player) {
                    cir.setReturnValue(false);
                    return;
                }
            }
        }

        if (config.nulls) {
            if (this.key.getName().equals(leftKey.saveString())) {
                if (lastKeyPressed == LEFT && isLeftKeyPressed) {
                    cir.setReturnValue(true);
                } else {
                    cir.setReturnValue(false);
                }
            } else if (this.key.getName().equals(rightKey.saveString())) {
                if (lastKeyPressed == RIGHT && isRightKeyPressed) {
                    cir.setReturnValue(true);
                } else {
                    cir.setReturnValue(false);
                }
            }
        }
    }
}
