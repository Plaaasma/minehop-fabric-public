package net.nerdorg.minehop.mixin.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.AttackIndicator;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Identifier;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.MinehopClient;
import net.nerdorg.minehop.config.ConfigWrapper;
import net.nerdorg.minehop.config.MinehopConfig;
import com.mojang.blaze3d.opengl.GlStateManager;
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

    @Shadow @Final private static Identifier HOTBAR_TEXTURE;

    @Shadow @Final private static Identifier HOTBAR_SELECTION_TEXTURE;

    @Shadow @Final private static Identifier HOTBAR_OFFHAND_LEFT_TEXTURE;

    @Shadow @Final private static Identifier HOTBAR_OFFHAND_RIGHT_TEXTURE;

    @Shadow @Final private static Identifier HOTBAR_ATTACK_INDICATOR_BACKGROUND_TEXTURE;

    @Shadow @Final private static Identifier HOTBAR_ATTACK_INDICATOR_PROGRESS_TEXTURE;

    @Inject(method = "renderHotbarItem", at = @At("HEAD"), cancellable = true)
    private void renderHotbarItem(DrawContext context, int x, int y, RenderTickCounter tickCounter, PlayerEntity player, ItemStack stack, int seed, CallbackInfo ci) {
        if (!stack.isEmpty()) {
            float f = (float)stack.getBobbingAnimationTime() - tickCounter.getTickProgress(false);
            if (f > 0.0F) {
                float g = 1.0F + f / 5.0F;
                context.getMatrices().push();
                context.getMatrices().translate((float)(x + 8), (float)(y + 12), 0.0F);
                context.getMatrices().scale(1.0F / g, (g + 1.0F) / 2.0F, 1.0F);
                context.getMatrices().translate((float)(-(x + 8)), (float)(-(y + 12)), 0.0F);
            }

            context.drawItem(player, stack, x, y, seed);
            if (f > 0.0F) {
                context.getMatrices().pop();
            }

            context.drawStackOverlay(this.client.textRenderer, stack, x, y);
        }
    }

    @Inject(at = @At("TAIL"), method = "render")
    private void renderSqueedometerHud(DrawContext context, RenderTickCounter tickCounter, CallbackInfo info) {
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
            config.movement.speed_coefficient = Minehop.o_speed_coefficient;
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

        if (config.jHud.speedHud.show_current_speed && config.enabled) {
            MinehopClient.squeedometerHud.drawMain(context, tickCounter.getTickProgress(true), config);
        }
        if (config.enabled) {
            MinehopClient.squeedometerHud.drawJHUD(context, config);
            if (!MinehopClient.spectatorList.isEmpty()) {
                MinehopClient.squeedometerHud.drawSpectators(context, tickCounter.getTickProgress(true));
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
    private void renderHotbar(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (!ConfigWrapper.config.hideSelf) {
            PlayerEntity playerEntity = this.getCameraPlayer();
            if (playerEntity != null) {
                ItemStack itemStack = playerEntity.getOffHandStack();
                Arm arm = playerEntity.getMainArm().getOpposite();
                int i = context.getScaledWindowWidth() / 2;
                context.getMatrices().push();
                context.getMatrices().translate(0.0F, 0.0F, -90.0F);
                context.drawGuiTexture(RenderLayer::getGuiTextured, HOTBAR_TEXTURE, i - 91, context.getScaledWindowHeight() - 22, 182, 22);
                context.drawGuiTexture(RenderLayer::getGuiTextured,HOTBAR_SELECTION_TEXTURE, i - 91 - 1 + playerEntity.getInventory().getSelectedSlot() * 20, context.getScaledWindowHeight() - 22 - 1, 24, 23);
                if (!itemStack.isEmpty()) {
                    if (arm == Arm.LEFT) {
                        context.drawGuiTexture(RenderLayer::getGuiTextured,HOTBAR_OFFHAND_LEFT_TEXTURE, i - 91 - 29, context.getScaledWindowHeight() - 23, 29, 24);
                    } else {
                        context.drawGuiTexture(RenderLayer::getGuiTextured,HOTBAR_OFFHAND_RIGHT_TEXTURE, i + 91, context.getScaledWindowHeight() - 23, 29, 24);
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
                    this.renderHotbarItem(context, n, o, tickCounter, playerEntity, (ItemStack)playerEntity.getInventory().getMainStacks().get(m), l++, ci);
                }

                if (!itemStack.isEmpty()) {
                    m = context.getScaledWindowHeight() - 16 - 3;
                    if (arm == Arm.LEFT) {
                        this.renderHotbarItem(context, i - 91 - 26, m, tickCounter, playerEntity, itemStack, l++, ci);
                    } else {
                        this.renderHotbarItem(context, i + 91 + 10, m, tickCounter, playerEntity, itemStack, l++, ci);
                    }
                }

                GlStateManager._enableBlend();
                if (this.client.options.getAttackIndicator().getValue() == AttackIndicator.HOTBAR) {
                    float f = this.client.player.getAttackCooldownProgress(0.0F);
                    if (f < 1.0F) {
                        n = context.getScaledWindowHeight() - 20;
                        o = i + 91 + 6;
                        if (arm == Arm.RIGHT) {
                            o = i - 91 - 22;
                        }

                        int p = (int)(f * 19.0F);
                        context.drawGuiTexture(RenderLayer::getGuiTextured, HOTBAR_ATTACK_INDICATOR_BACKGROUND_TEXTURE, o, n, 18, 18);
                        context.drawGuiTexture(RenderLayer::getGuiTextured, HOTBAR_ATTACK_INDICATOR_PROGRESS_TEXTURE, 18, 18, 0, 18 - p, o, n + 18 - p, 18, p);
                    }
                }

                GlStateManager._disableBlend();
            }
        }
        ci.cancel();
    }
}