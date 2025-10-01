package net.nerdorg.minehop.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.PolygonMode;
import net.minecraft.client.render.RenderLayer;
import com.mojang.blaze3d.vertex.VertexFormat;

import java.util.OptionalDouble;

public abstract class ModRenderLayer extends RenderLayer {

    public ModRenderLayer(String pName, int pBufferSize, boolean pAffectsCrumbling, boolean pSortOnUpload, Runnable pSetupState, Runnable pClearState) {
        super(pName, pBufferSize, pAffectsCrumbling, pSortOnUpload, pSetupState, pClearState);
    }

    public static RenderLayer getLineOfWidth(int width) {
        return of(width + "wide_line", 1536, false, false,
                RenderPipeline.builder().withVertexFormat(VertexFormat.builder().build(), VertexFormat.DrawMode.LINES)
                        .withCull(false)
                        .withoutBlend()
                        .withPolygonMode(PolygonMode.FILL)
                        .build(),



                RenderLayer.MultiPhaseParameters.builder()
                        .lineWidth(new LineWidth(OptionalDouble.of(width)))
                        .layering(VIEW_OFFSET_Z_LAYERING)
                        .target(ITEM_ENTITY_TARGET)
                        .build(false));
    }
    // Cant comprehend how to fix it. I'm not good at java.
}
