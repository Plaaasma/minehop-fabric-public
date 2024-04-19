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

    @Shadow private int scaledHeight;

    @Shadow private int scaledWidth;

    @Shadow protected abstract void renderHotbarItem(DrawContext context, int x, int y, float tickDelta, PlayerEntity player, ItemStack stack, int seed);

    @Shadow @Final private MinecraftClient client;

    @Shadow @Final private static Identifier HOTBAR_ATTACK_INDICATOR_BACKGROUND_TEXTURE;

    @Shadow @Final private static Identifier HOTBAR_ATTACK_INDICATOR_PROGRESS_TEXTURE;

    @Shadow @Final private static Identifier HOTBAR_OFFHAND_RIGHT_TEXTURE;

    @Shadow @Final private static Identifier HOTBAR_OFFHAND_LEFT_TEXTURE;

    @Shadow @Final private static Identifier HOTBAR_SELECTION_TEXTURE;

    @Shadow @Final private static Identifier HOTBAR_TEXTURE;

    @Inject(at = @At("TAIL"), method = "render(Lnet/minecraft/client/gui/DrawContext;F)V")
    private void renderSqueedometerHud(DrawContext context, float tickDelta, CallbackInfo info) {
        MinehopConfig config;
        if (Minehop.override_config) {
            config = new MinehopConfig();
            config.sv_friction = Minehop.o_sv_friction;
            config.sv_accelerate = Minehop.o_sv_accelerate;
            config.sv_airaccelerate = Minehop.o_sv_airaccelerate;
            config.sv_maxairspeed = Minehop.o_sv_maxairspeed;
            config.speed_mul = Minehop.o_speed_mul;
            config.sv_gravity = Minehop.o_sv_gravity;
            config.show_ssj = ConfigWrapper.config.show_ssj;
            config.show_efficiency = ConfigWrapper.config.show_efficiency;
            config.show_current_speed = ConfigWrapper.config.show_current_speed;
            config.show_gauge = ConfigWrapper.config.show_gauge;
        }
        else {
            config = ConfigWrapper.config;
        }

        if (config.show_current_speed) {
            MinehopClient.squeedometerHud.drawMain(context, tickDelta);
        }
        MinehopClient.squeedometerHud.drawJHUD(context, config);
        if (MinehopClient.spectatorList.size() > 0) {
            MinehopClient.squeedometerHud.drawSpectators(context, tickDelta);
        }

    }

    @Inject(at = @At("HEAD"), method = "renderHealthBar", cancellable = true)
    private void renderHealth(DrawContext context, PlayerEntity player, int x, int y, int lines, int regeneratingHeartIndex, float maxHealth, int lastHealth, int health, int absorption, boolean blinking, CallbackInfo ci) {
        if (MinehopClient.hideSelf) {
            ci.cancel();
        }
    }

    @Inject(at = @At("HEAD"), method = "renderStatusBars", cancellable = true)
    private void renderStatusBars(DrawContext context, CallbackInfo ci) {
        if (MinehopClient.hideSelf) {
            ci.cancel();
        }
    }

    @Inject(at = @At("HEAD"), method = "renderExperienceBar", cancellable = true)
    private void renderExperienceBar(DrawContext context, int x, CallbackInfo ci) {
        if (MinehopClient.hideSelf) {
            ci.cancel();
        }
    }



    @Inject(at = @At("HEAD"), method = "renderHotbar", cancellable = true)
    private void renderHotbar(float tickDelta, DrawContext context, CallbackInfo ci) {
        if (!MinehopClient.hideSelf) {
            PlayerEntity playerEntity = this.getCameraPlayer();
            if (playerEntity != null) {
                ItemStack itemStack = playerEntity.getOffHandStack();
                Arm arm = playerEntity.getMainArm().getOpposite();
                int i = this.scaledWidth / 2;
                context.getMatrices().push();
                context.getMatrices().translate(0.0F, 0.0F, -90.0F);
                context.drawGuiTexture(HOTBAR_TEXTURE, i - 91, this.scaledHeight - 22, 182, 22);
                context.drawGuiTexture(HOTBAR_SELECTION_TEXTURE, i - 91 - 1 + playerEntity.getInventory().selectedSlot * 20, this.scaledHeight - 22 - 1, 24, 23);
                if (!itemStack.isEmpty()) {
                    if (arm == Arm.LEFT) {
                        context.drawGuiTexture(HOTBAR_OFFHAND_LEFT_TEXTURE, i - 91 - 29, this.scaledHeight - 23, 29, 24);
                    } else {
                        context.drawGuiTexture(HOTBAR_OFFHAND_RIGHT_TEXTURE, i + 91, this.scaledHeight - 23, 29, 24);
                    }
                }

                context.getMatrices().pop();
                int l = 1;

                int m;
                int n;
                int o;
                for(m = 0; m < 9; ++m) {
                    n = i - 90 + m * 20 + 2;
                    o = this.scaledHeight - 16 - 3;
                    this.renderHotbarItem(context, n, o, tickDelta, playerEntity, (ItemStack)playerEntity.getInventory().main.get(m), l++);
                }

                if (!itemStack.isEmpty()) {
                    m = this.scaledHeight - 16 - 3;
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
                        n = this.scaledHeight - 20;
                        o = i + 91 + 6;
                        if (arm == Arm.RIGHT) {
                            o = i - 91 - 22;
                        }

                        int p = (int)(f * 19.0F);
                        context.drawGuiTexture(HOTBAR_ATTACK_INDICATOR_BACKGROUND_TEXTURE, o, n, 18, 18);
                        context.drawGuiTexture(HOTBAR_ATTACK_INDICATOR_PROGRESS_TEXTURE, 18, 18, 0, 18 - p, o, n + 18 - p, 18, p);
                    }
                }

                RenderSystem.disableBlend();
            }
        }
        ci.cancel();
    }
}