package net.nerdorg.minehop.entity.custom;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.nerdorg.minehop.item.ModItems;
import net.nerdorg.minehop.util.SurfContact;
import net.nerdorg.minehop.util.SurfRampPlacementManager;
import net.nerdorg.minehop.util.SurfRampVisualStyle;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class SurfRampEntity extends Mob {
    private static final EntityDataAccessor<Float> START_X = SynchedEntityData.defineId(SurfRampEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> START_Y = SynchedEntityData.defineId(SurfRampEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> START_Z = SynchedEntityData.defineId(SurfRampEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> END_X = SynchedEntityData.defineId(SurfRampEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> END_Y = SynchedEntityData.defineId(SurfRampEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> END_Z = SynchedEntityData.defineId(SurfRampEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DROP = SynchedEntityData.defineId(SurfRampEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> WIDTH = SynchedEntityData.defineId(SurfRampEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> TWO_SIDED = SynchedEntityData.defineId(SurfRampEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> SIDE_SIGN = SynchedEntityData.defineId(SurfRampEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> TEXTURE_BLOCK_ID = SynchedEntityData.defineId(SurfRampEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> RENDER_MODE = SynchedEntityData.defineId(SurfRampEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> WIREFRAME_COLOR_RGB = SynchedEntityData.defineId(SurfRampEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> WIREFRAME_FILL = SynchedEntityData.defineId(SurfRampEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> WIREFRAME_FILL_COLOR_RGB = SynchedEntityData.defineId(SurfRampEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> WIREFRAME_FILL_ALPHA = SynchedEntityData.defineId(SurfRampEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> PATH_POINTS = SynchedEntityData.defineId(SurfRampEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Float> PATH_T_START = SynchedEntityData.defineId(SurfRampEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> PATH_T_END = SynchedEntityData.defineId(SurfRampEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> LINKED_START = SynchedEntityData.defineId(SurfRampEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> LINKED_END = SynchedEntityData.defineId(SurfRampEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<String> CHAIN_ID = SynchedEntityData.defineId(SurfRampEntity.class, EntityDataSerializers.STRING);

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
    private static final Map<Level, RampRegistry> ACTIVE_RAMPS_BY_WORLD = new ConcurrentHashMap<>();
    // Exact-search acceleration: samples are grouped in blocks of this many consecutive indices, each with its XZ
    // bounds, so whole blocks whose lower-bound distance already rules them out are skipped (see SurfaceSearchCache).
    private static final int SEARCH_BLOCK_SIZE = 8;
    private static final int SEARCH_SUPER_BLOCK_BLOCKS = 8;
    private static final int SURFACE_MEMO_SIZE = 32;
    private String cachedPathPointsRaw = "";
    private List<Vec3> cachedPathPoints = List.of();
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
    private BlockState cachedTextureBlockState = Blocks.SMOOTH_STONE.defaultBlockState();
    // Geometry-derived values that were recomputed (and allocated) on every call. Like the other geometry caches
    // they are cleared by invalidateGeometryCaches(), which runs for every change of a geometry field (setters and
    // onSyncedDataUpdated on both sides). No field initializers: Entity's constructor calls overridden methods
    // (setPos) before this class's initializers run.
    @Nullable
    private Vec3 cachedStart;
    @Nullable
    private Vec3 cachedEnd;
    private byte cachedCurvedState;
    @Nullable
    private CenterlineSampleCache centerlineSampleCache;
    private int geometryVersion;
    // Registry this ramp is a member of (set on registration), notified when the bounding box changes.
    @Nullable
    private RampRegistry registeredIn;

    public SurfRampEntity(EntityType<? extends Mob> entityType, Level world) {
        super(entityType, world);
        this.noPhysics = true;
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createSurfRampAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(START_X, 0.5F);
        builder.define(START_Y, 0.0F);
        builder.define(START_Z, 0.5F);
        builder.define(END_X, 1.5F);
        builder.define(END_Y, 0.0F);
        builder.define(END_Z, 0.5F);
        builder.define(DROP, 1.0F);
        builder.define(WIDTH, 1.0F);
        builder.define(TWO_SIDED, false);
        builder.define(SIDE_SIGN, 1);
        builder.define(TEXTURE_BLOCK_ID, DEFAULT_TEXTURE_BLOCK_ID);
        builder.define(RENDER_MODE, SurfRampVisualStyle.MODE_BLOCK);
        builder.define(WIREFRAME_COLOR_RGB, SurfRampVisualStyle.DEFAULT_WIREFRAME_COLOR);
        builder.define(WIREFRAME_FILL, SurfRampVisualStyle.DEFAULT_WIREFRAME_FILL);
        builder.define(WIREFRAME_FILL_COLOR_RGB, SurfRampVisualStyle.DEFAULT_WIREFRAME_FILL_COLOR);
        builder.define(WIREFRAME_FILL_ALPHA, SurfRampVisualStyle.DEFAULT_WIREFRAME_FILL_ALPHA);
        builder.define(PATH_POINTS, "");
        builder.define(PATH_T_START, 0.0F);
        builder.define(PATH_T_END, 1.0F);
        builder.define(LINKED_START, false);
        builder.define(LINKED_END, false);
        builder.define(CHAIN_ID, DEFAULT_CHAIN_ID);
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
    public void onSyncedDataUpdated(EntityDataAccessor<?> data) {
        super.onSyncedDataUpdated(data);
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
    public void onRemoval(Entity.RemovalReason reason) {
        super.onRemoval(reason);
        // Unloaded ramps (setRemoved without remove()) stay registered until a lookup drops them, as before. The
        // spatial index only visits candidates, so let the next lookup's index rebuild do that cleanup.
        RampRegistry registry = this.registeredIn;
        if (registry != null) {
            registry.markDirty();
        }
    }

    @Override
    public void setPos(double x, double y, double z) {
        super.setPos(x, y, z);
        // Vanilla only changes an entity's bounding box here (and refreshBounds() does for ramps): keep the
        // registry's spatial index in sync. registeredIn is null during construction (Entity's constructor calls
        // setPos before this class's fields are initialised).
        RampRegistry registry = this.registeredIn;
        if (registry != null) {
            registry.markDirty();
        }
    }

    @Override
    public Iterable<Entity> getIndirectPassengers() {
        // Entity tracking (ChunkMap.TrackedEntity#getEffectiveRange) asks every tracked entity for this whenever a
        // player moves; vanilla builds a stream pipeline even without passengers. Ramps never carry any in practice:
        // same (empty) result without the allocations, and the vanilla path when there are passengers.
        if (this.getPassengers().isEmpty()) {
            return List.of();
        }
        return super.getIndirectPassengers();
    }

    @Override
    public boolean canCollideWith(Entity other) {
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
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    protected boolean updateInWaterStateAndDoFluidPushing() {
        // Ramps ignore fluids, but vanilla's baseTick still sweeps every block inside the bounding box
        // for water and lava each tick. With full-size ramp boxes that is thousands of block lookups per
        // ramp per tick (hundreds of ramps froze a server), so skip it: never in water or lava.
        // (1.21.11: same as 1.21.4, updateWaterState -> checkWaterState/updateMovementInFluid is the only
        // per-tick box sweep reached from SurfRampEntity.tick(); isInsideWall is off via noClip.)
        return false;
    }

    @Override
    public boolean requiresCustomPersistence() {
        return true;
    }

    @Override
    public boolean removeWhenFarAway(double distanceSquared) {
        return false;
    }

    @Override
    public boolean hurtServer(ServerLevel world, DamageSource source, float amount) {
        if (source.is(DamageTypes.GENERIC_KILL) || source.is(DamageTypes.FELL_OUT_OF_WORLD)) {
            this.kill(world);
            return true;
        }
        return false;
    }

    @Override
    public void kill(ServerLevel world) {
        this.remove(Entity.RemovalReason.KILLED);
    }

    @Override
    public void readAdditionalSaveData(ValueInput nbt) {
        // 1.21.6+: entity data goes through ReadView/WriteView. Same keys and the same defaults
        // the 1.21.4 NbtCompound getters produced for missing entries (still no super call, as before).
        this.setGeometry(
                new Vec3(nbt.getDoubleOr("startX", 0.0D), nbt.getDoubleOr("startY", 0.0D), nbt.getDoubleOr("startZ", 0.0D)),
                new Vec3(nbt.getDoubleOr("endX", 0.0D), nbt.getDoubleOr("endY", 0.0D), nbt.getDoubleOr("endZ", 0.0D)),
                nbt.getDoubleOr("drop", 0.0D),
                nbt.getDoubleOr("width", 0.0D),
                nbt.getBooleanOr("twoSided", false),
                nbt.getIntOr("sideSign", 0)
        );
        this.setPathPointsEncoded(nbt.getStringOr("pathPoints", ""));
        this.setPathTRange(
                nbt.getDoubleOr("pathTStart", 0.0D),
                nbt.getDoubleOr("pathTEnd", 1.0D)
        );
        this.setLinkedSeams(
                nbt.getBooleanOr("linkedStart", false),
                nbt.getBooleanOr("linkedEnd", false)
        );
        this.setChainId(nbt.getStringOr("chainId", DEFAULT_CHAIN_ID));
        this.setTextureBlockId(nbt.getStringOr("textureBlockId", DEFAULT_TEXTURE_BLOCK_ID));
        this.setRenderMode(nbt.getStringOr("renderMode", SurfRampVisualStyle.MODE_BLOCK));
        this.setWireframeColorRgb(nbt.getIntOr("wireframeColorRgb", SurfRampVisualStyle.DEFAULT_WIREFRAME_COLOR));
        this.setWireframeFillEnabled(nbt.getBooleanOr("wireframeFill", false));
        this.setWireframeFillColorRgb(nbt.getIntOr("wireframeFillColorRgb", SurfRampVisualStyle.DEFAULT_WIREFRAME_FILL_COLOR));
        this.setWireframeFillAlpha(nbt.getIntOr("wireframeFillAlpha", SurfRampVisualStyle.DEFAULT_WIREFRAME_FILL_ALPHA));
        this.refreshBounds();
        // Entity.readData calls refreshPosition() AFTER this method, which resets the bounding box to
        // the type's 1x1 dimensions. Rebuild it on the next tick, or a ramp loaded from disk only
        // "exists" (surf detection, anticheat surf exemption) around its midpoint on the server.
        this.boundsDirty = true;
    }

    @Override
    public void addAdditionalSaveData(ValueOutput nbt) {
        Vec3 start = this.getStart();
        Vec3 end = this.getEnd();

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
        nbt.putString("pathPoints", this.entityData.get(PATH_POINTS));
        nbt.putDouble("pathTStart", this.getPathTStart());
        nbt.putDouble("pathTEnd", this.getPathTEnd());
        nbt.putBoolean("linkedStart", this.isLinkedStart());
        nbt.putBoolean("linkedEnd", this.isLinkedEnd());
        nbt.putString("chainId", this.getChainId());
    }

    public void setGeometry(Vec3 start, Vec3 end, double drop, double width, boolean twoSided, int sideSign) {
        this.entityData.set(START_X, (float) start.x);
        this.entityData.set(START_Y, (float) start.y);
        this.entityData.set(START_Z, (float) start.z);

        this.entityData.set(END_X, (float) end.x);
        this.entityData.set(END_Y, (float) end.y);
        this.entityData.set(END_Z, (float) end.z);

        this.entityData.set(DROP, (float) Math.max(drop, 0.01D));
        this.entityData.set(WIDTH, (float) Math.max(width, 0.15D));
        this.entityData.set(TWO_SIDED, twoSided);

        int clampedSign = sideSign >= 0 ? 1 : -1;
        this.entityData.set(SIDE_SIGN, clampedSign);
        this.invalidateGeometryCaches();

        Vec3 midpoint = start.add(end).scale(0.5D);
        this.snapTo(midpoint.x, midpoint.y + Math.max(drop, 0.01D) * 0.5D, midpoint.z, 0.0F, 0.0F);
        this.refreshBounds();
    }

    public Vec3 getStart() {
        Vec3 start = this.cachedStart;
        if (start == null) {
            start = new Vec3(this.entityData.get(START_X), this.entityData.get(START_Y), this.entityData.get(START_Z));
            this.cachedStart = start;
        }
        return start;
    }

    public Vec3 getEnd() {
        Vec3 end = this.cachedEnd;
        if (end == null) {
            end = new Vec3(this.entityData.get(END_X), this.entityData.get(END_Y), this.entityData.get(END_Z));
            this.cachedEnd = end;
        }
        return end;
    }

    public double getDrop() {
        return this.entityData.get(DROP);
    }

    public double getRampWidth() {
        return this.entityData.get(WIDTH);
    }

    public boolean isTwoSided() {
        return this.entityData.get(TWO_SIDED);
    }

    public int getSideSign() {
        int sign = this.entityData.get(SIDE_SIGN);
        return sign >= 0 ? 1 : -1;
    }

    public boolean isLinkedStart() {
        return this.entityData.get(LINKED_START);
    }

    public boolean isLinkedEnd() {
        return this.entityData.get(LINKED_END);
    }

    public String getChainId() {
        String raw = this.entityData.get(CHAIN_ID);
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
        this.entityData.set(CHAIN_ID, value);
    }

    public String getPathPointsEncoded() {
        String encoded = this.entityData.get(PATH_POINTS);
        return encoded == null ? "" : encoded;
    }

    public String getTextureBlockId() {
        return sanitizeTextureBlockId(this.entityData.get(TEXTURE_BLOCK_ID));
    }

    public void setTextureBlockId(String textureBlockId) {
        String sanitized = sanitizeTextureBlockId(textureBlockId);
        this.entityData.set(TEXTURE_BLOCK_ID, sanitized);
        this.cachedTextureBlockIdForState = null;
    }

    public String getRenderMode() {
        return SurfRampVisualStyle.sanitizeMode(this.entityData.get(RENDER_MODE));
    }

    public void setRenderMode(String renderMode) {
        this.entityData.set(RENDER_MODE, SurfRampVisualStyle.sanitizeMode(renderMode));
    }

    public boolean isWireframeMode() {
        return SurfRampVisualStyle.MODE_WIREFRAME.equals(this.getRenderMode());
    }

    public int getWireframeColorRgb() {
        return SurfRampVisualStyle.sanitizeColor(
                this.entityData.get(WIREFRAME_COLOR_RGB),
                SurfRampVisualStyle.DEFAULT_WIREFRAME_COLOR
        );
    }

    public void setWireframeColorRgb(int wireframeColorRgb) {
        this.entityData.set(
                WIREFRAME_COLOR_RGB,
                SurfRampVisualStyle.sanitizeColor(wireframeColorRgb, SurfRampVisualStyle.DEFAULT_WIREFRAME_COLOR)
        );
    }

    public boolean isWireframeFillEnabled() {
        return this.entityData.get(WIREFRAME_FILL);
    }

    public void setWireframeFillEnabled(boolean wireframeFillEnabled) {
        this.entityData.set(WIREFRAME_FILL, wireframeFillEnabled);
    }

    public int getWireframeFillColorRgb() {
        return SurfRampVisualStyle.sanitizeColor(
                this.entityData.get(WIREFRAME_FILL_COLOR_RGB),
                SurfRampVisualStyle.DEFAULT_WIREFRAME_FILL_COLOR
        );
    }

    public void setWireframeFillColorRgb(int wireframeFillColorRgb) {
        this.entityData.set(
                WIREFRAME_FILL_COLOR_RGB,
                SurfRampVisualStyle.sanitizeColor(wireframeFillColorRgb, SurfRampVisualStyle.DEFAULT_WIREFRAME_FILL_COLOR)
        );
    }

    public int getWireframeFillAlpha() {
        return SurfRampVisualStyle.sanitizeAlpha(
                this.entityData.get(WIREFRAME_FILL_ALPHA),
                SurfRampVisualStyle.DEFAULT_WIREFRAME_FILL_ALPHA
        );
    }

    public void setWireframeFillAlpha(int wireframeFillAlpha) {
        this.entityData.set(
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
        if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) {
            this.cachedTextureBlockIdForState = textureId;
            this.cachedTextureBlockState = Blocks.SMOOTH_STONE.defaultBlockState();
            return this.cachedTextureBlockState;
        }
        Block block = BuiltInRegistries.BLOCK.getValue(id);
        if (block == Blocks.AIR) {
            this.cachedTextureBlockIdForState = textureId;
            this.cachedTextureBlockState = Blocks.SMOOTH_STONE.defaultBlockState();
            return this.cachedTextureBlockState;
        }
        this.cachedTextureBlockIdForState = textureId;
        this.cachedTextureBlockState = block.defaultBlockState();
        return this.cachedTextureBlockState;
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (held.is(ModItems.SURF_STICK.get())) {
            if (!this.level().isClientSide() && player instanceof ServerPlayer serverPlayer) {
                SurfRampPlacementManager.openEditor(serverPlayer, this);
            }
            return InteractionResult.SUCCESS;
        }

        if (!player.isShiftKeyDown() || !player.getAbilities().instabuild) {
            return InteractionResult.PASS;
        }

        if (!(held.getItem() instanceof BlockItem blockItem)) {
            return InteractionResult.PASS;
        }

        Identifier blockId = BuiltInRegistries.BLOCK.getKey(blockItem.getBlock());
        if (blockId == null) {
            return InteractionResult.PASS;
        }
        String newTextureId = sanitizeTextureBlockId(blockId.toString());
        if (!this.level().isClientSide()) {
            this.setTextureBlockId(newTextureId);
            player.displayClientMessage(Component.literal("Ramp texture set to " + newTextureId), true);
        }
        return InteractionResult.SUCCESS;
    }

    public boolean isCurved() {
        byte state = this.cachedCurvedState;
        if (state == 0) {
            state = this.computeCurved() ? (byte) 2 : (byte) 1;
            this.cachedCurvedState = state;
        }
        return state == 2;
    }

    private boolean computeCurved() {
        List<Vec3> pathPoints = this.getPathPoints();
        if (pathPoints.size() > 2) {
            return true;
        }
        Vec3 start = this.getStart();
        Vec3 end = this.getEnd();
        return Math.abs(end.x - start.x) > 0.25D && Math.abs(end.z - start.z) > 0.25D;
    }

    public void setCenterlinePoints(List<Vec3> points) {
        this.setCenterlinePoints(points, 0.0D, 1.0D);
    }

    public void setCenterlinePoints(List<Vec3> points, double pathStartT, double pathEndT) {
        this.setPathPointsEncoded(this.encodePathPoints(points));
        this.setPathTRange(pathStartT, pathEndT);
    }

    public void setLinkedSeams(boolean linkedStart, boolean linkedEnd) {
        this.entityData.set(LINKED_START, linkedStart);
        this.entityData.set(LINKED_END, linkedEnd);
        this.invalidateGeometryCaches();
    }

    public static Vec3 blockCenter(BlockPos pos) {
        return new Vec3(pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D);
    }

    public static int resolveInsideCurveSideSign(Vec3 start, Vec3 end) {
        Vec3 control = computeControlPoint(start, end);
        Vec3 first = new Vec3(control.x - start.x, 0.0D, control.z - start.z);
        Vec3 second = new Vec3(end.x - control.x, 0.0D, end.z - control.z);
        double cross = first.x * second.z - first.z * second.x;
        if (Math.abs(cross) < 1.0E-6D) {
            return 1;
        }
        return cross > 0.0D ? 1 : -1;
    }

    public static boolean hasNearbyRamp(Level world, AABB queryBox, double expand) {
        if (world == null || queryBox == null) {
            return false;
        }
        RampRegistry registry = ACTIVE_RAMPS_BY_WORLD.get(world);
        if (registry == null || registry.ramps.isEmpty()) {
            return false;
        }
        AABB expanded = queryBox.inflate(expand);
        RampIndexSnapshot snapshot = registry.snapshot();
        int[] candidates = snapshot.candidates(expanded);
        int count = candidates == null ? snapshot.ordered.length : candidates.length;
        for (int k = 0; k < count; k++) {
            SurfRampEntity ramp = snapshot.ordered[candidates == null ? k : candidates[k]];
            if (ramp == null || !ramp.isAlive() || ramp.isRemoved()) {
                registry.remove(ramp);
                continue;
            }
            if (ramp.getBoundingBox().intersects(expanded)) {
                return true;
            }
        }
        if (registry.ramps.isEmpty()) {
            ACTIVE_RAMPS_BY_WORLD.remove(world, registry);
        }
        return false;
    }

    /**
     * The ramps whose bounding box intersects {@code queryBox} inflated by {@code expand}, in registry iteration
     * order (callers break ties by list order). A spatial index narrows the candidates; every candidate is still
     * checked exactly as the former full scan did.
     */
    public static List<SurfRampEntity> collectNearbyRamps(Level world, AABB queryBox, double expand) {
        List<SurfRampEntity> ramps = new ArrayList<>();
        if (world == null || queryBox == null) {
            return ramps;
        }
        RampRegistry registry = ACTIVE_RAMPS_BY_WORLD.get(world);
        if (registry == null || registry.ramps.isEmpty()) {
            return ramps;
        }
        AABB expanded = queryBox.inflate(expand);
        RampIndexSnapshot snapshot = registry.snapshot();
        int[] candidates = snapshot.candidates(expanded);
        int count = candidates == null ? snapshot.ordered.length : candidates.length;
        for (int k = 0; k < count; k++) {
            SurfRampEntity ramp = snapshot.ordered[candidates == null ? k : candidates[k]];
            if (ramp == null || !ramp.isAlive() || ramp.isRemoved()) {
                registry.remove(ramp);
                continue;
            }
            if (ramp.getBoundingBox().intersects(expanded)) {
                ramps.add(ramp);
            }
        }
        if (registry.ramps.isEmpty()) {
            ACTIVE_RAMPS_BY_WORLD.remove(world, registry);
        }
        return ramps;
    }

    public static List<SurfRampEntity> collectAllActiveRamps(Level world) {
        List<SurfRampEntity> ramps = new ArrayList<>();
        if (world == null) {
            return ramps;
        }
        RampRegistry registry = ACTIVE_RAMPS_BY_WORLD.get(world);
        if (registry == null || registry.ramps.isEmpty()) {
            return ramps;
        }
        Iterator<SurfRampEntity> iterator = registry.ramps.iterator();
        while (iterator.hasNext()) {
            SurfRampEntity ramp = iterator.next();
            if (ramp == null || !ramp.isAlive() || ramp.isRemoved()) {
                registry.remove(ramp);
                continue;
            }
            ramps.add(ramp);
        }
        if (registry.ramps.isEmpty()) {
            ACTIVE_RAMPS_BY_WORLD.remove(world, registry);
        }
        return ramps;
    }

    public boolean isNearEndpointXZ(double x, double z, double endpointTThreshold, double lateralToleranceExtra) {
        CenterlineSampleCache cache = this.getOrBuildCenterlineSampleCache();
        double lateralTolerance = this.getRampWidth() + Math.max(lateralToleranceExtra, 0.0D);
        // Every sample lies inside the cache bounds, so their distance is >= this bound (exactly, in floating
        // point too): when even the bound is outside the tolerance, the scan below would return false.
        if (cache.lowerBoundDistanceSquared(x, z) > lateralTolerance * lateralTolerance) {
            return false;
        }
        int samples = cache.samples;
        double bestDistanceSquared = Double.MAX_VALUE;
        double bestT = 0.0D;

        for (int i = 0; i <= samples; i++) {
            double dx = x - cache.centerX[i];
            double dz = z - cache.centerZ[i];
            double distanceSquared = dx * dx + dz * dz;
            if (distanceSquared < bestDistanceSquared) {
                bestDistanceSquared = distanceSquared;
                bestT = (double) i / (double) samples;
            }
        }

        if (bestDistanceSquared > lateralTolerance * lateralTolerance) {
            return false;
        }

        double clampedThreshold = Mth.clamp(endpointTThreshold, 0.01D, 0.45D);
        return bestT <= clampedThreshold || bestT >= 1.0D - clampedThreshold;
    }

    public boolean isNearRampXZ(double x, double z, double lateralToleranceExtra) {
        CenterlineSampleCache cache = this.getOrBuildCenterlineSampleCache();
        double lateralTolerance = this.getRampWidth() + Math.max(lateralToleranceExtra, 0.0D);
        if (cache.lowerBoundDistanceSquared(x, z) > lateralTolerance * lateralTolerance) {
            return false;
        }
        int samples = cache.samples;
        double bestDistanceSquared = Double.MAX_VALUE;

        for (int i = 0; i <= samples; i++) {
            double dx = x - cache.centerX[i];
            double dz = z - cache.centerZ[i];
            double distanceSquared = dx * dx + dz * dz;
            if (distanceSquared < bestDistanceSquared) {
                bestDistanceSquared = distanceSquared;
            }
        }

        return bestDistanceSquared <= lateralTolerance * lateralTolerance;
    }

    // The centerline samples isNearEndpointXZ / isNearRampXZ scan (t = i / samples), computed once per geometry
    // with the same getCenterlinePoint calls they used to make on every query.
    private CenterlineSampleCache getOrBuildCenterlineSampleCache() {
        CenterlineSampleCache cache = this.centerlineSampleCache;
        if (cache != null) {
            return cache;
        }
        int samples = Mth.clamp((int) Math.ceil(this.getHorizontalLength() * 10.0D), 24, 220);
        double[] centerX = new double[samples + 1];
        double[] centerZ = new double[samples + 1];
        for (int i = 0; i <= samples; i++) {
            double t = (double) i / (double) samples;
            Vec3 center = this.getCenterlinePoint(t);
            centerX[i] = center.x;
            centerZ[i] = center.z;
        }
        cache = new CenterlineSampleCache(samples, centerX, centerZ);
        this.centerlineSampleCache = cache;
        return cache;
    }

    private void registerActiveRamp() {
        Level world = this.level();
        if (world == null) {
            return;
        }
        RampRegistry registry = ACTIVE_RAMPS_BY_WORLD.computeIfAbsent(world, key -> new RampRegistry());
        this.registeredIn = registry;
        if (registry.ramps.add(this)) {
            registry.markDirty();
        }
    }

    private void unregisterActiveRamp() {
        Level world = this.level();
        if (world == null) {
            return;
        }
        RampRegistry registry = ACTIVE_RAMPS_BY_WORLD.get(world);
        if (registry == null) {
            return;
        }
        registry.remove(this);
        if (registry.ramps.isEmpty()) {
            ACTIVE_RAMPS_BY_WORLD.remove(world, registry);
        }
    }

    @Nullable
    public SurfContact sampleContact(double sampleX, double sampleZ, double minFeetY, double maxFeetY) {
        if (!this.isWithinSurfaceSearchBounds(sampleX, sampleZ, minFeetY, maxFeetY)) {
            return null;
        }
        SurfaceSearchCache cache = this.getOrBuildSurfaceSearchCache();
        if (cache != null && (cache.surfaceYUpperBound < minFeetY - CONTACT_THICKNESS_BELOW
                || cache.surfaceYLowerBound > maxFeetY + CONTACT_THICKNESS_ABOVE)) {
            // Every surface point lies within [lower, upper]: the check below would reject it.
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
        SurfaceSearchCache cache = this.getOrBuildSurfaceSearchCache();
        if (cache != null && (targetFeetY - cache.surfaceYUpperBound > maxVerticalDistance
                || cache.surfaceYLowerBound - targetFeetY > maxVerticalDistance)) {
            // surfaceY <= upper (resp. >= lower) makes |targetFeetY - surfaceY| > maxVerticalDistance below.
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
        SurfaceSearchCache cache = this.getOrBuildSurfaceSearchCache();
        if (cache != null && (cache.surfaceYUpperBound < feetY - maxBelow || cache.surfaceYLowerBound > feetY + maxAbove)) {
            return null;
        }
        SurfacePoint point = this.findSurfacePoint(sampleX, sampleZ);
        if (point == null) {
            return null;
        }
        if (point.surfaceY < feetY - maxBelow || point.surfaceY > feetY + maxAbove) {
            return null;
        }
        return new SurfContact(point.normal, point.surfaceY, this.isHardEndpointT(point.t));
    }

    public CollisionAppendStats appendCollisionShapes(AABB queryBox, List<VoxelShape> shapes) {
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
        AABB expandedQuery = queryBox.inflate(0.2D);
        double queryCenterX = (expandedQuery.minX + expandedQuery.maxX) * 0.5D;
        double queryCenterZ = (expandedQuery.minZ + expandedQuery.maxZ) * 0.5D;

        double queryHalfX = (expandedQuery.maxX - expandedQuery.minX) * 0.5D;
        double queryHalfZ = (expandedQuery.maxZ - expandedQuery.minZ) * 0.5D;
        double queryRadiusXZ = Math.hypot(queryHalfX, queryHalfZ) + 0.24D;
        double segmentLength = Math.max(cache.segmentLength, 0.06D);
        int requiredSegments = (int) Math.ceil(queryRadiusXZ / segmentLength) + 4;
        int halfWindowSegments = Math.max(6, requiredSegments);
        double closestT = cache.estimateClosestT(queryCenterX, queryCenterZ);
        int centerSegment = Mth.clamp((int) Math.floor(closestT * (double) cache.segmentCount), 0, cache.segmentCount);
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

    public int prewarmCollisionShapeCache(AABB queryBox, int maxSegmentsToBuild) {
        if (queryBox == null || maxSegmentsToBuild <= 0) {
            return 0;
        }
        CollisionShapeCache cache = this.getOrBuildCollisionShapeCache();
        if (cache == null) {
            return 0;
        }

        AABB expandedQuery = queryBox.inflate(0.2D);
        double queryCenterX = (expandedQuery.minX + expandedQuery.maxX) * 0.5D;
        double queryCenterZ = (expandedQuery.minZ + expandedQuery.maxZ) * 0.5D;
        double queryHalfX = (expandedQuery.maxX - expandedQuery.minX) * 0.5D;
        double queryHalfZ = (expandedQuery.maxZ - expandedQuery.minZ) * 0.5D;
        double queryRadiusXZ = Math.hypot(queryHalfX, queryHalfZ) + 0.24D;
        double segmentLength = Math.max(cache.segmentLength, 0.06D);
        int halfWindowSegments = Math.max(6, (int) Math.ceil(queryRadiusXZ / segmentLength) + 4);
        double closestT = cache.estimateClosestT(queryCenterX, queryCenterZ);
        int centerSegment = Mth.clamp((int) Math.floor(closestT * (double) cache.segmentCount), 0, cache.segmentCount);
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
        AABB bounds = this.getBoundingBox();
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
            Vec3 center = this.getCenterlinePoint(t);
            Vec3 tangent = this.getTangent(t);

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
        SurfaceSearchCache cache = new SurfaceSearchCache(
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
        // Drop, width, sides and seam flags are geometry fields: a change of any of them invalidates this cache.
        cache.initSearchParameters(
                Math.max(this.getRampWidth(), 0.15D),
                this.getDrop(),
                this.getSideSign(),
                this.isTwoSided(),
                this.isLinkedStart(),
                this.isLinkedEnd()
        );
        return cache;
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

            double sampleT0 = Mth.clamp(t0, 0.0D, 1.0D);
            double sampleT1 = Mth.clamp(t1, 0.0D, 1.0D);
            if (sampleT1 <= sampleT0 + 1.0E-6D) {
                continue;
            }

            Vec3 center0 = this.getCenterlinePoint(sampleT0);
            Vec3 center1 = this.getCenterlinePoint(sampleT1);
            Vec3 left0 = this.getLeftNormal(sampleT0);
            Vec3 left1 = this.getLeftNormal(sampleT1);
            double edgeL0 = lateralStart;
            double edgeL1 = width;

            Vec3 p00 = this.sampleCollisionVertex(sampleT0, center0, left0, edgeL0, width, sideSign);
            Vec3 p01 = this.sampleCollisionVertex(sampleT0, center0, left0, edgeL1, width, sideSign);
            Vec3 p10 = this.sampleCollisionVertex(sampleT1, center1, left1, edgeL0, width, sideSign);
            Vec3 p11 = this.sampleCollisionVertex(sampleT1, center1, left1, edgeL1, width, sideSign);

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
                        new AABB(minX, minY, minZ, maxX, maxY, maxZ)
                );
            }
        }

        int sampleCount = Mth.clamp(segmentCount, 36, 140);
        double[] sampleCenterX = new double[sampleCount + 1];
        double[] sampleCenterZ = new double[sampleCount + 1];
        for (int i = 0; i <= sampleCount; i++) {
            double t = (double) i / (double) sampleCount;
            Vec3 center = this.getCenterlinePoint(t);
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

        Vec3 center0 = this.getCenterlinePoint(sampleT0);
        Vec3 center1 = this.getCenterlinePoint(sampleT1);
        Vec3 left0 = this.getLeftNormal(sampleT0);
        Vec3 left1 = this.getLeftNormal(sampleT1);
        boolean curved = this.isCurved();
        double sampleTMid = (sampleT0 + sampleT1) * 0.5D;
        Vec3 centerMid = curved ? this.getCenterlinePoint(sampleTMid) : null;
        Vec3 leftMid = curved ? this.getLeftNormal(sampleTMid) : null;
        List<CollisionPatchCache> patchList = new ArrayList<>(cache.widthSlices);

        for (int j = 0; j < cache.widthSlices; j++) {
            double localL0 = cache.lateralStart + (double) j * cache.lateralStep;
            double localL1 = localL0 + cache.lateralStep;

            Vec3 p00 = this.sampleCollisionVertex(sampleT0, center0, left0, localL0, cache.width, cache.sideSign);
            Vec3 p01 = this.sampleCollisionVertex(sampleT0, center0, left0, localL1, cache.width, cache.sideSign);
            Vec3 p10 = this.sampleCollisionVertex(sampleT1, center1, left1, localL0, cache.width, cache.sideSign);
            Vec3 p11 = this.sampleCollisionVertex(sampleT1, center1, left1, localL1, cache.width, cache.sideSign);
            Vec3 midA = null;
            Vec3 midB = null;
            if (curved) {
                midA = this.sampleCollisionVertex(sampleTMid, centerMid, leftMid, localL0, cache.width, cache.sideSign);
                midB = this.sampleCollisionVertex(sampleTMid, centerMid, leftMid, localL1, cache.width, cache.sideSign);
            }

            AABB patchBox = this.makeCollisionPatchBox(p00, p01, p10, p11, midA, midB);
            if (patchBox == null) {
                continue;
            }
            patchList.add(new CollisionPatchCache(patchBox, Shapes.create(patchBox)));
        }

        if (patchList.isEmpty()) {
            return CollisionPatchCache.EMPTY_ARRAY;
        }
        return patchList.toArray(new CollisionPatchCache[0]);
    }

    private Vec3 sampleCollisionVertex(double t, Vec3 center, Vec3 left, double localLateral, double width, int sideSign) {
        double worldLateral;
        double normalizedLateral;
        if (this.isTwoSided()) {
            worldLateral = localLateral;
            normalizedLateral = Math.abs(localLateral) / width;
        } else {
            worldLateral = localLateral * sideSign;
            normalizedLateral = localLateral / width;
        }

        Vec3 worldPos = center.add(left.scale(worldLateral));
        double baseY = this.sampleBaseY(t);
        double surfaceY = baseY + this.getDrop() * (1.0D - Mth.clamp(normalizedLateral, 0.0D, 1.0D));
        return new Vec3(worldPos.x, surfaceY, worldPos.z);
    }

    @Nullable
    private AABB makeCollisionPatchBox(Vec3 p00, Vec3 p01, Vec3 p10, Vec3 p11, @Nullable Vec3 midA, @Nullable Vec3 midB) {
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

        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private Vec3 getLeftNormal(double t) {
        Vec3 tangent = this.getTangent(t);
        Vec3 left = new Vec3(-tangent.z, 0.0D, tangent.x);
        if (left.lengthSqr() < 1.0E-8D) {
            return new Vec3(1.0D, 0.0D, 0.0D);
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
            Vec3 center = this.getCenterlinePoint(t);
            minX = Math.min(minX, center.x - width - 0.35D);
            maxX = Math.max(maxX, center.x + width + 0.35D);
            minZ = Math.min(minZ, center.z - width - 0.35D);
            maxZ = Math.max(maxZ, center.z + width + 0.35D);
            double baseY = this.sampleBaseY(t);
            minBaseY = Math.min(minBaseY, baseY);
            maxBaseY = Math.max(maxBaseY, baseY);
        }

        if (!Double.isFinite(minBaseY) || !Double.isFinite(maxBaseY)) {
            Vec3 start = this.getStart();
            Vec3 end = this.getEnd();
            minBaseY = Math.min(start.y, end.y);
            maxBaseY = Math.max(start.y, end.y);
        }

        double minY = minBaseY - 0.65D;
        double maxY = maxBaseY + this.getDrop() + 0.45D;

        this.setBoundingBox(new AABB(minX, minY, minZ, maxX, maxY, maxZ));
        this.boundsDirty = false;
        RampRegistry registry = this.registeredIn;
        if (registry != null) {
            registry.markDirty();
        }
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
        return Mth.clamp(
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
        this.cachedCollisionSegmentCount = Mth.clamp(
                (int) Math.ceil(Math.max(this.cachedHorizontalLength * 14.0D * curveBoost, 36.0D)),
                24,
                240
        );
        this.cachedCollisionShapeSegmentCount = Mth.clamp(
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

    /**
     * Changes whenever a geometry field changes (both sides): lets client-side caches of derived data (the renderer's
     * meshes) know when to rebuild.
     */
    public int getGeometryVersion() {
        return this.geometryVersion;
    }

    private void invalidateGeometryCaches() {
        this.geometryVersion++;
        this.cachedStart = null;
        this.cachedEnd = null;
        this.cachedCurvedState = 0;
        this.centerlineSampleCache = null;
        this.cachedDerivedGeometrySignature = Long.MIN_VALUE;
        this.collisionShapeCache = null;
        this.collisionShapeCacheSignature = Long.MIN_VALUE;
        this.surfaceSearchCache = null;
        this.surfaceSearchCacheSignature = Long.MIN_VALUE;
        this.boundsDirty = true;
    }

    private double computeHorizontalLengthRaw() {
        List<Vec3> points = this.getPathPoints();
        if (points.size() > 1) {
            double tStart = this.getPathTStart();
            double tEnd = this.getPathTEnd();
            int samples = Mth.clamp(
                    (int) Math.ceil(Math.max((tEnd - tStart) * (points.size() - 1) * 26.0D, 24.0D)),
                    24,
                    320
            );
            double length = 0.0D;
            Vec3 previous = this.samplePathCenterline(points, tStart);
            for (int i = 1; i <= samples; i++) {
                double t = Mth.lerp((double) i / (double) samples, tStart, tEnd);
                Vec3 current = this.samplePathCenterline(points, t);
                length += new Vec3(current.x - previous.x, 0.0D, current.z - previous.z).length();
                previous = current;
            }
            return Math.max(length, 0.01D);
        }
        Vec3 start = this.getStart();
        Vec3 end = this.getEnd();
        return new Vec3(end.x - start.x, 0.0D, end.z - start.z).length();
    }

    @Nullable
    private SurfacePoint findSurfacePoint(double sampleX, double sampleZ) {
        SurfaceSearchCache cache = this.getOrBuildSurfaceSearchCache();
        if (cache == null || cache.segmentCount <= 0) {
            return null;
        }
        // The result is a pure function of (sampleX, sampleZ) and the geometry the cache was built from, and the
        // surf pipeline queries the same foot positions several times per tick (contact, fallbacks, re-seat):
        // reuse the result of an identical query (bit-equal coordinates) made against this cache.
        long xBits = Double.doubleToRawLongBits(sampleX);
        long zBits = Double.doubleToRawLongBits(sampleZ);
        int slot = SurfaceSearchCache.memoSlot(xBits, zBits);
        SurfaceMemoEntry memo = cache.memo[slot];
        if (memo != null && memo.xBits == xBits && memo.zBits == zBits) {
            return memo.point;
        }
        SurfacePoint point = this.computeSurfacePoint(cache, sampleX, sampleZ);
        cache.memo[slot] = new SurfaceMemoEntry(xBits, zBits, point);
        return point;
    }

    @Nullable
    private SurfacePoint computeSurfacePoint(SurfaceSearchCache cache, double sampleX, double sampleZ) {
        if (cache.lowerBoundDistanceSquared(sampleX, sampleZ) > cache.acceptRadiusSquared) {
            // Out of reach of every sample (cheap whole-ramp test before the closest-sample search).
            return null;
        }
        int segmentCount = cache.segmentCount;
        int closestIndex = cache.closestIndex(sampleX, sampleZ);
        double closestDx = sampleX - cache.centerX[closestIndex];
        double closestDz = sampleZ - cache.centerZ[closestIndex];
        if (closestDx * closestDx + closestDz * closestDz > cache.acceptRadiusSquared) {
            // No sample of the whole ramp is close enough to accept: all three searches below would find nothing.
            return null;
        }
        double estimatedT = (double) closestIndex / (double) Math.max(segmentCount, 1);
        int centerIndex = Mth.clamp((int) Math.round(estimatedT * segmentCount), 0, segmentCount);
        int best = this.findBestSurfaceSample(sampleX, sampleZ, cache, centerIndex);
        return best < 0 ? null : this.buildSurfacePoint(cache, best, sampleX, sampleZ);
    }

    /**
     * The search looks for the accepted sample with the smallest metric in three nested index windows around
     * centerIndex: the local one, then (only if that accepted nothing) the expanded one, then the whole range, each
     * keeping its first best sample. A window is only reached when the smaller one accepted nothing, so it is enough
     * to scan the part of it that is new (in increasing index order). Returns the sample index or -1.
     */
    private int findBestSurfaceSample(double sampleX, double sampleZ, SurfaceSearchCache cache, int centerIndex) {
        int segmentCount = cache.segmentCount;
        if (segmentCount <= 0) {
            return -1;
        }
        int localStart = Mth.clamp(centerIndex - cache.halfWindow, 0, segmentCount);
        int localEnd = Mth.clamp(centerIndex + cache.halfWindow, 0, segmentCount);
        // The block holding centerIndex lies inside the local window (halfWindow >= 10 > block size) and holds the
        // samples nearest the query: scanning it first gives the pruning below a good bound right away.
        int seedBlock = centerIndex / SEARCH_BLOCK_SIZE;
        int best = this.scanSurfaceBlock(sampleX, sampleZ, cache, seedBlock, localStart, localEnd, -1);
        for (int block = localStart / SEARCH_BLOCK_SIZE; block <= localEnd / SEARCH_BLOCK_SIZE; block++) {
            if (block != seedBlock) {
                best = this.scanSurfaceBlock(sampleX, sampleZ, cache, block, localStart, localEnd, best);
            }
        }
        if (best >= 0) {
            return best;
        }
        int expandedStart = Mth.clamp(centerIndex - cache.expandedHalfWindow, 0, segmentCount);
        int expandedEnd = Mth.clamp(centerIndex + cache.expandedHalfWindow, 0, segmentCount);
        best = this.scanSurfaceRange(sampleX, sampleZ, cache, expandedStart, localStart - 1, -1);
        best = this.scanSurfaceRange(sampleX, sampleZ, cache, localEnd + 1, expandedEnd, best);
        if (best >= 0) {
            return best;
        }
        int fullStart = Mth.clamp(Math.min(-cache.seamExtraSamples, segmentCount + cache.seamExtraSamples), 0, segmentCount);
        int fullEnd = Mth.clamp(Math.max(-cache.seamExtraSamples, segmentCount + cache.seamExtraSamples), 0, segmentCount);
        best = this.scanSurfaceRange(sampleX, sampleZ, cache, fullStart, Math.min(expandedStart, localStart) - 1, -1);
        return this.scanSurfaceRange(sampleX, sampleZ, cache, Math.max(expandedEnd, localEnd) + 1, fullEnd, best);
    }

    private int scanSurfaceRange(double sampleX, double sampleZ, SurfaceSearchCache cache, int from, int to, int best) {
        if (from > to) {
            return best;
        }
        for (int block = from / SEARCH_BLOCK_SIZE; block <= to / SEARCH_BLOCK_SIZE; block++) {
            best = this.scanSurfaceBlock(sampleX, sampleZ, cache, block, from, to, best);
        }
        return best;
    }

    // Metric of sample i (computed exactly as in scanSurfaceBlock).
    private static double surfaceSampleMetric(SurfaceSearchCache cache, int i, double sampleX, double sampleZ) {
        double deltaX = sampleX - cache.centerX[i];
        double deltaZ = sampleZ - cache.centerZ[i];
        double along = deltaX * cache.tangentX[i] + deltaZ * cache.tangentZ[i];
        return deltaX * deltaX + deltaZ * deltaZ + Math.abs(along) * 0.02D;
    }

    /**
     * Scans the samples of {@code block} within [from, to] and returns the best accepted sample among them and
     * {@code best} (-1 = none): the smallest metric, the lowest index on equal metrics (what an increasing scan with
     * a strict "<" keeps, whatever order the blocks are visited in). The whole block is skipped when all its samples
     * fail the along test or the reach test, or when their metric lower bound exceeds the current best's metric.
     */
    private int scanSurfaceBlock(double sampleX, double sampleZ, SurfaceSearchCache cache, int block, int from, int to, int best) {
        int blockStart = Math.max(from, block * SEARCH_BLOCK_SIZE);
        int blockEnd = Math.min(to, block * SEARCH_BLOCK_SIZE + SEARCH_BLOCK_SIZE - 1);
        if (blockStart > blockEnd) {
            return best;
        }
        double alongLimit = cache.alongLimit;
        // Bounds of the block samples' along values (see SurfaceSearchCache.blockTangentDeviation), padded far
        // beyond rounding error; NaN never prunes.
        int first = block * SEARCH_BLOCK_SIZE;
        double firstDx = sampleX - cache.centerX[first];
        double firstDz = sampleZ - cache.centerZ[first];
        double base = firstDx * cache.tangentX[first] + firstDz * cache.tangentZ[first];
        double reach = Math.abs(firstDx) + Math.abs(firstDz);
        double slack = reach * cache.blockTangentDeviation[block] + 1.0E-5D + reach * 1.0E-9D;
        double alongLow = base - slack - cache.blockOffsetMax[block];
        double alongHigh = base + slack - cache.blockOffsetMin[block];
        if (alongLow > alongLimit || alongHigh < -alongLimit) {
            return best;
        }
        double distanceBound = cache.blockLowerBoundDistanceSquared(block, sampleX, sampleZ);
        if (distanceBound > cache.acceptRadiusSquared) {
            return best;
        }
        double bestMetric = Double.MAX_VALUE;
        if (best >= 0) {
            bestMetric = surfaceSampleMetric(cache, best, sampleX, sampleZ);
            // Every sample's metric (distance^2 + |along| * 0.02) is >= this bound (monotonic rounding).
            double metricBound = distanceBound + Math.max(0.0D, Math.max(alongLow, -alongHigh)) * 0.02D;
            if (metricBound > bestMetric) {
                return best;
            }
        }

        int segmentCount = cache.segmentCount;
        double halfSegmentLength = cache.halfSegmentLength;
        boolean linkedStart = cache.linkedStart;
        boolean linkedEnd = cache.linkedEnd;
        boolean linked = linkedStart || linkedEnd;
        boolean twoSided = cache.twoSided;
        double twoSidedMax = cache.width + CONTACT_EPSILON;
        double oneSidedMax = cache.width + CONTACT_EPSILON + 0.16D;
        int sideSign = cache.sideSign;
        for (int i = blockStart; i <= blockEnd; i++) {
            double deltaX = sampleX - cache.centerX[i];
            double deltaZ = sampleZ - cache.centerZ[i];
            double along = deltaX * cache.tangentX[i] + deltaZ * cache.tangentZ[i];
            // alongLimit is the largest threshold below (seam boost 0.30 on linked ramps, else 0).
            if (Math.abs(along) > alongLimit) {
                continue;
            }
            if (linked) {
                double t = (double) i / (double) segmentCount;
                double seamAlongBoost = 0.0D;
                if (linkedStart && t < 0.14D) {
                    seamAlongBoost = 0.30D;
                }
                if (linkedEnd && t > 0.86D) {
                    seamAlongBoost = Math.max(seamAlongBoost, 0.30D);
                }
                if (Math.abs(along) > halfSegmentLength + seamAlongBoost + CONTACT_EPSILON) {
                    continue;
                }
            }

            double lateral = deltaX * cache.leftX[i] + deltaZ * cache.leftZ[i];
            if (twoSided) {
                if (Math.abs(lateral) > twoSidedMax) {
                    continue;
                }
            } else {
                double orientedLateral = lateral * sideSign;
                if (orientedLateral < -CONTACT_EPSILON || orientedLateral > oneSidedMax) {
                    continue;
                }
            }

            double metric = deltaX * deltaX + deltaZ * deltaZ + Math.abs(along) * 0.02D;
            if (metric < bestMetric || (metric == bestMetric && i < best)) {
                bestMetric = metric;
                best = i;
            }
        }
        return best;
    }

    // The surface point of accepted sample i for the query (sampleX, sampleZ).
    private SurfacePoint buildSurfacePoint(SurfaceSearchCache cache, int i, double sampleX, double sampleZ) {
        double width = cache.width;
        double drop = cache.drop;
        int sideSign = cache.sideSign;
        double crossSlope = -drop / Math.max(width, 0.15D);
        double t = (double) i / (double) cache.segmentCount;
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
        double lateral = deltaX * leftX + deltaZ * leftZ;
        double normalizedLateral;
        double normalSideSign;
        if (cache.twoSided) {
            normalizedLateral = Math.abs(lateral) / width;
            normalSideSign = lateral >= 0.0D ? 1.0D : -1.0D;
        } else {
            double orientedLateral = lateral * sideSign;
            normalizedLateral = Mth.clamp(orientedLateral / width, 0.0D, 1.0D);
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
        return new SurfacePoint(surfaceY, new Vec3(normalX, normalY, normalZ), t);
    }

    private boolean isHardEndpointT(double t) {
        double clampedT = Mth.clamp(t, 0.0D, 1.0D);
        double horizontalLength = Math.max(this.getHorizontalLength(), 0.5D);
        double hardEndpointThreshold = Mth.clamp(0.75D / horizontalLength, 0.012D, 0.06D);
        if (!this.isLinkedStart() && clampedT <= hardEndpointThreshold) {
            return true;
        }
        return !this.isLinkedEnd() && clampedT >= 1.0D - hardEndpointThreshold;
    }

    private Vec3 getCenterlinePoint(double t) {
        List<Vec3> pathPoints = this.getPathPoints();
        if (pathPoints.size() >= 2) {
            double mappedT = this.mapLocalPathTToGlobalPathT(t);
            return this.samplePathCenterline(pathPoints, mappedT);
        }

        Vec3 start = this.getStart();
        Vec3 end = this.getEnd();
        if (!this.isCurved()) {
            return start.lerp(end, t);
        }

        Vec3 control = computeControlPoint(start, end);
        double invT = 1.0D - t;
        double x = invT * invT * start.x + 2.0D * invT * t * control.x + t * t * end.x;
        double y = invT * invT * start.y + 2.0D * invT * t * control.y + t * t * end.y;
        double z = invT * invT * start.z + 2.0D * invT * t * control.z + t * t * end.z;
        return new Vec3(x, y, z);
    }

    private Vec3 getTangent(double t) {
        List<Vec3> pathPoints = this.getPathPoints();
        if (pathPoints.size() >= 2) {
            double mappedT = this.mapLocalPathTToGlobalPathT(t);
            double dt = 1.0D / Math.max(64.0D, pathPoints.size() * 32.0D);
            dt = Math.max(dt, 1.0E-4D);
            double t0 = Math.max(0.0D, mappedT - dt);
            double t1 = Math.min(1.0D, mappedT + dt);
            Vec3 before = this.samplePathCenterline(pathPoints, t0);
            Vec3 after = this.samplePathCenterline(pathPoints, t1);
            Vec3 tangent = new Vec3(after.x - before.x, 0.0D, after.z - before.z);
            if (tangent.lengthSqr() < 1.0E-8D) {
                int segmentCount = pathPoints.size() - 1;
                int segmentIndex = Mth.clamp((int) Math.floor(Mth.clamp(mappedT, 0.0D, 1.0D) * segmentCount), 0, segmentCount - 1);
                Vec3 segStart = pathPoints.get(segmentIndex);
                Vec3 segEnd = pathPoints.get(segmentIndex + 1);
                tangent = new Vec3(segEnd.x - segStart.x, 0.0D, segEnd.z - segStart.z);
                if (tangent.lengthSqr() < 1.0E-8D && segmentIndex > 0) {
                    Vec3 prevStart = pathPoints.get(segmentIndex - 1);
                    tangent = new Vec3(segStart.x - prevStart.x, 0.0D, segStart.z - prevStart.z);
                }
            }
            if (tangent.lengthSqr() < 1.0E-8D) {
                Vec3 start = pathPoints.get(0);
                Vec3 end = pathPoints.get(pathPoints.size() - 1);
                tangent = new Vec3(end.x - start.x, 0.0D, end.z - start.z);
            }
            if (tangent.lengthSqr() < 1.0E-8D) {
                return new Vec3(1.0D, 0.0D, 0.0D);
            }
            return tangent.normalize();
        }

        Vec3 start = this.getStart();
        Vec3 end = this.getEnd();

        Vec3 tangent;
        if (this.isCurved()) {
            Vec3 control = computeControlPoint(start, end);
            Vec3 first = control.subtract(start).scale(2.0D * (1.0D - t));
            Vec3 second = end.subtract(control).scale(2.0D * t);
            tangent = new Vec3(first.x + second.x, 0.0D, first.z + second.z);
        } else {
            tangent = new Vec3(end.x - start.x, 0.0D, end.z - start.z);
        }

        if (tangent.lengthSqr() < 1.0E-8D) {
            return new Vec3(1.0D, 0.0D, 0.0D);
        }

        return tangent.normalize();
    }

    private double getPathTStart() {
        return Mth.clamp(this.entityData.get(PATH_T_START), 0.0F, 1.0F);
    }

    private double getPathTEnd() {
        return Mth.clamp(this.entityData.get(PATH_T_END), 0.0F, 1.0F);
    }

    private void setPathTRange(double pathStartT, double pathEndT) {
        double start = Mth.clamp(pathStartT, 0.0D, 1.0D);
        double end = Mth.clamp(pathEndT, 0.0D, 1.0D);
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

        this.entityData.set(PATH_T_START, (float) start);
        this.entityData.set(PATH_T_END, (float) end);
        this.invalidateGeometryCaches();
    }

    private double mapLocalPathTToGlobalPathT(double localT) {
        double clampedLocalT = Mth.clamp(localT, 0.0D, 1.0D);
        double start = this.getPathTStart();
        double end = this.getPathTEnd();
        if (end < start) {
            double swap = start;
            start = end;
            end = swap;
        }
        return Mth.lerp(clampedLocalT, start, end);
    }

    private Vec3 samplePathCenterline(List<Vec3> pathPoints, double pathT) {
        double clampedT = Mth.clamp(pathT, 0.0D, 1.0D);
        if (pathPoints.size() == 2) {
            return pathPoints.get(0).lerp(pathPoints.get(1), clampedT);
        }

        int segmentCount = pathPoints.size() - 1;
        double scaled = clampedT * segmentCount;
        int segmentIndex = Mth.clamp((int) Math.floor(scaled), 0, segmentCount - 1);
        double localT = scaled - segmentIndex;

        Vec3 p0 = pathPoints.get(Math.max(segmentIndex - 1, 0));
        Vec3 p1 = pathPoints.get(segmentIndex);
        Vec3 p2 = pathPoints.get(segmentIndex + 1);
        Vec3 p3 = pathPoints.get(Math.min(segmentIndex + 2, pathPoints.size() - 1));
        return catmullRom(p0, p1, p2, p3, localT);
    }

    private List<Vec3> getPathPoints() {
        String raw = this.entityData.get(PATH_POINTS);
        if (!Objects.equals(raw, this.cachedPathPointsRaw)) {
            this.cachedPathPointsRaw = raw;
            this.cachedPathPoints = this.parsePathPoints(raw);
        }
        return this.cachedPathPoints;
    }

    private List<Vec3> parsePathPoints(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }

        String[] tokens = raw.split(";");
        List<Vec3> parsed = new ArrayList<>();
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
                parsed.add(new Vec3(x, y, z));
            } catch (NumberFormatException ignored) {
            }
        }

        if (parsed.size() < 2) {
            return List.of();
        }
        return List.copyOf(parsed);
    }

    private String encodePathPoints(List<Vec3> points) {
        if (points == null || points.size() < 2) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        int written = 0;
        for (Vec3 point : points) {
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
        this.entityData.set(PATH_POINTS, safe);
        this.cachedPathPointsRaw = safe;
        this.cachedPathPoints = this.parsePathPoints(safe);
        this.invalidateGeometryCaches();
    }

    private static Vec3 catmullRom(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, double t) {
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
        return new Vec3(x, y, z);
    }

    private static Vec3 computeControlPoint(Vec3 start, Vec3 end) {
        return new Vec3(start.x, Mth.lerp(0.5D, start.y, end.y), end.z);
    }

    private static String sanitizeTextureBlockId(String textureBlockId) {
        Identifier parsed = Identifier.tryParse(textureBlockId);
        if (parsed == null || !BuiltInRegistries.BLOCK.containsKey(parsed)) {
            return DEFAULT_TEXTURE_BLOCK_ID;
        }
        if (BuiltInRegistries.BLOCK.getValue(parsed) == Blocks.AIR) {
            return DEFAULT_TEXTURE_BLOCK_ID;
        }
        return parsed.toString();
    }

    public Vec3 sampleCenterline(double t) {
        return this.getCenterlinePoint(Mth.clamp(t, 0.0D, 1.0D));
    }

    public Vec3 sampleCenterlineCached(double t) {
        SurfaceSearchCache cache = this.getOrBuildSurfaceSearchCache();
        if (cache == null || cache.segmentCount <= 0) {
            return this.sampleCenterline(t);
        }
        double scaled = Mth.clamp(t, 0.0D, 1.0D) * (double) cache.segmentCount;
        double x = sampleInterpolated(cache.centerX, scaled);
        double y = sampleInterpolated(cache.baseY, scaled);
        double z = sampleInterpolated(cache.centerZ, scaled);
        return new Vec3(x, y, z);
    }

    public Vec3 sampleLeft(double t) {
        Vec3 tangent = this.getTangent(Mth.clamp(t, 0.0D, 1.0D));
        return new Vec3(-tangent.z, 0.0D, tangent.x).normalize();
    }

    public Vec3 sampleLeftCached(double t) {
        SurfaceSearchCache cache = this.getOrBuildSurfaceSearchCache();
        if (cache == null || cache.segmentCount <= 0) {
            return this.sampleLeft(t);
        }
        double scaled = Mth.clamp(t, 0.0D, 1.0D) * (double) cache.segmentCount;
        double lx = sampleInterpolated(cache.leftX, scaled);
        double lz = sampleInterpolated(cache.leftZ, scaled);
        double length = Math.hypot(lx, lz);
        if (length < 1.0E-8D) {
            return new Vec3(1.0D, 0.0D, 0.0D);
        }
        return new Vec3(lx / length, 0.0D, lz / length);
    }

    public double sampleBaseY(double t) {
        double clamped = Mth.clamp(t, 0.0D, 1.0D);
        List<Vec3> pathPoints = this.getPathPoints();
        if (pathPoints.size() >= 2) {
            double mappedT = this.mapLocalPathTToGlobalPathT(clamped);
            return this.samplePathCenterline(pathPoints, mappedT).y;
        }
        return Mth.lerp(clamped, this.getStart().y, this.getEnd().y);
    }

    public double sampleBaseYCached(double t) {
        SurfaceSearchCache cache = this.getOrBuildSurfaceSearchCache();
        if (cache == null || cache.segmentCount <= 0) {
            return this.sampleBaseY(t);
        }
        double scaled = Mth.clamp(t, 0.0D, 1.0D) * (double) cache.segmentCount;
        return sampleInterpolated(cache.baseY, scaled);
    }

    private static double sampleInterpolated(double[] values, double scaledIndex) {
        if (values == null || values.length == 0) {
            return 0.0D;
        }
        int maxIndex = values.length - 1;
        int index0 = Mth.clamp((int) Math.floor(scaledIndex), 0, maxIndex);
        int index1 = Math.min(index0 + 1, maxIndex);
        double t = Mth.clamp(scaledIndex - (double) index0, 0.0D, 1.0D);
        return Mth.lerp(t, values[index0], values[index1]);
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

        // XZ bounds of the centers of each block of SEARCH_BLOCK_SIZE consecutive samples, of each super block of
        // SEARCH_SUPER_BLOCK_BLOCKS blocks, and of all samples.
        private final int blockCount;
        private final double[] blockMinX;
        private final double[] blockMaxX;
        private final double[] blockMinZ;
        private final double[] blockMaxZ;
        private final int superBlockCount;
        private final double[] superMinX;
        private final double[] superMaxX;
        private final double[] superMinZ;
        private final double[] superMaxZ;
        private final double allMinX;
        private final double allMaxX;
        private final double allMinZ;
        private final double allMaxZ;
        // Along-axis bounds per block: along_i = (P - C_i) . T_i = (P - C_ref) . T_ref + (P - C_ref) . (T_i - T_ref)
        // - (C_i - C_ref) . T_i, with C_ref/T_ref the block's first sample, |T_i - T_ref| <= blockTangentDeviation
        // and (C_i - C_ref) . T_i within [blockOffsetMin, blockOffsetMax].
        private final double[] blockTangentDeviation;
        private final double[] blockOffsetMin;
        private final double[] blockOffsetMax;
        // Bounds of every surfaceY findSurfacePoint can return (baseY + along * alongSlope + drop * (1 - lateral)).
        private double surfaceYLowerBound = Double.NaN;
        private double surfaceYUpperBound = Double.NaN;
        // Search parameters from the ramp's geometry fields (any change of them replaces this cache).
        private double width;
        private double drop;
        private int sideSign;
        private boolean twoSided;
        private boolean linkedStart;
        private boolean linkedEnd;
        private int seamExtraSamples;
        private double halfSegmentLength;
        private int halfWindow;
        private int expandedHalfWindow;
        // Largest along offset any sample can accept (seam boost included).
        private double alongLimit;
        // An accepted sample has |along| <= alongLimit and |lateral| within the width (+ the one-sided margins);
        // along and lateral are the components of (query - center[i]) in an orthonormal frame, so it lies within
        // this radius of center[i] (the 0.01 padding absorbs rounding).
        private double acceptRadiusSquared;
        // findSurfacePoint results for recent queries against this cache (direct-mapped; entries are immutable).
        private final SurfaceMemoEntry[] memo = new SurfaceMemoEntry[SURFACE_MEMO_SIZE];

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
            int sampleCount = segmentCount + 1;
            this.blockCount = (sampleCount + SEARCH_BLOCK_SIZE - 1) / SEARCH_BLOCK_SIZE;
            this.blockMinX = new double[this.blockCount];
            this.blockMaxX = new double[this.blockCount];
            this.blockMinZ = new double[this.blockCount];
            this.blockMaxZ = new double[this.blockCount];
            for (int block = 0; block < this.blockCount; block++) {
                double minX = Double.POSITIVE_INFINITY;
                double maxX = Double.NEGATIVE_INFINITY;
                double minZ = Double.POSITIVE_INFINITY;
                double maxZ = Double.NEGATIVE_INFINITY;
                int end = Math.min(sampleCount, (block + 1) * SEARCH_BLOCK_SIZE);
                for (int i = block * SEARCH_BLOCK_SIZE; i < end; i++) {
                    minX = Math.min(minX, centerX[i]);
                    maxX = Math.max(maxX, centerX[i]);
                    minZ = Math.min(minZ, centerZ[i]);
                    maxZ = Math.max(maxZ, centerZ[i]);
                }
                this.blockMinX[block] = minX;
                this.blockMaxX[block] = maxX;
                this.blockMinZ[block] = minZ;
                this.blockMaxZ[block] = maxZ;
            }
            this.superBlockCount = (this.blockCount + SEARCH_SUPER_BLOCK_BLOCKS - 1) / SEARCH_SUPER_BLOCK_BLOCKS;
            this.superMinX = new double[this.superBlockCount];
            this.superMaxX = new double[this.superBlockCount];
            this.superMinZ = new double[this.superBlockCount];
            this.superMaxZ = new double[this.superBlockCount];
            double allMinX = Double.POSITIVE_INFINITY;
            double allMaxX = Double.NEGATIVE_INFINITY;
            double allMinZ = Double.POSITIVE_INFINITY;
            double allMaxZ = Double.NEGATIVE_INFINITY;
            for (int superBlock = 0; superBlock < this.superBlockCount; superBlock++) {
                double minX = Double.POSITIVE_INFINITY;
                double maxX = Double.NEGATIVE_INFINITY;
                double minZ = Double.POSITIVE_INFINITY;
                double maxZ = Double.NEGATIVE_INFINITY;
                int end = Math.min(this.blockCount, (superBlock + 1) * SEARCH_SUPER_BLOCK_BLOCKS);
                for (int block = superBlock * SEARCH_SUPER_BLOCK_BLOCKS; block < end; block++) {
                    minX = Math.min(minX, this.blockMinX[block]);
                    maxX = Math.max(maxX, this.blockMaxX[block]);
                    minZ = Math.min(minZ, this.blockMinZ[block]);
                    maxZ = Math.max(maxZ, this.blockMaxZ[block]);
                }
                this.superMinX[superBlock] = minX;
                this.superMaxX[superBlock] = maxX;
                this.superMinZ[superBlock] = minZ;
                this.superMaxZ[superBlock] = maxZ;
                allMinX = Math.min(allMinX, minX);
                allMaxX = Math.max(allMaxX, maxX);
                allMinZ = Math.min(allMinZ, minZ);
                allMaxZ = Math.max(allMaxZ, maxZ);
            }
            this.allMinX = allMinX;
            this.allMaxX = allMaxX;
            this.allMinZ = allMinZ;
            this.allMaxZ = allMaxZ;
            this.blockTangentDeviation = new double[this.blockCount];
            this.blockOffsetMin = new double[this.blockCount];
            this.blockOffsetMax = new double[this.blockCount];
            for (int block = 0; block < this.blockCount; block++) {
                int first = block * SEARCH_BLOCK_SIZE;
                int end = Math.min(sampleCount, first + SEARCH_BLOCK_SIZE);
                double deviation = 0.0D;
                double offsetMin = Double.POSITIVE_INFINITY;
                double offsetMax = Double.NEGATIVE_INFINITY;
                for (int i = first; i < end; i++) {
                    double dtx = tangentX[i] - tangentX[first];
                    double dtz = tangentZ[i] - tangentZ[first];
                    deviation = Math.max(deviation, Math.sqrt(dtx * dtx + dtz * dtz));
                    double offset = (centerX[i] - centerX[first]) * tangentX[i] + (centerZ[i] - centerZ[first]) * tangentZ[i];
                    offsetMin = Math.min(offsetMin, offset);
                    offsetMax = Math.max(offsetMax, offset);
                }
                this.blockTangentDeviation[block] = deviation;
                this.blockOffsetMin[block] = offsetMin;
                this.blockOffsetMax[block] = offsetMax;
            }
        }

        private void initSearchParameters(double width, double drop, int sideSign, boolean twoSided, boolean linkedStart, boolean linkedEnd) {
            this.width = width;
            this.drop = drop;
            this.sideSign = sideSign;
            this.twoSided = twoSided;
            this.linkedStart = linkedStart;
            this.linkedEnd = linkedEnd;
            this.seamExtraSamples = (linkedStart || linkedEnd) ? 5 : 0;
            this.halfSegmentLength = Math.max(this.segmentLength, 0.06D) * 1.30D + COLLISION_PATCH_OVERLAP + 0.06D;
            this.halfWindow = Mth.clamp((int) Math.ceil(this.segmentCount * 0.10D) + 6 + this.seamExtraSamples, 10, 64);
            this.expandedHalfWindow = Mth.clamp(this.halfWindow * 2 + this.seamExtraSamples, 18, this.segmentCount + this.seamExtraSamples);
            this.alongLimit = this.halfSegmentLength + ((linkedStart || linkedEnd) ? 0.30D : 0.0D) + CONTACT_EPSILON;
            double lateralMax = twoSided ? width + CONTACT_EPSILON : width + CONTACT_EPSILON + 0.16D;
            double acceptRadius = Math.sqrt(this.alongLimit * this.alongLimit + lateralMax * lateralMax) + 0.01D;
            this.acceptRadiusSquared = acceptRadius * acceptRadius;
            this.computeSurfaceYBounds(drop, width, this.alongLimit);
        }

        // Computes the surfaceY bounds for the ramp's drop/width and the largest along offset a sample can accept.
        private void computeSurfaceYBounds(double drop, double width, double alongMax) {
            double minBaseY = Double.POSITIVE_INFINITY;
            double maxBaseY = Double.NEGATIVE_INFINITY;
            double maxSlope = 0.0D;
            for (int i = 0; i <= this.segmentCount; i++) {
                minBaseY = Math.min(minBaseY, this.baseY[i]);
                maxBaseY = Math.max(maxBaseY, this.baseY[i]);
                maxSlope = Math.max(maxSlope, Math.abs(this.alongSlope[i]));
            }
            // |along| <= alongMax; the lateral fraction is within [0, 1 + epsilon / width] (two-sided ramps can sit
            // just past the edge), so the drop term is within +-|drop| * (1 + epsilon / width). 1e-6 absorbs rounding.
            double dropTerm = Math.abs(drop) * (1.0D + CONTACT_EPSILON / width);
            double alongTerm = alongMax * maxSlope;
            this.surfaceYLowerBound = minBaseY - alongTerm - dropTerm - 1.0E-6D;
            this.surfaceYUpperBound = maxBaseY + alongTerm + dropTerm + 1.0E-6D;
        }

        // Lower bound of the squared distance from (x, z) to every sample.
        private double lowerBoundDistanceSquared(double x, double z) {
            return boundsDistanceSquared(this.allMinX, this.allMaxX, this.allMinZ, this.allMaxZ, x, z);
        }

        // True when every sample of the block has |along| > alongLimit (along = (x - centerX[i]) * tangentX[i] +
        // (z - centerZ[i]) * tangentZ[i]). The bound is padded far beyond rounding error; NaN never prunes.

        private static int memoSlot(long xBits, long zBits) {
            long hash = xBits * 0x9E3779B97F4A7C15L + zBits * 0xC2B2AE3D27D4EB4FL;
            return (int) (hash >>> 59) & (SURFACE_MEMO_SIZE - 1);
        }

        // Lower bound of (x - centerX[i])^2 + (z - centerZ[i])^2 over the block's samples.
        private double blockLowerBoundDistanceSquared(int block, double x, double z) {
            return boundsDistanceSquared(this.blockMinX[block], this.blockMaxX[block], this.blockMinZ[block], this.blockMaxZ[block], x, z);
        }

        // Squared distance from (x, z) to the box; a lower bound of the squared distance to any point inside it. IEEE
        // rounding is monotonic, so this also holds for the computed distances (NaN yields 0 = no pruning).
        private static double boundsDistanceSquared(double minX, double maxX, double minZ, double maxZ, double x, double z) {
            double dx = minX - x;
            if (!(dx > 0.0D)) {
                dx = x - maxX;
                if (!(dx > 0.0D)) {
                    dx = 0.0D;
                }
            }
            double dz = minZ - z;
            if (!(dz > 0.0D)) {
                dz = z - maxZ;
                if (!(dz > 0.0D)) {
                    dz = 0.0D;
                }
            }
            return dx * dx + dz * dz;
        }

        // Index of the sample closest to (x, z); on equal distances the lowest index, i.e. exactly what a linear
        // "if (distanceSq < best)" scan over 0..segmentCount returns. Starts in the most promising block, then visits
        // only blocks whose lower bound does not exceed the best distance found so far.
        private int closestIndex(double x, double z) {
            // Seed with the most promising block of the most promising super block.
            int seedSuper = 0;
            double seedSuperBound = Double.POSITIVE_INFINITY;
            for (int superBlock = 0; superBlock < this.superBlockCount; superBlock++) {
                double bound = boundsDistanceSquared(this.superMinX[superBlock], this.superMaxX[superBlock],
                        this.superMinZ[superBlock], this.superMaxZ[superBlock], x, z);
                if (bound < seedSuperBound) {
                    seedSuperBound = bound;
                    seedSuper = superBlock;
                }
            }
            int seedBlock = seedSuper * SEARCH_SUPER_BLOCK_BLOCKS;
            double seedBound = Double.POSITIVE_INFINITY;
            int seedEnd = Math.min(this.blockCount, (seedSuper + 1) * SEARCH_SUPER_BLOCK_BLOCKS);
            for (int block = seedSuper * SEARCH_SUPER_BLOCK_BLOCKS; block < seedEnd; block++) {
                double bound = this.blockLowerBoundDistanceSquared(block, x, z);
                if (bound < seedBound) {
                    seedBound = bound;
                    seedBlock = block;
                }
            }
            int sampleCount = this.segmentCount + 1;
            int bestIndex = 0;
            double bestDistanceSq = Double.MAX_VALUE;
            for (int pass = -1; pass < this.superBlockCount; pass++) {
                int firstBlock;
                int endBlock;
                if (pass < 0) {
                    firstBlock = seedBlock;
                    endBlock = seedBlock + 1;
                } else {
                    if (boundsDistanceSquared(this.superMinX[pass], this.superMaxX[pass],
                            this.superMinZ[pass], this.superMaxZ[pass], x, z) > bestDistanceSq) {
                        continue;
                    }
                    firstBlock = pass * SEARCH_SUPER_BLOCK_BLOCKS;
                    endBlock = Math.min(this.blockCount, firstBlock + SEARCH_SUPER_BLOCK_BLOCKS);
                }
                for (int block = firstBlock; block < endBlock; block++) {
                    if (pass >= 0 && (block == seedBlock || this.blockLowerBoundDistanceSquared(block, x, z) > bestDistanceSq)) {
                        continue;
                    }
                    int end = Math.min(sampleCount, (block + 1) * SEARCH_BLOCK_SIZE);
                    for (int i = block * SEARCH_BLOCK_SIZE; i < end; i++) {
                        double dx = x - this.centerX[i];
                        double dz = z - this.centerZ[i];
                        double distanceSq = dx * dx + dz * dz;
                        if (distanceSq < bestDistanceSq || (distanceSq == bestDistanceSq && i < bestIndex)) {
                            bestDistanceSq = distanceSq;
                            bestIndex = i;
                        }
                    }
                }
            }
            return bestIndex;
        }
    }

    private record SurfaceMemoEntry(long xBits, long zBits, @Nullable SurfacePoint point) {
    }

    private static final class CenterlineSampleCache {
        private final int samples;
        private final double[] centerX;
        private final double[] centerZ;
        private final double minX;
        private final double maxX;
        private final double minZ;
        private final double maxZ;

        private CenterlineSampleCache(int samples, double[] centerX, double[] centerZ) {
            this.samples = samples;
            this.centerX = centerX;
            this.centerZ = centerZ;
            double minX = Double.POSITIVE_INFINITY;
            double maxX = Double.NEGATIVE_INFINITY;
            double minZ = Double.POSITIVE_INFINITY;
            double maxZ = Double.NEGATIVE_INFINITY;
            for (int i = 0; i < centerX.length; i++) {
                minX = Math.min(minX, centerX[i]);
                maxX = Math.max(maxX, centerX[i]);
                minZ = Math.min(minZ, centerZ[i]);
                maxZ = Math.max(maxZ, centerZ[i]);
            }
            this.minX = minX;
            this.maxX = maxX;
            this.minZ = minZ;
            this.maxZ = maxZ;
        }

        // Lower bound of the squared distance from (x, z) to every sample (see blockLowerBoundDistanceSquared).
        private double lowerBoundDistanceSquared(double x, double z) {
            double dx = this.minX - x;
            if (!(dx > 0.0D)) {
                dx = x - this.maxX;
                if (!(dx > 0.0D)) {
                    dx = 0.0D;
                }
            }
            double dz = this.minZ - z;
            if (!(dz > 0.0D)) {
                dz = z - this.maxZ;
                if (!(dz > 0.0D)) {
                    dz = 0.0D;
                }
            }
            return dx * dx + dz * dz;
        }
    }

    /**
     * The active ramps of one level. {@code ramps} is the membership authority and defines the iteration order of
     * lookups (unchanged); the snapshot adds a spatial index over the ramps' bounding boxes and is rebuilt after any
     * membership or bounding-box change.
     */
    private static final class RampRegistry {
        private final Set<SurfRampEntity> ramps = Collections.newSetFromMap(new ConcurrentHashMap<>());
        private final AtomicInteger modCount = new AtomicInteger();
        @Nullable
        private volatile RampIndexSnapshot snapshot;

        private void markDirty() {
            this.modCount.incrementAndGet();
        }

        private void remove(SurfRampEntity ramp) {
            if (this.ramps.remove(ramp)) {
                this.markDirty();
            }
        }

        private RampIndexSnapshot snapshot() {
            RampIndexSnapshot current = this.snapshot;
            if (current != null && current.modCount == this.modCount.get()) {
                return current;
            }
            synchronized (this) {
                current = this.snapshot;
                if (current != null && current.modCount == this.modCount.get()) {
                    return current;
                }
                // Drop dead ramps first, as the former full scans did on every lookup (the index only visits
                // candidates, so unloaded ramps far from any query would otherwise stay referenced).
                for (SurfRampEntity ramp : this.ramps) {
                    if (ramp == null || !ramp.isAlive() || ramp.isRemoved()) {
                        this.ramps.remove(ramp);
                    }
                }
                int mod = this.modCount.get();
                current = RampIndexSnapshot.build(this.ramps, mod);
                this.snapshot = current;
                return current;
            }
        }
    }

    private static final class RampIndexSnapshot {
        // A query box spanning more cells than this just scans every ramp (in order).
        private static final int MAX_QUERY_CELLS = 64;
        // A ramp box spanning more cells than this (or not finite) is a candidate of every query.
        private static final int MAX_RAMP_CELLS = 1024;
        private static final int[] NO_RAMPS = new int[0];

        private final int modCount;
        private final SurfRampEntity[] ordered;
        private final Long2ObjectOpenHashMap<int[]> cells;
        private final int[] everywhere;

        private RampIndexSnapshot(int modCount, SurfRampEntity[] ordered, Long2ObjectOpenHashMap<int[]> cells, int[] everywhere) {
            this.modCount = modCount;
            this.ordered = ordered;
            this.cells = cells;
            this.everywhere = everywhere;
        }

        private static RampIndexSnapshot build(Set<SurfRampEntity> ramps, int modCount) {
            List<SurfRampEntity> ordered = new ArrayList<>(ramps.size());
            for (SurfRampEntity ramp : ramps) {
                ordered.add(ramp);
            }
            Long2ObjectOpenHashMap<IntArrayList> lists = new Long2ObjectOpenHashMap<>();
            IntArrayList everywhere = new IntArrayList();
            for (int rank = 0; rank < ordered.size(); rank++) {
                SurfRampEntity ramp = ordered.get(rank);
                AABB box = ramp == null ? null : ramp.getBoundingBox();
                if (box == null || !Double.isFinite(box.minX) || !Double.isFinite(box.maxX)
                        || !Double.isFinite(box.minZ) || !Double.isFinite(box.maxZ)) {
                    everywhere.add(rank);
                    continue;
                }
                int minCellX = Mth.floor(box.minX) >> 4;
                int maxCellX = Mth.floor(box.maxX) >> 4;
                int minCellZ = Mth.floor(box.minZ) >> 4;
                int maxCellZ = Mth.floor(box.maxZ) >> 4;
                long cellCount = ((long) maxCellX - minCellX + 1L) * ((long) maxCellZ - minCellZ + 1L);
                if (cellCount <= 0L || cellCount > MAX_RAMP_CELLS) {
                    everywhere.add(rank);
                    continue;
                }
                for (int cellX = minCellX; cellX <= maxCellX; cellX++) {
                    for (int cellZ = minCellZ; cellZ <= maxCellZ; cellZ++) {
                        lists.computeIfAbsent(cellKey(cellX, cellZ), key -> new IntArrayList()).add(rank);
                    }
                }
            }
            Long2ObjectOpenHashMap<int[]> cells = new Long2ObjectOpenHashMap<>(lists.size());
            for (Long2ObjectOpenHashMap.Entry<IntArrayList> entry : lists.long2ObjectEntrySet()) {
                cells.put(entry.getLongKey(), entry.getValue().toIntArray());
            }
            return new RampIndexSnapshot(modCount, ordered.toArray(new SurfRampEntity[0]), cells, everywhere.toIntArray());
        }

        private static long cellKey(int cellX, int cellZ) {
            return ((long) cellX << 32) | (cellZ & 0xFFFFFFFFL);
        }

        /**
         * Ascending ranks of the ramps whose indexed box may intersect {@code box} (a superset of the ramps that
         * do), or {@code null} to visit every ramp. Two AABBs that intersect overlap in X and Z, and floor/shift are
         * monotonic, so they share at least one cell.
         */
        @Nullable
        private int[] candidates(AABB box) {
            if (!Double.isFinite(box.minX) || !Double.isFinite(box.maxX)
                    || !Double.isFinite(box.minZ) || !Double.isFinite(box.maxZ)) {
                return null;
            }
            int minCellX = Mth.floor(box.minX) >> 4;
            int maxCellX = Mth.floor(box.maxX) >> 4;
            int minCellZ = Mth.floor(box.minZ) >> 4;
            int maxCellZ = Mth.floor(box.maxZ) >> 4;
            long cellCount = ((long) maxCellX - minCellX + 1L) * ((long) maxCellZ - minCellZ + 1L);
            if (cellCount <= 0L || cellCount > MAX_QUERY_CELLS) {
                return null;
            }
            int[] single = null;
            IntArrayList merged = null;
            for (int cellX = minCellX; cellX <= maxCellX; cellX++) {
                for (int cellZ = minCellZ; cellZ <= maxCellZ; cellZ++) {
                    int[] list = this.cells.get(cellKey(cellX, cellZ));
                    if (list == null) {
                        continue;
                    }
                    if (single == null && merged == null) {
                        single = list;
                        continue;
                    }
                    if (merged == null) {
                        merged = new IntArrayList(single.length + list.length + this.everywhere.length);
                        merged.addElements(0, single);
                    }
                    merged.addElements(merged.size(), list);
                }
            }
            if (merged == null && this.everywhere.length == 0) {
                return single == null ? NO_RAMPS : single;
            }
            if (merged == null) {
                merged = new IntArrayList((single == null ? 0 : single.length) + this.everywhere.length);
                if (single != null) {
                    merged.addElements(0, single);
                }
            }
            merged.addElements(merged.size(), this.everywhere);
            int[] ranks = merged.toIntArray();
            java.util.Arrays.sort(ranks);
            int unique = 0;
            for (int k = 0; k < ranks.length; k++) {
                if (k == 0 || ranks[k] != ranks[k - 1]) {
                    ranks[unique++] = ranks[k];
                }
            }
            return unique == ranks.length ? ranks : java.util.Arrays.copyOf(ranks, unique);
        }
    }

    private static final class CollisionPatchCache {
        private static final CollisionPatchCache[] EMPTY_ARRAY = new CollisionPatchCache[0];
        private final AABB box;
        private final VoxelShape shape;

        private CollisionPatchCache(AABB box, VoxelShape shape) {
            this.box = box;
            this.shape = shape;
        }
    }

    private static final class CollisionSegmentCache {
        private final double sampleT0;
        private final double sampleT1;
        private final AABB bounds;
        @Nullable
        private volatile CollisionPatchCache[] patches;

        private CollisionSegmentCache(double sampleT0, double sampleT1, AABB bounds) {
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

    public record SurfacePoint(double surfaceY, Vec3 normal, double t) {
    }
}
