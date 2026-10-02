package net.nerdorg.minehop.mixin.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.AttackIndicator;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Identifier;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.MinehopClient;
import net.nerdorg.minehop.config.ConfigWrapper;
import net.nerdorg.minehop.config.MinehopConfig;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.gui.DrawContext;

@Mixin(InGameHud.class)
public abstract class InGameHudMixin {
    @Shadow @Nullable protected abstract PlayerEntity getCameraPlayer();

    @Shadow @Final private MinecraftClient client;

    // 1.20.1: the hotbar is drawn from the widgets/icons atlases (GUI sprites came in 1.20.2), and
    // vanilla's renderHotbarItem is used directly (the 1.21.4 mixin re-implemented the vanilla one).
    @Shadow @Final private static Identifier WIDGETS_TEXTURE;

    @Shadow @Final private static Identifier ICONS;

    @Shadow protected abstract void renderHotbarItem(DrawContext context, int x, int y, float tickDelta, PlayerEntity player, ItemStack stack, int seed);

    @Inject(at = @At("TAIL"), method = "render(Lnet/minecraft/client/gui/DrawContext;F)V")
    private void renderSqueedometerHud(DrawContext context, float tickDelta, CallbackInfo info) {
        // The HUD editor draws its own WYSIWYG previews; don't double-render the live HUD behind it.
        if (this.client.currentScreen instanceof net.nerdorg.minehop.client.HudEditorScreen) {
            return;
        }
        MinehopConfig config;
        if (Minehop.override_config) {
            config = new MinehopConfig();
            config.enabled = Minehop.o_enabled;
            config.fall_damage = Minehop.o_fall_damage;
            config.movement.sv_friction = Minehop.o_sv_friction;
            config.movement.sv_accelerate = Minehop.o_sv_accelerate;
            config.movement.sv_airaccelerate = Minehop.o_sv_airaccelerate;
            config.movement.sv_maxairspeed = Minehop.o_sv_maxairspeed;
            config.movement.speed_mul = Minehop.o_speed_mul;
            config.movement.sv_gravity = Minehop.o_sv_gravity;
            config.movement.sv_stopspeed = Minehop.o_sv_stopspeed;
            config.movement.speed_coefficient = Minehop.o_speed_coefficient;
            config.movement.auto_step_up = Minehop.o_auto_step_up;
            config.movement.css_crouch_jump = Minehop.o_css_crouch_jump;
            config.nulls = ConfigWrapper.config.nulls;
            config.jHud.ssjHud = ConfigWrapper.config.jHud.ssjHud;
            config.jHud.efficiencyHud = ConfigWrapper.config.jHud.efficiencyHud;
            config.jHud.speedHud = ConfigWrapper.config.jHud.speedHud;
            config.jHud.prespeedHud = ConfigWrapper.config.jHud.prespeedHud;
            config.jHud.gaugeHud = ConfigWrapper.config.jHud.gaugeHud;
            config.jHud.timerHud = ConfigWrapper.config.jHud.timerHud;
        }
        else {
            config = ConfigWrapper.config;
        }

        if (config.jHud.speedHud.show_current_speed && config.enabled) {
            MinehopClient.squeedometerHud.drawMain(context, tickDelta, config);
        }
        if (config.enabled) {
            MinehopClient.squeedometerHud.drawJHUD(context, config);
            if (!MinehopClient.spectatorList.isEmpty()) {
                MinehopClient.squeedometerHud.drawSpectators(context, tickDelta);
            }
        }
    }

    @Inject(at = @At("HEAD"), method = "renderHealthBar", cancellable = true)
    private void renderHealth(DrawContext context, PlayerEntity player, int x, int y, int lines, int regeneratingHeartIndex, float maxHealth, int lastHealth, int health, int absorption, boolean blinking, CallbackInfo ci) {
        if (ConfigWrapper.config.hideSelf) {
            ci.cancel();
        }
    }

    @Inject(at = @At("HEAD"), method = "renderStatusBars", cancellable = true)
    private void renderStatusBars(DrawContext context, CallbackInfo ci) {
        if (ConfigWrapper.config.hideSelf) {
            ci.cancel();
        }
    }

    @Inject(at = @At("HEAD"), method = "renderExperienceBar", cancellable = true)
    private void renderExperienceBar(DrawContext context, int x, CallbackInfo ci) {
        if (ConfigWrapper.config.hideSelf) {
            ci.cancel();
        }
    }



    @Inject(at = @At("HEAD"), method = "renderHotbar", cancellable = true)
    private void renderHotbar(float tickDelta, DrawContext context, CallbackInfo ci) {
        if (!ConfigWrapper.config.hideSelf) {
            PlayerEntity playerEntity = this.getCameraPlayer();
            if (playerEntity != null) {
                ItemStack itemStack = playerEntity.getOffHandStack();
                Arm arm = playerEntity.getMainArm().getOpposite();
                int i = context.getScaledWindowWidth() / 2;
                context.getMatrices().push();
                context.getMatrices().translate(0.0F, 0.0F, -90.0F);
                context.drawTexture(WIDGETS_TEXTURE, i - 91, context.getScaledWindowHeight() - 22, 0, 0, 182, 22);
                context.drawTexture(WIDGETS_TEXTURE, i - 91 - 1 + playerEntity.getInventory().selectedSlot * 20, context.getScaledWindowHeight() - 22 - 1, 0, 22, 24, 22);
                if (!itemStack.isEmpty()) {
                    if (arm == Arm.LEFT) {
                        context.drawTexture(WIDGETS_TEXTURE, i - 91 - 29, context.getScaledWindowHeight() - 23, 24, 22, 29, 24);
                    } else {
                        context.drawTexture(WIDGETS_TEXTURE, i + 91, context.getScaledWindowHeight() - 23, 53, 22, 29, 24);
                    }
                }

                context.getMatrices().pop();
                int l = 1;

                int m;
                int n;
                int o;
                for(m = 0; m < 9; ++m) {
                    n = i - 90 + m * 20 + 2;
                    o = context.getScaledWindowHeight() - 16 - 3;
                    this.renderHotbarItem(context, n, o, tickDelta, playerEntity, (ItemStack)playerEntity.getInventory().main.get(m), l++);
                }

                if (!itemStack.isEmpty()) {
                    m = context.getScaledWindowHeight() - 16 - 3;
                    if (arm == Arm.LEFT) {
                        this.renderHotbarItem(context, i - 91 - 26, m, tickDelta, playerEntity, itemStack, l++);
                    } else {
                        this.renderHotbarItem(context, i + 91 + 10, m, tickDelta, playerEntity, itemStack, l++);
                    }
                }

                RenderSystem.enableBlend();
                if (this.client.options.getAttackIndicator().getValue() == AttackIndicator.HOTBAR) {
                    float f = this.client.player.getAttackCooldownProgress(0.0F);
                    if (f < 1.0F) {
                        n = context.getScaledWindowHeight() - 20;
                        o = i + 91 + 6;
                        if (arm == Arm.RIGHT) {
                            o = i - 91 - 22;
                        }

                        int p = (int)(f * 19.0F);
                        context.drawTexture(ICONS, o, n, 0, 94, 18, 18);
                        context.drawTexture(ICONS, o, n + 18 - p, 18, 112 - p, 18, p);
                    }
                }

                RenderSystem.disableBlend();
            }
        }
        ci.cancel();
    }
}
