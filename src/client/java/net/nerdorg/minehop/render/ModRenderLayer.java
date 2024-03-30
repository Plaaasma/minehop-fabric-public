package net.nerdorg.minehop.render;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;

import java.util.OptionalDouble;

public class ModRenderLayer extends RenderLayer {
    public ModRenderLayer(String pName, VertexFormat pFormat, VertexFormat.DrawMode pMode, int pBufferSize, boolean pAffectsCrumbling, boolean pSortOnUpload, Runnable pSetupState, Runnable pClearState) {
        super(pName, pFormat, pMode, pBufferSize, pAffectsCrumbling, pSortOnUpload, pSetupState, pClearState);
    }

    public static RenderLayer getLineOfWidth(int width) {
        return of(width + "wide_line",
                VertexFormats.LINES,
                VertexFormat.DrawMode.LINES,
                1536,
                RenderLayer.MultiPhaseParameters.builder()
                        .program(LINES_PROGRAM)
                        .lineWidth(new LineWidth(OptionalDouble.of(width)))
                        .layering(VIEW_OFFSET_Z_LAYERING)
                        .transparency(TRANSLUCENT_TRANSPARENCY)
                        .target(ITEM_ENTITY_TARGET)
                        .writeMaskState(ALL_MASK)
                        .cull(DISABLE_CULLING)
                        .build(false));
    }
}
