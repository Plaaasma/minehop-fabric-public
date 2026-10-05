package net.nerdorg.minehop.client;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.MultiBufferSource;

/**
 * Renders {@link RampPreviewGuiElementRenderState} (the surf-stick settings ramp preview) into its
 * picture-in-picture texture. The incoming stack is GUI-oriented (x right, y down) scaled by the GUI
 * scale with z mirrored; z is un-mirrored so the screen's 1.21.4 transforms apply unchanged. The
 * origin sits where 1.21.4 translated to: preview centre x, 62% down the preview box.
 */
public class RampPreviewGuiElementRenderer extends PictureInPictureRenderer<RampPreviewGuiElementRenderState> {
    public RampPreviewGuiElementRenderer(MultiBufferSource.BufferSource vertexConsumers) {
        super(vertexConsumers);
    }

    @Override
    public Class<RampPreviewGuiElementRenderState> getRenderStateClass() {
        return RampPreviewGuiElementRenderState.class;
    }

    @Override
    protected void renderToTexture(RampPreviewGuiElementRenderState state, PoseStack matrices) {
        // Same diffuse lighting as 1.21.4's in-GUI 3D rendering (DiffuseLighting.enableGuiDepthLighting).
        Minecraft.getInstance().gameRenderer.getLighting().setupFor(Lighting.Entry.ITEMS_3D);
        matrices.scale(1.0F, 1.0F, -1.0F);
        state.drawer().draw(matrices, this.bufferSource);
    }

    @Override
    protected float getTranslateY(int height, int windowScaleFactor) {
        // Texture spans the preview box inset by 1 GUI px; 1.21.4 origin was 0.62 * box height from its top.
        return 0.62F * height + 0.24F * windowScaleFactor;
    }

    @Override
    protected String getTextureLabel() {
        return "minehop ramp preview";
    }
}
