package net.nerdorg.minehop.entity.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.block.Blocks;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.entity.custom.SurfRampEntity;
import net.nerdorg.minehop.render.ModRenderLayer;
import net.nerdorg.minehop.render.RenderUtil;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

public class SurfRampRenderer extends MobEntityRenderer<SurfRampEntity, SurfRampEntityRenderState, SurfRampModel> {
    private static final double UV_SCALE = 0.5D;
    private static final float HORIZONTAL_TEXTURE_INSET_PIXELS = 1.0F;
    private static final double UV_SPLIT_EPSILON = 1.0E-7D;
    private static final double SEAM_ENDPOINT_MATCH_EPSILON = 0.14D;
    private static final double SEAM_OUTER_JOIN_MIN_DISTANCE = 0.03D;
    private static final double SEAM_SEARCH_RADIUS = 0.55D;
    private static final double SEAM_BRIDGE_DETAIL_DISTANCE_SQ = 32.0D * 32.0D;
    private static final Identifier WIREFRAME_FILL_TEXTURE = Identifier.of("minehop", "textures/misc/white.png");
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

    public SurfRampRenderer(EntityRendererFactory.Context context) {
        super(context, new SurfRampModel(context.getPart(ModModelLayers.SURF_RAMP_ENTITY)), 0.0F);
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
    public void updateRenderState(SurfRampEntity entity, SurfRampEntityRenderState state, float tickDelta) {
        state.surfRampEntity = entity;
    }

    @Override
    public void render(SurfRampEntityRenderState renderState, MatrixStack matrixStack, VertexConsumerProvider vertexConsumerProvider, int light) {
        SurfRampEntity entity = renderState.surfRampEntity;
        if (entity == null) {
            return;
        }

        Vec3d entityPos = entity.getPos();
        Vec3d cameraPos = this.getCameraPos();
        boolean renderUnderside = this.shouldRenderUnderside(entity, cameraPos);
        if (entity.isWireframeMode()) {
            int segments = this.getLodSegmentCount(entity, entityPos, true);
            this.renderWireframe(entity, entityPos, segments, matrixStack, vertexConsumerProvider, light);
        } else {
            int segments = this.getLodSegmentCount(entity, entityPos, false);
            Sprite rampSprite = this.resolveRampSprite(entity);
            VertexConsumer consumer = vertexConsumerProvider.getBuffer(RenderLayer.getEntityCutoutNoCull(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE));
            if (entity.isTwoSided()) {
                this.renderTwoSidedSolid(entity, entityPos, segments, matrixStack, consumer, rampSprite, light, renderUnderside);
            } else {
                this.renderOneSidedSolid(entity, entityPos, cameraPos, segments, matrixStack, consumer, rampSprite, light, renderUnderside);
            }
        }

        if (MinecraftClient.getInstance().getEntityRenderDispatcher().shouldRenderHitboxes()) {
            this.renderCollisionPolygons(entity, matrixStack, vertexConsumerProvider, entityPos);
        }
    }

    private int getLodSegmentCount(SurfRampEntity entity, Vec3d entityPos, boolean wireframe) {
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

        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.gameRenderer != null && client.gameRenderer.getCamera() != null) {
            Vec3d cameraPos = client.gameRenderer.getCamera().getPos();
            if (cameraPos != null) {
                double distanceSq = cameraPos.squaredDistanceTo(entityPos);
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
                ? MathHelper.clamp(baseSegments, 4, 18)
                : MathHelper.clamp(baseSegments, 6, 24);
    }

    private void renderOneSidedSolid(
            SurfRampEntity entity,
            Vec3d entityPos,
            @Nullable Vec3d cameraPos,
            int segments,
            MatrixStack matrixStack,
            VertexConsumer consumer,
            Sprite sprite,
            int light,
            boolean renderUnderside
    ) {
        Vec3d firstTop = null;
        Vec3d firstOuter = null;
        Vec3d firstInnerBase = null;

        Vec3d previousTop = null;
        Vec3d previousOuter = null;
        Vec3d previousInnerBase = null;
        Vec3d previousCenter = null;
        double accumulatedU = 0.0D;

        for (int i = 0; i <= segments; i++) {
            double t = (double) i / (double) segments;
            Vec3d center = entity.sampleCenterlineCached(t);
            Vec3d left = entity.sampleLeftCached(t);
            double baseY = entity.sampleBaseYCached(t);
            double topY = baseY + entity.getDrop();

            Vec3d top = new Vec3d(center.x, topY, center.z).subtract(entityPos);
            Vec3d outer = new Vec3d(
                    center.x + left.x * entity.getRampWidth() * entity.getSideSign(),
                    baseY,
                    center.z + left.z * entity.getRampWidth() * entity.getSideSign()
            ).subtract(entityPos);
            Vec3d innerBase = new Vec3d(center.x, baseY, center.z).subtract(entityPos);

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

    private EndpointSlice computeOneSidedEndpointSlice(SurfRampEntity entity, Vec3d entityPos, double t) {
        Vec3d center = entity.sampleCenterlineCached(t);
        Vec3d left = entity.sampleLeftCached(t);
        double baseY = entity.sampleBaseYCached(t);
        double topY = baseY + entity.getDrop();

        Vec3d topWorld = new Vec3d(center.x, topY, center.z);
        Vec3d innerWorld = new Vec3d(center.x, baseY, center.z);
        Vec3d outerWorld = new Vec3d(
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
            Vec3d entityPos,
            MatrixStack matrixStack,
            VertexConsumer consumer,
            Sprite sprite,
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
        Vec3d otherCenter = other.sampleCenterlineCached(otherT);
        Vec3d otherLeft = other.sampleLeftCached(otherT);
        double otherBaseY = other.sampleBaseYCached(otherT);
        double otherTopY = otherBaseY + other.getDrop();
        Vec3d otherTopWorld = new Vec3d(otherCenter.x, otherTopY, otherCenter.z);
        Vec3d otherInnerWorld = new Vec3d(otherCenter.x, otherBaseY, otherCenter.z);
        Vec3d otherOuterWorld = new Vec3d(
                otherCenter.x + otherLeft.x * other.getRampWidth() * other.getSideSign(),
                otherBaseY,
                otherCenter.z + otherLeft.z * other.getRampWidth() * other.getSideSign()
        );
        Vec3d otherTopLocal = otherTopWorld.subtract(entityPos);
        Vec3d otherInnerLocal = otherInnerWorld.subtract(entityPos);
        Vec3d otherOuterLocal = otherOuterWorld.subtract(entityPos);

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
    private EndpointConnection findConnectedOneSidedEndpoint(SurfRampEntity entity, Vec3d endpointWorld) {
        if (entity == null || entity.getWorld() == null || endpointWorld == null) {
            return null;
        }
        if (entity.isTwoSided()) {
            return null;
        }

        Box searchBox = new Box(
                endpointWorld.x - SEAM_SEARCH_RADIUS,
                endpointWorld.y - SEAM_SEARCH_RADIUS,
                endpointWorld.z - SEAM_SEARCH_RADIUS,
                endpointWorld.x + SEAM_SEARCH_RADIUS,
                endpointWorld.y + SEAM_SEARCH_RADIUS,
                endpointWorld.z + SEAM_SEARCH_RADIUS
        );
        List<SurfRampEntity> nearby = SurfRampEntity.collectNearbyRamps(entity.getWorld(), searchBox, 0.0D);
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
            Vec3d otherStart = other.getStart();
            double startDistSq = otherStart.squaredDistanceTo(endpointWorld);
            if (startDistSq <= maxDistSq && startDistSq < bestDistanceSquared) {
                bestDistanceSquared = startDistSq;
                best = new EndpointConnection(other, true);
            }

            Vec3d otherEnd = other.getEnd();
            double endDistSq = otherEnd.squaredDistanceTo(endpointWorld);
            if (endDistSq <= maxDistSq && endDistSq < bestDistanceSquared) {
                bestDistanceSquared = endDistSq;
                best = new EndpointConnection(other, false);
            }
        }

        return best;
    }

    private record EndpointConnection(SurfRampEntity other, boolean otherAtStart) {}

    private record EndpointSlice(Vec3d worldCenter, Vec3d topLocal, Vec3d innerLocal, Vec3d outerLocal) {}

    private boolean shouldResolveSeamConnection(Vec3d cameraPos, Vec3d endpointWorld) {
        if (cameraPos == null || endpointWorld == null) {
            return false;
        }
        return cameraPos.squaredDistanceTo(endpointWorld) <= SEAM_BRIDGE_DETAIL_DISTANCE_SQ;
    }

    @Nullable
    private Vec3d getCameraPos() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.gameRenderer == null || client.gameRenderer.getCamera() == null) {
            return null;
        }
        return client.gameRenderer.getCamera().getPos();
    }

    private boolean shouldRenderUnderside(SurfRampEntity entity, @Nullable Vec3d cameraPos) {
        if (entity == null || cameraPos == null) {
            return true;
        }
        Box bounds = entity.getBoundingBox();
        double approxBaseY = bounds.minY + 0.65D;
        return cameraPos.y <= approxBaseY + 0.45D;
    }

    private void renderTwoSidedSolid(
            SurfRampEntity entity,
            Vec3d entityPos,
            int segments,
            MatrixStack matrixStack,
            VertexConsumer consumer,
            Sprite sprite,
            int light,
            boolean renderUnderside
    ) {
        Vec3d firstRidge = null;
        Vec3d firstLeftBase = null;
        Vec3d firstRightBase = null;

        Vec3d previousRidge = null;
        Vec3d previousLeftBase = null;
        Vec3d previousRightBase = null;
        Vec3d previousCenter = null;
        double accumulatedU = 0.0D;

        for (int i = 0; i <= segments; i++) {
            double t = (double) i / (double) segments;
            Vec3d center = entity.sampleCenterlineCached(t);
            Vec3d left = entity.sampleLeftCached(t);
            double baseY = entity.sampleBaseYCached(t);
            double topY = baseY + entity.getDrop();

            Vec3d ridge = new Vec3d(center.x, topY, center.z).subtract(entityPos);
            Vec3d leftBase = new Vec3d(center.x + left.x * entity.getRampWidth(), baseY, center.z + left.z * entity.getRampWidth()).subtract(entityPos);
            Vec3d rightBase = new Vec3d(center.x - left.x * entity.getRampWidth(), baseY, center.z - left.z * entity.getRampWidth()).subtract(entityPos);

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
            Vec3d entityPos,
            int segments,
            MatrixStack matrixStack,
            VertexConsumerProvider provider,
            int light
    ) {
        int wireRgb = entity.getWireframeColorRgb();
        int wireRed = (wireRgb >> 16) & 0xFF;
        int wireGreen = (wireRgb >> 8) & 0xFF;
        int wireBlue = wireRgb & 0xFF;

        boolean fillEnabled = entity.isWireframeFillEnabled();
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

        Matrix4f positionMatrix = matrixStack.peek().getPositionMatrix();
        VertexConsumer wireConsumer = provider.getBuffer(ModRenderLayer.getLineOfWidth(WIREFRAME_LINE_WIDTH));
        VertexConsumer fillConsumer = fillEnabled
                ? provider.getBuffer(RenderLayer.getEntityTranslucent(WIREFRAME_FILL_TEXTURE))
                : null;

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
            Vec3d entityPos,
            int segments,
            MatrixStack matrixStack,
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
        Vec3d firstTop = null;
        Vec3d firstOuter = null;
        Vec3d firstInner = null;

        Vec3d previousTop = null;
        Vec3d previousOuter = null;
        Vec3d previousInner = null;
        Vec3d[] previousSurfaceBands = new Vec3d[WIREFRAME_SURFACE_LINE_FRACTIONS.length];

        for (int i = 0; i <= segments; i++) {
            double t = (double) i / (double) segments;
            Vec3d center = entity.sampleCenterlineCached(t);
            Vec3d left = entity.sampleLeftCached(t);
            double baseY = entity.sampleBaseYCached(t);
            double topY = baseY + entity.getDrop();

            Vec3d top = new Vec3d(center.x, topY, center.z).subtract(entityPos);
            Vec3d outer = new Vec3d(
                    center.x + left.x * entity.getRampWidth() * entity.getSideSign(),
                    baseY,
                    center.z + left.z * entity.getRampWidth() * entity.getSideSign()
            ).subtract(entityPos);
            Vec3d inner = new Vec3d(center.x, baseY, center.z).subtract(entityPos);

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

            Vec3d[] currentSurfaceBands = new Vec3d[WIREFRAME_SURFACE_LINE_FRACTIONS.length];
            for (int bandIndex = 0; bandIndex < WIREFRAME_SURFACE_LINE_FRACTIONS.length; bandIndex++) {
                Vec3d surfacePoint = top.lerp(outer, WIREFRAME_SURFACE_LINE_FRACTIONS[bandIndex]);
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
            Vec3d entityPos,
            int segments,
            MatrixStack matrixStack,
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
        Vec3d firstRidge = null;
        Vec3d firstLeft = null;
        Vec3d firstRight = null;

        Vec3d previousRidge = null;
        Vec3d previousLeft = null;
        Vec3d previousRight = null;
        Vec3d[] previousLeftSurfaceBands = new Vec3d[WIREFRAME_SURFACE_LINE_FRACTIONS.length];
        Vec3d[] previousRightSurfaceBands = new Vec3d[WIREFRAME_SURFACE_LINE_FRACTIONS.length];

        for (int i = 0; i <= segments; i++) {
            double t = (double) i / (double) segments;
            Vec3d center = entity.sampleCenterlineCached(t);
            Vec3d left = entity.sampleLeftCached(t);
            double baseY = entity.sampleBaseYCached(t);
            double topY = baseY + entity.getDrop();

            Vec3d ridge = new Vec3d(center.x, topY, center.z).subtract(entityPos);
            Vec3d leftBase = new Vec3d(center.x + left.x * entity.getRampWidth(), baseY, center.z + left.z * entity.getRampWidth()).subtract(entityPos);
            Vec3d rightBase = new Vec3d(center.x - left.x * entity.getRampWidth(), baseY, center.z - left.z * entity.getRampWidth()).subtract(entityPos);

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

            Vec3d[] currentLeftSurfaceBands = new Vec3d[WIREFRAME_SURFACE_LINE_FRACTIONS.length];
            Vec3d[] currentRightSurfaceBands = new Vec3d[WIREFRAME_SURFACE_LINE_FRACTIONS.length];
            for (int bandIndex = 0; bandIndex < WIREFRAME_SURFACE_LINE_FRACTIONS.length; bandIndex++) {
                double fraction = WIREFRAME_SURFACE_LINE_FRACTIONS[bandIndex];
                Vec3d leftSurface = ridge.lerp(leftBase, fraction);
                Vec3d rightSurface = ridge.lerp(rightBase, fraction);
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
            Vec3d entityPos,
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
            Vec3d center = entity.sampleCenterlineCached(t);
            Vec3d left = entity.sampleLeftCached(t);
            double baseY = entity.sampleBaseYCached(t);
            double topY = baseY + entity.getDrop();

            Vec3d top = new Vec3d(center.x, topY, center.z).subtract(entityPos);
            Vec3d outer = new Vec3d(
                    center.x + left.x * entity.getRampWidth() * entity.getSideSign(),
                    baseY,
                    center.z + left.z * entity.getRampWidth() * entity.getSideSign()
            ).subtract(entityPos);
            Vec3d inner = new Vec3d(center.x, baseY, center.z).subtract(entityPos);

            this.drawWireLine(wireConsumer, positionMatrix, top, outer, wireRed, wireGreen, wireBlue);
            this.drawWireLine(wireConsumer, positionMatrix, top, inner, wireRed, wireGreen, wireBlue);
            this.drawWireLine(wireConsumer, positionMatrix, outer, inner, wireRed, wireGreen, wireBlue);
        }
    }

    private void drawTwoSidedWireRibs(
            SurfRampEntity entity,
            Vec3d entityPos,
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
            Vec3d center = entity.sampleCenterlineCached(t);
            Vec3d left = entity.sampleLeftCached(t);
            double baseY = entity.sampleBaseYCached(t);
            double topY = baseY + entity.getDrop();

            Vec3d ridge = new Vec3d(center.x, topY, center.z).subtract(entityPos);
            Vec3d leftBase = new Vec3d(
                    center.x + left.x * entity.getRampWidth(),
                    baseY,
                    center.z + left.z * entity.getRampWidth()
            ).subtract(entityPos);
            Vec3d rightBase = new Vec3d(
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

        Vec3d previousCenter = entity.sampleCenterlineCached(0.0D);
        double totalLength = 0.0D;
        distances[0] = 0.0D;
        tSamples[0] = 0.0D;

        for (int i = 1; i <= sampleCount; i++) {
            double t = (double) i / (double) sampleCount;
            Vec3d center = entity.sampleCenterlineCached(t);
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
            Vec3d a,
            Vec3d b,
            int red,
            int green,
            int blue
    ) {
        wireConsumer.vertex(positionMatrix, (float) a.x, (float) a.y, (float) a.z)
                .color(red, green, blue, WIREFRAME_LINE_ALPHA)
                .normal(1.0F, 1.0F, 1.0F);
        wireConsumer.vertex(positionMatrix, (float) b.x, (float) b.y, (float) b.z)
                .color(red, green, blue, WIREFRAME_LINE_ALPHA)
                .normal(1.0F, 1.0F, 1.0F);
    }

    private void drawCollisionLine(
            VertexConsumerProvider provider,
            MatrixStack matrixStack,
            Vec3d a,
            Vec3d b
    ) {
        RenderUtil.drawLine(
                provider,
                matrixStack,
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
            MatrixStack matrices,
            Vec3d a,
            Vec3d b,
            Vec3d c,
            Vec3d d,
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
            MatrixStack matrices,
            Vec3d a,
            Vec3d b,
            Vec3d c,
            Vec3d d,
            int red,
            int green,
            int blue,
            int alpha,
            int light
    ) {
        Matrix4f positionMatrix = matrices.peek().getPositionMatrix();
        Vec3d normal = b.subtract(a).crossProduct(d.subtract(a));
        if (normal.lengthSquared() < 1.0E-8D) {
            normal = new Vec3d(0.0D, 1.0D, 0.0D);
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
            MatrixStack matrices,
            Vec3d a,
            Vec3d b,
            Vec3d c,
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
            MatrixStack matrices,
            Vec3d a,
            Vec3d b,
            Vec3d c,
            int red,
            int green,
            int blue,
            int alpha,
            int light
    ) {
        Matrix4f positionMatrix = matrices.peek().getPositionMatrix();
        Vec3d normal = b.subtract(a).crossProduct(c.subtract(a));
        if (normal.lengthSquared() < 1.0E-8D) {
            normal = new Vec3d(0.0D, 1.0D, 0.0D);
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
            Vec3d pos,
            float u,
            float v,
            int red,
            int green,
            int blue,
            int alpha,
            int light,
            Vec3d normal
    ) {
        consumer.vertex(positionMatrix, (float) pos.x, (float) pos.y, (float) pos.z)
                .color(red, green, blue, alpha)
                .texture(clamp01(u), clamp01(v))
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(light)
                .normal((float) normal.x, (float) normal.y, (float) normal.z);
    }

    private void renderCollisionPolygons(
            SurfRampEntity entity,
            MatrixStack matrixStack,
            VertexConsumerProvider provider,
            Vec3d entityPos
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
            Vec3d entityPos,
            int samples,
            MatrixStack matrixStack,
            VertexConsumerProvider provider
    ) {
        ArcLengthTable arcLengthTable = this.buildArcLengthTable(entity, samples);
        Vec3d previousTop = null;
        Vec3d previousOuter = null;
        Vec3d previousInner = null;
        Vec3d[] previousSurfaceBands = new Vec3d[COLLISION_DEBUG_SURFACE_LINE_FRACTIONS.length];

        for (int i = 0; i <= samples; i++) {
            double targetDistance = arcLengthTable.totalLength() * ((double) i / (double) samples);
            double t = arcLengthTable.tAtDistance(targetDistance);
            Vec3d center = entity.sampleCenterlineCached(t);
            Vec3d left = entity.sampleLeftCached(t);
            double baseY = entity.sampleBaseYCached(t);
            double topY = baseY + entity.getDrop();

            Vec3d top = new Vec3d(center.x, topY, center.z).subtract(entityPos);
            Vec3d outer = new Vec3d(
                    center.x + left.x * entity.getRampWidth() * entity.getSideSign(),
                    baseY,
                    center.z + left.z * entity.getRampWidth() * entity.getSideSign()
            ).subtract(entityPos);
            Vec3d inner = new Vec3d(center.x, baseY, center.z).subtract(entityPos);

            if (previousTop != null) {
                this.drawCollisionLine(provider, matrixStack, previousTop, top);
                this.drawCollisionLine(provider, matrixStack, previousOuter, outer);
                this.drawCollisionLine(provider, matrixStack, previousInner, inner);
            }

            Vec3d[] currentSurfaceBands = new Vec3d[COLLISION_DEBUG_SURFACE_LINE_FRACTIONS.length];
            for (int bandIndex = 0; bandIndex < COLLISION_DEBUG_SURFACE_LINE_FRACTIONS.length; bandIndex++) {
                Vec3d surfacePoint = top.lerp(outer, COLLISION_DEBUG_SURFACE_LINE_FRACTIONS[bandIndex]);
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
            Vec3d center = entity.sampleCenterlineCached(t);
            Vec3d left = entity.sampleLeftCached(t);
            double baseY = entity.sampleBaseYCached(t);
            double topY = baseY + entity.getDrop();

            Vec3d top = new Vec3d(center.x, topY, center.z).subtract(entityPos);
            Vec3d outer = new Vec3d(
                    center.x + left.x * entity.getRampWidth() * entity.getSideSign(),
                    baseY,
                    center.z + left.z * entity.getRampWidth() * entity.getSideSign()
            ).subtract(entityPos);
            Vec3d inner = new Vec3d(center.x, baseY, center.z).subtract(entityPos);

            this.drawCollisionLine(provider, matrixStack, top, outer);
            this.drawCollisionLine(provider, matrixStack, top, inner);
            this.drawCollisionLine(provider, matrixStack, outer, inner);
        }
    }

    private void renderTwoSidedCollisionDebug(
            SurfRampEntity entity,
            Vec3d entityPos,
            int samples,
            MatrixStack matrixStack,
            VertexConsumerProvider provider
    ) {
        ArcLengthTable arcLengthTable = this.buildArcLengthTable(entity, samples);
        Vec3d previousRidge = null;
        Vec3d previousLeft = null;
        Vec3d previousRight = null;
        Vec3d[] previousLeftSurfaceBands = new Vec3d[COLLISION_DEBUG_SURFACE_LINE_FRACTIONS.length];
        Vec3d[] previousRightSurfaceBands = new Vec3d[COLLISION_DEBUG_SURFACE_LINE_FRACTIONS.length];

        for (int i = 0; i <= samples; i++) {
            double targetDistance = arcLengthTable.totalLength() * ((double) i / (double) samples);
            double t = arcLengthTable.tAtDistance(targetDistance);
            Vec3d center = entity.sampleCenterlineCached(t);
            Vec3d left = entity.sampleLeftCached(t);
            double baseY = entity.sampleBaseYCached(t);
            double topY = baseY + entity.getDrop();

            Vec3d ridge = new Vec3d(center.x, topY, center.z).subtract(entityPos);
            Vec3d leftBase = new Vec3d(
                    center.x + left.x * entity.getRampWidth(),
                    baseY,
                    center.z + left.z * entity.getRampWidth()
            ).subtract(entityPos);
            Vec3d rightBase = new Vec3d(
                    center.x - left.x * entity.getRampWidth(),
                    baseY,
                    center.z - left.z * entity.getRampWidth()
            ).subtract(entityPos);

            if (previousRidge != null) {
                this.drawCollisionLine(provider, matrixStack, previousRidge, ridge);
                this.drawCollisionLine(provider, matrixStack, previousLeft, leftBase);
                this.drawCollisionLine(provider, matrixStack, previousRight, rightBase);
            }

            Vec3d[] currentLeftSurfaceBands = new Vec3d[COLLISION_DEBUG_SURFACE_LINE_FRACTIONS.length];
            Vec3d[] currentRightSurfaceBands = new Vec3d[COLLISION_DEBUG_SURFACE_LINE_FRACTIONS.length];
            for (int bandIndex = 0; bandIndex < COLLISION_DEBUG_SURFACE_LINE_FRACTIONS.length; bandIndex++) {
                double fraction = COLLISION_DEBUG_SURFACE_LINE_FRACTIONS[bandIndex];
                Vec3d leftSurface = ridge.lerp(leftBase, fraction);
                Vec3d rightSurface = ridge.lerp(rightBase, fraction);
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
            Vec3d center = entity.sampleCenterlineCached(t);
            Vec3d left = entity.sampleLeftCached(t);
            double baseY = entity.sampleBaseYCached(t);
            double topY = baseY + entity.getDrop();

            Vec3d ridge = new Vec3d(center.x, topY, center.z).subtract(entityPos);
            Vec3d leftBase = new Vec3d(
                    center.x + left.x * entity.getRampWidth(),
                    baseY,
                    center.z + left.z * entity.getRampWidth()
            ).subtract(entityPos);
            Vec3d rightBase = new Vec3d(
                    center.x - left.x * entity.getRampWidth(),
                    baseY,
                    center.z - left.z * entity.getRampWidth()
            ).subtract(entityPos);

            this.drawCollisionLine(provider, matrixStack, ridge, leftBase);
            this.drawCollisionLine(provider, matrixStack, ridge, rightBase);
            this.drawCollisionLine(provider, matrixStack, leftBase, rightBase);
        }
    }

    private Sprite resolveRampSprite(SurfRampEntity entity) {
        MinecraftClient client = MinecraftClient.getInstance();
        BakedModel model = client.getBlockRenderManager().getModel(entity.getTextureBlockState());
        Sprite sprite = model.getParticleSprite();
        if (sprite != null) {
            return sprite;
        }
        return client.getBlockRenderManager().getModel(Blocks.SMOOTH_STONE.getDefaultState()).getParticleSprite();
    }

    private static void drawTexturedQuad(
            SurfRampEntity entity,
            VertexConsumer consumer,
            MatrixStack matrices,
            Vec3d a,
            Vec3d b,
            Vec3d c,
            Vec3d d,
            Sprite sprite,
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

        Vec3d currentA = a;
        Vec3d currentB = b;
        Vec3d currentC = c;
        Vec3d currentD = d;
        double currentU0 = u0;
        double currentU1 = u1;

        while (currentU1 > currentU0 + UV_SPLIT_EPSILON) {
            double nextBoundary = Math.floor(currentU0) + 1.0D;
            if (currentU1 <= nextBoundary + UV_SPLIT_EPSILON) {
                drawTexturedQuadSegment(entity, consumer, matrices, currentA, currentB, currentC, currentD, sprite, light, currentU0, currentU1, v0, v1);
                break;
            }

            double t = (nextBoundary - currentU0) / (currentU1 - currentU0);
            Vec3d splitTop = lerp(currentA, currentB, t);
            Vec3d splitBottom = lerp(currentD, currentC, t);
            drawTexturedQuadSegment(entity, consumer, matrices, currentA, splitTop, splitBottom, currentD, sprite, light, currentU0, nextBoundary, v0, v1);

            currentA = splitTop;
            currentD = splitBottom;
            currentU0 = nextBoundary;
        }
    }

    private static void drawTexturedQuadSegment(
            SurfRampEntity entity,
            VertexConsumer consumer,
            MatrixStack matrices,
            Vec3d a,
            Vec3d b,
            Vec3d c,
            Vec3d d,
            Sprite sprite,
            int light,
            double u0,
            double u1,
            float v0,
            float v1
    ) {
        double tileBase = Math.floor(u0);
        float localU0 = clamp01((float) (u0 - tileBase));
        float localU1 = clamp01((float) (u1 - tileBase));

        Matrix4f positionMatrix = matrices.peek().getPositionMatrix();

        Vec3d normal = b.subtract(a).crossProduct(d.subtract(a));
        if (normal.lengthSquared() < 1.0E-8D) {
            normal = new Vec3d(0.0D, 1.0D, 0.0D);
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
            MatrixStack matrices,
            Vec3d a,
            Vec3d b,
            Vec3d c,
            Sprite sprite,
            int light
    ) {
        Matrix4f positionMatrix = matrices.peek().getPositionMatrix();
        Vec3d normal = b.subtract(a).crossProduct(c.subtract(a));
        if (normal.lengthSquared() < 1.0E-8D) {
            normal = new Vec3d(0.0D, 1.0D, 0.0D);
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
            MatrixStack matrices,
            Vec3d a,
            Vec3d b,
            Vec3d c,
            Sprite sprite,
            int light
    ) {
        drawTexturedTriangle(entity, consumer, matrices, a, b, c, sprite, light);
        drawTexturedTriangle(entity, consumer, matrices, a, c, b, sprite, light);
    }

    private static void putFaceVertex(
            VertexConsumer consumer,
            Matrix4f positionMatrix,
            Vec3d pos,
            float u,
            float v,
            Sprite sprite,
            int light,
            Vec3d normal
    ) {
        float wrappedU = clamp01(u);
        float wrappedV = clamp01(v);
        float pixelInsetU = HORIZONTAL_TEXTURE_INSET_PIXELS / 16.0F;
        if (pixelInsetU > 0.49F) {
            pixelInsetU = 0.49F;
        }
        float uRange = 1.0F - pixelInsetU * 2.0F;
        float croppedU = pixelInsetU + wrappedU * uRange;

        float atlasU = sprite.getMinU() + (sprite.getMaxU() - sprite.getMinU()) * croppedU;
        float atlasV = sprite.getMinV() + (sprite.getMaxV() - sprite.getMinV()) * wrappedV;
        consumer.vertex(positionMatrix, (float) pos.x, (float) pos.y, (float) pos.z)
                .color(255, 255, 255, 255)
                .texture(atlasU, atlasV)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(light)
                .normal((float) normal.x, (float) normal.y, (float) normal.z);
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

    private static Vec3d lerp(Vec3d from, Vec3d to, double t) {
        return from.add(to.subtract(from).multiply(t));
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
    public Identifier getTexture(SurfRampEntityRenderState state) {
        return SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE;
    }
}

