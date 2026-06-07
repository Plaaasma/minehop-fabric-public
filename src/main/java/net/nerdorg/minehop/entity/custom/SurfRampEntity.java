package net.nerdorg.minehop.entity.custom;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.World;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.nerdorg.minehop.item.ModItems;
import net.nerdorg.minehop.util.SurfContact;
import net.nerdorg.minehop.util.SurfRampPlacementManager;
import net.nerdorg.minehop.util.SurfRampVisualStyle;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class SurfRampEntity extends MobEntity {
    private static final TrackedData<Float> START_X = DataTracker.registerData(SurfRampEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> START_Y = DataTracker.registerData(SurfRampEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> START_Z = DataTracker.registerData(SurfRampEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> END_X = DataTracker.registerData(SurfRampEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> END_Y = DataTracker.registerData(SurfRampEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> END_Z = DataTracker.registerData(SurfRampEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> DROP = DataTracker.registerData(SurfRampEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> WIDTH = DataTracker.registerData(SurfRampEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Boolean> TWO_SIDED = DataTracker.registerData(SurfRampEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Integer> SIDE_SIGN = DataTracker.registerData(SurfRampEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<String> TEXTURE_BLOCK_ID = DataTracker.registerData(SurfRampEntity.class, TrackedDataHandlerRegistry.STRING);
    private static final TrackedData<String> RENDER_MODE = DataTracker.registerData(SurfRampEntity.class, TrackedDataHandlerRegistry.STRING);
    private static final TrackedData<Integer> WIREFRAME_COLOR_RGB = DataTracker.registerData(SurfRampEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Boolean> WIREFRAME_FILL = DataTracker.registerData(SurfRampEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Integer> WIREFRAME_FILL_COLOR_RGB = DataTracker.registerData(SurfRampEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> WIREFRAME_FILL_ALPHA = DataTracker.registerData(SurfRampEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<String> PATH_POINTS = DataTracker.registerData(SurfRampEntity.class, TrackedDataHandlerRegistry.STRING);
    private static final TrackedData<Float> PATH_T_START = DataTracker.registerData(SurfRampEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> PATH_T_END = DataTracker.registerData(SurfRampEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Boolean> LINKED_START = DataTracker.registerData(SurfRampEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Boolean> LINKED_END = DataTracker.registerData(SurfRampEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<String> CHAIN_ID = DataTracker.registerData(SurfRampEntity.class, TrackedDataHandlerRegistry.STRING);

    private static final double CONTACT_EPSILON = 0.14D;
    private static final double CONTACT_THICKNESS_BELOW = 0.20D;
    private static final double CONTACT_THICKNESS_ABOVE = 0.14D;
    private static final double COLLISION_SURFACE_THICKNESS = 0.34D;
    private static final double COLLISION_PATCH_OVERLAP = 0.14D;
    private static final int COLLISION_WINDOW_EXTRA_SEGMENTS = 3;
    private static final int COLLISION_APPEND_MAX_SEGMENTS = 16;
    private static final int COLLISION_APPEND_MAX_PATCHES = 128;
    private static final String DEFAULT_TEXTURE_BLOCK_ID = "minecraft:smooth_stone";
    private static final String DEFAULT_CHAIN_ID = "";
    private static final int MAX_PATH_POINTS = 1024;
    private static final Map<World, Set<SurfRampEntity>> ACTIVE_RAMPS_BY_WORLD = new ConcurrentHashMap<>();
    private String cachedPathPointsRaw = "";
    private List<Vec3d> cachedPathPoints = List.of();
    private long cachedDerivedGeometrySignature = Long.MIN_VALUE;
    private double cachedHorizontalLength = 0.01D;
    private int cachedCollisionSegmentCount = 24;
    private int cachedCollisionShapeSegmentCount = 18;
    @Nullable
    private CollisionShapeCache collisionShapeCache;
    private long collisionShapeCacheSignature = Long.MIN_VALUE;
    @Nullable
    private SurfaceSearchCache surfaceSearchCache;
    private long surfaceSearchCacheSignature = Long.MIN_VALUE;
    private boolean boundsDirty = true;
    private String cachedTextureBlockIdForState = null;
    private BlockState cachedTextureBlockState = Blocks.SMOOTH_STONE.getDefaultState();

    public SurfRampEntity(EntityType<? extends MobEntity> entityType, World world) {
        super(entityType, world);
        this.noClip = true;
        this.setPersistent();
    }

    public static DefaultAttributeContainer.Builder createSurfRampAttributes() {
        return MobEntity.createMobAttributes()
                .add(EntityAttributes.MAX_HEALTH, 20.0D);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(START_X, 0.5F);
        builder.add(START_Y, 0.0F);
        builder.add(START_Z, 0.5F);
        builder.add(END_X, 1.5F);
        builder.add(END_Y, 0.0F);
        builder.add(END_Z, 0.5F);
        builder.add(DROP, 1.0F);
        builder.add(WIDTH, 1.0F);
        builder.add(TWO_SIDED, false);
        builder.add(SIDE_SIGN, 1);
        builder.add(TEXTURE_BLOCK_ID, DEFAULT_TEXTURE_BLOCK_ID);
        builder.add(RENDER_MODE, SurfRampVisualStyle.MODE_BLOCK);
        builder.add(WIREFRAME_COLOR_RGB, SurfRampVisualStyle.DEFAULT_WIREFRAME_COLOR);
        builder.add(WIREFRAME_FILL, SurfRampVisualStyle.DEFAULT_WIREFRAME_FILL);
        builder.add(WIREFRAME_FILL_COLOR_RGB, SurfRampVisualStyle.DEFAULT_WIREFRAME_FILL_COLOR);
        builder.add(WIREFRAME_FILL_ALPHA, SurfRampVisualStyle.DEFAULT_WIREFRAME_FILL_ALPHA);
        builder.add(PATH_POINTS, "");
        builder.add(PATH_T_START, 0.0F);
        builder.add(PATH_T_END, 1.0F);
        builder.add(LINKED_START, false);
        builder.add(LINKED_END, false);
        builder.add(CHAIN_ID, DEFAULT_CHAIN_ID);
    }

    @Override
    public void tick() {
        this.baseTick();
        this.setNoGravity(true);
        if (this.boundsDirty) {
            this.refreshBounds();
        }
        this.registerActiveRamp();
    }

    @Override
    public void onTrackedDataSet(TrackedData<?> data) {
        super.onTrackedDataSet(data);
        // On the CLIENT geometry arrives via dataTracker sync (also after a GUI edit). Without this,
        // a resized/edited ramp keeps its old bounding box + caches client-side, so the collision
        // solver's bbox-gated lookup misses the now-bigger ramp and the player falls through. Force
        // a rebuild whenever any geometry-affecting field changes.
        if (data == START_X || data == START_Y || data == START_Z
                || data == END_X || data == END_Y || data == END_Z
                || data == DROP || data == WIDTH || data == TWO_SIDED || data == SIDE_SIGN
                || data == PATH_POINTS || data == PATH_T_START || data == PATH_T_END
                || data == LINKED_START || data == LINKED_END) {
            this.invalidateGeometryCaches();
        }
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        this.unregisterActiveRamp();
        super.remove(reason);
    }

    @Override
    public boolean collidesWith(Entity other) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean isPushedByFluids() {
        return false;
    }

    @Override
    public boolean cannotDespawn() {
        return true;
    }

    @Override
    public boolean canImmediatelyDespawn(double distanceSquared) {
        return false;
    }

    @Override
    public boolean damage(ServerWorld world, DamageSource source, float amount) {
        if (source.isOf(DamageTypes.GENERIC_KILL) || source.isOf(DamageTypes.OUT_OF_WORLD)) {
            this.kill(world);
            return true;
        }
        return false;
    }

    @Override
    public void kill(ServerWorld world) {
        this.remove(Entity.RemovalReason.KILLED);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        this.setGeometry(
                new Vec3d(nbt.getDouble("startX"), nbt.getDouble("startY"), nbt.getDouble("startZ")),
                new Vec3d(nbt.getDouble("endX"), nbt.getDouble("endY"), nbt.getDouble("endZ")),
                nbt.getDouble("drop"),
                nbt.getDouble("width"),
                nbt.getBoolean("twoSided"),
                nbt.getInt("sideSign")
        );
        this.setPathPointsEncoded(nbt.contains("pathPoints") ? nbt.getString("pathPoints") : "");
        this.setPathTRange(
                nbt.contains("pathTStart") ? nbt.getDouble("pathTStart") : 0.0D,
                nbt.contains("pathTEnd") ? nbt.getDouble("pathTEnd") : 1.0D
        );
        this.setLinkedSeams(
                nbt.contains("linkedStart") && nbt.getBoolean("linkedStart"),
                nbt.contains("linkedEnd") && nbt.getBoolean("linkedEnd")
        );
        this.setChainId(nbt.contains("chainId") ? nbt.getString("chainId") : DEFAULT_CHAIN_ID);
        if (nbt.contains("textureBlockId")) {
            this.setTextureBlockId(nbt.getString("textureBlockId"));
        } else {
            this.setTextureBlockId(DEFAULT_TEXTURE_BLOCK_ID);
        }
        if (nbt.contains("renderMode")) {
            this.setRenderMode(nbt.getString("renderMode"));
        } else {
            this.setRenderMode(SurfRampVisualStyle.MODE_BLOCK);
        }
        if (nbt.contains("wireframeColorRgb")) {
            this.setWireframeColorRgb(nbt.getInt("wireframeColorRgb"));
        } else {
            this.setWireframeColorRgb(SurfRampVisualStyle.DEFAULT_WIREFRAME_COLOR);
        }
        this.setWireframeFillEnabled(nbt.contains("wireframeFill") && nbt.getBoolean("wireframeFill"));
        if (nbt.contains("wireframeFillColorRgb")) {
            this.setWireframeFillColorRgb(nbt.getInt("wireframeFillColorRgb"));
        } else {
            this.setWireframeFillColorRgb(SurfRampVisualStyle.DEFAULT_WIREFRAME_FILL_COLOR);
        }
        if (nbt.contains("wireframeFillAlpha")) {
            this.setWireframeFillAlpha(nbt.getInt("wireframeFillAlpha"));
        } else {
            this.setWireframeFillAlpha(SurfRampVisualStyle.DEFAULT_WIREFRAME_FILL_ALPHA);
        }
        this.refreshBounds();
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        Vec3d start = this.getStart();
        Vec3d end = this.getEnd();

        nbt.putDouble("startX", start.x);
        nbt.putDouble("startY", start.y);
        nbt.putDouble("startZ", start.z);

        nbt.putDouble("endX", end.x);
        nbt.putDouble("endY", end.y);
        nbt.putDouble("endZ", end.z);

        nbt.putDouble("drop", this.getDrop());
        nbt.putDouble("width", this.getRampWidth());
        nbt.putBoolean("twoSided", this.isTwoSided());
        nbt.putInt("sideSign", this.getSideSign());
        nbt.putString("textureBlockId", this.getTextureBlockId());
        nbt.putString("renderMode", this.getRenderMode());
        nbt.putInt("wireframeColorRgb", this.getWireframeColorRgb());
        nbt.putBoolean("wireframeFill", this.isWireframeFillEnabled());
        nbt.putInt("wireframeFillColorRgb", this.getWireframeFillColorRgb());
        nbt.putInt("wireframeFillAlpha", this.getWireframeFillAlpha());
        nbt.putString("pathPoints", this.dataTracker.get(PATH_POINTS));
        nbt.putDouble("pathTStart", this.getPathTStart());
        nbt.putDouble("pathTEnd", this.getPathTEnd());
        nbt.putBoolean("linkedStart", this.isLinkedStart());
        nbt.putBoolean("linkedEnd", this.isLinkedEnd());
        nbt.putString("chainId", this.getChainId());
    }

    public void setGeometry(Vec3d start, Vec3d end, double drop, double width, boolean twoSided, int sideSign) {
        this.dataTracker.set(START_X, (float) start.x);
        this.dataTracker.set(START_Y, (float) start.y);
        this.dataTracker.set(START_Z, (float) start.z);

        this.dataTracker.set(END_X, (float) end.x);
        this.dataTracker.set(END_Y, (float) end.y);
        this.dataTracker.set(END_Z, (float) end.z);

        this.dataTracker.set(DROP, (float) Math.max(drop, 0.01D));
        this.dataTracker.set(WIDTH, (float) Math.max(width, 0.15D));
        this.dataTracker.set(TWO_SIDED, twoSided);

        int clampedSign = sideSign >= 0 ? 1 : -1;
        this.dataTracker.set(SIDE_SIGN, clampedSign);
        this.invalidateGeometryCaches();

        Vec3d midpoint = start.add(end).multiply(0.5D);
        this.refreshPositionAndAngles(midpoint.x, midpoint.y + Math.max(drop, 0.01D) * 0.5D, midpoint.z, 0.0F, 0.0F);
        this.refreshBounds();
    }

    public Vec3d getStart() {
        return new Vec3d(this.dataTracker.get(START_X), this.dataTracker.get(START_Y), this.dataTracker.get(START_Z));
    }

    public Vec3d getEnd() {
        return new Vec3d(this.dataTracker.get(END_X), this.dataTracker.get(END_Y), this.dataTracker.get(END_Z));
    }

    public double getDrop() {
        return this.dataTracker.get(DROP);
    }

    public double getRampWidth() {
        return this.dataTracker.get(WIDTH);
    }

    public boolean isTwoSided() {
        return this.dataTracker.get(TWO_SIDED);
    }

    public int getSideSign() {
        int sign = this.dataTracker.get(SIDE_SIGN);
        return sign >= 0 ? 1 : -1;
    }

    public boolean isLinkedStart() {
        return this.dataTracker.get(LINKED_START);
    }

    public boolean isLinkedEnd() {
        return this.dataTracker.get(LINKED_END);
    }

    public String getChainId() {
        String raw = this.dataTracker.get(CHAIN_ID);
        if (raw == null) {
            return DEFAULT_CHAIN_ID;
        }
        String trimmed = raw.trim();
        return trimmed.length() <= 96 ? trimmed : trimmed.substring(0, 96);
    }

    public void setChainId(String chainId) {
        String value = chainId == null ? DEFAULT_CHAIN_ID : chainId.trim();
        if (value.length() > 96) {
            value = value.substring(0, 96);
        }
        this.dataTracker.set(CHAIN_ID, value);
    }

    public String getPathPointsEncoded() {
        String encoded = this.dataTracker.get(PATH_POINTS);
        return encoded == null ? "" : encoded;
    }

    public String getTextureBlockId() {
        return sanitizeTextureBlockId(this.dataTracker.get(TEXTURE_BLOCK_ID));
    }

    public void setTextureBlockId(String textureBlockId) {
        String sanitized = sanitizeTextureBlockId(textureBlockId);
        this.dataTracker.set(TEXTURE_BLOCK_ID, sanitized);
        this.cachedTextureBlockIdForState = null;
    }

    public String getRenderMode() {
        return SurfRampVisualStyle.sanitizeMode(this.dataTracker.get(RENDER_MODE));
    }

    public void setRenderMode(String renderMode) {
        this.dataTracker.set(RENDER_MODE, SurfRampVisualStyle.sanitizeMode(renderMode));
    }

    public boolean isWireframeMode() {
        return SurfRampVisualStyle.MODE_WIREFRAME.equals(this.getRenderMode());
    }

    public int getWireframeColorRgb() {
        return SurfRampVisualStyle.sanitizeColor(
                this.dataTracker.get(WIREFRAME_COLOR_RGB),
                SurfRampVisualStyle.DEFAULT_WIREFRAME_COLOR
        );
    }

    public void setWireframeColorRgb(int wireframeColorRgb) {
        this.dataTracker.set(
                WIREFRAME_COLOR_RGB,
                SurfRampVisualStyle.sanitizeColor(wireframeColorRgb, SurfRampVisualStyle.DEFAULT_WIREFRAME_COLOR)
        );
    }

    public boolean isWireframeFillEnabled() {
        return this.dataTracker.get(WIREFRAME_FILL);
    }

    public void setWireframeFillEnabled(boolean wireframeFillEnabled) {
        this.dataTracker.set(WIREFRAME_FILL, wireframeFillEnabled);
    }

    public int getWireframeFillColorRgb() {
        return SurfRampVisualStyle.sanitizeColor(
                this.dataTracker.get(WIREFRAME_FILL_COLOR_RGB),
                SurfRampVisualStyle.DEFAULT_WIREFRAME_FILL_COLOR
        );
    }

    public void setWireframeFillColorRgb(int wireframeFillColorRgb) {
        this.dataTracker.set(
                WIREFRAME_FILL_COLOR_RGB,
                SurfRampVisualStyle.sanitizeColor(wireframeFillColorRgb, SurfRampVisualStyle.DEFAULT_WIREFRAME_FILL_COLOR)
        );
    }

    public int getWireframeFillAlpha() {
        return SurfRampVisualStyle.sanitizeAlpha(
                this.dataTracker.get(WIREFRAME_FILL_ALPHA),
                SurfRampVisualStyle.DEFAULT_WIREFRAME_FILL_ALPHA
        );
    }

    public void setWireframeFillAlpha(int wireframeFillAlpha) {
        this.dataTracker.set(
                WIREFRAME_FILL_ALPHA,
                SurfRampVisualStyle.sanitizeAlpha(wireframeFillAlpha, SurfRampVisualStyle.DEFAULT_WIREFRAME_FILL_ALPHA)
        );
    }

    public BlockState getTextureBlockState() {
        String textureId = this.getTextureBlockId();
        if (textureId.equals(this.cachedTextureBlockIdForState)) {
            return this.cachedTextureBlockState;
        }

        Identifier id = Identifier.tryParse(textureId);
        if (id == null || !Registries.BLOCK.containsId(id)) {
            this.cachedTextureBlockIdForState = textureId;
            this.cachedTextureBlockState = Blocks.SMOOTH_STONE.getDefaultState();
            return this.cachedTextureBlockState;
        }
        Block block = Registries.BLOCK.get(id);
        if (block == Blocks.AIR) {
            this.cachedTextureBlockIdForState = textureId;
            this.cachedTextureBlockState = Blocks.SMOOTH_STONE.getDefaultState();
            return this.cachedTextureBlockState;
        }
        this.cachedTextureBlockIdForState = textureId;
        this.cachedTextureBlockState = block.getDefaultState();
        return this.cachedTextureBlockState;
    }

    @Override
    public ActionResult interactMob(PlayerEntity player, Hand hand) {
        ItemStack held = player.getStackInHand(hand);
        if (held.isOf(ModItems.SURF_STICK)) {
            if (!this.getWorld().isClient && player instanceof ServerPlayerEntity serverPlayer) {
                SurfRampPlacementManager.openEditor(serverPlayer, this);
            }
            return ActionResult.SUCCESS;
        }

        if (!player.isSneaking() || !player.getAbilities().creativeMode) {
            return ActionResult.PASS;
        }

        if (!(held.getItem() instanceof BlockItem blockItem)) {
            return ActionResult.PASS;
        }

        Identifier blockId = Registries.BLOCK.getId(blockItem.getBlock());
        if (blockId == null) {
            return ActionResult.PASS;
        }
        String newTextureId = sanitizeTextureBlockId(blockId.toString());
        if (!this.getWorld().isClient) {
            this.setTextureBlockId(newTextureId);
            player.sendMessage(Text.literal("Ramp texture set to " + newTextureId), true);
        }
        return ActionResult.SUCCESS;
    }

    public boolean isCurved() {
        List<Vec3d> pathPoints = this.getPathPoints();
        if (pathPoints.size() > 2) {
            return true;
        }
        Vec3d start = this.getStart();
        Vec3d end = this.getEnd();
        return Math.abs(end.x - start.x) > 0.25D && Math.abs(end.z - start.z) > 0.25D;
    }

    public void setCenterlinePoints(List<Vec3d> points) {
        this.setCenterlinePoints(points, 0.0D, 1.0D);
    }

    public void setCenterlinePoints(List<Vec3d> points, double pathStartT, double pathEndT) {
        this.setPathPointsEncoded(this.encodePathPoints(points));
        this.setPathTRange(pathStartT, pathEndT);
    }

    public void setLinkedSeams(boolean linkedStart, boolean linkedEnd) {
        this.dataTracker.set(LINKED_START, linkedStart);
        this.dataTracker.set(LINKED_END, linkedEnd);
        this.invalidateGeometryCaches();
    }

    public static Vec3d blockCenter(BlockPos pos) {
        return new Vec3d(pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D);
    }

    public static int resolveInsideCurveSideSign(Vec3d start, Vec3d end) {
        Vec3d control = computeControlPoint(start, end);
        Vec3d first = new Vec3d(control.x - start.x, 0.0D, control.z - start.z);
        Vec3d second = new Vec3d(end.x - control.x, 0.0D, end.z - control.z);
        double cross = first.x * second.z - first.z * second.x;
        if (Math.abs(cross) < 1.0E-6D) {
            return 1;
        }
        return cross > 0.0D ? 1 : -1;
    }

    public static boolean hasNearbyRamp(World world, Box queryBox, double expand) {
        if (world == null || queryBox == null) {
            return false;
        }
        Set<SurfRampEntity> rampsInWorld = ACTIVE_RAMPS_BY_WORLD.get(world);
        if (rampsInWorld == null || rampsInWorld.isEmpty()) {
            return false;
        }
        Box expanded = queryBox.expand(expand);
        Iterator<SurfRampEntity> iterator = rampsInWorld.iterator();
        while (iterator.hasNext()) {
            SurfRampEntity ramp = iterator.next();
            if (ramp == null || !ramp.isAlive() || ramp.isRemoved()) {
                rampsInWorld.remove(ramp);
                continue;
            }
            if (ramp.getBoundingBox().intersects(expanded)) {
                return true;
            }
        }
        if (rampsInWorld.isEmpty()) {
            ACTIVE_RAMPS_BY_WORLD.remove(world, rampsInWorld);
        }
        return false;
    }

    public static List<SurfRampEntity> collectNearbyRamps(World world, Box queryBox, double expand) {
        List<SurfRampEntity> ramps = new ArrayList<>();
        if (world == null || queryBox == null) {
            return ramps;
        }
        Set<SurfRampEntity> rampsInWorld = ACTIVE_RAMPS_BY_WORLD.get(world);
        if (rampsInWorld == null || rampsInWorld.isEmpty()) {
            return ramps;
        }
        Box expanded = queryBox.expand(expand);
        Iterator<SurfRampEntity> iterator = rampsInWorld.iterator();
        while (iterator.hasNext()) {
            SurfRampEntity ramp = iterator.next();
            if (ramp == null || !ramp.isAlive() || ramp.isRemoved()) {
                rampsInWorld.remove(ramp);
                continue;
            }
            if (ramp.getBoundingBox().intersects(expanded)) {
                ramps.add(ramp);
            }
        }
        if (rampsInWorld.isEmpty()) {
            ACTIVE_RAMPS_BY_WORLD.remove(world, rampsInWorld);
        }
        return ramps;
    }

    public static List<SurfRampEntity> collectAllActiveRamps(World world) {
        List<SurfRampEntity> ramps = new ArrayList<>();
        if (world == null) {
            return ramps;
        }
        Set<SurfRampEntity> rampsInWorld = ACTIVE_RAMPS_BY_WORLD.get(world);
        if (rampsInWorld == null || rampsInWorld.isEmpty()) {
            return ramps;
        }
        Iterator<SurfRampEntity> iterator = rampsInWorld.iterator();
        while (iterator.hasNext()) {
            SurfRampEntity ramp = iterator.next();
            if (ramp == null || !ramp.isAlive() || ramp.isRemoved()) {
                rampsInWorld.remove(ramp);
                continue;
            }
            ramps.add(ramp);
        }
        if (rampsInWorld.isEmpty()) {
            ACTIVE_RAMPS_BY_WORLD.remove(world, rampsInWorld);
        }
        return ramps;
    }

    public boolean isNearEndpointXZ(double x, double z, double endpointTThreshold, double lateralToleranceExtra) {
        int samples = MathHelper.clamp((int) Math.ceil(this.getHorizontalLength() * 10.0D), 24, 220);
        double bestDistanceSquared = Double.MAX_VALUE;
        double bestT = 0.0D;

        for (int i = 0; i <= samples; i++) {
            double t = (double) i / (double) samples;
            Vec3d center = this.getCenterlinePoint(t);
            double dx = x - center.x;
            double dz = z - center.z;
            double distanceSquared = dx * dx + dz * dz;
            if (distanceSquared < bestDistanceSquared) {
                bestDistanceSquared = distanceSquared;
                bestT = t;
            }
        }

        double lateralTolerance = this.getRampWidth() + Math.max(lateralToleranceExtra, 0.0D);
        if (bestDistanceSquared > lateralTolerance * lateralTolerance) {
            return false;
        }

        double clampedThreshold = MathHelper.clamp(endpointTThreshold, 0.01D, 0.45D);
        return bestT <= clampedThreshold || bestT >= 1.0D - clampedThreshold;
    }

    public boolean isNearRampXZ(double x, double z, double lateralToleranceExtra) {
        int samples = MathHelper.clamp((int) Math.ceil(this.getHorizontalLength() * 10.0D), 24, 220);
        double bestDistanceSquared = Double.MAX_VALUE;

        for (int i = 0; i <= samples; i++) {
            double t = (double) i / (double) samples;
            Vec3d center = this.getCenterlinePoint(t);
            double dx = x - center.x;
            double dz = z - center.z;
            double distanceSquared = dx * dx + dz * dz;
            if (distanceSquared < bestDistanceSquared) {
                bestDistanceSquared = distanceSquared;
            }
        }

        double lateralTolerance = this.getRampWidth() + Math.max(lateralToleranceExtra, 0.0D);
        return bestDistanceSquared <= lateralTolerance * lateralTolerance;
    }

    private void registerActiveRamp() {
        World world = this.getWorld();
        if (world == null) {
            return;
        }
        Set<SurfRampEntity> rampsInWorld = ACTIVE_RAMPS_BY_WORLD.computeIfAbsent(
                world,
                key -> Collections.newSetFromMap(new ConcurrentHashMap<>())
        );
        rampsInWorld.add(this);
    }

    private void unregisterActiveRamp() {
        World world = this.getWorld();
        if (world == null) {
            return;
        }
        Set<SurfRampEntity> rampsInWorld = ACTIVE_RAMPS_BY_WORLD.get(world);
        if (rampsInWorld == null) {
            return;
        }
        rampsInWorld.remove(this);
        if (rampsInWorld.isEmpty()) {
            ACTIVE_RAMPS_BY_WORLD.remove(world, rampsInWorld);
        }
    }

    @Nullable
    public SurfContact sampleContact(double sampleX, double sampleZ, double minFeetY, double maxFeetY) {
        if (!this.isWithinSurfaceSearchBounds(sampleX, sampleZ, minFeetY, maxFeetY)) {
            return null;
        }
        SurfacePoint point = this.findSurfacePoint(sampleX, sampleZ);
        if (point == null) {
            return null;
        }

        if (point.surfaceY < minFeetY - CONTACT_THICKNESS_BELOW || point.surfaceY > maxFeetY + CONTACT_THICKNESS_ABOVE) {
            return null;
        }

        return new SurfContact(point.normal, point.surfaceY, this.isHardEndpointT(point.t));
    }

    @Nullable
    public SurfContact sampleNearestContact(double sampleX, double sampleZ, double targetFeetY, double maxVerticalDistance) {
        if (!this.isWithinSurfaceSearchBounds(
                sampleX,
                sampleZ,
                targetFeetY - maxVerticalDistance,
                targetFeetY + maxVerticalDistance
        )) {
            return null;
        }
        SurfacePoint point = this.findSurfacePoint(sampleX, sampleZ);
        if (point == null) {
            return null;
        }
        if (Math.abs(targetFeetY - point.surfaceY) > maxVerticalDistance) {
            return null;
        }
        return new SurfContact(point.normal, point.surfaceY, this.isHardEndpointT(point.t));
    }

    @Nullable
    public SurfContact sampleSurface(double sampleX, double sampleZ, double feetY, double maxBelow, double maxAbove) {
        SurfacePoint point = this.findSurfacePoint(sampleX, sampleZ);
        if (point == null) {
            return null;
        }
        if (point.surfaceY < feetY - maxBelow || point.surfaceY > feetY + maxAbove) {
            return null;
        }
        return new SurfContact(point.normal, point.surfaceY, this.isHardEndpointT(point.t));
    }

    public CollisionAppendStats appendCollisionShapes(Box queryBox, List<VoxelShape> shapes) {
        if (shapes == null || queryBox == null) {
            return CollisionAppendStats.EMPTY;
        }
        CollisionShapeCache cache = this.getOrBuildCollisionShapeCache();
        if (cache == null) {
            return CollisionAppendStats.EMPTY;
        }
        int segmentsTested = 0;
        int segmentsIntersected = 0;
        int patchesTested = 0;
        int patchesAppended = 0;
        Box expandedQuery = queryBox.expand(0.2D);
        double queryCenterX = (expandedQuery.minX + expandedQuery.maxX) * 0.5D;
        double queryCenterZ = (expandedQuery.minZ + expandedQuery.maxZ) * 0.5D;

        double queryHalfX = (expandedQuery.maxX - expandedQuery.minX) * 0.5D;
        double queryHalfZ = (expandedQuery.maxZ - expandedQuery.minZ) * 0.5D;
        double queryRadiusXZ = Math.hypot(queryHalfX, queryHalfZ) + 0.24D;
        double segmentLength = Math.max(cache.segmentLength, 0.06D);
        int requiredSegments = (int) Math.ceil(queryRadiusXZ / segmentLength) + 4;
        int halfWindowSegments = Math.max(6, requiredSegments);
        double closestT = cache.estimateClosestT(queryCenterX, queryCenterZ);
        int centerSegment = MathHelper.clamp((int) Math.floor(closestT * (double) cache.segmentCount), 0, cache.segmentCount);
        int loopStart = Math.max(
                cache.minLogicalIndex,
                centerSegment - halfWindowSegments - cache.extraSegments
        );
        int loopEnd = Math.min(
                cache.maxLogicalIndexExclusive,
                centerSegment + halfWindowSegments + cache.extraSegments
        );
        if (loopEnd <= loopStart) {
            loopStart = cache.minLogicalIndex;
            loopEnd = cache.maxLogicalIndexExclusive;
        }
        int segmentBudget = Math.max(
                COLLISION_APPEND_MAX_SEGMENTS,
                Math.min(220, requiredSegments + 12)
        );
        int patchBudget = Math.max(
                COLLISION_APPEND_MAX_PATCHES,
                Math.min(1280, segmentBudget * Math.max(cache.widthSlices, 4))
        );

        for (int logicalIndex = loopStart; logicalIndex < loopEnd; logicalIndex++) {
            if (segmentsIntersected >= segmentBudget || patchesAppended >= patchBudget) {
                break;
            }
            CollisionSegmentCache segment = cache.getSegment(logicalIndex);
            if (segment == null) {
                continue;
            }
            segmentsTested++;
            if (!segment.bounds.intersects(expandedQuery)) {
                continue;
            }
            segmentsIntersected++;
            CollisionPatchCache[] patches = segment.getOrBuildPatches(this, cache);
            for (CollisionPatchCache patch : patches) {
                if (patchesAppended >= patchBudget) {
                    break;
                }
                patchesTested++;
                if (patch.box.intersects(expandedQuery)) {
                    shapes.add(patch.shape);
                    patchesAppended++;
                }
            }
        }
        return new CollisionAppendStats(segmentsTested, segmentsIntersected, patchesTested, patchesAppended);
    }

    public int prewarmCollisionShapeCache(Box queryBox, int maxSegmentsToBuild) {
        if (queryBox == null || maxSegmentsToBuild <= 0) {
            return 0;
        }
        CollisionShapeCache cache = this.getOrBuildCollisionShapeCache();
        if (cache == null) {
            return 0;
        }

        Box expandedQuery = queryBox.expand(0.2D);
        double queryCenterX = (expandedQuery.minX + expandedQuery.maxX) * 0.5D;
        double queryCenterZ = (expandedQuery.minZ + expandedQuery.maxZ) * 0.5D;
        double queryHalfX = (expandedQuery.maxX - expandedQuery.minX) * 0.5D;
        double queryHalfZ = (expandedQuery.maxZ - expandedQuery.minZ) * 0.5D;
        double queryRadiusXZ = Math.hypot(queryHalfX, queryHalfZ) + 0.24D;
        double segmentLength = Math.max(cache.segmentLength, 0.06D);
        int halfWindowSegments = Math.max(6, (int) Math.ceil(queryRadiusXZ / segmentLength) + 4);
        double closestT = cache.estimateClosestT(queryCenterX, queryCenterZ);
        int centerSegment = MathHelper.clamp((int) Math.floor(closestT * (double) cache.segmentCount), 0, cache.segmentCount);
        int loopStart = Math.max(
                cache.minLogicalIndex,
                centerSegment - halfWindowSegments - cache.extraSegments
        );
        int loopEnd = Math.min(
                cache.maxLogicalIndexExclusive,
                centerSegment + halfWindowSegments + cache.extraSegments
        );
        if (loopEnd <= loopStart) {
            loopStart = cache.minLogicalIndex;
            loopEnd = cache.maxLogicalIndexExclusive;
        }

        int builtSegments = 0;
        for (int logicalIndex = loopStart; logicalIndex < loopEnd && builtSegments < maxSegmentsToBuild; logicalIndex++) {
            CollisionSegmentCache segment = cache.getSegment(logicalIndex);
            if (segment == null || segment.patches != null) {
                continue;
            }
            if (!segment.bounds.intersects(expandedQuery)) {
                continue;
            }
            segment.getOrBuildPatches(this, cache);
            builtSegments++;
        }
        return builtSegments;
    }

    public void prewarmSurfaceSearchCache() {
        this.getOrBuildSurfaceSearchCache();
    }

    private boolean isWithinSurfaceSearchBounds(double sampleX, double sampleZ, double minY, double maxY) {
        Box bounds = this.getBoundingBox();
        double lateralExtra = Math.max(this.getRampWidth() * 0.35D, 0.55D);
        if (sampleX < bounds.minX - lateralExtra || sampleX > bounds.maxX + lateralExtra
                || sampleZ < bounds.minZ - lateralExtra || sampleZ > bounds.maxZ + lateralExtra) {
            return false;
        }
        double verticalPad = Math.max(this.getDrop(), 0.25D) + 0.80D;
        return maxY >= bounds.minY - verticalPad && minY <= bounds.maxY + verticalPad;
    }

    @Nullable
    private CollisionShapeCache getOrBuildCollisionShapeCache() {
        if (this.collisionShapeCache != null) {
            return this.collisionShapeCache;
        }

        CollisionShapeCache rebuilt = this.buildCollisionShapeCache();
        this.collisionShapeCache = rebuilt;
        this.collisionShapeCacheSignature = 1L;
        return rebuilt;
    }

    @Nullable
    private SurfaceSearchCache getOrBuildSurfaceSearchCache() {
        if (this.surfaceSearchCache != null) {
            return this.surfaceSearchCache;
        }
        SurfaceSearchCache rebuilt = this.buildSurfaceSearchCache();
        this.surfaceSearchCache = rebuilt;
        this.surfaceSearchCacheSignature = 1L;
        return rebuilt;
    }

    @Nullable
    private SurfaceSearchCache buildSurfaceSearchCache() {
        int segmentCount = this.getCollisionSegmentCount();
        if (segmentCount <= 0) {
            return null;
        }

        int sampleCount = segmentCount + 1;
        double[] centerX = new double[sampleCount];
        double[] centerZ = new double[sampleCount];
        double[] tangentX = new double[sampleCount];
        double[] tangentZ = new double[sampleCount];
        double[] leftX = new double[sampleCount];
        double[] leftZ = new double[sampleCount];
        double[] baseY = new double[sampleCount];

        for (int i = 0; i <= segmentCount; i++) {
            double t = (double) i / (double) segmentCount;
            Vec3d center = this.getCenterlinePoint(t);
            Vec3d tangent = this.getTangent(t);

            double tx = tangent.x;
            double tz = tangent.z;
            double tangentLength = Math.sqrt(tx * tx + tz * tz);
            if (tangentLength < 1.0E-8D) {
                tx = 1.0D;
                tz = 0.0D;
                tangentLength = 1.0D;
            }
            tx /= tangentLength;
            tz /= tangentLength;

            centerX[i] = center.x;
            centerZ[i] = center.z;
            tangentX[i] = tx;
            tangentZ[i] = tz;
            leftX[i] = -tz;
            leftZ[i] = tx;
            baseY[i] = this.sampleBaseY(t);
        }

        double[] alongSlope = new double[sampleCount];
        for (int i = 0; i <= segmentCount; i++) {
            int prev = Math.max(i - 1, 0);
            int next = Math.min(i + 1, segmentCount);
            double dx = centerX[next] - centerX[prev];
            double dz = centerZ[next] - centerZ[prev];
            double horizontalDistance = Math.hypot(dx, dz);
            if (horizontalDistance < 1.0E-6D) {
                alongSlope[i] = 0.0D;
            } else {
                alongSlope[i] = (baseY[next] - baseY[prev]) / horizontalDistance;
            }
        }

        double horizontalLength = Math.max(this.getHorizontalLength(), 0.5D);
        double segmentLength = Math.max(horizontalLength / Math.max(segmentCount, 1), 0.06D);
        return new SurfaceSearchCache(
                segmentCount,
                segmentLength,
                centerX,
                centerZ,
                tangentX,
                tangentZ,
                leftX,
                leftZ,
                baseY,
                alongSlope
        );
    }

    @Nullable
    private CollisionShapeCache buildCollisionShapeCache() {
        double width = Math.max(this.getRampWidth(), 0.15D);
        int segmentCount = this.getCollisionShapeSegmentCount();
        int widthSlices = this.getCollisionShapeWidthSlices(width);
        if (segmentCount <= 0 || widthSlices <= 0) {
            return null;
        }

        double lateralStart = this.isTwoSided() ? -width : 0.0D;
        double lateralStep = (width - lateralStart) / (double) widthSlices;
        int sideSign = this.getSideSign();

        double tPad = 0.18D;
        double seamPad = 2.25D;
        double startPad = this.isLinkedStart() ? seamPad : tPad;
        double endPad = this.isLinkedEnd() ? seamPad : tPad;
        int extraSegments = (this.isLinkedStart() || this.isLinkedEnd())
                ? COLLISION_WINDOW_EXTRA_SEGMENTS
                : 1;

        int minLogicalIndex = -extraSegments;
        int maxLogicalIndexExclusive = segmentCount + extraSegments;
        CollisionSegmentCache[] segments = new CollisionSegmentCache[maxLogicalIndexExclusive - minLogicalIndex];

        for (int logicalIndex = minLogicalIndex; logicalIndex < maxLogicalIndexExclusive; logicalIndex++) {
            double t0 = ((double) logicalIndex - startPad) / (double) segmentCount;
            double t1 = ((double) logicalIndex + 1.0D + endPad) / (double) segmentCount;
            if (t1 <= 0.0D || t0 >= 1.0D) {
                continue;
            }

            double sampleT0 = MathHelper.clamp(t0, 0.0D, 1.0D);
            double sampleT1 = MathHelper.clamp(t1, 0.0D, 1.0D);
            if (sampleT1 <= sampleT0 + 1.0E-6D) {
                continue;
            }

            Vec3d center0 = this.getCenterlinePoint(sampleT0);
            Vec3d center1 = this.getCenterlinePoint(sampleT1);
            Vec3d left0 = this.getLeftNormal(sampleT0);
            Vec3d left1 = this.getLeftNormal(sampleT1);
            double edgeL0 = lateralStart;
            double edgeL1 = width;

            Vec3d p00 = this.sampleCollisionVertex(sampleT0, center0, left0, edgeL0, width, sideSign);
            Vec3d p01 = this.sampleCollisionVertex(sampleT0, center0, left0, edgeL1, width, sideSign);
            Vec3d p10 = this.sampleCollisionVertex(sampleT1, center1, left1, edgeL0, width, sideSign);
            Vec3d p11 = this.sampleCollisionVertex(sampleT1, center1, left1, edgeL1, width, sideSign);

            double minX = Math.min(Math.min(p00.x, p01.x), Math.min(p10.x, p11.x)) - COLLISION_PATCH_OVERLAP;
            double maxX = Math.max(Math.max(p00.x, p01.x), Math.max(p10.x, p11.x)) + COLLISION_PATCH_OVERLAP;
            double minZ = Math.min(Math.min(p00.z, p01.z), Math.min(p10.z, p11.z)) - COLLISION_PATCH_OVERLAP;
            double maxZ = Math.max(Math.max(p00.z, p01.z), Math.max(p10.z, p11.z)) + COLLISION_PATCH_OVERLAP;
            double minCornerY = Math.min(Math.min(p00.y, p01.y), Math.min(p10.y, p11.y));
            double maxCornerY = Math.max(Math.max(p00.y, p01.y), Math.max(p10.y, p11.y));
            double minY = minCornerY - COLLISION_SURFACE_THICKNESS;
            double maxY = maxCornerY + 0.06D;
            if (maxX > minX && maxY > minY && maxZ > minZ) {
                segments[logicalIndex - minLogicalIndex] = new CollisionSegmentCache(
                        sampleT0,
                        sampleT1,
                        new Box(minX, minY, minZ, maxX, maxY, maxZ)
                );
            }
        }

        int sampleCount = MathHelper.clamp(segmentCount, 36, 140);
        double[] sampleCenterX = new double[sampleCount + 1];
        double[] sampleCenterZ = new double[sampleCount + 1];
        for (int i = 0; i <= sampleCount; i++) {
            double t = (double) i / (double) sampleCount;
            Vec3d center = this.getCenterlinePoint(t);
            sampleCenterX[i] = center.x;
            sampleCenterZ[i] = center.z;
        }

        double horizontalLength = Math.max(this.getHorizontalLength(), 0.5D);
        double segmentLength = Math.max(horizontalLength / Math.max(segmentCount, 1), 0.06D);

        return new CollisionShapeCache(
                segmentCount,
                extraSegments,
                minLogicalIndex,
                maxLogicalIndexExclusive,
                segments,
                sampleCenterX,
                sampleCenterZ,
                segmentLength,
                width,
                widthSlices,
                lateralStart,
                lateralStep,
                sideSign
        );
    }

    private CollisionPatchCache[] buildCollisionPatchCaches(CollisionShapeCache cache, CollisionSegmentCache segment) {
        if (cache == null || segment == null || cache.widthSlices <= 0) {
            return CollisionPatchCache.EMPTY_ARRAY;
        }

        double sampleT0 = segment.sampleT0;
        double sampleT1 = segment.sampleT1;
        if (sampleT1 <= sampleT0 + 1.0E-6D) {
            return CollisionPatchCache.EMPTY_ARRAY;
        }

        Vec3d center0 = this.getCenterlinePoint(sampleT0);
        Vec3d center1 = this.getCenterlinePoint(sampleT1);
        Vec3d left0 = this.getLeftNormal(sampleT0);
        Vec3d left1 = this.getLeftNormal(sampleT1);
        boolean curved = this.isCurved();
        double sampleTMid = (sampleT0 + sampleT1) * 0.5D;
        Vec3d centerMid = curved ? this.getCenterlinePoint(sampleTMid) : null;
        Vec3d leftMid = curved ? this.getLeftNormal(sampleTMid) : null;
        List<CollisionPatchCache> patchList = new ArrayList<>(cache.widthSlices);

        for (int j = 0; j < cache.widthSlices; j++) {
            double localL0 = cache.lateralStart + (double) j * cache.lateralStep;
            double localL1 = localL0 + cache.lateralStep;

            Vec3d p00 = this.sampleCollisionVertex(sampleT0, center0, left0, localL0, cache.width, cache.sideSign);
            Vec3d p01 = this.sampleCollisionVertex(sampleT0, center0, left0, localL1, cache.width, cache.sideSign);
            Vec3d p10 = this.sampleCollisionVertex(sampleT1, center1, left1, localL0, cache.width, cache.sideSign);
            Vec3d p11 = this.sampleCollisionVertex(sampleT1, center1, left1, localL1, cache.width, cache.sideSign);
            Vec3d midA = null;
            Vec3d midB = null;
            if (curved) {
                midA = this.sampleCollisionVertex(sampleTMid, centerMid, leftMid, localL0, cache.width, cache.sideSign);
                midB = this.sampleCollisionVertex(sampleTMid, centerMid, leftMid, localL1, cache.width, cache.sideSign);
            }

            Box patchBox = this.makeCollisionPatchBox(p00, p01, p10, p11, midA, midB);
            if (patchBox == null) {
                continue;
            }
            patchList.add(new CollisionPatchCache(patchBox, VoxelShapes.cuboid(patchBox)));
        }

        if (patchList.isEmpty()) {
            return CollisionPatchCache.EMPTY_ARRAY;
        }
        return patchList.toArray(new CollisionPatchCache[0]);
    }

    private Vec3d sampleCollisionVertex(double t, Vec3d center, Vec3d left, double localLateral, double width, int sideSign) {
        double worldLateral;
        double normalizedLateral;
        if (this.isTwoSided()) {
            worldLateral = localLateral;
            normalizedLateral = Math.abs(localLateral) / width;
        } else {
            worldLateral = localLateral * sideSign;
            normalizedLateral = localLateral / width;
        }

        Vec3d worldPos = center.add(left.multiply(worldLateral));
        double baseY = this.sampleBaseY(t);
        double surfaceY = baseY + this.getDrop() * (1.0D - MathHelper.clamp(normalizedLateral, 0.0D, 1.0D));
        return new Vec3d(worldPos.x, surfaceY, worldPos.z);
    }

    @Nullable
    private Box makeCollisionPatchBox(Vec3d p00, Vec3d p01, Vec3d p10, Vec3d p11, @Nullable Vec3d midA, @Nullable Vec3d midB) {
        double minX = Math.min(Math.min(p00.x, p01.x), Math.min(p10.x, p11.x));
        double maxX = Math.max(Math.max(p00.x, p01.x), Math.max(p10.x, p11.x));
        double minZ = Math.min(Math.min(p00.z, p01.z), Math.min(p10.z, p11.z));
        double maxZ = Math.max(Math.max(p00.z, p01.z), Math.max(p10.z, p11.z));
        double minCornerY = Math.min(Math.min(p00.y, p01.y), Math.min(p10.y, p11.y));
        double maxCornerY = Math.max(Math.max(p00.y, p01.y), Math.max(p10.y, p11.y));

        if (midA != null) {
            minX = Math.min(minX, midA.x);
            maxX = Math.max(maxX, midA.x);
            minZ = Math.min(minZ, midA.z);
            maxZ = Math.max(maxZ, midA.z);
            minCornerY = Math.min(minCornerY, midA.y);
            maxCornerY = Math.max(maxCornerY, midA.y);
        }
        if (midB != null) {
            minX = Math.min(minX, midB.x);
            maxX = Math.max(maxX, midB.x);
            minZ = Math.min(minZ, midB.z);
            maxZ = Math.max(maxZ, midB.z);
            minCornerY = Math.min(minCornerY, midB.y);
            maxCornerY = Math.max(maxCornerY, midB.y);
        }

        minX -= COLLISION_PATCH_OVERLAP;
        maxX += COLLISION_PATCH_OVERLAP;
        minZ -= COLLISION_PATCH_OVERLAP;
        maxZ += COLLISION_PATCH_OVERLAP;
        double maxY = maxCornerY + 0.06D;
        double minY = minCornerY - COLLISION_SURFACE_THICKNESS;
        if (maxX <= minX || maxZ <= minZ || maxY <= minY) {
            return null;
        }

        return new Box(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private Vec3d getLeftNormal(double t) {
        Vec3d tangent = this.getTangent(t);
        Vec3d left = new Vec3d(-tangent.z, 0.0D, tangent.x);
        if (left.lengthSquared() < 1.0E-8D) {
            return new Vec3d(1.0D, 0.0D, 0.0D);
        }
        return left.normalize();
    }

    private void refreshBounds() {
        int segmentCount = this.getCollisionSegmentCount();
        double width = this.getRampWidth();

        double minX = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        double minBaseY = Double.POSITIVE_INFINITY;
        double maxBaseY = Double.NEGATIVE_INFINITY;

        for (int i = 0; i <= segmentCount; i++) {
            double t = (double) i / (double) segmentCount;
            Vec3d center = this.getCenterlinePoint(t);
            minX = Math.min(minX, center.x - width - 0.35D);
            maxX = Math.max(maxX, center.x + width + 0.35D);
            minZ = Math.min(minZ, center.z - width - 0.35D);
            maxZ = Math.max(maxZ, center.z + width + 0.35D);
            double baseY = this.sampleBaseY(t);
            minBaseY = Math.min(minBaseY, baseY);
            maxBaseY = Math.max(maxBaseY, baseY);
        }

        if (!Double.isFinite(minBaseY) || !Double.isFinite(maxBaseY)) {
            Vec3d start = this.getStart();
            Vec3d end = this.getEnd();
            minBaseY = Math.min(start.y, end.y);
            maxBaseY = Math.max(start.y, end.y);
        }

        double minY = minBaseY - 0.65D;
        double maxY = maxBaseY + this.getDrop() + 0.45D;

        this.setBoundingBox(new Box(minX, minY, minZ, maxX, maxY, maxZ));
        this.boundsDirty = false;
    }

    private int getCollisionSegmentCount() {
        this.refreshDerivedGeometryCache();
        return this.cachedCollisionSegmentCount;
    }

    private int getCollisionShapeSegmentCount() {
        this.refreshDerivedGeometryCache();
        return this.cachedCollisionShapeSegmentCount;
    }

    private int getCollisionShapeWidthSlices(double width) {
        return MathHelper.clamp(
                (int) Math.ceil(Math.max(width * 3.0D, this.getDrop() * 4.0D)),
                4,
                24
        );
    }

    private double getHorizontalLength() {
        this.refreshDerivedGeometryCache();
        return this.cachedHorizontalLength;
    }

    private void refreshDerivedGeometryCache() {
        if (this.cachedDerivedGeometrySignature != Long.MIN_VALUE) {
            return;
        }
        double horizontalLength = this.computeHorizontalLengthRaw();
        this.cachedHorizontalLength = Math.max(horizontalLength, 0.01D);
        double curveBoost = this.isCurved() ? 1.8D : 1.0D;
        this.cachedCollisionSegmentCount = MathHelper.clamp(
                (int) Math.ceil(Math.max(this.cachedHorizontalLength * 14.0D * curveBoost, 36.0D)),
                24,
                240
        );
        this.cachedCollisionShapeSegmentCount = MathHelper.clamp(
                (int) Math.ceil(Math.max(this.cachedHorizontalLength * 7.0D * curveBoost, 20.0D)),
                16,
                160
        );
        this.cachedDerivedGeometrySignature = 1L;
        this.collisionShapeCache = null;
        this.collisionShapeCacheSignature = Long.MIN_VALUE;
        this.surfaceSearchCache = null;
        this.surfaceSearchCacheSignature = Long.MIN_VALUE;
    }

    private void invalidateGeometryCaches() {
        this.cachedDerivedGeometrySignature = Long.MIN_VALUE;
        this.collisionShapeCache = null;
        this.collisionShapeCacheSignature = Long.MIN_VALUE;
        this.surfaceSearchCache = null;
        this.surfaceSearchCacheSignature = Long.MIN_VALUE;
        this.boundsDirty = true;
    }

    private double computeHorizontalLengthRaw() {
        List<Vec3d> points = this.getPathPoints();
        if (points.size() > 1) {
            double tStart = this.getPathTStart();
            double tEnd = this.getPathTEnd();
            int samples = MathHelper.clamp(
                    (int) Math.ceil(Math.max((tEnd - tStart) * (points.size() - 1) * 26.0D, 24.0D)),
                    24,
                    320
            );
            double length = 0.0D;
            Vec3d previous = this.samplePathCenterline(points, tStart);
            for (int i = 1; i <= samples; i++) {
                double t = MathHelper.lerp((double) i / (double) samples, tStart, tEnd);
                Vec3d current = this.samplePathCenterline(points, t);
                length += new Vec3d(current.x - previous.x, 0.0D, current.z - previous.z).length();
                previous = current;
            }
            return Math.max(length, 0.01D);
        }
        Vec3d start = this.getStart();
        Vec3d end = this.getEnd();
        return new Vec3d(end.x - start.x, 0.0D, end.z - start.z).length();
    }

    @Nullable
    private SurfacePoint findSurfacePoint(double sampleX, double sampleZ) {
        SurfaceSearchCache cache = this.getOrBuildSurfaceSearchCache();
        if (cache == null || cache.segmentCount <= 0) {
            return null;
        }
        int segmentCount = cache.segmentCount;
        double width = Math.max(this.getRampWidth(), 0.15D);
        double halfSegmentLength = Math.max(cache.segmentLength, 0.06D) * 1.30D + COLLISION_PATCH_OVERLAP + 0.06D;
        int seamExtraSamples = (this.isLinkedStart() || this.isLinkedEnd()) ? 5 : 0;
        double estimatedT = cache.estimateClosestT(sampleX, sampleZ);
        int centerIndex = MathHelper.clamp((int) Math.round(estimatedT * segmentCount), 0, segmentCount);
        int halfWindow = MathHelper.clamp((int) Math.ceil(segmentCount * 0.10D) + 6 + seamExtraSamples, 10, 64);

        SurfacePoint localBest = this.findSurfacePointInIndexRange(
                sampleX,
                sampleZ,
                cache,
                width,
                halfSegmentLength,
                centerIndex - halfWindow,
                centerIndex + halfWindow
        );
        if (localBest != null) {
            return localBest;
        }

        int expandedHalfWindow = MathHelper.clamp(halfWindow * 2 + seamExtraSamples, 18, segmentCount + seamExtraSamples);
        SurfacePoint expandedBest = this.findSurfacePointInIndexRange(
                sampleX,
                sampleZ,
                cache,
                width,
                halfSegmentLength,
                centerIndex - expandedHalfWindow,
                centerIndex + expandedHalfWindow
        );
        if (expandedBest != null) {
            return expandedBest;
        }

        return this.findSurfacePointInIndexRange(
                sampleX,
                sampleZ,
                cache,
                width,
                halfSegmentLength,
                -seamExtraSamples,
                segmentCount + seamExtraSamples
        );
    }

    @Nullable
    private SurfacePoint findSurfacePointInIndexRange(
            double sampleX,
            double sampleZ,
            SurfaceSearchCache cache,
            double width,
            double halfSegmentLength,
            int minIndex,
            int maxIndex
    ) {
        if (cache == null) {
            return null;
        }
        int segmentCount = cache.segmentCount;
        if (segmentCount <= 0) {
            return null;
        }

        double bestMetric = Double.MAX_VALUE;
        int startIndex = MathHelper.clamp(Math.min(minIndex, maxIndex), 0, segmentCount);
        int endIndex = MathHelper.clamp(Math.max(minIndex, maxIndex), 0, segmentCount);
        double drop = this.getDrop();
        int sideSign = this.getSideSign();
        double crossSlope = -drop / Math.max(width, 0.15D);

        double bestSurfaceY = 0.0D;
        double bestNormalX = 0.0D;
        double bestNormalY = 1.0D;
        double bestNormalZ = 0.0D;
        double bestT = 0.0D;
        boolean found = false;

        for (int i = startIndex; i <= endIndex; i++) {
            double t = (double) i / (double) segmentCount;
            double centerX = cache.centerX[i];
            double centerZ = cache.centerZ[i];
            double tangentX = cache.tangentX[i];
            double tangentZ = cache.tangentZ[i];
            double leftX = cache.leftX[i];
            double leftZ = cache.leftZ[i];
            double alongSlope = cache.alongSlope[i];
            double baseY = cache.baseY[i];

            double deltaX = sampleX - centerX;
            double deltaZ = sampleZ - centerZ;
            double along = deltaX * tangentX + deltaZ * tangentZ;
            double seamAlongBoost = 0.0D;
            if (this.isLinkedStart() && t < 0.14D) {
                seamAlongBoost = 0.30D;
            }
            if (this.isLinkedEnd() && t > 0.86D) {
                seamAlongBoost = Math.max(seamAlongBoost, 0.30D);
            }
            if (Math.abs(along) > halfSegmentLength + seamAlongBoost + CONTACT_EPSILON) {
                continue;
            }

            double lateral = deltaX * leftX + deltaZ * leftZ;
            double normalizedLateral;
            double normalSideSign;
            if (this.isTwoSided()) {
                if (Math.abs(lateral) > width + CONTACT_EPSILON) {
                    continue;
                }
                normalizedLateral = Math.abs(lateral) / width;
                normalSideSign = lateral >= 0.0D ? 1.0D : -1.0D;
            } else {
                double orientedLateral = lateral * sideSign;
                double oneSidedMax = width + CONTACT_EPSILON + 0.16D;
                if (orientedLateral < -CONTACT_EPSILON || orientedLateral > oneSidedMax) {
                    continue;
                }
                normalizedLateral = MathHelper.clamp(orientedLateral / width, 0.0D, 1.0D);
                normalSideSign = sideSign;
            }

            // Interpolate base height to the query's actual along-position within the segment.
            // Using the discrete cache.baseY[i] alone quantizes surfaceY to the nearest segment
            // center, so on a longitudinally-sloped ramp it stair-steps by segmentLength*slope
            // (~0.08) each time the closest segment flips -> a periodic vertical bump as the rider
            // crosses segment boundaries. The along*alongSlope term makes it continuous (exact for
            // a planar ramp, near-continuous through curves).
            double surfaceY = baseY + along * alongSlope + drop * (1.0D - normalizedLateral);

            double slopeDirectionX = leftX * normalSideSign;
            double slopeDirectionZ = leftZ * normalSideSign;
            double gradX = slopeDirectionX * crossSlope + tangentX * alongSlope;
            double gradZ = slopeDirectionZ * crossSlope + tangentZ * alongSlope;
            double normalLength = Math.sqrt(gradX * gradX + 1.0D + gradZ * gradZ);
            if (normalLength < 1.0E-8D) {
                normalLength = 1.0D;
            }
            double invNormalLength = 1.0D / normalLength;
            double normalX = -gradX * invNormalLength;
            double normalY = invNormalLength;
            double normalZ = -gradZ * invNormalLength;

            double metric = deltaX * deltaX + deltaZ * deltaZ + Math.abs(along) * 0.02D;
            if (metric < bestMetric) {
                bestMetric = metric;
                bestSurfaceY = surfaceY;
                bestNormalX = normalX;
                bestNormalY = normalY;
                bestNormalZ = normalZ;
                bestT = t;
                found = true;
            }
        }

        if (!found) {
            return null;
        }
        return new SurfacePoint(bestSurfaceY, new Vec3d(bestNormalX, bestNormalY, bestNormalZ), bestT);
    }

    private boolean isHardEndpointT(double t) {
        double clampedT = MathHelper.clamp(t, 0.0D, 1.0D);
        double horizontalLength = Math.max(this.getHorizontalLength(), 0.5D);
        double hardEndpointThreshold = MathHelper.clamp(0.75D / horizontalLength, 0.012D, 0.06D);
        if (!this.isLinkedStart() && clampedT <= hardEndpointThreshold) {
            return true;
        }
        return !this.isLinkedEnd() && clampedT >= 1.0D - hardEndpointThreshold;
    }

    private Vec3d getCenterlinePoint(double t) {
        List<Vec3d> pathPoints = this.getPathPoints();
        if (pathPoints.size() >= 2) {
            double mappedT = this.mapLocalPathTToGlobalPathT(t);
            return this.samplePathCenterline(pathPoints, mappedT);
        }

        Vec3d start = this.getStart();
        Vec3d end = this.getEnd();
        if (!this.isCurved()) {
            return start.lerp(end, t);
        }

        Vec3d control = computeControlPoint(start, end);
        double invT = 1.0D - t;
        double x = invT * invT * start.x + 2.0D * invT * t * control.x + t * t * end.x;
        double y = invT * invT * start.y + 2.0D * invT * t * control.y + t * t * end.y;
        double z = invT * invT * start.z + 2.0D * invT * t * control.z + t * t * end.z;
        return new Vec3d(x, y, z);
    }

    private Vec3d getTangent(double t) {
        List<Vec3d> pathPoints = this.getPathPoints();
        if (pathPoints.size() >= 2) {
            double mappedT = this.mapLocalPathTToGlobalPathT(t);
            double dt = 1.0D / Math.max(64.0D, pathPoints.size() * 32.0D);
            dt = Math.max(dt, 1.0E-4D);
            double t0 = Math.max(0.0D, mappedT - dt);
            double t1 = Math.min(1.0D, mappedT + dt);
            Vec3d before = this.samplePathCenterline(pathPoints, t0);
            Vec3d after = this.samplePathCenterline(pathPoints, t1);
            Vec3d tangent = new Vec3d(after.x - before.x, 0.0D, after.z - before.z);
            if (tangent.lengthSquared() < 1.0E-8D) {
                int segmentCount = pathPoints.size() - 1;
                int segmentIndex = MathHelper.clamp((int) Math.floor(MathHelper.clamp(mappedT, 0.0D, 1.0D) * segmentCount), 0, segmentCount - 1);
                Vec3d segStart = pathPoints.get(segmentIndex);
                Vec3d segEnd = pathPoints.get(segmentIndex + 1);
                tangent = new Vec3d(segEnd.x - segStart.x, 0.0D, segEnd.z - segStart.z);
                if (tangent.lengthSquared() < 1.0E-8D && segmentIndex > 0) {
                    Vec3d prevStart = pathPoints.get(segmentIndex - 1);
                    tangent = new Vec3d(segStart.x - prevStart.x, 0.0D, segStart.z - prevStart.z);
                }
            }
            if (tangent.lengthSquared() < 1.0E-8D) {
                Vec3d start = pathPoints.get(0);
                Vec3d end = pathPoints.get(pathPoints.size() - 1);
                tangent = new Vec3d(end.x - start.x, 0.0D, end.z - start.z);
            }
            if (tangent.lengthSquared() < 1.0E-8D) {
                return new Vec3d(1.0D, 0.0D, 0.0D);
            }
            return tangent.normalize();
        }

        Vec3d start = this.getStart();
        Vec3d end = this.getEnd();

        Vec3d tangent;
        if (this.isCurved()) {
            Vec3d control = computeControlPoint(start, end);
            Vec3d first = control.subtract(start).multiply(2.0D * (1.0D - t));
            Vec3d second = end.subtract(control).multiply(2.0D * t);
            tangent = new Vec3d(first.x + second.x, 0.0D, first.z + second.z);
        } else {
            tangent = new Vec3d(end.x - start.x, 0.0D, end.z - start.z);
        }

        if (tangent.lengthSquared() < 1.0E-8D) {
            return new Vec3d(1.0D, 0.0D, 0.0D);
        }

        return tangent.normalize();
    }

    private double getPathTStart() {
        return MathHelper.clamp(this.dataTracker.get(PATH_T_START), 0.0F, 1.0F);
    }

    private double getPathTEnd() {
        return MathHelper.clamp(this.dataTracker.get(PATH_T_END), 0.0F, 1.0F);
    }

    private void setPathTRange(double pathStartT, double pathEndT) {
        double start = MathHelper.clamp(pathStartT, 0.0D, 1.0D);
        double end = MathHelper.clamp(pathEndT, 0.0D, 1.0D);
        if (end < start) {
            double swap = start;
            start = end;
            end = swap;
        }

        if (end - start < 1.0E-4D) {
            double center = (start + end) * 0.5D;
            start = Math.max(0.0D, center - 5.0E-4D);
            end = Math.min(1.0D, center + 5.0E-4D);
            if (end - start < 1.0E-4D) {
                start = 0.0D;
                end = 1.0D;
            }
        }

        this.dataTracker.set(PATH_T_START, (float) start);
        this.dataTracker.set(PATH_T_END, (float) end);
        this.invalidateGeometryCaches();
    }

    private double mapLocalPathTToGlobalPathT(double localT) {
        double clampedLocalT = MathHelper.clamp(localT, 0.0D, 1.0D);
        double start = this.getPathTStart();
        double end = this.getPathTEnd();
        if (end < start) {
            double swap = start;
            start = end;
            end = swap;
        }
        return MathHelper.lerp(clampedLocalT, start, end);
    }

    private Vec3d samplePathCenterline(List<Vec3d> pathPoints, double pathT) {
        double clampedT = MathHelper.clamp(pathT, 0.0D, 1.0D);
        if (pathPoints.size() == 2) {
            return pathPoints.get(0).lerp(pathPoints.get(1), clampedT);
        }

        int segmentCount = pathPoints.size() - 1;
        double scaled = clampedT * segmentCount;
        int segmentIndex = MathHelper.clamp((int) Math.floor(scaled), 0, segmentCount - 1);
        double localT = scaled - segmentIndex;

        Vec3d p0 = pathPoints.get(Math.max(segmentIndex - 1, 0));
        Vec3d p1 = pathPoints.get(segmentIndex);
        Vec3d p2 = pathPoints.get(segmentIndex + 1);
        Vec3d p3 = pathPoints.get(Math.min(segmentIndex + 2, pathPoints.size() - 1));
        return catmullRom(p0, p1, p2, p3, localT);
    }

    private List<Vec3d> getPathPoints() {
        String raw = this.dataTracker.get(PATH_POINTS);
        if (!Objects.equals(raw, this.cachedPathPointsRaw)) {
            this.cachedPathPointsRaw = raw;
            this.cachedPathPoints = this.parsePathPoints(raw);
        }
        return this.cachedPathPoints;
    }

    private List<Vec3d> parsePathPoints(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }

        String[] tokens = raw.split(";");
        List<Vec3d> parsed = new ArrayList<>();
        for (String token : tokens) {
            if (parsed.size() >= MAX_PATH_POINTS) {
                break;
            }
            String[] xyz = token.split(",");
            if (xyz.length != 3) {
                continue;
            }
            try {
                double x = Double.parseDouble(xyz[0]);
                double y = Double.parseDouble(xyz[1]);
                double z = Double.parseDouble(xyz[2]);
                parsed.add(new Vec3d(x, y, z));
            } catch (NumberFormatException ignored) {
            }
        }

        if (parsed.size() < 2) {
            return List.of();
        }
        return List.copyOf(parsed);
    }

    private String encodePathPoints(List<Vec3d> points) {
        if (points == null || points.size() < 2) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        int written = 0;
        for (Vec3d point : points) {
            if (point == null) {
                continue;
            }
            if (written >= MAX_PATH_POINTS) {
                break;
            }
            if (written > 0) {
                builder.append(';');
            }
            builder.append(String.format(java.util.Locale.ROOT, "%.4f,%.4f,%.4f", point.x, point.y, point.z));
            written++;
        }
        return written >= 2 ? builder.toString() : "";
    }

    private void setPathPointsEncoded(String encodedPoints) {
        String safe = encodedPoints == null ? "" : encodedPoints;
        this.dataTracker.set(PATH_POINTS, safe);
        this.cachedPathPointsRaw = safe;
        this.cachedPathPoints = this.parsePathPoints(safe);
        this.invalidateGeometryCaches();
    }

    private static Vec3d catmullRom(Vec3d p0, Vec3d p1, Vec3d p2, Vec3d p3, double t) {
        double t2 = t * t;
        double t3 = t2 * t;

        double x = 0.5D * (
                (2.0D * p1.x)
                        + (-p0.x + p2.x) * t
                        + (2.0D * p0.x - 5.0D * p1.x + 4.0D * p2.x - p3.x) * t2
                        + (-p0.x + 3.0D * p1.x - 3.0D * p2.x + p3.x) * t3
        );
        double y = 0.5D * (
                (2.0D * p1.y)
                        + (-p0.y + p2.y) * t
                        + (2.0D * p0.y - 5.0D * p1.y + 4.0D * p2.y - p3.y) * t2
                        + (-p0.y + 3.0D * p1.y - 3.0D * p2.y + p3.y) * t3
        );
        double z = 0.5D * (
                (2.0D * p1.z)
                        + (-p0.z + p2.z) * t
                        + (2.0D * p0.z - 5.0D * p1.z + 4.0D * p2.z - p3.z) * t2
                        + (-p0.z + 3.0D * p1.z - 3.0D * p2.z + p3.z) * t3
        );
        return new Vec3d(x, y, z);
    }

    private static Vec3d computeControlPoint(Vec3d start, Vec3d end) {
        return new Vec3d(start.x, MathHelper.lerp(0.5D, start.y, end.y), end.z);
    }

    private static String sanitizeTextureBlockId(String textureBlockId) {
        Identifier parsed = Identifier.tryParse(textureBlockId);
        if (parsed == null || !Registries.BLOCK.containsId(parsed)) {
            return DEFAULT_TEXTURE_BLOCK_ID;
        }
        if (Registries.BLOCK.get(parsed) == Blocks.AIR) {
            return DEFAULT_TEXTURE_BLOCK_ID;
        }
        return parsed.toString();
    }

    public Vec3d sampleCenterline(double t) {
        return this.getCenterlinePoint(MathHelper.clamp(t, 0.0D, 1.0D));
    }

    public Vec3d sampleCenterlineCached(double t) {
        SurfaceSearchCache cache = this.getOrBuildSurfaceSearchCache();
        if (cache == null || cache.segmentCount <= 0) {
            return this.sampleCenterline(t);
        }
        double scaled = MathHelper.clamp(t, 0.0D, 1.0D) * (double) cache.segmentCount;
        double x = sampleInterpolated(cache.centerX, scaled);
        double y = sampleInterpolated(cache.baseY, scaled);
        double z = sampleInterpolated(cache.centerZ, scaled);
        return new Vec3d(x, y, z);
    }

    public Vec3d sampleLeft(double t) {
        Vec3d tangent = this.getTangent(MathHelper.clamp(t, 0.0D, 1.0D));
        return new Vec3d(-tangent.z, 0.0D, tangent.x).normalize();
    }

    public Vec3d sampleLeftCached(double t) {
        SurfaceSearchCache cache = this.getOrBuildSurfaceSearchCache();
        if (cache == null || cache.segmentCount <= 0) {
            return this.sampleLeft(t);
        }
        double scaled = MathHelper.clamp(t, 0.0D, 1.0D) * (double) cache.segmentCount;
        double lx = sampleInterpolated(cache.leftX, scaled);
        double lz = sampleInterpolated(cache.leftZ, scaled);
        double length = Math.hypot(lx, lz);
        if (length < 1.0E-8D) {
            return new Vec3d(1.0D, 0.0D, 0.0D);
        }
        return new Vec3d(lx / length, 0.0D, lz / length);
    }

    public double sampleBaseY(double t) {
        double clamped = MathHelper.clamp(t, 0.0D, 1.0D);
        List<Vec3d> pathPoints = this.getPathPoints();
        if (pathPoints.size() >= 2) {
            double mappedT = this.mapLocalPathTToGlobalPathT(clamped);
            return this.samplePathCenterline(pathPoints, mappedT).y;
        }
        return MathHelper.lerp(clamped, this.getStart().y, this.getEnd().y);
    }

    public double sampleBaseYCached(double t) {
        SurfaceSearchCache cache = this.getOrBuildSurfaceSearchCache();
        if (cache == null || cache.segmentCount <= 0) {
            return this.sampleBaseY(t);
        }
        double scaled = MathHelper.clamp(t, 0.0D, 1.0D) * (double) cache.segmentCount;
        return sampleInterpolated(cache.baseY, scaled);
    }

    private static double sampleInterpolated(double[] values, double scaledIndex) {
        if (values == null || values.length == 0) {
            return 0.0D;
        }
        int maxIndex = values.length - 1;
        int index0 = MathHelper.clamp((int) Math.floor(scaledIndex), 0, maxIndex);
        int index1 = Math.min(index0 + 1, maxIndex);
        double t = MathHelper.clamp(scaledIndex - (double) index0, 0.0D, 1.0D);
        return MathHelper.lerp(t, values[index0], values[index1]);
    }

    private static final class SurfaceSearchCache {
        private final int segmentCount;
        private final double segmentLength;
        private final double[] centerX;
        private final double[] centerZ;
        private final double[] tangentX;
        private final double[] tangentZ;
        private final double[] leftX;
        private final double[] leftZ;
        private final double[] baseY;
        private final double[] alongSlope;

        private SurfaceSearchCache(
                int segmentCount,
                double segmentLength,
                double[] centerX,
                double[] centerZ,
                double[] tangentX,
                double[] tangentZ,
                double[] leftX,
                double[] leftZ,
                double[] baseY,
                double[] alongSlope
        ) {
            this.segmentCount = segmentCount;
            this.segmentLength = segmentLength;
            this.centerX = centerX;
            this.centerZ = centerZ;
            this.tangentX = tangentX;
            this.tangentZ = tangentZ;
            this.leftX = leftX;
            this.leftZ = leftZ;
            this.baseY = baseY;
            this.alongSlope = alongSlope;
        }

        private double estimateClosestT(double x, double z) {
            int bestIndex = 0;
            double bestDistanceSq = Double.MAX_VALUE;
            for (int i = 0; i <= this.segmentCount; i++) {
                double dx = x - this.centerX[i];
                double dz = z - this.centerZ[i];
                double distanceSq = dx * dx + dz * dz;
                if (distanceSq < bestDistanceSq) {
                    bestDistanceSq = distanceSq;
                    bestIndex = i;
                }
            }
            return (double) bestIndex / (double) Math.max(this.segmentCount, 1);
        }
    }

    private static final class CollisionPatchCache {
        private static final CollisionPatchCache[] EMPTY_ARRAY = new CollisionPatchCache[0];
        private final Box box;
        private final VoxelShape shape;

        private CollisionPatchCache(Box box, VoxelShape shape) {
            this.box = box;
            this.shape = shape;
        }
    }

    private static final class CollisionSegmentCache {
        private final double sampleT0;
        private final double sampleT1;
        private final Box bounds;
        @Nullable
        private volatile CollisionPatchCache[] patches;

        private CollisionSegmentCache(double sampleT0, double sampleT1, Box bounds) {
            this.sampleT0 = sampleT0;
            this.sampleT1 = sampleT1;
            this.bounds = bounds;
        }

        private CollisionPatchCache[] getOrBuildPatches(SurfRampEntity ramp, CollisionShapeCache cache) {
            CollisionPatchCache[] local = this.patches;
            if (local != null) {
                return local;
            }
            synchronized (this) {
                if (this.patches == null) {
                    this.patches = ramp.buildCollisionPatchCaches(cache, this);
                }
                return this.patches;
            }
        }
    }

    private static final class CollisionShapeCache {
        private final int segmentCount;
        private final int extraSegments;
        private final int minLogicalIndex;
        private final int maxLogicalIndexExclusive;
        private final CollisionSegmentCache[] segments;
        private final double[] sampleCenterX;
        private final double[] sampleCenterZ;
        private final double segmentLength;
        private final double width;
        private final int widthSlices;
        private final double lateralStart;
        private final double lateralStep;
        private final int sideSign;

        private CollisionShapeCache(
                int segmentCount,
                int extraSegments,
                int minLogicalIndex,
                int maxLogicalIndexExclusive,
                CollisionSegmentCache[] segments,
                double[] sampleCenterX,
                double[] sampleCenterZ,
                double segmentLength,
                double width,
                int widthSlices,
                double lateralStart,
                double lateralStep,
                int sideSign
        ) {
            this.segmentCount = segmentCount;
            this.extraSegments = extraSegments;
            this.minLogicalIndex = minLogicalIndex;
            this.maxLogicalIndexExclusive = maxLogicalIndexExclusive;
            this.segments = segments;
            this.sampleCenterX = sampleCenterX;
            this.sampleCenterZ = sampleCenterZ;
            this.segmentLength = segmentLength;
            this.width = width;
            this.widthSlices = widthSlices;
            this.lateralStart = lateralStart;
            this.lateralStep = lateralStep;
            this.sideSign = sideSign;
        }

        @Nullable
        private CollisionSegmentCache getSegment(int logicalIndex) {
            int cacheIndex = logicalIndex - this.minLogicalIndex;
            if (cacheIndex < 0 || cacheIndex >= this.segments.length) {
                return null;
            }
            return this.segments[cacheIndex];
        }

        private double estimateClosestT(double x, double z) {
            if (this.sampleCenterX.length == 0 || this.sampleCenterX.length != this.sampleCenterZ.length) {
                return 0.0D;
            }

            int bestIndex = 0;
            double bestDistanceSq = Double.MAX_VALUE;
            for (int i = 0; i < this.sampleCenterX.length; i++) {
                double dx = x - this.sampleCenterX[i];
                double dz = z - this.sampleCenterZ[i];
                double distanceSq = dx * dx + dz * dz;
                if (distanceSq < bestDistanceSq) {
                    bestDistanceSq = distanceSq;
                    bestIndex = i;
                }
            }

            int denominator = Math.max(this.sampleCenterX.length - 1, 1);
            return (double) bestIndex / (double) denominator;
        }
    }

    public record CollisionAppendStats(int segmentsTested, int segmentsIntersected, int patchesTested, int patchesAppended) {
        public static final CollisionAppendStats EMPTY = new CollisionAppendStats(0, 0, 0, 0);
    }

    public record SurfacePoint(double surfaceY, Vec3d normal, double t) {
    }
}
