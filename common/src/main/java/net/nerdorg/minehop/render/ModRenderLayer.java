package net.nerdorg.minehop.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalDouble;
import net.minecraft.client.renderer.RenderType;

public class ModRenderLayer extends RenderType {
    private static final RenderType TRANSLUCENT_COLOR_QUADS = create(
            "minehop_translucent_color_quads",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS,
            1536,
            RenderType.CompositeState.builder()
                    .setShaderState(POSITION_COLOR_SHADER)
                    .setLayeringState(VIEW_OFFSET_Z_LAYERING)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setOutputState(ITEM_ENTITY_TARGET)
                    .setWriteMaskState(COLOR_DEPTH_WRITE)
                    .setCullState(NO_CULL)
                    .createCompositeState(false)
    );
    private static final Map<Integer, RenderType> LINE_LAYERS = new HashMap<>();

    public ModRenderLayer(String pName, VertexFormat pFormat, VertexFormat.Mode pMode, int pBufferSize, boolean pAffectsCrumbling, boolean pSortOnUpload, Runnable pSetupState, Runnable pClearState) {
        super(pName, pFormat, pMode, pBufferSize, pAffectsCrumbling, pSortOnUpload, pSetupState, pClearState);
    }

    public static RenderType getLineOfWidth(int width) {
        int safeWidth = Math.max(1, width);
        synchronized (LINE_LAYERS) {
            return LINE_LAYERS.computeIfAbsent(
                    safeWidth,
                    key -> create(
                            key + "wide_line",
                            DefaultVertexFormat.POSITION_COLOR_NORMAL,
                            VertexFormat.Mode.LINES,
                            1536,
                            RenderType.CompositeState.builder()
                                    .setShaderState(RENDERTYPE_LINES_SHADER)
                                    .setLineState(new LineStateShard(OptionalDouble.of(key)))
                                    .setLayeringState(VIEW_OFFSET_Z_LAYERING)
                                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                                    .setOutputState(ITEM_ENTITY_TARGET)
                                    .setWriteMaskState(COLOR_DEPTH_WRITE)
                                    .setCullState(NO_CULL)
                                    .createCompositeState(false)
                    )
            );
        }
    }

    public static RenderType getTranslucentColorQuads() {
        return TRANSLUCENT_COLOR_QUADS;
    }
}
