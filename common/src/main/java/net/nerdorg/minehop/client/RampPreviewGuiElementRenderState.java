package net.nerdorg.minehop.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import net.minecraft.client.renderer.MultiBufferSource;
import org.jetbrains.annotations.Nullable;

/**
 * 1.21.6+ GUI rendering is deferred, so the surf-stick screen's 3D ramp preview is drawn as a
 * "special" GUI element (rendered into its own texture by {@link RampPreviewGuiElementRenderer}).
 * {@code drawer} emits the same geometry the 1.21.4 screen drew inline, in the preview's local space.
 */
public record RampPreviewGuiElementRenderState(
        Drawer drawer,
        int x0,
        int y0,
        int x1,
        int y1,
        float scale,
        @Nullable ScreenRectangle scissorArea,
        @Nullable ScreenRectangle bounds
) implements PictureInPictureRenderState {
    public RampPreviewGuiElementRenderState(Drawer drawer, int x0, int y0, int x1, int y1, @Nullable ScreenRectangle scissorArea) {
        this(drawer, x0, y0, x1, y1, 1.0F, scissorArea, PictureInPictureRenderState.getBounds(x0, y0, x1, y1, scissorArea));
    }

    @FunctionalInterface
    public interface Drawer {
        void draw(PoseStack matrices, MultiBufferSource vertexConsumers);
    }
}
