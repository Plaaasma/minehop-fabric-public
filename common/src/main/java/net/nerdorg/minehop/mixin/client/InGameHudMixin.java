package net.nerdorg.minehop.mixin.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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

@Mixin(Gui.class)
public abstract class InGameHudMixin {
    @Shadow @Nullable protected abstract Player getCameraPlayer();

    @Shadow @Final private Minecraft minecraft;

    @Shadow @Final private static ResourceLocation HOTBAR_SPRITE;

    @Shadow @Final private static ResourceLocation HOTBAR_SELECTION_SPRITE;

    @Shadow @Final private static ResourceLocation HOTBAR_OFFHAND_LEFT_SPRITE;

    @Shadow @Final private static ResourceLocation HOTBAR_OFFHAND_RIGHT_SPRITE;

    @Shadow @Final private static ResourceLocation HOTBAR_ATTACK_INDICATOR_BACKGROUND_SPRITE;

    @Shadow @Final private static ResourceLocation HOTBAR_ATTACK_INDICATOR_PROGRESS_SPRITE;

    @Inject(method = "renderSlot", at = @At("HEAD"), cancellable = true)
    private void renderHotbarItem(GuiGraphics context, int x, int y, DeltaTracker tickCounter, Player player, ItemStack stack, int seed, CallbackInfo ci) {
        if (!stack.isEmpty()) {
            float f = (float)stack.getPopTime() - tickCounter.getGameTimeDeltaPartialTick(false);
            if (f > 0.0F) {
                float g = 1.0F + f / 5.0F;
                context.pose().pushPose();
                context.pose().translate((float)(x + 8), (float)(y + 12), 0.0F);
                context.pose().scale(1.0F / g, (g + 1.0F) / 2.0F, 1.0F);
                context.pose().translate((float)(-(x + 8)), (float)(-(y + 12)), 0.0F);
            }

            context.renderItem(player, stack, x, y, seed);
            if (f > 0.0F) {
                context.pose().popPose();
            }

            context.renderItemDecorations(this.minecraft.font, stack, x, y);
        }
    }

    @Inject(at = @At("TAIL"), method = "render")
    private void renderSqueedometerHud(GuiGraphics context, DeltaTracker tickCounter, CallbackInfo info) {
        // The HUD editor draws its own WYSIWYG previews; don't double-render the live HUD behind it.
        if (this.minecraft.screen instanceof net.nerdorg.minehop.client.HudEditorScreen) {
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
            MinehopClient.squeedometerHud.drawMain(context, tickCounter.getGameTimeDeltaPartialTick(true), config);
        }
        if (config.enabled) {
            MinehopClient.squeedometerHud.drawJHUD(context, config);
            if (!MinehopClient.spectatorList.isEmpty()) {
                MinehopClient.squeedometerHud.drawSpectators(context, tickCounter.getGameTimeDeltaPartialTick(true));
            }
        }
    }

    @Inject(at = @At("HEAD"), method = "renderHearts", cancellable = true)
    private void renderHealth(GuiGraphics context, Player player, int x, int y, int lines, int regeneratingHeartIndex, float maxHealth, int lastHealth, int health, int absorption, boolean blinking, CallbackInfo ci) {
        if (net.nerdorg.minehop.client.ClientVisibility.hideSelf()) {
            ci.cancel();
        }
    }

    @Inject(at = @At("HEAD"), method = "renderPlayerHealth", cancellable = true)
    private void renderStatusBars(GuiGraphics context, CallbackInfo ci) {
        if (net.nerdorg.minehop.client.ClientVisibility.hideSelf()) {
            ci.cancel();
        }
    }

    @Inject(at = @At("HEAD"), method = "renderExperienceBar", cancellable = true)
    private void renderExperienceBar(GuiGraphics context, int x, CallbackInfo ci) {
        if (net.nerdorg.minehop.client.ClientVisibility.hideSelf()) {
            ci.cancel();
        }
    }



    @Inject(at = @At("HEAD"), method = "renderItemHotbar", cancellable = true)
    private void renderHotbar(GuiGraphics context, DeltaTracker tickCounter, CallbackInfo ci) {
        if (!net.nerdorg.minehop.client.ClientVisibility.hideSelf()) {
            Player playerEntity = this.getCameraPlayer();
            if (playerEntity != null) {
                ItemStack itemStack = playerEntity.getOffhandItem();
                HumanoidArm arm = playerEntity.getMainArm().getOpposite();
                int i = context.guiWidth() / 2;
                context.pose().pushPose();
                context.pose().translate(0.0F, 0.0F, -90.0F);
                context.blitSprite(RenderType::guiTextured, HOTBAR_SPRITE, i - 91, context.guiHeight() - 22, 182, 22);
                context.blitSprite(RenderType::guiTextured,HOTBAR_SELECTION_SPRITE, i - 91 - 1 + playerEntity.getInventory().selected * 20, context.guiHeight() - 22 - 1, 24, 23);
                if (!itemStack.isEmpty()) {
                    if (arm == HumanoidArm.LEFT) {
                        context.blitSprite(RenderType::guiTextured,HOTBAR_OFFHAND_LEFT_SPRITE, i - 91 - 29, context.guiHeight() - 23, 29, 24);
                    } else {
                        context.blitSprite(RenderType::guiTextured,HOTBAR_OFFHAND_RIGHT_SPRITE, i + 91, context.guiHeight() - 23, 29, 24);
                    }
                }

                context.pose().popPose();
                int l = 1;

                int m;
                int n;
                int o;
                for(m = 0; m < 9; ++m) {
                    n = i - 90 + m * 20 + 2;
                    o = context.guiHeight() - 16 - 3;
                    this.renderHotbarItem(context, n, o, tickCounter, playerEntity, (ItemStack)playerEntity.getInventory().items.get(m), l++, ci);
                }

                if (!itemStack.isEmpty()) {
                    m = context.guiHeight() - 16 - 3;
                    if (arm == HumanoidArm.LEFT) {
                        this.renderHotbarItem(context, i - 91 - 26, m, tickCounter, playerEntity, itemStack, l++, ci);
                    } else {
                        this.renderHotbarItem(context, i + 91 + 10, m, tickCounter, playerEntity, itemStack, l++, ci);
                    }
                }

                RenderSystem.enableBlend();
                if (this.minecraft.options.attackIndicator().get() == AttackIndicatorStatus.HOTBAR) {
                    float f = this.minecraft.player.getAttackStrengthScale(0.0F);
                    if (f < 1.0F) {
                        n = context.guiHeight() - 20;
                        o = i + 91 + 6;
                        if (arm == HumanoidArm.RIGHT) {
                            o = i - 91 - 22;
                        }

                        int p = (int)(f * 19.0F);
                        context.blitSprite(RenderType::guiTextured, HOTBAR_ATTACK_INDICATOR_BACKGROUND_SPRITE, o, n, 18, 18);
                        context.blitSprite(RenderType::guiTextured, HOTBAR_ATTACK_INDICATOR_PROGRESS_SPRITE, 18, 18, 0, 18 - p, o, n + 18 - p, 18, p);
                    }
                }

                RenderSystem.disableBlend();
            }
        }
        ci.cancel();
    }
}
