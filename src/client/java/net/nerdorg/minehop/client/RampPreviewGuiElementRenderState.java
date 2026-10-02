package net.nerdorg.minehop.client;

import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.render.state.special.SpecialGuiElementRenderState;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import org.jetbrains.annotations.Nullable;

/**
 * 1.21.6+ GUI rendering is deferred, so the surf-stick screen's 3D ramp preview is drawn as a
 * "special" GUI element (rendered into its own texture by {@link RampPreviewGuiElementRenderer}).
 * {@code drawer} emits the same geometry the 1.21.4 screen drew inline, in the preview's local space.
 */
public record RampPreviewGuiElementRenderState(
        Drawer drawer,
        int x1,
        int y1,
        int x2,
        int y2,
        float scale,
        @Nullable ScreenRect scissorArea,
        @Nullable ScreenRect bounds
) implements SpecialGuiElementRenderState {
    public RampPreviewGuiElementRenderState(Drawer drawer, int x1, int y1, int x2, int y2, @Nullable ScreenRect scissorArea) {
        this(drawer, x1, y1, x2, y2, 1.0F, scissorArea, SpecialGuiElementRenderState.createBounds(x1, y1, x2, y2, scissorArea));
    }

    @FunctionalInterface
    public interface Drawer {
        void draw(MatrixStack matrices, VertexConsumerProvider vertexConsumers);
    }
}
