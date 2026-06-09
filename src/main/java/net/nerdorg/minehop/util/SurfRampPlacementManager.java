package net.nerdorg.minehop.util;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.nerdorg.minehop.data.DataManager;
import net.nerdorg.minehop.entity.ModEntities;
import net.nerdorg.minehop.entity.custom.SurfRampEntity;
import net.nerdorg.minehop.networking.PacketHandler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SurfRampPlacementManager {
    private static final String DEFAULT_SURF_TEXTURE_BLOCK_ID = "minecraft:smooth_stone";
    private static final Map<UUID, PlacementState> PLACEMENT_STATES = new HashMap<>();
    private static final Map<UUID, PlacementOptions> PLACEMENT_OPTIONS = new HashMap<>();
    private static final Map<UUID, EditState> EDIT_STATES = new HashMap<>();
    private static final int AUTO_SPLIT_MAX_POINTS_PER_RAMP = 18;
    private static final double AUTO_SPLIT_MAX_HORIZONTAL_LENGTH = 5.0D;
    private static final double AUTO_SPLIT_MAX_LOCAL_BEND_DEGREES = 10.0D;
    private static final double AUTO_SPLIT_MAX_SEGMENT_DIRECTION_DELTA_DEGREES = 10.0D;
    private static final int AUTO_SPLIT_SHARED_POINTS = 1;
    private static final double AUTO_SPLIT_RESAMPLE_MIN_STEP = 0.25D;
    private static final double ONE_SIDED_BLOCK_FACE_CLEARANCE = 1.0D / 32.0D;
    private static final double HALF_BLOCK_EDGE_LENGTH = 0.5D;
    private static final double AUTO_SPLIT_CORNER_BLEND_MIN_TURN_DEGREES = 2.0D;
    private static final double AUTO_SPLIT_CORNER_BLEND_RATIO = 0.50D;
    private static final double AUTO_SPLIT_CORNER_BLEND_MIN_DISTANCE = 0.08D;
    private static final double AUTO_SPLIT_CORNER_BLEND_MAX_DISTANCE = 2.25D;
    private static final double AUTO_SPLIT_CORNER_BLEND_TURN_STEP_DEGREES = 4.0D;
    private static final int AUTO_SPLIT_CORNER_BLEND_MAX_SAMPLES = 10;
    private static final int MAX_SURF_RAMP_SEGMENTS_PER_PLOT = 64;

    public static void setPoint(ServerPlayerEntity player, BlockPos point) {
        if (player == null || point == null) {
            return;
        }
        if (!player.hasPermissionLevel(4)
                && player.getWorld() instanceof ServerWorld serverWorld
                && !UserPlotManager.canBuildAt(player, serverWorld, point)) {
            Logger.logFailure(player, "You can only place surf ramps inside your own plot.");
            return;
        }

        PlacementState state = PLACEMENT_STATES.computeIfAbsent(player.getUuid(), key -> new PlacementState());
        PlacementOptions options = PLACEMENT_OPTIONS.computeIfAbsent(player.getUuid(), key -> new PlacementOptions());
        BlockPos immutablePoint = point.toImmutable();
        if (!state.points.isEmpty() && state.points.get(state.points.size() - 1).equals(immutablePoint)) {
            Logger.logFailure(player, "Point is identical to the previous point.");
            return;
        }

        state.points.add(immutablePoint);
        state.playerDecisionPosition = player.getPos();
        if (state.points.size() == 1) {
            Logger.logSuccess(player, "Surf point 1 set to " + immutablePoint.toShortString());
            Logger.log(player, Text.literal("Surf stick: adjust drop/width/texture in the opened GUI, then right click more blocks."));
            Logger.log(player, Text.literal("Use /surfstick finish to create the ramp, or /surfstick clear to reset."));
            PacketHandler.openSurfStickSettings(
                    player,
                    options.width,
                    options.drop,
                    options.textureBlockId,
                    options.oneSided,
                    options.outsideCurve,
                    options.renderMode,
                    options.wireframeColor,
                    options.wireframeFill,
                    options.wireframeFillColor,
                    options.wireframeFillAlpha,
                    false
            );
        } else {
            Logger.logActionBar(player, "Surf point " + state.points.size() + " set | /surfstick finish to build");
        }

        syncPreview(player, state);
    }

    public static int chooseMode(ServerPlayerEntity player, boolean oneSided) {
        if (player == null) {
            return 0;
        }

        PlacementOptions options = PLACEMENT_OPTIONS.computeIfAbsent(player.getUuid(), key -> new PlacementOptions());
        options.oneSided = oneSided;

        if (oneSided) {
            Logger.logSuccess(player, "Surf ramp mode set to one-sided.");
            Logger.log(player, Text.literal("Set one-sided face to inside/outside with the surf stick GUI."));
        } else {
            Logger.logSuccess(player, "Surf ramp mode set to two-sided.");
        }

        PlacementState state = PLACEMENT_STATES.computeIfAbsent(player.getUuid(), key -> new PlacementState());
        syncPreview(player, state);
        return 1;
    }

    public static int chooseSide(ServerPlayerEntity player, boolean playerSide) {
        if (player == null) {
            return 0;
        }

        PlacementState state = PLACEMENT_STATES.computeIfAbsent(player.getUuid(), key -> new PlacementState());
        PlacementOptions options = PLACEMENT_OPTIONS.computeIfAbsent(player.getUuid(), key -> new PlacementOptions());
        options.outsideCurve = !playerSide;
        state.playerDecisionPosition = player.getPos();
        Logger.logSuccess(player, "One-sided face set to " + (playerSide ? "my side / inside" : "other side / outside") + ".");
        syncPreview(player, state);
        return 1;
    }

    public static int finishSelection(ServerPlayerEntity player) {
        if (player == null) {
            return 0;
        }

        PlacementState state = PLACEMENT_STATES.get(player.getUuid());
        if (state == null || state.points.size() < 2) {
            Logger.logFailure(player, "Set at least two points with the surf stick first.");
            return 0;
        }

        if (!(player.getWorld() instanceof ServerWorld serverWorld)) {
            Logger.logFailure(player, "Could not create ramp in this world.");
            return 0;
        }
        if (!player.hasPermissionLevel(4)) {
            for (BlockPos selectedPoint : state.points) {
                if (selectedPoint == null) {
                    continue;
                }
                if (!UserPlotManager.canBuildAt(player, serverWorld, selectedPoint)) {
                    Logger.logFailure(player, "All surf points must stay inside your own plot.");
                    return 0;
                }
            }
        }

        PlacementOptions options = PLACEMENT_OPTIONS.computeIfAbsent(player.getUuid(), key -> new PlacementOptions());
        Vec3d decisionPosition = state.playerDecisionPosition != null ? state.playerDecisionPosition : player.getPos();

        List<Vec3d> centerlinePoints = toCenterlinePoints(state.points);
        if (centerlinePoints.size() < 2) {
            Logger.logFailure(player, "No valid ramp path was created (points may be too close).");
            return 0;
        }

        Vec3d referenceStart = centerlinePoints.get(0);
        Vec3d referenceEnd = centerlinePoints.get(centerlinePoints.size() - 1);
        if (referenceStart.squaredDistanceTo(referenceEnd) < 1.0E-4D) {
            Logger.logFailure(player, "Ramp start and end are too close.");
            return 0;
        }
        centerlinePoints = extendCenterlineToSelectedBlockEdges(centerlinePoints);

        boolean twoSided = !options.oneSided;
        int sideSign = 1;
        if (!twoSided) {
            int insideCurveSign = hasCurvedPath(centerlinePoints)
                    ? resolvePathInsideCurveSideSign(centerlinePoints)
                    : findPlayerSideSign(referenceStart, referenceEnd, decisionPosition);
            sideSign = options.outsideCurve ? -insideCurveSign : insideCurveSign;
            centerlinePoints = snapOneSidedCenterlineToBlockFaces(centerlinePoints, sideSign);
        }

        centerlinePoints = softenCenterlineCorners(centerlinePoints);
        centerlinePoints = densifyCenterlineForSegmentation(centerlinePoints);
        if (centerlinePoints.size() < 2) {
            Logger.logFailure(player, "No valid ramp path was created (points may be too close).");
            return 0;
        }

        List<SegmentSlice> rampSegments = splitCenterlineForSpawn(centerlinePoints);
        if (rampSegments.isEmpty()) {
            Logger.logFailure(player, "No valid ramp segments were created.");
            return 0;
        }

        DataManager.MapData targetPlot = UserPlotManager.getPlotAt(serverWorld, centerlinePoints.get(0));
        if (targetPlot != null) {
            if (!player.hasPermissionLevel(4)
                    && !rampFitsInPlot(targetPlot, centerlinePoints, options.width, twoSided, sideSign)) {
                Logger.logFailure(player, "The ramp (including its width) must stay fully inside your plot.");
                return 0;
            }
            int existingSegments = UserPlotManager.countSurfRampsInPlot(serverWorld, targetPlot);
            int newSegments = countSpawnableSegments(centerlinePoints, rampSegments);
            int allowedRemaining = Math.max(0, MAX_SURF_RAMP_SEGMENTS_PER_PLOT - existingSegments);
            if (newSegments > allowedRemaining) {
                Logger.logFailure(
                        player,
                        "This plot allows up to " + MAX_SURF_RAMP_SEGMENTS_PER_PLOT
                                + " surf ramp segments. Existing: " + existingSegments
                                + ", trying to add: " + newSegments + "."
                );
                return 0;
            }
        } else if (!player.hasPermissionLevel(4)) {
            Logger.logFailure(player, "Surf ramps can only be placed inside your plot.");
            return 0;
        }

        String chainId = UUID.randomUUID().toString();
        List<Vec3d> sharedPathPoints = List.copyOf(centerlinePoints);
        int sharedPathSpan = Math.max(sharedPathPoints.size() - 1, 1);

        int spawned = 0;
        for (int segmentIdx = 0; segmentIdx < rampSegments.size(); segmentIdx++) {
            SegmentSlice segmentSlice = rampSegments.get(segmentIdx);
            if (segmentSlice == null || segmentSlice.endIndex <= segmentSlice.startIndex) {
                continue;
            }
            List<Vec3d> segmentPoints = centerlinePoints.subList(segmentSlice.startIndex, segmentSlice.endIndex + 1);
            Vec3d segmentStart = segmentPoints.get(0);
            Vec3d segmentEnd = segmentPoints.get(segmentPoints.size() - 1);
            if (segmentStart.squaredDistanceTo(segmentEnd) < 1.0E-4D) {
                continue;
            }

            double pathStartT = (double) segmentSlice.startIndex / (double) sharedPathSpan;
            double pathEndT = (double) segmentSlice.endIndex / (double) sharedPathSpan;

            SurfRampEntity ramp = new SurfRampEntity(ModEntities.SURF_RAMP_ENTITY, serverWorld);
            ramp.setGeometry(segmentStart, segmentEnd, options.drop, options.width, twoSided, sideSign);
            ramp.setCenterlinePoints(sharedPathPoints, pathStartT, pathEndT);
            ramp.setChainId(chainId);
            ramp.setTextureBlockId(options.textureBlockId);
            ramp.setRenderMode(options.renderMode);
            ramp.setWireframeColorRgb(options.wireframeColor);
            ramp.setWireframeFillEnabled(options.wireframeFill);
            ramp.setWireframeFillColorRgb(options.wireframeFillColor);
            ramp.setWireframeFillAlpha(options.wireframeFillAlpha);
            ramp.setLinkedSeams(segmentIdx > 0, segmentIdx < rampSegments.size() - 1);
            if (serverWorld.spawnEntity(ramp)) {
                spawned++;
            }
        }

        if (spawned <= 0) {
            Logger.logFailure(player, "Ramp creation failed.");
            return 0;
        }

        String modeText = options.oneSided ? "one-sided" : "two-sided";
        if (spawned == 1) {
            Logger.logSuccess(player, "Created one " + modeText + " surf ramp through " + state.points.size() + " points.");
        } else {
            Logger.logSuccess(player, "Created " + spawned + " seamless " + modeText + " surf ramp segments through " + state.points.size() + " points.");
        }

        state.clearPoints();
        PacketHandler.clearSurfStickPreview(player);
        return 1;
    }

    public static int clearSelection(ServerPlayerEntity player) {
        if (player == null) {
            return 0;
        }
        PlacementState state = PLACEMENT_STATES.computeIfAbsent(player.getUuid(), key -> new PlacementState());
        state.clearPoints();
        PacketHandler.clearSurfStickPreview(player);
        Logger.logSuccess(player, "Cleared surf stick selection.");
        return 1;
    }

    public static void onPlayerDisconnect(UUID playerUuid) {
        if (playerUuid == null) {
            return;
        }
        PLACEMENT_STATES.remove(playerUuid);
        PLACEMENT_OPTIONS.remove(playerUuid);
        EDIT_STATES.remove(playerUuid);
    }

    public static void cancelSelectionFromGui(ServerPlayerEntity player) {
        if (player == null) {
            return;
        }
        EditState editState = EDIT_STATES.remove(player.getUuid());
        if (editState != null) {
            Logger.logActionBar(player, "Surf ramp edit canceled.");
            return;
        }
        PlacementState state = PLACEMENT_STATES.computeIfAbsent(player.getUuid(), key -> new PlacementState());
        state.clearPoints();
        PacketHandler.clearSurfStickPreview(player);
        Logger.logActionBar(player, "Surf stick creation canceled.");
    }

    public static void openEditor(ServerPlayerEntity player, SurfRampEntity ramp) {
        if (player == null || ramp == null) {
            return;
        }
        if (!UserPlotManager.canEditEntity(player, ramp)) {
            Logger.logFailure(player, "You can only edit surf ramps in your own plot.");
            return;
        }
        if (!(player.getWorld() instanceof ServerWorld serverWorld) || ramp.getWorld() != serverWorld) {
            Logger.logFailure(player, "Could not edit that surf ramp in this world.");
            return;
        }

        List<SurfRampEntity> connected = collectConnectedSegments(ramp);
        if (connected.isEmpty()) {
            connected = List.of(ramp);
        }

        String pathPointsEncoded = ramp.getPathPointsEncoded();
        boolean hasPathCurve = hasCurvedPath(parsePathPoints(pathPointsEncoded));
        int insideCurveSign = hasPathCurve
                ? resolvePathInsideCurveSideSign(parsePathPoints(pathPointsEncoded))
                : ramp.getSideSign();
        int normalizedInsideSign = insideCurveSign >= 0 ? 1 : -1;

        boolean oneSided = !ramp.isTwoSided();
        boolean outsideCurve = oneSided && ramp.getSideSign() != normalizedInsideSign;

        List<UUID> targetUuids = new ArrayList<>();
        for (SurfRampEntity segment : connected) {
            if (segment != null && segment.isAlive() && !segment.isRemoved()) {
                targetUuids.add(segment.getUuid());
            }
        }
        if (targetUuids.isEmpty()) {
            Logger.logFailure(player, "No ramp segments were found to edit.");
            return;
        }

        EditState editState = new EditState(
                serverWorld.getRegistryKey().getValue().toString(),
                ramp.getChainId(),
                pathPointsEncoded,
                targetUuids,
                normalizedInsideSign
        );
        EDIT_STATES.put(player.getUuid(), editState);

        PacketHandler.openSurfStickSettings(
                player,
                ramp.getRampWidth(),
                ramp.getDrop(),
                ramp.getTextureBlockId(),
                oneSided,
                outsideCurve,
                ramp.getRenderMode(),
                ramp.getWireframeColorRgb(),
                ramp.isWireframeFillEnabled(),
                ramp.getWireframeFillColorRgb(),
                ramp.getWireframeFillAlpha(),
                true
        );
        Logger.logActionBar(player, "Editing surf ramp chain (" + targetUuids.size() + " segments).");
    }

    public static void deleteEditedRamp(ServerPlayerEntity player) {
        if (player == null) {
            return;
        }
        EditState editState = EDIT_STATES.remove(player.getUuid());
        if (editState == null) {
            Logger.logFailure(player, "No surf ramp edit is active.");
            return;
        }

        List<SurfRampEntity> targets = resolveEditTargets(player, editState);
        if (targets.isEmpty()) {
            Logger.logFailure(player, "No ramp segments were found to delete.");
            return;
        }

        int removed = 0;
        for (SurfRampEntity segment : targets) {
            if (segment == null || segment.isRemoved()) {
                continue;
            }
            if (segment.getWorld() instanceof ServerWorld segmentWorld) {
                segment.kill(segmentWorld);
            } else {
                segment.remove(net.minecraft.entity.Entity.RemovalReason.KILLED);
            }
            removed++;
        }

        if (removed > 0) {
            Logger.logSuccess(player, "Deleted surf ramp chain (" + removed + " segments).");
        } else {
            Logger.logFailure(player, "Could not delete the selected ramp chain.");
        }
    }

    public static int setWidth(ServerPlayerEntity player, double width) {
        if (player == null) {
            return 0;
        }
        PlacementOptions options = PLACEMENT_OPTIONS.computeIfAbsent(player.getUuid(), key -> new PlacementOptions());
        options.width = MathHelper.clamp(width, 0.15D, 64.0D);
        Logger.logSuccess(player, "Surf ramp width set to " + String.format("%.2f", options.width));

        PlacementState state = PLACEMENT_STATES.get(player.getUuid());
        if (state != null) {
            syncPreview(player, state);
        }
        return 1;
    }

    public static int setDrop(ServerPlayerEntity player, double drop) {
        if (player == null) {
            return 0;
        }
        PlacementOptions options = PLACEMENT_OPTIONS.computeIfAbsent(player.getUuid(), key -> new PlacementOptions());
        options.drop = MathHelper.clamp(drop, 0.1D, 64.0D);
        Logger.logSuccess(player, "Surf ramp drop set to " + String.format("%.2f", options.drop));

        PlacementState state = PLACEMENT_STATES.get(player.getUuid());
        if (state != null) {
            syncPreview(player, state);
        }
        return 1;
    }

    public static int setTexture(ServerPlayerEntity player, String textureBlockId) {
        if (player == null) {
            return 0;
        }
        PlacementOptions options = PLACEMENT_OPTIONS.computeIfAbsent(player.getUuid(), key -> new PlacementOptions());
        String sanitized = sanitizeTextureBlockId(textureBlockId);
        options.textureBlockId = sanitized;
        Logger.logSuccess(player, "Surf ramp texture set to " + sanitized);

        PlacementState state = PLACEMENT_STATES.get(player.getUuid());
        if (state != null) {
            syncPreview(player, state);
        }
        return 1;
    }

    public static void applyOptionsFromGui(
            ServerPlayerEntity player,
            double width,
            double drop,
            String textureBlockId,
            boolean oneSided,
            boolean outsideCurve,
            String renderMode,
            int wireframeColor,
            boolean wireframeFill,
            int wireframeFillColor,
            int wireframeFillAlpha
    ) {
        if (player == null) {
            return;
        }
        PlacementOptions options = PLACEMENT_OPTIONS.computeIfAbsent(player.getUuid(), key -> new PlacementOptions());
        options.width = MathHelper.clamp(width, 0.15D, 64.0D);
        options.drop = MathHelper.clamp(drop, 0.1D, 64.0D);
        options.textureBlockId = sanitizeTextureBlockId(textureBlockId);
        options.oneSided = oneSided;
        options.outsideCurve = outsideCurve;
        options.renderMode = SurfRampVisualStyle.sanitizeMode(renderMode);
        options.wireframeColor = SurfRampVisualStyle.sanitizeColor(wireframeColor, SurfRampVisualStyle.DEFAULT_WIREFRAME_COLOR);
        options.wireframeFill = wireframeFill;
        options.wireframeFillColor = SurfRampVisualStyle.sanitizeColor(wireframeFillColor, SurfRampVisualStyle.DEFAULT_WIREFRAME_FILL_COLOR);
        options.wireframeFillAlpha = SurfRampVisualStyle.sanitizeAlpha(wireframeFillAlpha, SurfRampVisualStyle.DEFAULT_WIREFRAME_FILL_ALPHA);

        EditState editState = EDIT_STATES.remove(player.getUuid());
        if (editState != null) {
            applyOptionsToEditedRamps(player, options, editState);
            PlacementState placementState = PLACEMENT_STATES.get(player.getUuid());
            if (placementState != null && !placementState.points.isEmpty()) {
                syncPreview(player, placementState);
            }
            return;
        }

        PlacementState state = PLACEMENT_STATES.get(player.getUuid());
        if (state != null) {
            syncPreview(player, state);
        }
        Logger.logActionBar(
                player,
                "Surf settings: " + (options.oneSided ? "one-sided " + (options.outsideCurve ? "outside" : "inside") : "two-sided")
                        + " | width " + String.format("%.2f", options.width)
                        + " | drop " + String.format("%.2f", options.drop)
                        + " | " + describeVisualStyle(options)
        );
    }

    private static String describeVisualStyle(PlacementOptions options) {
        if (options == null) {
            return DEFAULT_SURF_TEXTURE_BLOCK_ID;
        }
        if (SurfRampVisualStyle.MODE_WIREFRAME.equals(options.renderMode)) {
            String wire = String.format("#%06X", options.wireframeColor & 0xFFFFFF);
            if (options.wireframeFill) {
                return "wire " + wire
                        + " fill " + String.format("#%06X", options.wireframeFillColor & 0xFFFFFF)
                        + " a" + options.wireframeFillAlpha;
            }
            return "wire " + wire;
        }
        return options.textureBlockId;
    }

    private static void syncPreview(ServerPlayerEntity player, PlacementState state) {
        if (player == null || state == null) {
            return;
        }
        if (state.points.isEmpty()) {
            PacketHandler.clearSurfStickPreview(player);
            return;
        }

        PlacementOptions options = PLACEMENT_OPTIONS.computeIfAbsent(player.getUuid(), key -> new PlacementOptions());
        PacketHandler.sendSurfStickPreview(player, state.points, options.width, options.drop, options.oneSided, options.outsideCurve);
    }

    private static void applyOptionsToEditedRamps(ServerPlayerEntity player, PlacementOptions options, EditState editState) {
        if (player == null || options == null || editState == null) {
            return;
        }

        List<SurfRampEntity> targets = resolveEditTargets(player, editState);
        if (targets.isEmpty()) {
            Logger.logFailure(player, "No ramp segments were found to edit.");
            return;
        }

        boolean twoSided = !options.oneSided;
        int sideSign = editState.insideCurveSign >= 0 ? 1 : -1;
        if (!twoSided) {
            sideSign = options.outsideCurve ? -sideSign : sideSign;
        }

        int updated = 0;
        for (SurfRampEntity ramp : targets) {
            if (ramp == null || ramp.isRemoved()) {
                continue;
            }
            boolean geometryChanged = Math.abs(ramp.getDrop() - options.drop) > 1.0E-4D
                    || Math.abs(ramp.getRampWidth() - options.width) > 1.0E-4D
                    || ramp.isTwoSided() != twoSided
                    || ramp.getSideSign() != sideSign;
            if (geometryChanged) {
                Vec3d start = ramp.getStart();
                Vec3d end = ramp.getEnd();
                ramp.setGeometry(start, end, options.drop, options.width, twoSided, sideSign);
            }
            ramp.setTextureBlockId(options.textureBlockId);
            ramp.setRenderMode(options.renderMode);
            ramp.setWireframeColorRgb(options.wireframeColor);
            ramp.setWireframeFillEnabled(options.wireframeFill);
            ramp.setWireframeFillColorRgb(options.wireframeFillColor);
            ramp.setWireframeFillAlpha(options.wireframeFillAlpha);
            updated++;
        }

        if (updated <= 0) {
            Logger.logFailure(player, "No ramp segments were updated.");
            return;
        }

        Logger.logSuccess(
                player,
                "Updated " + updated + " surf ramp segment"
                        + (updated == 1 ? "" : "s")
                        + " (" + (options.oneSided ? "one-sided" : "two-sided") + ", "
                        + describeVisualStyle(options) + ")."
        );
    }

    private static List<SurfRampEntity> resolveEditTargets(ServerPlayerEntity player, EditState editState) {
        if (player == null || editState == null) {
            return List.of();
        }
        if (!(player.getWorld() instanceof ServerWorld serverWorld)) {
            return List.of();
        }
        String currentWorldKey = serverWorld.getRegistryKey().getValue().toString();
        if (!currentWorldKey.equals(editState.worldKey)) {
            return List.of();
        }

        List<SurfRampEntity> resolved = new ArrayList<>();
        for (UUID uuid : editState.targetRampUuids) {
            if (uuid == null) {
                continue;
            }
            net.minecraft.entity.Entity entity = serverWorld.getEntity(uuid);
            if (entity instanceof SurfRampEntity ramp && ramp.isAlive() && !ramp.isRemoved()) {
                resolved.add(ramp);
            }
        }
        if (!resolved.isEmpty()) {
            return filterEditableTargets(player, resolved);
        }

        List<SurfRampEntity> all = SurfRampEntity.collectAllActiveRamps(serverWorld);
        if (all.isEmpty()) {
            return List.of();
        }

        if (!editState.chainId.isBlank()) {
            for (SurfRampEntity ramp : all) {
                if (editState.chainId.equals(ramp.getChainId())) {
                    resolved.add(ramp);
                }
            }
            if (!resolved.isEmpty()) {
                return filterEditableTargets(player, resolved);
            }
        }

        if (!editState.pathPointsEncoded.isBlank()) {
            for (SurfRampEntity ramp : all) {
                if (editState.pathPointsEncoded.equals(ramp.getPathPointsEncoded())) {
                    resolved.add(ramp);
                }
            }
        }
        return filterEditableTargets(player, resolved);
    }

    private static List<SurfRampEntity> filterEditableTargets(ServerPlayerEntity player, List<SurfRampEntity> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        if (player == null || player.hasPermissionLevel(4)) {
            return candidates;
        }
        List<SurfRampEntity> filtered = new ArrayList<>();
        for (SurfRampEntity ramp : candidates) {
            if (ramp == null || ramp.isRemoved()) {
                continue;
            }
            if (UserPlotManager.canEditEntity(player, ramp)) {
                filtered.add(ramp);
            }
        }
        return filtered;
    }

    private static List<SurfRampEntity> collectConnectedSegments(SurfRampEntity sourceRamp) {
        if (sourceRamp == null) {
            return List.of();
        }
        if (!(sourceRamp.getWorld() instanceof ServerWorld serverWorld)) {
            return List.of(sourceRamp);
        }

        List<SurfRampEntity> all = SurfRampEntity.collectAllActiveRamps(serverWorld);
        if (all.isEmpty()) {
            return List.of(sourceRamp);
        }

        String chainId = sourceRamp.getChainId();
        if (!chainId.isBlank()) {
            List<SurfRampEntity> byChain = new ArrayList<>();
            for (SurfRampEntity ramp : all) {
                if (chainId.equals(ramp.getChainId())) {
                    byChain.add(ramp);
                }
            }
            if (!byChain.isEmpty()) {
                return byChain;
            }
        }

        String pathEncoded = sourceRamp.getPathPointsEncoded();
        if (!pathEncoded.isBlank()) {
            List<SurfRampEntity> byPath = new ArrayList<>();
            for (SurfRampEntity ramp : all) {
                if (pathEncoded.equals(ramp.getPathPointsEncoded())) {
                    byPath.add(ramp);
                }
            }
            if (!byPath.isEmpty()) {
                return byPath;
            }
        }
        return List.of(sourceRamp);
    }

    private static List<Vec3d> parsePathPoints(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return List.of();
        }
        String[] tokens = encoded.split(";");
        List<Vec3d> points = new ArrayList<>();
        for (String token : tokens) {
            String[] xyz = token.split(",");
            if (xyz.length != 3) {
                continue;
            }
            try {
                double x = Double.parseDouble(xyz[0]);
                double y = Double.parseDouble(xyz[1]);
                double z = Double.parseDouble(xyz[2]);
                points.add(new Vec3d(x, y, z));
            } catch (NumberFormatException ignored) {
            }
        }
        if (points.size() < 2) {
            return List.of();
        }
        return List.copyOf(points);
    }

    private static int findPlayerSideSign(Vec3d start, Vec3d end, Vec3d playerPos) {
        Vec3d tangent = new Vec3d(end.x - start.x, 0.0D, end.z - start.z);
        if (tangent.lengthSquared() < 1.0E-8D) {
            return 1;
        }

        tangent = tangent.normalize();
        Vec3d left = new Vec3d(-tangent.z, 0.0D, tangent.x).normalize();
        Vec3d midpoint = start.add(end).multiply(0.5D);
        Vec3d toPlayer = new Vec3d(playerPos.x - midpoint.x, 0.0D, playerPos.z - midpoint.z);
        double lateral = toPlayer.dotProduct(left);
        return lateral >= 0.0D ? 1 : -1;
    }

    private static boolean isCurved(Vec3d start, Vec3d end) {
        return Math.abs(start.x - end.x) > 1.0E-5D && Math.abs(start.z - end.z) > 1.0E-5D;
    }

    private static List<Vec3d> toCenterlinePoints(List<BlockPos> points) {
        List<Vec3d> centerline = new ArrayList<>();
        if (points == null) {
            return centerline;
        }

        Vec3d previous = null;
        for (BlockPos point : points) {
            if (point == null) {
                continue;
            }
            Vec3d centered = SurfRampEntity.blockCenter(point);
            if (previous != null && centered.squaredDistanceTo(previous) < 1.0E-4D) {
                continue;
            }
            centerline.add(centered);
            previous = centered;
        }
        return centerline;
    }

    private static List<Vec3d> extendCenterlineToSelectedBlockEdges(List<Vec3d> points) {
        if (points == null || points.size() < 2) {
            return points == null ? List.of() : List.copyOf(points);
        }

        List<Vec3d> extended = new ArrayList<>(points);
        Vec3d startDirection = horizontalDirection(extended.get(0), extended.get(1));
        if (startDirection.lengthSquared() > 1.0E-8D) {
            extended.set(0, extended.get(0).subtract(startDirection.multiply(distanceToBlockEdge(startDirection))));
        }

        int lastIndex = extended.size() - 1;
        Vec3d endDirection = horizontalDirection(extended.get(lastIndex - 1), extended.get(lastIndex));
        if (endDirection.lengthSquared() > 1.0E-8D) {
            extended.set(lastIndex, extended.get(lastIndex).add(endDirection.multiply(distanceToBlockEdge(endDirection))));
        }
        return List.copyOf(extended);
    }

    private static double distanceToBlockEdge(Vec3d direction) {
        if (direction == null) {
            return 0.0D;
        }
        double dominantAxis = Math.max(Math.abs(direction.x), Math.abs(direction.z));
        if (dominantAxis < 1.0E-8D) {
            return 0.0D;
        }
        return HALF_BLOCK_EDGE_LENGTH / dominantAxis;
    }

    private static Vec3d horizontalDirection(Vec3d from, Vec3d to) {
        if (from == null || to == null) {
            return Vec3d.ZERO;
        }
        Vec3d delta = new Vec3d(to.x - from.x, 0.0D, to.z - from.z);
        if (delta.lengthSquared() < 1.0E-8D) {
            return Vec3d.ZERO;
        }
        return delta.normalize();
    }

    private static List<Vec3d> snapOneSidedCenterlineToBlockFaces(List<Vec3d> points, int sideSign) {
        if (points == null || points.size() < 2) {
            return points == null ? List.of() : List.copyOf(points);
        }

        int normalizedSideSign = sideSign >= 0 ? 1 : -1;
        List<Vec3d> snapped = new ArrayList<>();
        for (int i = 0; i < points.size(); i++) {
            Vec3d point = points.get(i);
            Vec3d left = getPointLeft(points, i);
            Vec3d snappedPoint = point.add(left.multiply((-0.5D + ONE_SIDED_BLOCK_FACE_CLEARANCE) * normalizedSideSign));
            appendIfDistinct(snapped, snappedPoint);
        }
        return List.copyOf(snapped);
    }

    private static Vec3d getPointLeft(List<Vec3d> points, int index) {
        if (points == null || points.size() < 2) {
            return new Vec3d(1.0D, 0.0D, 0.0D);
        }

        Vec3d previous = points.get(Math.max(0, index - 1));
        Vec3d next = points.get(Math.min(points.size() - 1, index + 1));
        Vec3d tangent = new Vec3d(next.x - previous.x, 0.0D, next.z - previous.z);
        if (tangent.lengthSquared() < 1.0E-8D && index > 0) {
            Vec3d current = points.get(index);
            previous = points.get(index - 1);
            tangent = new Vec3d(current.x - previous.x, 0.0D, current.z - previous.z);
        }
        if (tangent.lengthSquared() < 1.0E-8D && index < points.size() - 1) {
            Vec3d current = points.get(index);
            next = points.get(index + 1);
            tangent = new Vec3d(next.x - current.x, 0.0D, next.z - current.z);
        }
        if (tangent.lengthSquared() < 1.0E-8D) {
            return new Vec3d(1.0D, 0.0D, 0.0D);
        }

        tangent = tangent.normalize();
        return new Vec3d(-tangent.z, 0.0D, tangent.x).normalize();
    }

    private static List<Vec3d> densifyCenterlineForSegmentation(List<Vec3d> points) {
        if (points == null || points.size() < 2) {
            return points == null ? List.of() : List.copyOf(points);
        }

        double targetStep = Math.max(
                AUTO_SPLIT_RESAMPLE_MIN_STEP,
                Math.min(0.5D, AUTO_SPLIT_MAX_HORIZONTAL_LENGTH * 0.25D)
        );
        List<Vec3d> densified = new ArrayList<>();
        Vec3d previous = points.get(0);
        densified.add(previous);

        for (int i = 1; i < points.size(); i++) {
            Vec3d current = points.get(i);
            double dx = current.x - previous.x;
            double dz = current.z - previous.z;
            double horizontalDistance = Math.sqrt(dx * dx + dz * dz);

            int slices = Math.max(1, (int) Math.ceil(horizontalDistance / targetStep));
            for (int s = 1; s < slices; s++) {
                double t = (double) s / (double) slices;
                Vec3d intermediate = previous.lerp(current, t);
                if (intermediate.squaredDistanceTo(densified.get(densified.size() - 1)) > 1.0E-6D) {
                    densified.add(intermediate);
                }
            }

            if (current.squaredDistanceTo(densified.get(densified.size() - 1)) > 1.0E-6D) {
                densified.add(current);
            }
            previous = current;
        }

        return List.copyOf(densified);
    }

    private static List<Vec3d> softenCenterlineCorners(List<Vec3d> points) {
        if (points == null || points.size() < 3) {
            return points == null ? List.of() : List.copyOf(points);
        }

        List<Vec3d> softened = new ArrayList<>();
        appendIfDistinct(softened, points.get(0));

        for (int i = 1; i < points.size() - 1; i++) {
            Vec3d previous = points.get(i - 1);
            Vec3d current = points.get(i);
            Vec3d next = points.get(i + 1);

            double incomingHorizontal = horizontalDistance(previous, current);
            double outgoingHorizontal = horizontalDistance(current, next);
            if (incomingHorizontal < 1.0E-5D || outgoingHorizontal < 1.0E-5D) {
                appendIfDistinct(softened, current);
                continue;
            }

            double turnDegrees = computeTurnDegrees(previous, current, next);
            if (turnDegrees < AUTO_SPLIT_CORNER_BLEND_MIN_TURN_DEGREES) {
                appendIfDistinct(softened, current);
                continue;
            }

            double blendDistance = Math.min(
                    Math.min(incomingHorizontal, outgoingHorizontal) * AUTO_SPLIT_CORNER_BLEND_RATIO,
                    AUTO_SPLIT_CORNER_BLEND_MAX_DISTANCE
            );
            if (blendDistance < AUTO_SPLIT_CORNER_BLEND_MIN_DISTANCE) {
                appendIfDistinct(softened, current);
                continue;
            }

            double beforeT = MathHelper.clamp(blendDistance / incomingHorizontal, 0.0D, 1.0D);
            double afterT = MathHelper.clamp(blendDistance / outgoingHorizontal, 0.0D, 1.0D);
            Vec3d beforeCorner = current.lerp(previous, beforeT);
            Vec3d afterCorner = current.lerp(next, afterT);

            if (beforeCorner.squaredDistanceTo(afterCorner) < 1.0E-6D) {
                appendIfDistinct(softened, current);
                continue;
            }

            appendIfDistinct(softened, beforeCorner);

            int blendSamples = MathHelper.clamp(
                    (int) Math.ceil(turnDegrees / AUTO_SPLIT_CORNER_BLEND_TURN_STEP_DEGREES),
                    2,
                    AUTO_SPLIT_CORNER_BLEND_MAX_SAMPLES
            );
            for (int sample = 1; sample < blendSamples; sample++) {
                double t = (double) sample / (double) blendSamples;
                Vec3d blended = quadraticBezier(beforeCorner, current, afterCorner, t);
                appendIfDistinct(softened, blended);
            }

            appendIfDistinct(softened, afterCorner);
        }

        appendIfDistinct(softened, points.get(points.size() - 1));
        return List.copyOf(softened);
    }

    private static boolean hasCurvedPath(List<Vec3d> points) {
        if (points == null || points.size() < 3) {
            return false;
        }
        for (int i = 0; i < points.size() - 2; i++) {
            Vec3d a = points.get(i);
            Vec3d b = points.get(i + 1);
            Vec3d c = points.get(i + 2);
            double abx = b.x - a.x;
            double abz = b.z - a.z;
            double bcx = c.x - b.x;
            double bcz = c.z - b.z;
            double cross = abx * bcz - abz * bcx;
            if (Math.abs(cross) > 1.0E-4D) {
                return true;
            }
        }
        return false;
    }

    private static int resolvePathInsideCurveSideSign(List<Vec3d> points) {
        if (points == null || points.size() < 3) {
            return 1;
        }

        double weightedCross = 0.0D;
        for (int i = 0; i < points.size() - 2; i++) {
            Vec3d a = points.get(i);
            Vec3d b = points.get(i + 1);
            Vec3d c = points.get(i + 2);
            double abx = b.x - a.x;
            double abz = b.z - a.z;
            double bcx = c.x - b.x;
            double bcz = c.z - b.z;
            double cross = abx * bcz - abz * bcx;
            weightedCross += cross;
        }

        if (Math.abs(weightedCross) < 1.0E-6D) {
            Vec3d start = points.get(0);
            Vec3d end = points.get(points.size() - 1);
            return SurfRampEntity.resolveInsideCurveSideSign(start, end);
        }
        return weightedCross > 0.0D ? 1 : -1;
    }

    private static List<SegmentSlice> splitCenterlineForSpawn(List<Vec3d> points) {
        List<SegmentSlice> segments = new ArrayList<>();
        if (points == null || points.size() < 2) {
            return segments;
        }
        if (points.size() == 2) {
            segments.add(new SegmentSlice(0, 1));
            return segments;
        }

        int startIndex = 0;
        int guard = 0;
        while (startIndex < points.size() - 1 && guard++ < points.size() * 3) {
            List<Vec3d> candidate = new ArrayList<>();
            candidate.add(points.get(startIndex));
            int endIndex = startIndex + 1;

            for (int i = startIndex + 1; i < points.size(); i++) {
                candidate.add(points.get(i));

                if (candidate.size() >= 3) {
                    boolean pointsExceeded = candidate.size() > AUTO_SPLIT_MAX_POINTS_PER_RAMP;
                    boolean lengthExceeded = computeHorizontalLength(candidate) > AUTO_SPLIT_MAX_HORIZONTAL_LENGTH;
                    boolean turnExceeded = computeMaxLocalTurnDegrees(candidate) > AUTO_SPLIT_MAX_LOCAL_BEND_DEGREES;
                    boolean directionExceeded = computeSegmentDirectionDeltaDegrees(candidate) > AUTO_SPLIT_MAX_SEGMENT_DIRECTION_DELTA_DEGREES;
                    if (pointsExceeded || lengthExceeded || turnExceeded || directionExceeded) {
                        candidate.remove(candidate.size() - 1);
                        break;
                    }
                }
                endIndex = i;
            }

            if (endIndex <= startIndex) {
                endIndex = Math.min(startIndex + 1, points.size() - 1);
                candidate = new ArrayList<>(points.subList(startIndex, endIndex + 1));
            }

            if (candidate.size() >= 2) {
                segments.add(new SegmentSlice(startIndex, endIndex));
            }

            if (endIndex >= points.size() - 1) {
                break;
            }

            int rewind = Math.max(0, AUTO_SPLIT_SHARED_POINTS - 1);
            startIndex = Math.max(endIndex - rewind, startIndex + 1);
        }

        return segments;
    }

    private static boolean rampFitsInPlot(DataManager.MapData plot, List<Vec3d> centerlinePoints, double rampWidth, boolean twoSided, int sideSign) {
        if (plot == null || centerlinePoints == null || centerlinePoints.size() < 2) {
            return false;
        }
        double safeWidth = Math.max(rampWidth, 0.15D);
        double sampleCount = Math.max(64.0D, centerlinePoints.size() * 32.0D);
        int normalizedSideSign = sideSign >= 0 ? 1 : -1;
        for (int i = 0; i <= sampleCount; i++) {
            double t = (double) i / sampleCount;
            Vec3d center = samplePath(centerlinePoints, t);
            Vec3d left = samplePathLeft(centerlinePoints, t);
            if (!fitsPlotAt(plot, center.x, center.z)) {
                return false;
            }
            if (twoSided) {
                if (!fitsPlotAt(plot, center.x + left.x * safeWidth, center.z + left.z * safeWidth)
                        || !fitsPlotAt(plot, center.x - left.x * safeWidth, center.z - left.z * safeWidth)) {
                    return false;
                }
            } else {
                double offsetX = center.x + left.x * safeWidth * normalizedSideSign;
                double offsetZ = center.z + left.z * safeWidth * normalizedSideSign;
                if (!fitsPlotAt(plot, offsetX, offsetZ)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean fitsPlotAt(DataManager.MapData plot, double x, double z) {
        return !(x < plot.plotMinX || x > plot.plotMaxX
                || z < plot.plotMinZ || z > plot.plotMaxZ);
    }

    private static Vec3d samplePath(List<Vec3d> points, double t) {
        double clampedT = MathHelper.clamp(t, 0.0D, 1.0D);
        if (points.size() == 2) {
            return points.get(0).lerp(points.get(1), clampedT);
        }
        int segmentCount = points.size() - 1;
        double scaled = clampedT * segmentCount;
        int segmentIndex = MathHelper.clamp((int) Math.floor(scaled), 0, segmentCount - 1);
        double localT = scaled - segmentIndex;
        Vec3d p0 = points.get(Math.max(segmentIndex - 1, 0));
        Vec3d p1 = points.get(segmentIndex);
        Vec3d p2 = points.get(segmentIndex + 1);
        Vec3d p3 = points.get(Math.min(segmentIndex + 2, points.size() - 1));
        double t2 = localT * localT;
        double t3 = t2 * localT;
        double x = 0.5D * (
                (2.0D * p1.x)
                        + (-p0.x + p2.x) * localT
                        + (2.0D * p0.x - 5.0D * p1.x + 4.0D * p2.x - p3.x) * t2
                        + (-p0.x + 3.0D * p1.x - 3.0D * p2.x + p3.x) * t3
        );
        double y = 0.5D * (
                (2.0D * p1.y)
                        + (-p0.y + p2.y) * localT
                        + (2.0D * p0.y - 5.0D * p1.y + 4.0D * p2.y - p3.y) * t2
                        + (-p0.y + 3.0D * p1.y - 3.0D * p2.y + p3.y) * t3
        );
        double z = 0.5D * (
                (2.0D * p1.z)
                        + (-p0.z + p2.z) * localT
                        + (2.0D * p0.z - 5.0D * p1.z + 4.0D * p2.z - p3.z) * t2
                        + (-p0.z + 3.0D * p1.z - 3.0D * p2.z + p3.z) * t3
        );
        return new Vec3d(x, y, z);
    }

    private static Vec3d samplePathLeft(List<Vec3d> points, double t) {
        double dt = 1.0D / Math.max(64.0D, points.size() * 32.0D);
        Vec3d before = samplePath(points, Math.max(0.0D, t - dt));
        Vec3d after = samplePath(points, Math.min(1.0D, t + dt));
        Vec3d tangent = new Vec3d(after.x - before.x, 0.0D, after.z - before.z);
        if (tangent.lengthSquared() < 1.0E-8D) {
            Vec3d s = points.get(0);
            Vec3d e = points.get(points.size() - 1);
            tangent = new Vec3d(e.x - s.x, 0.0D, e.z - s.z);
        }
        if (tangent.lengthSquared() < 1.0E-8D) {
            return new Vec3d(1.0D, 0.0D, 0.0D);
        }
        tangent = tangent.normalize();
        return new Vec3d(-tangent.z, 0.0D, tangent.x).normalize();
    }

    private static int countSpawnableSegments(List<Vec3d> centerlinePoints, List<SegmentSlice> rampSegments) {
        if (centerlinePoints == null || centerlinePoints.size() < 2 || rampSegments == null || rampSegments.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (SegmentSlice segmentSlice : rampSegments) {
            if (segmentSlice == null || segmentSlice.endIndex <= segmentSlice.startIndex) {
                continue;
            }
            if (segmentSlice.startIndex < 0 || segmentSlice.endIndex >= centerlinePoints.size()) {
                continue;
            }
            Vec3d segmentStart = centerlinePoints.get(segmentSlice.startIndex);
            Vec3d segmentEnd = centerlinePoints.get(segmentSlice.endIndex);
            if (segmentStart.squaredDistanceTo(segmentEnd) < 1.0E-4D) {
                continue;
            }
            count++;
        }
        return count;
    }

    private static final class SegmentSlice {
        private final int startIndex;
        private final int endIndex;

        private SegmentSlice(int startIndex, int endIndex) {
            this.startIndex = startIndex;
            this.endIndex = endIndex;
        }
    }

    private static double computeHorizontalLength(List<Vec3d> points) {
        if (points == null || points.size() < 2) {
            return 0.0D;
        }
        double length = 0.0D;
        Vec3d previous = points.get(0);
        for (int i = 1; i < points.size(); i++) {
            Vec3d current = points.get(i);
            double dx = current.x - previous.x;
            double dz = current.z - previous.z;
            length += Math.sqrt(dx * dx + dz * dz);
            previous = current;
        }
        return length;
    }

    private static double computeMaxLocalTurnDegrees(List<Vec3d> points) {
        if (points == null || points.size() < 3) {
            return 0.0D;
        }
        double maxTurn = 0.0D;
        for (int i = 0; i < points.size() - 2; i++) {
            double turn = computeTurnDegrees(points.get(i), points.get(i + 1), points.get(i + 2));
            if (turn > maxTurn) {
                maxTurn = turn;
            }
        }
        return maxTurn;
    }

    private static double computeSegmentDirectionDeltaDegrees(List<Vec3d> points) {
        if (points == null || points.size() < 3) {
            return 0.0D;
        }

        Vec3d start = points.get(0);
        Vec3d next = points.get(1);
        Vec3d previous = points.get(points.size() - 2);
        Vec3d end = points.get(points.size() - 1);

        double startDx = next.x - start.x;
        double startDz = next.z - start.z;
        double endDx = end.x - previous.x;
        double endDz = end.z - previous.z;

        double startLen = Math.sqrt(startDx * startDx + startDz * startDz);
        double endLen = Math.sqrt(endDx * endDx + endDz * endDz);
        if (startLen < 1.0E-6D || endLen < 1.0E-6D) {
            return 0.0D;
        }

        double dot = MathHelper.clamp((startDx * endDx + startDz * endDz) / (startLen * endLen), -1.0D, 1.0D);
        return Math.toDegrees(Math.acos(dot));
    }

    private static double computeTurnDegrees(Vec3d a, Vec3d b, Vec3d c) {
        double abx = b.x - a.x;
        double abz = b.z - a.z;
        double bcx = c.x - b.x;
        double bcz = c.z - b.z;

        double abLen = Math.sqrt(abx * abx + abz * abz);
        double bcLen = Math.sqrt(bcx * bcx + bcz * bcz);
        if (abLen < 1.0E-6D || bcLen < 1.0E-6D) {
            return 0.0D;
        }

        double dot = MathHelper.clamp((abx * bcx + abz * bcz) / (abLen * bcLen), -1.0D, 1.0D);
        return Math.toDegrees(Math.acos(dot));
    }

    private static double horizontalDistance(Vec3d a, Vec3d b) {
        double dx = b.x - a.x;
        double dz = b.z - a.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static Vec3d quadraticBezier(Vec3d start, Vec3d control, Vec3d end, double t) {
        double clampedT = MathHelper.clamp(t, 0.0D, 1.0D);
        double invT = 1.0D - clampedT;
        double x = invT * invT * start.x + 2.0D * invT * clampedT * control.x + clampedT * clampedT * end.x;
        double y = invT * invT * start.y + 2.0D * invT * clampedT * control.y + clampedT * clampedT * end.y;
        double z = invT * invT * start.z + 2.0D * invT * clampedT * control.z + clampedT * clampedT * end.z;
        return new Vec3d(x, y, z);
    }

    private static void appendIfDistinct(List<Vec3d> points, Vec3d point) {
        if (points == null || point == null) {
            return;
        }
        if (points.isEmpty() || points.get(points.size() - 1).squaredDistanceTo(point) > 1.0E-6D) {
            points.add(point);
        }
    }

    private static String sanitizeTextureBlockId(String textureBlockId) {
        if (textureBlockId == null) {
            return DEFAULT_SURF_TEXTURE_BLOCK_ID;
        }
        Identifier parsed = Identifier.tryParse(textureBlockId.trim());
        if (parsed == null || !Registries.BLOCK.containsId(parsed)) {
            return DEFAULT_SURF_TEXTURE_BLOCK_ID;
        }
        Block block = Registries.BLOCK.get(parsed);
        if (block == Blocks.AIR) {
            return DEFAULT_SURF_TEXTURE_BLOCK_ID;
        }
        return parsed.toString();
    }

    private static final class PlacementState {
        private final List<BlockPos> points = new ArrayList<>();
        private Vec3d playerDecisionPosition;

        private void clearPoints() {
            this.points.clear();
            this.playerDecisionPosition = null;
        }
    }

    private static final class EditState {
        private final String worldKey;
        private final String chainId;
        private final String pathPointsEncoded;
        private final List<UUID> targetRampUuids;
        private final int insideCurveSign;

        private EditState(
                String worldKey,
                String chainId,
                String pathPointsEncoded,
                List<UUID> targetRampUuids,
                int insideCurveSign
        ) {
            this.worldKey = worldKey == null ? "" : worldKey;
            this.chainId = chainId == null ? "" : chainId;
            this.pathPointsEncoded = pathPointsEncoded == null ? "" : pathPointsEncoded;
            this.targetRampUuids = targetRampUuids == null ? List.of() : List.copyOf(targetRampUuids);
            this.insideCurveSign = insideCurveSign >= 0 ? 1 : -1;
        }
    }

    private static final class PlacementOptions {
        private double width = 1.0D;
        private double drop = 1.0D;
        private String textureBlockId = DEFAULT_SURF_TEXTURE_BLOCK_ID;
        private boolean oneSided = true;
        private boolean outsideCurve = false;
        private String renderMode = SurfRampVisualStyle.MODE_BLOCK;
        private int wireframeColor = SurfRampVisualStyle.DEFAULT_WIREFRAME_COLOR;
        private boolean wireframeFill = SurfRampVisualStyle.DEFAULT_WIREFRAME_FILL;
        private int wireframeFillColor = SurfRampVisualStyle.DEFAULT_WIREFRAME_FILL_COLOR;
        private int wireframeFillAlpha = SurfRampVisualStyle.DEFAULT_WIREFRAME_FILL_ALPHA;
    }
}
