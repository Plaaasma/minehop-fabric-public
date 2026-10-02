package net.nerdorg.minehop.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.render.SpecialGuiElementRenderer;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;

/**
 * Renders {@link RampPreviewGuiElementRenderState} (the surf-stick settings ramp preview) into its
 * picture-in-picture texture. The incoming stack is GUI-oriented (x right, y down) scaled by the GUI
 * scale with z mirrored; z is un-mirrored so the screen's 1.21.4 transforms apply unchanged. The
 * origin sits where 1.21.4 translated to: preview centre x, 62% down the preview box.
 */
public class RampPreviewGuiElementRenderer extends SpecialGuiElementRenderer<RampPreviewGuiElementRenderState> {
    public RampPreviewGuiElementRenderer(VertexConsumerProvider.Immediate vertexConsumers) {
        super(vertexConsumers);
    }

    @Override
    public Class<RampPreviewGuiElementRenderState> getElementClass() {
        return RampPreviewGuiElementRenderState.class;
    }

    @Override
    protected void render(RampPreviewGuiElementRenderState state, MatrixStack matrices) {
        // Same diffuse lighting as 1.21.4's in-GUI 3D rendering (DiffuseLighting.enableGuiDepthLighting).
        MinecraftClient.getInstance().gameRenderer.getDiffuseLighting().setShaderLights(DiffuseLighting.Type.ITEMS_3D);
        matrices.scale(1.0F, 1.0F, -1.0F);
        state.drawer().draw(matrices, this.vertexConsumers);
    }

    @Override
    protected float getYOffset(int height, int windowScaleFactor) {
        // Texture spans the preview box inset by 1 GUI px; 1.21.4 origin was 0.62 * box height from its top.
        return 0.62F * height + 0.24F * windowScaleFactor;
    }

    @Override
    protected String getName() {
        return "minehop ramp preview";
    }
}
