package net.nerdorg.minehop.render;

import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.LayeringTransform;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.nerdorg.minehop.Minehop;

/**
 * Render layers are built from a {@link RenderPipeline} + {@link RenderSetup}.
 * <ul>
 *   <li>Translucent colour quads: position_color shader, translucent blend, depth write ON, no culling,
 *   view-offset-z layering (same state as the 1.21.4 MultiPhaseParameters). 26.2 reversed the depth buffer (depth
 *   tests compare GREATER_THAN_OR_EQUAL now). 26.3 draws translucent custom geometry through order-independent
 *   transparency when "improved transparency" is on, which needs the OIT pipelines of the layer (vanilla's
 *   position_color debug quad set); 26.3 also removed the per-layer output target.</li>
 *   <li>Wide lines: line width is a per-vertex attribute since 1.21.11 (VertexConsumer#setLineWidth). Up to 26.1
 *   vanilla's lines layer was translucent (lines shader, translucent blend, depth write, no culling, view-offset-z
 *   layering); 26.3's {@link RenderTypes#lines()} is opaque and that state moved to
 *   {@link RenderTypes#linesTranslucent()}. Like vanilla's block outline, the OIT-capable
 *   {@link RenderTypes#linesTranslucentNoDepthWrite()} is used while improved transparency is on. Every width shares
 *   the layer; callers must emit {@code setLineWidth(width)} per vertex.</li>
 * </ul>
 */
public class ModRenderLayer {
    private static final RenderPipeline TRANSLUCENT_COLOR_QUADS_PIPELINE = RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(Minehop.MOD_ID, "pipeline/translucent_color_quads"))
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, true))
            .withCull(false)
            .build();

    private static final RenderType TRANSLUCENT_COLOR_QUADS = RenderType.create(
            "minehop_translucent_color_quads",
            RenderSetup.builder(TRANSLUCENT_COLOR_QUADS_PIPELINE)
                    .setOitPipelines(RenderPipelines.OIT_DEBUG_QUADS)
                    .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                    .createRenderSetup()
    );

    private ModRenderLayer() {
    }

    /** All line widths share vanilla's translucent lines layer; set the width per vertex via {@code setLineWidth}. */
    public static RenderType getLineOfWidth(int width) {
        return Minecraft.getInstance().gameRenderer.useImprovedTransparency()
                ? RenderTypes.linesTranslucentNoDepthWrite()
                : RenderTypes.linesTranslucent();
    }

    public static RenderType getTranslucentColorQuads() {
        return TRANSLUCENT_COLOR_QUADS;
    }
}
