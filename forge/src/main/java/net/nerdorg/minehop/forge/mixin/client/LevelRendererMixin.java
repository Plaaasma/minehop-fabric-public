package net.nerdorg.minehop.forge.mixin.client;

import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.nerdorg.minehop.forge.platform.ForgeClientHelper;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Forge 54 (1.21.4) has no {@code RenderLevelStageEvent}. Fires Minehop's world-render hooks where Fabric API fires
 * {@code WorldRenderEvents}:
 * <ul>
 *     <li>AFTER_ENTITIES: Fabric injects in the main pass right before {@code popPush("blockentities")}; the very next
 *     call is {@code renderBlockEntities(poseStack, bufferSource, crumblingBufferSource, camera, ...)}, so its head is
 *     the same point, with the same pose stack, buffer source and camera.</li>
 *     <li>END: return of {@code renderLevel}.</li>
 * </ul>
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    // remap = false: Forge changed this method's signature (extra Frustum parameter, boolean return), so it has no SRG
    // mapping for the annotation processor; Forge runs official names at runtime, which is what this names.
    @Inject(method = "renderBlockEntities", at = @At("HEAD"), remap = false)
    private void minehop$afterEntities(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, MultiBufferSource.BufferSource crumblingBufferSource,
                                       Camera camera, float partialTick, Frustum frustum, CallbackInfoReturnable<Boolean> cir) {
        ForgeClientHelper.fireAfterEntities(poseStack, bufferSource, camera);
    }

    @Inject(method = "renderLevel", at = @At("RETURN"))
    private void minehop$end(GraphicsResourceAllocator allocator, DeltaTracker deltaTracker, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer,
                             Matrix4f frustumMatrix, Matrix4f projectionMatrix, CallbackInfo ci) {
        ForgeClientHelper.fireEnd(camera);
    }
}
