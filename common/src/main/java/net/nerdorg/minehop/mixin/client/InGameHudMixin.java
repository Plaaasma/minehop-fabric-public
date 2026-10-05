package net.nerdorg.minehop.mixin.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.MinehopClient;
import net.nerdorg.minehop.client.MinehopHudOverlay;
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

    // 1.20.1: the hotbar is drawn from the widgets/icons atlases (GUI sprites came in 1.20.2), and
    // vanilla's renderSlot is used directly (the 1.21.4 mixin re-implemented the vanilla one).
    @Shadow @Final private static ResourceLocation WIDGETS_LOCATION;

    @Shadow @Final private static ResourceLocation GUI_ICONS_LOCATION;

    @Shadow protected abstract void renderSlot(GuiGraphics context, int x, int y, float tickDelta, Player player, ItemStack stack, int seed);

    @Inject(at = @At("TAIL"), method = "render(Lnet/minecraft/client/gui/GuiGraphics;F)V")
    private void renderSqueedometerHud(GuiGraphics context, float tickDelta, CallbackInfo info) {
        // The body lives in MinehopHudOverlay so Forge 1.20.1 (whose ForgeGui never calls Gui#render) can draw the same
        // HUD from RenderGuiEvent.Post.
        MinehopHudOverlay.render(context, tickDelta);
    }

    @Inject(at = @At("HEAD"), method = "renderHearts", cancellable = true)
    private void renderHealth(GuiGraphics context, Player player, int x, int y, int lines, int regeneratingHeartIndex, float maxHealth, int lastHealth, int health, int absorption, boolean blinking, CallbackInfo ci) {
        if (ConfigWrapper.config.hideSelf) {
            ci.cancel();
        }
    }

    @Inject(at = @At("HEAD"), method = "renderPlayerHealth", cancellable = true)
    private void renderStatusBars(GuiGraphics context, CallbackInfo ci) {
        if (ConfigWrapper.config.hideSelf) {
            ci.cancel();
        }
    }

    @Inject(at = @At("HEAD"), method = "renderExperienceBar", cancellable = true)
    private void renderExperienceBar(GuiGraphics context, int x, CallbackInfo ci) {
        if (ConfigWrapper.config.hideSelf) {
            ci.cancel();
        }
    }



    @Inject(at = @At("HEAD"), method = "renderHotbar", cancellable = true)
    private void renderHotbar(float tickDelta, GuiGraphics context, CallbackInfo ci) {
        if (!ConfigWrapper.config.hideSelf) {
            Player playerEntity = this.getCameraPlayer();
            if (playerEntity != null) {
                ItemStack itemStack = playerEntity.getOffhandItem();
                HumanoidArm arm = playerEntity.getMainArm().getOpposite();
                int i = context.guiWidth() / 2;
                context.pose().pushPose();
                context.pose().translate(0.0F, 0.0F, -90.0F);
                context.blit(WIDGETS_LOCATION, i - 91, context.guiHeight() - 22, 0, 0, 182, 22);
                context.blit(WIDGETS_LOCATION, i - 91 - 1 + playerEntity.getInventory().selected * 20, context.guiHeight() - 22 - 1, 0, 22, 24, 22);
                if (!itemStack.isEmpty()) {
                    if (arm == HumanoidArm.LEFT) {
                        context.blit(WIDGETS_LOCATION, i - 91 - 29, context.guiHeight() - 23, 24, 22, 29, 24);
                    } else {
                        context.blit(WIDGETS_LOCATION, i + 91, context.guiHeight() - 23, 53, 22, 29, 24);
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
                    this.renderSlot(context, n, o, tickDelta, playerEntity, (ItemStack)playerEntity.getInventory().items.get(m), l++);
                }

                if (!itemStack.isEmpty()) {
                    m = context.guiHeight() - 16 - 3;
                    if (arm == HumanoidArm.LEFT) {
                        this.renderSlot(context, i - 91 - 26, m, tickDelta, playerEntity, itemStack, l++);
                    } else {
                        this.renderSlot(context, i + 91 + 10, m, tickDelta, playerEntity, itemStack, l++);
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
                        context.blit(GUI_ICONS_LOCATION, o, n, 0, 94, 18, 18);
                        context.blit(GUI_ICONS_LOCATION, o, n + 18 - p, 18, 112 - p, 18, p);
                    }
                }

                RenderSystem.disableBlend();
            }
        }
        ci.cancel();
    }
}
