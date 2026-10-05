package net.nerdorg.minehop.forge.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OutlineBufferSource;
import net.nerdorg.minehop.forge.platform.ForgeClientHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Forge 61 (1.21.11) has no {@code RenderLevelStageEvent}. Fabric API fires {@code WorldRenderEvents.AFTER_ENTITIES}
 * inside {@code LevelRenderer}'s main pass right after {@code renderBuffers.outlineBufferSource().endOutlineBatch()}
 * (entities and block entities drawn, translucent terrain not yet), with the pass's fresh (identity) pose stack and the
 * main buffer source. {@code endOutlineBatch} is called from exactly that one place in vanilla, so its return is the same
 * point (the pass is a lambda, which a mixin cannot target by a stable name).
 */
@Mixin(OutlineBufferSource.class)
public abstract class OutlineBufferSourceMixin {
    @Inject(method = "endOutlineBatch", at = @At("RETURN"))
    private void minehop$afterEntities(CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        ForgeClientHelper.fireAfterEntities(new PoseStack(), minecraft.renderBuffers().bufferSource(), minecraft.gameRenderer.getMainCamera());
    }
}
