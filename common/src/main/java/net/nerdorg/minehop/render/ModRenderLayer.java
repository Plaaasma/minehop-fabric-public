package net.nerdorg.minehop.render;

import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.LayeringTransform;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.nerdorg.minehop.Minehop;

/**
 * 1.21.11 render layers are built from a {@link RenderPipeline} + {@link RenderSetup} instead of
 * RenderPhase parameters.
 * <ul>
 *   <li>Translucent colour quads: position_color shader, translucent blend, depth write ON, no culling,
 *   view-offset-z layering, item-entity target (same state as the 1.21.4 MultiPhaseParameters).</li>
 *   <li>Wide lines: line width is a per-vertex attribute since 1.21.11 (VertexConsumer#lineWidth), and
 *   vanilla's lines layer already has the old state (lines shader, translucent blend, no culling,
 *   view-offset-z layering, item-entity target), so every width shares {@link RenderTypes#lines()}.
 *   Callers must emit {@code .lineWidth(width)} per vertex.</li>
 * </ul>
 */
public class ModRenderLayer {
    private static final RenderPipeline TRANSLUCENT_COLOR_QUADS_PIPELINE = RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(Minehop.MOD_ID, "pipeline/translucent_color_quads"))
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
            .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, true))
            .withCull(false)
            .build();

    private static final RenderType TRANSLUCENT_COLOR_QUADS = RenderType.create(
            "minehop_translucent_color_quads",
            RenderSetup.builder(TRANSLUCENT_COLOR_QUADS_PIPELINE)
                    .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                    .setOutputTarget(OutputTarget.ITEM_ENTITY_TARGET)
                    .createRenderSetup()
    );

    private ModRenderLayer() {
    }

    /** All line widths share vanilla's lines layer; set the width per vertex via {@code lineWidth}. */
    public static RenderType getLineOfWidth(int width) {
        return RenderTypes.lines();
    }

    public static RenderType getTranslucentColorQuads() {
        return TRANSLUCENT_COLOR_QUADS;
    }
}
