package net.nerdorg.minehop.render;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;

import java.util.HashMap;
import java.util.Map;
import java.util.OptionalDouble;

public class ModRenderLayer extends RenderLayer {
    private static final RenderLayer TRANSLUCENT_COLOR_QUADS = of(
            "minehop_translucent_color_quads",
            VertexFormats.POSITION_COLOR,
            VertexFormat.DrawMode.QUADS,
            1536,
            RenderLayer.MultiPhaseParameters.builder()
                    .program(POSITION_COLOR_PROGRAM)
                    .layering(VIEW_OFFSET_Z_LAYERING)
                    .transparency(TRANSLUCENT_TRANSPARENCY)
                    .target(ITEM_ENTITY_TARGET)
                    .writeMaskState(ALL_MASK)
                    .cull(DISABLE_CULLING)
                    .build(false)
    );
    private static final Map<Integer, RenderLayer> LINE_LAYERS = new HashMap<>();

    public ModRenderLayer(String pName, VertexFormat pFormat, VertexFormat.DrawMode pMode, int pBufferSize, boolean pAffectsCrumbling, boolean pSortOnUpload, Runnable pSetupState, Runnable pClearState) {
        super(pName, pFormat, pMode, pBufferSize, pAffectsCrumbling, pSortOnUpload, pSetupState, pClearState);
    }

    public static RenderLayer getLineOfWidth(int width) {
        int safeWidth = Math.max(1, width);
        synchronized (LINE_LAYERS) {
            return LINE_LAYERS.computeIfAbsent(
                    safeWidth,
                    key -> of(
                            key + "wide_line",
                            VertexFormats.LINES,
                            VertexFormat.DrawMode.LINES,
                            1536,
                            RenderLayer.MultiPhaseParameters.builder()
                                    .program(LINES_PROGRAM)
                                    .lineWidth(new LineWidth(OptionalDouble.of(key)))
                                    .layering(VIEW_OFFSET_Z_LAYERING)
                                    .transparency(TRANSLUCENT_TRANSPARENCY)
                                    .target(ITEM_ENTITY_TARGET)
                                    .writeMaskState(ALL_MASK)
                                    .cull(DISABLE_CULLING)
                                    .build(false)
                    )
            );
        }
    }

    public static RenderLayer getTranslucentColorQuads() {
        return TRANSLUCENT_COLOR_QUADS;
    }
}
