package net.nerdorg.minehop.entity.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.entity.custom.SurfRampEntity;
import net.nerdorg.minehop.render.ModRenderLayer;
import net.nerdorg.minehop.render.RenderUtil;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.jetbrains.annotations.Nullable;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Arrays;
import java.util.List;

public class SurfRampRenderer extends MobRenderer<SurfRampEntity, SurfRampEntityRenderState, SurfRampModel> {
    private static final double UV_SCALE = 0.5D;
    private static final float HORIZONTAL_TEXTURE_INSET_PIXELS = 1.0F;
    private static final double UV_SPLIT_EPSILON = 1.0E-7D;
    private static final double SEAM_ENDPOINT_MATCH_EPSILON = 0.14D;
    private static final double SEAM_OUTER_JOIN_MIN_DISTANCE = 0.03D;
    private static final double SEAM_SEARCH_RADIUS = 0.55D;
    private static final double SEAM_BRIDGE_DETAIL_DISTANCE_SQ = 32.0D * 32.0D;
    private static final Identifier WIREFRAME_FILL_TEXTURE = Identifier.fromNamespaceAndPath("minehop", "textures/misc/white.png");
    private static final int WIREFRAME_LINE_WIDTH = 2;
    private static final int WIREFRAME_LINE_ALPHA = 255;
    private static final double WIREFRAME_RIB_SPACING = 1.65D;
    private static final double[] WIREFRAME_SURFACE_LINE_FRACTIONS = new double[]{0.50D};
    private static final int COLLISION_DEBUG_LINE_WIDTH = 1;
    private static final int COLLISION_DEBUG_LINE_ALPHA = 235;
    private static final int COLLISION_DEBUG_COLOR_R = 255;
    private static final int COLLISION_DEBUG_COLOR_G = 40;
    private static final int COLLISION_DEBUG_COLOR_B = 40;
    private static final double COLLISION_DEBUG_RIB_SPACING = 0.70D;
    private static final double[] COLLISION_DEBUG_SURFACE_LINE_FRACTIONS = new double[]{0.33D, 0.66D};

    public SurfRampRenderer(EntityRendererProvider.Context context) {
        super(context, new SurfRampModel(context.bakeLayer(ModModelLayers.SURF_RAMP_ENTITY)), 0.0F);
    }

    @Override
    public SurfRampEntityRenderState createRenderState() {
        return new SurfRampEntityRenderState();
    }

    @Override
    public boolean shouldRender(SurfRampEntity entity, Frustum frustum, double x, double y, double z) {
        return super.shouldRender(entity, frustum, x, y, z);
    }

    @Override
    public void extractRenderState(SurfRampEntity entity, SurfRampEntityRenderState state, float tickDelta) {
        // 1.21.9+: the dispatcher positions the entity from the render state (x/y/z) and the light
        // comes from state.light, so the base state must be filled in (1.21.4 passed both separately).
        super.extractRenderState(entity, state, tickDelta);
        state.surfRampEntity = entity;
    }

    @Override
    public void submit(SurfRampEntityRenderState renderState, PoseStack matrixStack, SubmitNodeCollector queue, CameraRenderState cameraState) {
        SurfRampEntity entity = renderState.surfRampEntity;
        if (entity == null) {
            return;
        }
        int light = renderState.lightCoords;

        // 1.21.9+: geometry is submitted to the render command queue per render layer instead of being
        // written into a VertexConsumerProvider; each callback rebuilds the same vertices as 1.21.4.
        Vec3 entityPos = entity.position();
        Vec3 cameraPos = this.getCameraPos();
        boolean renderUnderside = this.shouldRenderUnderside(entity, cameraPos);
        if (entity.isWireframeMode()) {
            int segments = this.getLodSegmentCount(entity, entityPos, true);
            queue.submitCustomGeometry(matrixStack, ModRenderLayer.getLineOfWidth(WIREFRAME_LINE_WIDTH), (entry, wireConsumer) ->
                    this.renderWireframe(entity, entityPos, segments, toMatrixStack(entry), wireConsumer, null, light));
            if (entity.isWireframeFillEnabled()) {
                queue.submitCustomGeometry(matrixStack, RenderTypes.entityTranslucent(WIREFRAME_FILL_TEXTURE), (entry, fillConsumer) ->
                        this.renderWireframe(entity, entityPos, segments, toMatrixStack(entry), DISCARDING_CONSUMER, fillConsumer, light));
            }
        } else {
            int segments = this.getLodSegmentCount(entity, entityPos, false);
            TextureAtlasSprite rampSprite = this.resolveRampSprite(entity);
            queue.submitCustomGeometry(matrixStack, RenderTypes.entityCutout(TextureAtlas.LOCATION_BLOCKS), (entry, consumer) -> {
                PoseStack entryStack = toMatrixStack(entry);
                if (entity.isTwoSided()) {
                    this.renderTwoSidedSolid(entity, entityPos, segments, entryStack, consumer, rampSprite, light, renderUnderside);
                } else {
                    this.renderOneSidedSolid(entity, entityPos, cameraPos, segments, entryStack, consumer, rampSprite, light, renderUnderside);
                }
            });
        }

        if (Minecraft.getInstance().debugEntries.isCurrentlyEnabled(DebugScreenEntries.ENTITY_HITBOXES)) {
            queue.submitCustomGeometry(matrixStack, ModRenderLayer.getLineOfWidth(COLLISION_DEBUG_LINE_WIDTH), (entry, consumer) ->
                    this.renderCollisionPolygons(entity, toMatrixStack(entry), consumer, entityPos));
        }
    }

    /** A MatrixStack whose top entry is the queued command's entry, for the MatrixStack-based helpers. */
    private static PoseStack toMatrixStack(PoseStack.Pose entry) {
        PoseStack stack = new PoseStack();
        stack.last().pose().set(entry.pose());
        stack.last().normal().set(entry.normal());
        return stack;
    }

    /** Swallows vertices: the wireframe fill pass reuses the wireframe walker without emitting its lines. */
    private static final VertexConsumer DISCARDING_CONSUMER = new VertexConsumer() {
        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            return this;
        }

        @Override
        public VertexConsumer setColor(int argb) {
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            return this;
        }

        @Override
        public VertexConsumer setLineWidth(float width) {
            return this;
        }
    };

    private int getLodSegmentCount(SurfRampEntity entity, Vec3 entityPos, boolean wireframe) {
        int baseSegments;
        if (wireframe) {
            baseSegments = entity.isCurved() ? 12 : 8;
        } else {
            baseSegments = entity.isCurved() ? 18 : 12;
        }

        int fps = Minehop.clientRenderFps;
        if (fps > 0) {
            if (fps < 45) {
                baseSegments = (int) Math.ceil(baseSegments * 0.35D);
            } else if (fps < 75) {
                baseSegments = (int) Math.ceil(baseSegments * 0.45D);
            } else if (fps < 120) {
                baseSegments = (int) Math.ceil(baseSegments * 0.62D);
            } else if (fps < 180) {
                baseSegments = (int) Math.ceil(baseSegments * 0.78D);
            }
        }

        Minecraft client = Minecraft.getInstance();
        if (client != null && client.gameRenderer != null && client.gameRenderer.getMainCamera() != null) {
            Vec3 cameraPos = client.gameRenderer.getMainCamera().position();
            if (cameraPos != null) {
                double distanceSq = cameraPos.distanceToSqr(entityPos);
                if (distanceSq > 4096.0D) {
                    baseSegments = (int) Math.ceil(baseSegments * 0.35D);
                } else if (distanceSq > 1024.0D) {
                    baseSegments = (int) Math.ceil(baseSegments * 0.48D);
                } else if (distanceSq > 256.0D) {
                    baseSegments = (int) Math.ceil(baseSegments * 0.66D);
                }
            }
        }

        return wireframe
                ? Mth.clamp(baseSegments, 4, 18)
                : Mth.clamp(baseSegments, 6, 24);
    }

    private void renderOneSidedSolid(
            SurfRampEntity entity,
            Vec3 entityPos,
            @Nullable Vec3 cameraPos,
            int segments,
            PoseStack matrixStack,
            VertexConsumer consumer,
            TextureAtlasSprite sprite,
            int light,
            boolean renderUnderside
    ) {
        Vec3 firstTop = null;
        Vec3 firstOuter = null;
        Vec3 firstInnerBase = null;

        Vec3 previousTop = null;
        Vec3 previousOuter = null;
        Vec3 previousInnerBase = null;
        Vec3 previousCenter = null;
        double accumulatedU = 0.0D;

        for (int i = 0; i <= segments; i++) {
            double t = (double) i / (double) segments;
            Vec3 center = entity.sampleCenterlineCached(t);
            Vec3 left = entity.sampleLeftCached(t);
            double baseY = entity.sampleBaseYCached(t);
            double topY = baseY + entity.getDrop();

            Vec3 top = new Vec3(center.x, topY, center.z).subtract(entityPos);
            Vec3 outer = new Vec3(
                    center.x + left.x * entity.getRampWidth() * entity.getSideSign(),
                    baseY,
                    center.z + left.z * entity.getRampWidth() * entity.getSideSign()
            ).subtract(entityPos);
            Vec3 innerBase = new Vec3(center.x, baseY, center.z).subtract(entityPos);

            if (firstTop == null) {
                firstTop = top;
                firstOuter = outer;
                firstInnerBase = innerBase;
            }

            if (previousTop != null) {
                double u0 = accumulatedU;
                accumulatedU += center.distanceTo(previousCenter) * UV_SCALE;
                double u1 = accumulatedU;

                drawTexturedQuad(entity, consumer, matrixStack, previousTop, top, outer, previousOuter, sprite, light, u0, u1, 0.0F, 1.0F);
                drawTexturedQuad(entity, consumer, matrixStack, previousInnerBase, previousTop, top, innerBase, sprite, light, u0, u1, 0.0F, 1.0F);
                if (renderUnderside) {
                    drawTexturedQuad(entity, consumer, matrixStack, previousOuter, outer, innerBase, previousInnerBase, sprite, light, u0, u1, 0.0F, 1.0F);
                }
            }

            previousTop = top;
            previousOuter = outer;
            previousInnerBase = innerBase;
            previousCenter = center;
        }

        if (firstTop != null && previousTop != null) {
            EndpointSlice startSlice = this.computeOneSidedEndpointSlice(entity, entityPos, 0.0D);
            EndpointSlice endSlice = this.computeOneSidedEndpointSlice(entity, entityPos, 1.0D);

            EndpointConnection startConnection = null;
            EndpointConnection endConnection = null;

            boolean renderStartCap = !entity.isLinkedStart();
            boolean renderEndCap = !entity.isLinkedEnd();
            if (cameraPos != null && entity.isLinkedStart() && this.shouldResolveSeamConnection(cameraPos, startSlice.worldCenter)) {
                startConnection = this.findConnectedOneSidedEndpoint(entity, startSlice.worldCenter);
                renderStartCap = startConnection == null;
            }
            if (cameraPos != null && entity.isLinkedEnd() && this.shouldResolveSeamConnection(cameraPos, endSlice.worldCenter)) {
                endConnection = this.findConnectedOneSidedEndpoint(entity, endSlice.worldCenter);
                renderEndCap = endConnection == null;
            }

            if (renderStartCap) {
                drawDoubleSidedTexturedTriangle(entity, consumer, matrixStack, startSlice.topLocal, startSlice.outerLocal, startSlice.innerLocal, sprite, light);
            }
            if (renderEndCap) {
                drawDoubleSidedTexturedTriangle(entity, consumer, matrixStack, endSlice.topLocal, endSlice.innerLocal, endSlice.outerLocal, sprite, light);
            }

            if (startConnection != null) {
                this.drawOneSidedSeamBridge(entity, entityPos, matrixStack, consumer, sprite, light, startSlice, startConnection, renderUnderside);
            }
            if (endConnection != null) {
                this.drawOneSidedSeamBridge(entity, entityPos, matrixStack, consumer, sprite, light, endSlice, endConnection, renderUnderside);
            }
        }
    }

    private EndpointSlice computeOneSidedEndpointSlice(SurfRampEntity entity, Vec3 entityPos, double t) {
        Vec3 center = entity.sampleCenterlineCached(t);
        Vec3 left = entity.sampleLeftCached(t);
        double baseY = entity.sampleBaseYCached(t);
        double topY = baseY + entity.getDrop();

        Vec3 topWorld = new Vec3(center.x, topY, center.z);
        Vec3 innerWorld = new Vec3(center.x, baseY, center.z);
        Vec3 outerWorld = new Vec3(
                center.x + left.x * entity.getRampWidth() * entity.getSideSign(),
                baseY,
                center.z + left.z * entity.getRampWidth() * entity.getSideSign()
        );

        return new EndpointSlice(
                center,
                topWorld.subtract(entityPos),
                innerWorld.subtract(entityPos),
                outerWorld.subtract(entityPos)
        );
    }

    private void drawOneSidedSeamBridge(
            SurfRampEntity entity,
            Vec3 entityPos,
            PoseStack matrixStack,
            VertexConsumer consumer,
            TextureAtlasSprite sprite,
            int light,
            EndpointSlice slice,
            @Nullable EndpointConnection connection,
            boolean renderUnderside
    ) {
        if (connection == null || connection.other == null || entity.getId() >= connection.other.getId()) {
            return;
        }

        SurfRampEntity other = connection.other;
        double otherT = connection.otherAtStart ? 0.0D : 1.0D;
        Vec3 otherCenter = other.sampleCenterlineCached(otherT);
        Vec3 otherLeft = other.sampleLeftCached(otherT);
        double otherBaseY = other.sampleBaseYCached(otherT);
        double otherTopY = otherBaseY + other.getDrop();
        Vec3 otherTopWorld = new Vec3(otherCenter.x, otherTopY, otherCenter.z);
        Vec3 otherInnerWorld = new Vec3(otherCenter.x, otherBaseY, otherCenter.z);
        Vec3 otherOuterWorld = new Vec3(
                otherCenter.x + otherLeft.x * other.getRampWidth() * other.getSideSign(),
                otherBaseY,
                otherCenter.z + otherLeft.z * other.getRampWidth() * other.getSideSign()
        );
        Vec3 otherTopLocal = otherTopWorld.subtract(entityPos);
        Vec3 otherInnerLocal = otherInnerWorld.subtract(entityPos);
        Vec3 otherOuterLocal = otherOuterWorld.subtract(entityPos);

        double topDistance = otherTopLocal.distanceTo(slice.topLocal);
        double innerDistance = otherInnerLocal.distanceTo(slice.innerLocal);
        double outerDistance = otherOuterLocal.distanceTo(slice.outerLocal);
        if (topDistance < SEAM_OUTER_JOIN_MIN_DISTANCE
                && innerDistance < SEAM_OUTER_JOIN_MIN_DISTANCE
                && outerDistance < SEAM_OUTER_JOIN_MIN_DISTANCE) {
            return;
        }

        drawDoubleSidedTexturedTriangle(entity, consumer, matrixStack, slice.topLocal, otherTopLocal, otherOuterLocal, sprite, light);
        drawDoubleSidedTexturedTriangle(entity, consumer, matrixStack, slice.topLocal, otherOuterLocal, slice.outerLocal, sprite, light);

        drawDoubleSidedTexturedTriangle(entity, consumer, matrixStack, slice.innerLocal, slice.topLocal, otherTopLocal, sprite, light);
        drawDoubleSidedTexturedTriangle(entity, consumer, matrixStack, slice.innerLocal, otherTopLocal, otherInnerLocal, sprite, light);

        if (renderUnderside) {
            drawDoubleSidedTexturedTriangle(entity, consumer, matrixStack, slice.innerLocal, slice.outerLocal, otherOuterLocal, sprite, light);
            drawDoubleSidedTexturedTriangle(entity, consumer, matrixStack, slice.innerLocal, otherOuterLocal, otherInnerLocal, sprite, light);
        }
    }

    @Nullable
    private EndpointConnection findConnectedOneSidedEndpoint(SurfRampEntity entity, Vec3 endpointWorld) {
        if (entity == null || entity.level() == null || endpointWorld == null) {
            return null;
        }
        if (entity.isTwoSided()) {
            return null;
        }

        AABB searchBox = new AABB(
                endpointWorld.x - SEAM_SEARCH_RADIUS,
                endpointWorld.y - SEAM_SEARCH_RADIUS,
                endpointWorld.z - SEAM_SEARCH_RADIUS,
                endpointWorld.x + SEAM_SEARCH_RADIUS,
                endpointWorld.y + SEAM_SEARCH_RADIUS,
                endpointWorld.z + SEAM_SEARCH_RADIUS
        );
        List<SurfRampEntity> nearby = SurfRampEntity.collectNearbyRamps(entity.level(), searchBox, 0.0D);
        if (nearby.isEmpty()) {
            return null;
        }

        EndpointConnection best = null;
        double bestDistanceSquared = Double.MAX_VALUE;
        double maxDistSq = SEAM_ENDPOINT_MATCH_EPSILON * SEAM_ENDPOINT_MATCH_EPSILON;

        for (SurfRampEntity other : nearby) {
            if (other == entity || !other.isAlive() || other.isRemoved() || other.isTwoSided()) {
                continue;
            }
            Vec3 otherStart = other.getStart();
            double startDistSq = otherStart.distanceToSqr(endpointWorld);
            if (startDistSq <= maxDistSq && startDistSq < bestDistanceSquared) {
                bestDistanceSquared = startDistSq;
                best = new EndpointConnection(other, true);
            }

            Vec3 otherEnd = other.getEnd();
            double endDistSq = otherEnd.distanceToSqr(endpointWorld);
            if (endDistSq <= maxDistSq && endDistSq < bestDistanceSquared) {
                bestDistanceSquared = endDistSq;
                best = new EndpointConnection(other, false);
            }
        }

        return best;
    }

    private record EndpointConnection(SurfRampEntity other, boolean otherAtStart) {}

    private record EndpointSlice(Vec3 worldCenter, Vec3 topLocal, Vec3 innerLocal, Vec3 outerLocal) {}

    private boolean shouldResolveSeamConnection(Vec3 cameraPos, Vec3 endpointWorld) {
        if (cameraPos == null || endpointWorld == null) {
            return false;
        }
        return cameraPos.distanceToSqr(endpointWorld) <= SEAM_BRIDGE_DETAIL_DISTANCE_SQ;
    }

    @Nullable
    private Vec3 getCameraPos() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.gameRenderer == null || client.gameRenderer.getMainCamera() == null) {
            return null;
        }
        return client.gameRenderer.getMainCamera().position();
    }

    private boolean shouldRenderUnderside(SurfRampEntity entity, @Nullable Vec3 cameraPos) {
        if (entity == null || cameraPos == null) {
            return true;
        }
        AABB bounds = entity.getBoundingBox();
        double approxBaseY = bounds.minY + 0.65D;
        return cameraPos.y <= approxBaseY + 0.45D;
    }

    private void renderTwoSidedSolid(
            SurfRampEntity entity,
            Vec3 entityPos,
            int segments,
            PoseStack matrixStack,
            VertexConsumer consumer,
            TextureAtlasSprite sprite,
            int light,
            boolean renderUnderside
    ) {
        Vec3 firstRidge = null;
        Vec3 firstLeftBase = null;
        Vec3 firstRightBase = null;

        Vec3 previousRidge = null;
        Vec3 previousLeftBase = null;
        Vec3 previousRightBase = null;
        Vec3 previousCenter = null;
        double accumulatedU = 0.0D;

        for (int i = 0; i <= segments; i++) {
            double t = (double) i / (double) segments;
            Vec3 center = entity.sampleCenterlineCached(t);
            Vec3 left = entity.sampleLeftCached(t);
            double baseY = entity.sampleBaseYCached(t);
            double topY = baseY + entity.getDrop();

            Vec3 ridge = new Vec3(center.x, topY, center.z).subtract(entityPos);
            Vec3 leftBase = new Vec3(center.x + left.x * entity.getRampWidth(), baseY, center.z + left.z * entity.getRampWidth()).subtract(entityPos);
            Vec3 rightBase = new Vec3(center.x - left.x * entity.getRampWidth(), baseY, center.z - left.z * entity.getRampWidth()).subtract(entityPos);

            if (firstRidge == null) {
                firstRidge = ridge;
                firstLeftBase = leftBase;
                firstRightBase = rightBase;
            }

            if (previousRidge != null) {
                double u0 = accumulatedU;
                accumulatedU += center.distanceTo(previousCenter) * UV_SCALE;
                double u1 = accumulatedU;

                drawTexturedQuad(entity, consumer, matrixStack, previousRidge, ridge, leftBase, previousLeftBase, sprite, light, u0, u1, 0.0F, 1.0F);
                drawTexturedQuad(entity, consumer, matrixStack, previousRightBase, rightBase, ridge, previousRidge, sprite, light, u0, u1, 0.0F, 1.0F);
                if (renderUnderside) {
                    drawTexturedQuad(entity, consumer, matrixStack, previousLeftBase, leftBase, rightBase, previousRightBase, sprite, light, u0, u1, 0.0F, 1.0F);
                }
            }

            previousRidge = ridge;
            previousLeftBase = leftBase;
            previousRightBase = rightBase;
            previousCenter = center;
        }

        if (firstRidge != null && previousRidge != null) {
            drawDoubleSidedTexturedTriangle(entity, consumer, matrixStack, firstRidge, firstRightBase, firstLeftBase, sprite, light);
            drawDoubleSidedTexturedTriangle(entity, consumer, matrixStack, previousRidge, previousLeftBase, previousRightBase, sprite, light);
        }
    }

    private void renderWireframe(
            SurfRampEntity entity,
            Vec3 entityPos,
            int segments,
            PoseStack matrixStack,
            VertexConsumer wireConsumer,
            @Nullable VertexConsumer fillConsumer,
            int light
    ) {
        int wireRgb = entity.getWireframeColorRgb();
        int wireRed = (wireRgb >> 16) & 0xFF;
        int wireGreen = (wireRgb >> 8) & 0xFF;
        int wireBlue = wireRgb & 0xFF;

        boolean fillEnabled = entity.isWireframeFillEnabled() && fillConsumer != null;
        int fillRed = 0;
        int fillGreen = 0;
        int fillBlue = 0;
        int fillAlpha = 0;
        if (fillEnabled) {
            int fillRgb = entity.getWireframeFillColorRgb();
            fillRed = (fillRgb >> 16) & 0xFF;
            fillGreen = (fillRgb >> 8) & 0xFF;
            fillBlue = fillRgb & 0xFF;
            fillAlpha = entity.getWireframeFillAlpha();
        }

        Matrix4f positionMatrix = matrixStack.last().pose();

        if (entity.isTwoSided()) {
            this.renderTwoSidedWireframe(
                    entity,
                    entityPos,
                    segments,
                    matrixStack,
                    positionMatrix,
                    wireConsumer,
                    fillConsumer,
                    fillEnabled,
                    wireRed,
                    wireGreen,
                    wireBlue,
                    fillRed,
                    fillGreen,
                    fillBlue,
                    fillAlpha,
                    light
            );
        } else {
            this.renderOneSidedWireframe(
                    entity,
                    entityPos,
                    segments,
                    matrixStack,
                    positionMatrix,
                    wireConsumer,
                    fillConsumer,
                    fillEnabled,
                    wireRed,
                    wireGreen,
                    wireBlue,
                    fillRed,
                    fillGreen,
                    fillBlue,
                    fillAlpha,
                    light
            );
        }
    }

    private void renderOneSidedWireframe(
            SurfRampEntity entity,
            Vec3 entityPos,
            int segments,
            PoseStack matrixStack,
            Matrix4f positionMatrix,
            VertexConsumer wireConsumer,
            @Nullable VertexConsumer fillConsumer,
            boolean fillEnabled,
            int wireRed,
            int wireGreen,
            int wireBlue,
            int fillRed,
            int fillGreen,
            int fillBlue,
            int fillAlpha,
            int light
    ) {
        Vec3 firstTop = null;
        Vec3 firstOuter = null;
        Vec3 firstInner = null;

        Vec3 previousTop = null;
        Vec3 previousOuter = null;
        Vec3 previousInner = null;
        Vec3[] previousSurfaceBands = new Vec3[WIREFRAME_SURFACE_LINE_FRACTIONS.length];

        for (int i = 0; i <= segments; i++) {
            double t = (double) i / (double) segments;
            Vec3 center = entity.sampleCenterlineCached(t);
            Vec3 left = entity.sampleLeftCached(t);
            double baseY = entity.sampleBaseYCached(t);
            double topY = baseY + entity.getDrop();

            Vec3 top = new Vec3(center.x, topY, center.z).subtract(entityPos);
            Vec3 outer = new Vec3(
                    center.x + left.x * entity.getRampWidth() * entity.getSideSign(),
                    baseY,
                    center.z + left.z * entity.getRampWidth() * entity.getSideSign()
            ).subtract(entityPos);
            Vec3 inner = new Vec3(center.x, baseY, center.z).subtract(entityPos);

            if (firstTop == null) {
                firstTop = top;
                firstOuter = outer;
                firstInner = inner;
            }

            if (previousTop != null) {
                this.drawWireLine(wireConsumer, positionMatrix, previousTop, top, wireRed, wireGreen, wireBlue);
                this.drawWireLine(wireConsumer, positionMatrix, previousOuter, outer, wireRed, wireGreen, wireBlue);
                this.drawWireLine(wireConsumer, positionMatrix, previousInner, inner, wireRed, wireGreen, wireBlue);

                if (fillEnabled && fillConsumer != null) {
                    this.drawFilledQuadDoubleSided(entity, fillConsumer, matrixStack, previousTop, top, outer, previousOuter, fillRed, fillGreen, fillBlue, fillAlpha, light);
                    this.drawFilledQuadDoubleSided(entity, fillConsumer, matrixStack, previousInner, previousTop, top, inner, fillRed, fillGreen, fillBlue, fillAlpha, light);
                    this.drawFilledQuadDoubleSided(entity, fillConsumer, matrixStack, previousOuter, outer, inner, previousInner, fillRed, fillGreen, fillBlue, fillAlpha, light);
                }
            }

            Vec3[] currentSurfaceBands = new Vec3[WIREFRAME_SURFACE_LINE_FRACTIONS.length];
            for (int bandIndex = 0; bandIndex < WIREFRAME_SURFACE_LINE_FRACTIONS.length; bandIndex++) {
                Vec3 surfacePoint = top.lerp(outer, WIREFRAME_SURFACE_LINE_FRACTIONS[bandIndex]);
                currentSurfaceBands[bandIndex] = surfacePoint;
                if (previousSurfaceBands[bandIndex] != null) {
                    this.drawWireLine(wireConsumer, positionMatrix, previousSurfaceBands[bandIndex], surfacePoint, wireRed, wireGreen, wireBlue);
                }
            }

            previousSurfaceBands = currentSurfaceBands;
            previousTop = top;
            previousOuter = outer;
            previousInner = inner;
        }

        if (firstTop == null || previousTop == null || firstOuter == null || previousOuter == null || firstInner == null || previousInner == null) {
            return;
        }

        this.drawOneSidedWireRibs(entity, entityPos, segments, positionMatrix, wireConsumer, wireRed, wireGreen, wireBlue);

        if (!entity.isLinkedStart()) {
            if (fillEnabled && fillConsumer != null) {
                this.drawFilledTriangleDoubleSided(entity, fillConsumer, matrixStack, firstTop, firstOuter, firstInner, fillRed, fillGreen, fillBlue, fillAlpha, light);
            }
            this.drawWireLine(wireConsumer, positionMatrix, firstTop, firstOuter, wireRed, wireGreen, wireBlue);
            this.drawWireLine(wireConsumer, positionMatrix, firstTop, firstInner, wireRed, wireGreen, wireBlue);
            this.drawWireLine(wireConsumer, positionMatrix, firstOuter, firstInner, wireRed, wireGreen, wireBlue);
        }

        if (!entity.isLinkedEnd()) {
            if (fillEnabled && fillConsumer != null) {
                this.drawFilledTriangleDoubleSided(entity, fillConsumer, matrixStack, previousTop, previousInner, previousOuter, fillRed, fillGreen, fillBlue, fillAlpha, light);
            }
            this.drawWireLine(wireConsumer, positionMatrix, previousTop, previousOuter, wireRed, wireGreen, wireBlue);
            this.drawWireLine(wireConsumer, positionMatrix, previousTop, previousInner, wireRed, wireGreen, wireBlue);
            this.drawWireLine(wireConsumer, positionMatrix, previousOuter, previousInner, wireRed, wireGreen, wireBlue);
        }
    }

    private void renderTwoSidedWireframe(
            SurfRampEntity entity,
            Vec3 entityPos,
            int segments,
            PoseStack matrixStack,
            Matrix4f positionMatrix,
            VertexConsumer wireConsumer,
            @Nullable VertexConsumer fillConsumer,
            boolean fillEnabled,
            int wireRed,
            int wireGreen,
            int wireBlue,
            int fillRed,
            int fillGreen,
            int fillBlue,
            int fillAlpha,
            int light
    ) {
        Vec3 firstRidge = null;
        Vec3 firstLeft = null;
        Vec3 firstRight = null;

        Vec3 previousRidge = null;
        Vec3 previousLeft = null;
        Vec3 previousRight = null;
        Vec3[] previousLeftSurfaceBands = new Vec3[WIREFRAME_SURFACE_LINE_FRACTIONS.length];
        Vec3[] previousRightSurfaceBands = new Vec3[WIREFRAME_SURFACE_LINE_FRACTIONS.length];

        for (int i = 0; i <= segments; i++) {
            double t = (double) i / (double) segments;
            Vec3 center = entity.sampleCenterlineCached(t);
            Vec3 left = entity.sampleLeftCached(t);
            double baseY = entity.sampleBaseYCached(t);
            double topY = baseY + entity.getDrop();

            Vec3 ridge = new Vec3(center.x, topY, center.z).subtract(entityPos);
            Vec3 leftBase = new Vec3(center.x + left.x * entity.getRampWidth(), baseY, center.z + left.z * entity.getRampWidth()).subtract(entityPos);
            Vec3 rightBase = new Vec3(center.x - left.x * entity.getRampWidth(), baseY, center.z - left.z * entity.getRampWidth()).subtract(entityPos);

            if (firstRidge == null) {
                firstRidge = ridge;
                firstLeft = leftBase;
                firstRight = rightBase;
            }

            if (previousRidge != null) {
                this.drawWireLine(wireConsumer, positionMatrix, previousRidge, ridge, wireRed, wireGreen, wireBlue);
                this.drawWireLine(wireConsumer, positionMatrix, previousLeft, leftBase, wireRed, wireGreen, wireBlue);
                this.drawWireLine(wireConsumer, positionMatrix, previousRight, rightBase, wireRed, wireGreen, wireBlue);

                if (fillEnabled && fillConsumer != null) {
                    this.drawFilledQuadDoubleSided(entity, fillConsumer, matrixStack, previousRidge, ridge, leftBase, previousLeft, fillRed, fillGreen, fillBlue, fillAlpha, light);
                    this.drawFilledQuadDoubleSided(entity, fillConsumer, matrixStack, previousRight, rightBase, ridge, previousRidge, fillRed, fillGreen, fillBlue, fillAlpha, light);
                    this.drawFilledQuadDoubleSided(entity, fillConsumer, matrixStack, previousLeft, leftBase, rightBase, previousRight, fillRed, fillGreen, fillBlue, fillAlpha, light);
                }
            }

            Vec3[] currentLeftSurfaceBands = new Vec3[WIREFRAME_SURFACE_LINE_FRACTIONS.length];
            Vec3[] currentRightSurfaceBands = new Vec3[WIREFRAME_SURFACE_LINE_FRACTIONS.length];
            for (int bandIndex = 0; bandIndex < WIREFRAME_SURFACE_LINE_FRACTIONS.length; bandIndex++) {
                double fraction = WIREFRAME_SURFACE_LINE_FRACTIONS[bandIndex];
                Vec3 leftSurface = ridge.lerp(leftBase, fraction);
                Vec3 rightSurface = ridge.lerp(rightBase, fraction);
                currentLeftSurfaceBands[bandIndex] = leftSurface;
                currentRightSurfaceBands[bandIndex] = rightSurface;
                if (previousLeftSurfaceBands[bandIndex] != null) {
                    this.drawWireLine(wireConsumer, positionMatrix, previousLeftSurfaceBands[bandIndex], leftSurface, wireRed, wireGreen, wireBlue);
                }
                if (previousRightSurfaceBands[bandIndex] != null) {
                    this.drawWireLine(wireConsumer, positionMatrix, previousRightSurfaceBands[bandIndex], rightSurface, wireRed, wireGreen, wireBlue);
                }
            }

            previousLeftSurfaceBands = currentLeftSurfaceBands;
            previousRightSurfaceBands = currentRightSurfaceBands;
            previousRidge = ridge;
            previousLeft = leftBase;
            previousRight = rightBase;
        }

        if (firstRidge == null || previousRidge == null || firstLeft == null || firstRight == null || previousLeft == null || previousRight == null) {
            return;
        }

        this.drawTwoSidedWireRibs(entity, entityPos, segments, positionMatrix, wireConsumer, wireRed, wireGreen, wireBlue);

        if (!entity.isLinkedStart()) {
            if (fillEnabled && fillConsumer != null) {
                this.drawFilledTriangleDoubleSided(entity, fillConsumer, matrixStack, firstRidge, firstRight, firstLeft, fillRed, fillGreen, fillBlue, fillAlpha, light);
            }
            this.drawWireLine(wireConsumer, positionMatrix, firstRidge, firstLeft, wireRed, wireGreen, wireBlue);
            this.drawWireLine(wireConsumer, positionMatrix, firstRidge, firstRight, wireRed, wireGreen, wireBlue);
            this.drawWireLine(wireConsumer, positionMatrix, firstLeft, firstRight, wireRed, wireGreen, wireBlue);
        }

        if (!entity.isLinkedEnd()) {
            if (fillEnabled && fillConsumer != null) {
                this.drawFilledTriangleDoubleSided(entity, fillConsumer, matrixStack, previousRidge, previousLeft, previousRight, fillRed, fillGreen, fillBlue, fillAlpha, light);
            }
            this.drawWireLine(wireConsumer, positionMatrix, previousRidge, previousLeft, wireRed, wireGreen, wireBlue);
            this.drawWireLine(wireConsumer, positionMatrix, previousRidge, previousRight, wireRed, wireGreen, wireBlue);
            this.drawWireLine(wireConsumer, positionMatrix, previousLeft, previousRight, wireRed, wireGreen, wireBlue);
        }
    }

    private void drawOneSidedWireRibs(
            SurfRampEntity entity,
            Vec3 entityPos,
            int segments,
            Matrix4f positionMatrix,
            VertexConsumer wireConsumer,
            int wireRed,
            int wireGreen,
            int wireBlue
    ) {
        ArcLengthTable arcLengthTable = this.buildArcLengthTable(entity, segments);
        int ribCount = this.computeRibCount(arcLengthTable.totalLength());
        int startRib = 0;
        int endRib = ribCount;

        for (int ribIndex = startRib; ribIndex <= endRib; ribIndex++) {
            double targetDistance = arcLengthTable.totalLength() * ((double) ribIndex / (double) ribCount);
            double t = arcLengthTable.tAtDistance(targetDistance);
            Vec3 center = entity.sampleCenterlineCached(t);
            Vec3 left = entity.sampleLeftCached(t);
            double baseY = entity.sampleBaseYCached(t);
            double topY = baseY + entity.getDrop();

            Vec3 top = new Vec3(center.x, topY, center.z).subtract(entityPos);
            Vec3 outer = new Vec3(
                    center.x + left.x * entity.getRampWidth() * entity.getSideSign(),
                    baseY,
                    center.z + left.z * entity.getRampWidth() * entity.getSideSign()
            ).subtract(entityPos);
            Vec3 inner = new Vec3(center.x, baseY, center.z).subtract(entityPos);

            this.drawWireLine(wireConsumer, positionMatrix, top, outer, wireRed, wireGreen, wireBlue);
            this.drawWireLine(wireConsumer, positionMatrix, top, inner, wireRed, wireGreen, wireBlue);
            this.drawWireLine(wireConsumer, positionMatrix, outer, inner, wireRed, wireGreen, wireBlue);
        }
    }

    private void drawTwoSidedWireRibs(
            SurfRampEntity entity,
            Vec3 entityPos,
            int segments,
            Matrix4f positionMatrix,
            VertexConsumer wireConsumer,
            int wireRed,
            int wireGreen,
            int wireBlue
    ) {
        ArcLengthTable arcLengthTable = this.buildArcLengthTable(entity, segments);
        int ribCount = this.computeRibCount(arcLengthTable.totalLength());
        int startRib = 0;
        int endRib = ribCount;

        for (int ribIndex = startRib; ribIndex <= endRib; ribIndex++) {
            double targetDistance = arcLengthTable.totalLength() * ((double) ribIndex / (double) ribCount);
            double t = arcLengthTable.tAtDistance(targetDistance);
            Vec3 center = entity.sampleCenterlineCached(t);
            Vec3 left = entity.sampleLeftCached(t);
            double baseY = entity.sampleBaseYCached(t);
            double topY = baseY + entity.getDrop();

            Vec3 ridge = new Vec3(center.x, topY, center.z).subtract(entityPos);
            Vec3 leftBase = new Vec3(
                    center.x + left.x * entity.getRampWidth(),
                    baseY,
                    center.z + left.z * entity.getRampWidth()
            ).subtract(entityPos);
            Vec3 rightBase = new Vec3(
                    center.x - left.x * entity.getRampWidth(),
                    baseY,
                    center.z - left.z * entity.getRampWidth()
            ).subtract(entityPos);

            this.drawWireLine(wireConsumer, positionMatrix, ridge, leftBase, wireRed, wireGreen, wireBlue);
            this.drawWireLine(wireConsumer, positionMatrix, ridge, rightBase, wireRed, wireGreen, wireBlue);
            this.drawWireLine(wireConsumer, positionMatrix, leftBase, rightBase, wireRed, wireGreen, wireBlue);
        }
    }

    private ArcLengthTable buildArcLengthTable(SurfRampEntity entity, int segments) {
        int sampleCount = Math.max(72, segments * 6);
        double[] distances = new double[sampleCount + 1];
        double[] tSamples = new double[sampleCount + 1];

        Vec3 previousCenter = entity.sampleCenterlineCached(0.0D);
        double totalLength = 0.0D;
        distances[0] = 0.0D;
        tSamples[0] = 0.0D;

        for (int i = 1; i <= sampleCount; i++) {
            double t = (double) i / (double) sampleCount;
            Vec3 center = entity.sampleCenterlineCached(t);
            totalLength += center.distanceTo(previousCenter);
            distances[i] = totalLength;
            tSamples[i] = t;
            previousCenter = center;
        }

        return new ArcLengthTable(distances, tSamples, totalLength);
    }

    private int computeRibCount(double totalLength) {
        return this.computeRibCountForSpacing(totalLength, WIREFRAME_RIB_SPACING, 256);
    }

    private int computeRibCountForSpacing(double totalLength, double spacing, int maxCount) {
        if (totalLength <= 1.0E-6D) {
            return 1;
        }
        if (spacing <= 1.0E-4D) {
            spacing = WIREFRAME_RIB_SPACING;
        }
        int safeMax = Math.max(1, maxCount);
        return Math.max(1, Math.min(safeMax, (int) Math.ceil(totalLength / spacing)));
    }

    private void drawWireLine(
            VertexConsumer wireConsumer,
            Matrix4f positionMatrix,
            Vec3 a,
            Vec3 b,
            int red,
            int green,
            int blue
    ) {
        wireConsumer.addVertex(positionMatrix, (float) a.x, (float) a.y, (float) a.z)
                .setColor(red, green, blue, WIREFRAME_LINE_ALPHA)
                .setNormal(1.0F, 1.0F, 1.0F)
                .setLineWidth(WIREFRAME_LINE_WIDTH);
        wireConsumer.addVertex(positionMatrix, (float) b.x, (float) b.y, (float) b.z)
                .setColor(red, green, blue, WIREFRAME_LINE_ALPHA)
                .setNormal(1.0F, 1.0F, 1.0F)
                .setLineWidth(WIREFRAME_LINE_WIDTH);
    }

    private void drawCollisionLine(
            VertexConsumer provider,
            PoseStack matrixStack,
            Vec3 a,
            Vec3 b
    ) {
        RenderUtil.drawLine(
                provider,
                matrixStack.last(),
                new Vector3f((float) a.x, (float) a.y, (float) a.z),
                new Vector3f((float) b.x, (float) b.y, (float) b.z),
                COLLISION_DEBUG_LINE_WIDTH,
                COLLISION_DEBUG_LINE_ALPHA,
                COLLISION_DEBUG_COLOR_R,
                COLLISION_DEBUG_COLOR_G,
                COLLISION_DEBUG_COLOR_B
        );
    }

    private void drawFilledQuadDoubleSided(
            SurfRampEntity entity,
            VertexConsumer consumer,
            PoseStack matrices,
            Vec3 a,
            Vec3 b,
            Vec3 c,
            Vec3 d,
            int red,
            int green,
            int blue,
            int alpha,
            int light
    ) {
        this.drawFilledQuad(entity, consumer, matrices, a, b, c, d, red, green, blue, alpha, light);
        this.drawFilledQuad(entity, consumer, matrices, a, d, c, b, red, green, blue, alpha, light);
    }

    private void drawFilledQuad(
            SurfRampEntity entity,
            VertexConsumer consumer,
            PoseStack matrices,
            Vec3 a,
            Vec3 b,
            Vec3 c,
            Vec3 d,
            int red,
            int green,
            int blue,
            int alpha,
            int light
    ) {
        Matrix4f positionMatrix = matrices.last().pose();
        Vec3 normal = b.subtract(a).cross(d.subtract(a));
        if (normal.lengthSqr() < 1.0E-8D) {
            normal = new Vec3(0.0D, 1.0D, 0.0D);
        } else {
            normal = normal.normalize();
        }

        int lightA = light;
        int lightB = light;
        int lightC = light;
        int lightD = light;

        this.putFilledVertex(consumer, positionMatrix, a, 0.0F, 0.0F, red, green, blue, alpha, lightA, normal);
        this.putFilledVertex(consumer, positionMatrix, b, 1.0F, 0.0F, red, green, blue, alpha, lightB, normal);
        this.putFilledVertex(consumer, positionMatrix, c, 1.0F, 1.0F, red, green, blue, alpha, lightC, normal);
        this.putFilledVertex(consumer, positionMatrix, d, 0.0F, 1.0F, red, green, blue, alpha, lightD, normal);
    }

    private void drawFilledTriangleDoubleSided(
            SurfRampEntity entity,
            VertexConsumer consumer,
            PoseStack matrices,
            Vec3 a,
            Vec3 b,
            Vec3 c,
            int red,
            int green,
            int blue,
            int alpha,
            int light
    ) {
        this.drawFilledTriangle(entity, consumer, matrices, a, b, c, red, green, blue, alpha, light);
        this.drawFilledTriangle(entity, consumer, matrices, a, c, b, red, green, blue, alpha, light);
    }

    private void drawFilledTriangle(
            SurfRampEntity entity,
            VertexConsumer consumer,
            PoseStack matrices,
            Vec3 a,
            Vec3 b,
            Vec3 c,
            int red,
            int green,
            int blue,
            int alpha,
            int light
    ) {
        Matrix4f positionMatrix = matrices.last().pose();
        Vec3 normal = b.subtract(a).cross(c.subtract(a));
        if (normal.lengthSqr() < 1.0E-8D) {
            normal = new Vec3(0.0D, 1.0D, 0.0D);
        } else {
            normal = normal.normalize();
        }

        int lightA = light;
        int lightB = light;
        int lightC = light;

        this.putFilledVertex(consumer, positionMatrix, a, 0.0F, 0.0F, red, green, blue, alpha, lightA, normal);
        this.putFilledVertex(consumer, positionMatrix, b, 1.0F, 0.0F, red, green, blue, alpha, lightB, normal);
        this.putFilledVertex(consumer, positionMatrix, c, 0.5F, 1.0F, red, green, blue, alpha, lightC, normal);
        this.putFilledVertex(consumer, positionMatrix, c, 0.5F, 1.0F, red, green, blue, alpha, lightC, normal);
    }

    private void putFilledVertex(
            VertexConsumer consumer,
            Matrix4f positionMatrix,
            Vec3 pos,
            float u,
            float v,
            int red,
            int green,
            int blue,
            int alpha,
            int light,
            Vec3 normal
    ) {
        consumer.addVertex(positionMatrix, (float) pos.x, (float) pos.y, (float) pos.z)
                .setColor(red, green, blue, alpha)
                .setUv(clamp01(u), clamp01(v))
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal((float) normal.x, (float) normal.y, (float) normal.z);
    }

    private void renderCollisionPolygons(
            SurfRampEntity entity,
            PoseStack matrixStack,
            VertexConsumer provider,
            Vec3 entityPos
    ) {
        int samples = entity.isCurved() ? 60 : 36;
        if (entity.isTwoSided()) {
            this.renderTwoSidedCollisionDebug(entity, entityPos, samples, matrixStack, provider);
        } else {
            this.renderOneSidedCollisionDebug(entity, entityPos, samples, matrixStack, provider);
        }
    }

    private void renderOneSidedCollisionDebug(
            SurfRampEntity entity,
            Vec3 entityPos,
            int samples,
            PoseStack matrixStack,
            VertexConsumer provider
    ) {
        ArcLengthTable arcLengthTable = this.buildArcLengthTable(entity, samples);
        Vec3 previousTop = null;
        Vec3 previousOuter = null;
        Vec3 previousInner = null;
        Vec3[] previousSurfaceBands = new Vec3[COLLISION_DEBUG_SURFACE_LINE_FRACTIONS.length];

        for (int i = 0; i <= samples; i++) {
            double targetDistance = arcLengthTable.totalLength() * ((double) i / (double) samples);
            double t = arcLengthTable.tAtDistance(targetDistance);
            Vec3 center = entity.sampleCenterlineCached(t);
            Vec3 left = entity.sampleLeftCached(t);
            double baseY = entity.sampleBaseYCached(t);
            double topY = baseY + entity.getDrop();

            Vec3 top = new Vec3(center.x, topY, center.z).subtract(entityPos);
            Vec3 outer = new Vec3(
                    center.x + left.x * entity.getRampWidth() * entity.getSideSign(),
                    baseY,
                    center.z + left.z * entity.getRampWidth() * entity.getSideSign()
            ).subtract(entityPos);
            Vec3 inner = new Vec3(center.x, baseY, center.z).subtract(entityPos);

            if (previousTop != null) {
                this.drawCollisionLine(provider, matrixStack, previousTop, top);
                this.drawCollisionLine(provider, matrixStack, previousOuter, outer);
                this.drawCollisionLine(provider, matrixStack, previousInner, inner);
            }

            Vec3[] currentSurfaceBands = new Vec3[COLLISION_DEBUG_SURFACE_LINE_FRACTIONS.length];
            for (int bandIndex = 0; bandIndex < COLLISION_DEBUG_SURFACE_LINE_FRACTIONS.length; bandIndex++) {
                Vec3 surfacePoint = top.lerp(outer, COLLISION_DEBUG_SURFACE_LINE_FRACTIONS[bandIndex]);
                currentSurfaceBands[bandIndex] = surfacePoint;
                if (previousSurfaceBands[bandIndex] != null) {
                    this.drawCollisionLine(provider, matrixStack, previousSurfaceBands[bandIndex], surfacePoint);
                }
            }

            previousSurfaceBands = currentSurfaceBands;
            previousTop = top;
            previousOuter = outer;
            previousInner = inner;
        }

        int ribCount = this.computeRibCountForSpacing(arcLengthTable.totalLength(), COLLISION_DEBUG_RIB_SPACING, 192);
        for (int ribIndex = 0; ribIndex <= ribCount; ribIndex++) {
            double targetDistance = arcLengthTable.totalLength() * ((double) ribIndex / (double) ribCount);
            double t = arcLengthTable.tAtDistance(targetDistance);
            Vec3 center = entity.sampleCenterlineCached(t);
            Vec3 left = entity.sampleLeftCached(t);
            double baseY = entity.sampleBaseYCached(t);
            double topY = baseY + entity.getDrop();

            Vec3 top = new Vec3(center.x, topY, center.z).subtract(entityPos);
            Vec3 outer = new Vec3(
                    center.x + left.x * entity.getRampWidth() * entity.getSideSign(),
                    baseY,
                    center.z + left.z * entity.getRampWidth() * entity.getSideSign()
            ).subtract(entityPos);
            Vec3 inner = new Vec3(center.x, baseY, center.z).subtract(entityPos);

            this.drawCollisionLine(provider, matrixStack, top, outer);
            this.drawCollisionLine(provider, matrixStack, top, inner);
            this.drawCollisionLine(provider, matrixStack, outer, inner);
        }
    }

    private void renderTwoSidedCollisionDebug(
            SurfRampEntity entity,
            Vec3 entityPos,
            int samples,
            PoseStack matrixStack,
            VertexConsumer provider
    ) {
        ArcLengthTable arcLengthTable = this.buildArcLengthTable(entity, samples);
        Vec3 previousRidge = null;
        Vec3 previousLeft = null;
        Vec3 previousRight = null;
        Vec3[] previousLeftSurfaceBands = new Vec3[COLLISION_DEBUG_SURFACE_LINE_FRACTIONS.length];
        Vec3[] previousRightSurfaceBands = new Vec3[COLLISION_DEBUG_SURFACE_LINE_FRACTIONS.length];

        for (int i = 0; i <= samples; i++) {
            double targetDistance = arcLengthTable.totalLength() * ((double) i / (double) samples);
            double t = arcLengthTable.tAtDistance(targetDistance);
            Vec3 center = entity.sampleCenterlineCached(t);
            Vec3 left = entity.sampleLeftCached(t);
            double baseY = entity.sampleBaseYCached(t);
            double topY = baseY + entity.getDrop();

            Vec3 ridge = new Vec3(center.x, topY, center.z).subtract(entityPos);
            Vec3 leftBase = new Vec3(
                    center.x + left.x * entity.getRampWidth(),
                    baseY,
                    center.z + left.z * entity.getRampWidth()
            ).subtract(entityPos);
            Vec3 rightBase = new Vec3(
                    center.x - left.x * entity.getRampWidth(),
                    baseY,
                    center.z - left.z * entity.getRampWidth()
            ).subtract(entityPos);

            if (previousRidge != null) {
                this.drawCollisionLine(provider, matrixStack, previousRidge, ridge);
                this.drawCollisionLine(provider, matrixStack, previousLeft, leftBase);
                this.drawCollisionLine(provider, matrixStack, previousRight, rightBase);
            }

            Vec3[] currentLeftSurfaceBands = new Vec3[COLLISION_DEBUG_SURFACE_LINE_FRACTIONS.length];
            Vec3[] currentRightSurfaceBands = new Vec3[COLLISION_DEBUG_SURFACE_LINE_FRACTIONS.length];
            for (int bandIndex = 0; bandIndex < COLLISION_DEBUG_SURFACE_LINE_FRACTIONS.length; bandIndex++) {
                double fraction = COLLISION_DEBUG_SURFACE_LINE_FRACTIONS[bandIndex];
                Vec3 leftSurface = ridge.lerp(leftBase, fraction);
                Vec3 rightSurface = ridge.lerp(rightBase, fraction);
                currentLeftSurfaceBands[bandIndex] = leftSurface;
                currentRightSurfaceBands[bandIndex] = rightSurface;
                if (previousLeftSurfaceBands[bandIndex] != null) {
                    this.drawCollisionLine(provider, matrixStack, previousLeftSurfaceBands[bandIndex], leftSurface);
                }
                if (previousRightSurfaceBands[bandIndex] != null) {
                    this.drawCollisionLine(provider, matrixStack, previousRightSurfaceBands[bandIndex], rightSurface);
                }
            }

            previousLeftSurfaceBands = currentLeftSurfaceBands;
            previousRightSurfaceBands = currentRightSurfaceBands;
            previousRidge = ridge;
            previousLeft = leftBase;
            previousRight = rightBase;
        }

        int ribCount = this.computeRibCountForSpacing(arcLengthTable.totalLength(), COLLISION_DEBUG_RIB_SPACING, 192);
        for (int ribIndex = 0; ribIndex <= ribCount; ribIndex++) {
            double targetDistance = arcLengthTable.totalLength() * ((double) ribIndex / (double) ribCount);
            double t = arcLengthTable.tAtDistance(targetDistance);
            Vec3 center = entity.sampleCenterlineCached(t);
            Vec3 left = entity.sampleLeftCached(t);
            double baseY = entity.sampleBaseYCached(t);
            double topY = baseY + entity.getDrop();

            Vec3 ridge = new Vec3(center.x, topY, center.z).subtract(entityPos);
            Vec3 leftBase = new Vec3(
                    center.x + left.x * entity.getRampWidth(),
                    baseY,
                    center.z + left.z * entity.getRampWidth()
            ).subtract(entityPos);
            Vec3 rightBase = new Vec3(
                    center.x - left.x * entity.getRampWidth(),
                    baseY,
                    center.z - left.z * entity.getRampWidth()
            ).subtract(entityPos);

            this.drawCollisionLine(provider, matrixStack, ridge, leftBase);
            this.drawCollisionLine(provider, matrixStack, ridge, rightBase);
            this.drawCollisionLine(provider, matrixStack, leftBase, rightBase);
        }
    }

    private TextureAtlasSprite resolveRampSprite(SurfRampEntity entity) {
        Minecraft client = Minecraft.getInstance();
        BlockStateModel model = client.getModelManager().getBlockStateModelSet().get(entity.getTextureBlockState());
        TextureAtlasSprite sprite = model.particleMaterial().sprite();
        if (sprite != null) {
            return sprite;
        }
        return client.getModelManager().getBlockStateModelSet().get(Blocks.SMOOTH_STONE.defaultBlockState()).particleMaterial().sprite();
    }

    private static void drawTexturedQuad(
            SurfRampEntity entity,
            VertexConsumer consumer,
            PoseStack matrices,
            Vec3 a,
            Vec3 b,
            Vec3 c,
            Vec3 d,
            TextureAtlasSprite sprite,
            int light,
            double u0,
            double u1,
            float v0,
            float v1
    ) {
        if (u1 <= u0 + UV_SPLIT_EPSILON) {
            drawTexturedQuadSegment(entity, consumer, matrices, a, b, c, d, sprite, light, u0, u1, v0, v1);
            return;
        }
        double startTile = Math.floor(u0);
        if (u1 <= startTile + 1.0D + UV_SPLIT_EPSILON) {
            drawTexturedQuadSegment(entity, consumer, matrices, a, b, c, d, sprite, light, u0, u1, v0, v1);
            return;
        }

        Vec3 currentA = a;
        Vec3 currentB = b;
        Vec3 currentC = c;
        Vec3 currentD = d;
        double currentU0 = u0;
        double currentU1 = u1;

        while (currentU1 > currentU0 + UV_SPLIT_EPSILON) {
            double nextBoundary = Math.floor(currentU0) + 1.0D;
            if (currentU1 <= nextBoundary + UV_SPLIT_EPSILON) {
                drawTexturedQuadSegment(entity, consumer, matrices, currentA, currentB, currentC, currentD, sprite, light, currentU0, currentU1, v0, v1);
                break;
            }

            double t = (nextBoundary - currentU0) / (currentU1 - currentU0);
            Vec3 splitTop = lerp(currentA, currentB, t);
            Vec3 splitBottom = lerp(currentD, currentC, t);
            drawTexturedQuadSegment(entity, consumer, matrices, currentA, splitTop, splitBottom, currentD, sprite, light, currentU0, nextBoundary, v0, v1);

            currentA = splitTop;
            currentD = splitBottom;
            currentU0 = nextBoundary;
        }
    }

    private static void drawTexturedQuadSegment(
            SurfRampEntity entity,
            VertexConsumer consumer,
            PoseStack matrices,
            Vec3 a,
            Vec3 b,
            Vec3 c,
            Vec3 d,
            TextureAtlasSprite sprite,
            int light,
            double u0,
            double u1,
            float v0,
            float v1
    ) {
        double tileBase = Math.floor(u0);
        float localU0 = clamp01((float) (u0 - tileBase));
        float localU1 = clamp01((float) (u1 - tileBase));

        Matrix4f positionMatrix = matrices.last().pose();

        Vec3 normal = b.subtract(a).cross(d.subtract(a));
        if (normal.lengthSqr() < 1.0E-8D) {
            normal = new Vec3(0.0D, 1.0D, 0.0D);
        } else {
            normal = normal.normalize();
        }

        putFaceVertex(consumer, positionMatrix, a, localU0, v0, sprite, light, normal);
        putFaceVertex(consumer, positionMatrix, b, localU1, v0, sprite, light, normal);
        putFaceVertex(consumer, positionMatrix, c, localU1, v1, sprite, light, normal);
        putFaceVertex(consumer, positionMatrix, d, localU0, v1, sprite, light, normal);
    }

    private static void drawTexturedTriangle(
            SurfRampEntity entity,
            VertexConsumer consumer,
            PoseStack matrices,
            Vec3 a,
            Vec3 b,
            Vec3 c,
            TextureAtlasSprite sprite,
            int light
    ) {
        Matrix4f positionMatrix = matrices.last().pose();
        Vec3 normal = b.subtract(a).cross(c.subtract(a));
        if (normal.lengthSqr() < 1.0E-8D) {
            normal = new Vec3(0.0D, 1.0D, 0.0D);
        } else {
            normal = normal.normalize();
        }

        putFaceVertex(consumer, positionMatrix, a, 0.0F, 0.0F, sprite, light, normal);
        putFaceVertex(consumer, positionMatrix, b, 1.0F, 0.0F, sprite, light, normal);
        putFaceVertex(consumer, positionMatrix, c, 0.5F, 1.0F, sprite, light, normal);
        putFaceVertex(consumer, positionMatrix, c, 0.5F, 1.0F, sprite, light, normal);
    }

    private static void drawDoubleSidedTexturedTriangle(
            SurfRampEntity entity,
            VertexConsumer consumer,
            PoseStack matrices,
            Vec3 a,
            Vec3 b,
            Vec3 c,
            TextureAtlasSprite sprite,
            int light
    ) {
        drawTexturedTriangle(entity, consumer, matrices, a, b, c, sprite, light);
        drawTexturedTriangle(entity, consumer, matrices, a, c, b, sprite, light);
    }

    private static void putFaceVertex(
            VertexConsumer consumer,
            Matrix4f positionMatrix,
            Vec3 pos,
            float u,
            float v,
            TextureAtlasSprite sprite,
            int light,
            Vec3 normal
    ) {
        float wrappedU = clamp01(u);
        float wrappedV = clamp01(v);
        float pixelInsetU = HORIZONTAL_TEXTURE_INSET_PIXELS / 16.0F;
        if (pixelInsetU > 0.49F) {
            pixelInsetU = 0.49F;
        }
        float uRange = 1.0F - pixelInsetU * 2.0F;
        float croppedU = pixelInsetU + wrappedU * uRange;

        float atlasU = sprite.getU0() + (sprite.getU1() - sprite.getU0()) * croppedU;
        float atlasV = sprite.getV0() + (sprite.getV1() - sprite.getV0()) * wrappedV;
        consumer.addVertex(positionMatrix, (float) pos.x, (float) pos.y, (float) pos.z)
                .setColor(255, 255, 255, 255)
                .setUv(atlasU, atlasV)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal((float) normal.x, (float) normal.y, (float) normal.z);
    }

    private static float clamp01(float value) {
        if (value < 0.0F) {
            return 0.0F;
        }
        if (value > 1.0F) {
            return 1.0F;
        }
        return value;
    }

    private static Vec3 lerp(Vec3 from, Vec3 to, double t) {
        return from.add(to.subtract(from).scale(t));
    }

    private record ArcLengthTable(double[] distances, double[] tSamples, double totalLength) {
        private double tAtDistance(double distance) {
            if (this.totalLength <= 1.0E-6D || this.tSamples.length <= 1 || this.distances.length <= 1) {
                return 0.0D;
            }

            if (distance <= 0.0D) {
                return 0.0D;
            }
            if (distance >= this.totalLength) {
                return 1.0D;
            }

            int index = Arrays.binarySearch(this.distances, distance);
            if (index >= 0) {
                return this.tSamples[index];
            }

            int insertion = -index - 1;
            int lowerIndex = Math.max(0, insertion - 1);
            int upperIndex = Math.min(this.distances.length - 1, insertion);
            if (upperIndex <= lowerIndex) {
                return this.tSamples[lowerIndex];
            }

            double lowerDistance = this.distances[lowerIndex];
            double upperDistance = this.distances[upperIndex];
            if (upperDistance <= lowerDistance + 1.0E-8D) {
                return this.tSamples[upperIndex];
            }

            double alpha = (distance - lowerDistance) / (upperDistance - lowerDistance);
            return this.tSamples[lowerIndex] + (this.tSamples[upperIndex] - this.tSamples[lowerIndex]) * alpha;
        }
    }

    @Override
    public Identifier getTextureLocation(SurfRampEntityRenderState state) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}

