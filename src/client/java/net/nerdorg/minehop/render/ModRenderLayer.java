package net.nerdorg.minehop.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.LayeringTransform;
import net.minecraft.client.render.OutputTarget;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import net.nerdorg.minehop.Minehop;

/**
 * 1.21.11 render layers are built from a {@link RenderPipeline} + {@link RenderSetup} instead of
 * RenderPhase parameters.
 * <ul>
 *   <li>Translucent colour quads: position_color shader, translucent blend, depth write ON, no culling,
 *   view-offset-z layering, item-entity target (same state as the 1.21.4 MultiPhaseParameters).</li>
 *   <li>Wide lines: line width is a per-vertex attribute since 1.21.11 (VertexConsumer#lineWidth), and
 *   vanilla's lines layer already has the old state (lines shader, translucent blend, no culling,
 *   view-offset-z layering, item-entity target), so every width shares {@link RenderLayers#lines()}.
 *   Callers must emit {@code .lineWidth(width)} per vertex.</li>
 * </ul>
 */
public class ModRenderLayer {
    private static final RenderPipeline TRANSLUCENT_COLOR_QUADS_PIPELINE = RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
            .withLocation(Identifier.of(Minehop.MOD_ID, "pipeline/translucent_color_quads"))
            .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS)
            .withDepthWrite(true)
            .withCull(false)
            .build();

    private static final RenderLayer TRANSLUCENT_COLOR_QUADS = RenderLayer.of(
            "minehop_translucent_color_quads",
            RenderSetup.builder(TRANSLUCENT_COLOR_QUADS_PIPELINE)
                    .layeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                    .outputTarget(OutputTarget.ITEM_ENTITY_TARGET)
                    .build()
    );

    private ModRenderLayer() {
    }

    /** All line widths share vanilla's lines layer; set the width per vertex via {@code lineWidth}. */
    public static RenderLayer getLineOfWidth(int width) {
        return RenderLayers.lines();
    }

    public static RenderLayer getTranslucentColorQuads() {
        return TRANSLUCENT_COLOR_QUADS;
    }
}
