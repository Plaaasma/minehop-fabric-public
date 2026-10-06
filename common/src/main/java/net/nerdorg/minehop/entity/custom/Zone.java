package net.nerdorg.minehop.entity.custom;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.nerdorg.minehop.item.ModItems;
import net.nerdorg.minehop.util.ZonePlacementManager;

public class Zone extends Mob {
    private String paired_map = "";

    public Zone(EntityType<? extends Mob> entityType, Level world) {
        super(entityType, world);
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean requiresCustomPersistence() {
        return true;
    }

    @Override
    public boolean isNoGravity() {
        return true;
    }

    @Override
    public boolean isPersistenceRequired() {
        return true;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canCollideWith(Entity other) {
        return false;
    }

    @Override
    public boolean hurtServer(ServerLevel world, DamageSource source, float amount) {
        if (source.is(DamageTypes.GENERIC_KILL)) {
            return super.hurtServer(world, source, amount);
        }
        else {
            return false;
        }
    }

    @Override
    public void addAdditionalSaveData(ValueOutput nbt) {
        super.addAdditionalSaveData(nbt);
        nbt.putString("map", paired_map);
    }

    @Override
    public void readAdditionalSaveData(ValueInput nbt) {
        super.readAdditionalSaveData(nbt);
        paired_map = nbt.getStringOr("map", "");
    }

    public String getPairedMap() {
        return paired_map;
    }

    public void setPairedMap(String paired_map) {
        this.paired_map = paired_map;
    }

    protected void updateInteractionBounds(BlockPos corner1, BlockPos corner2) {
        if (corner1 == null || corner2 == null) {
            return;
        }

        double minX = Math.min(corner1.getX(), corner2.getX());
        double minY = Math.min(corner1.getY(), corner2.getY());
        double minZ = Math.min(corner1.getZ(), corner2.getZ());
        double maxX = Math.max(corner1.getX(), corner2.getX());
        double maxY = Math.max(corner1.getY(), corner2.getY());
        double maxZ = Math.max(corner1.getZ(), corner2.getZ());

        if (maxX <= minX) {
            maxX = minX + 1.0D;
        }
        if (maxY <= minY) {
            maxY = minY + 1.0D;
        }
        if (maxZ <= minZ) {
            maxZ = minZ + 1.0D;
        }

        this.setBoundingBox(new AABB(minX, minY, minZ, maxX, maxY, maxZ));
    }

    protected Vec3 getBoundsCenter(BlockPos corner1, BlockPos corner2) {
        if (corner1 == null || corner2 == null) {
            return this.position();
        }
        double minX = Math.min(corner1.getX(), corner2.getX());
        double minY = Math.min(corner1.getY(), corner2.getY());
        double minZ = Math.min(corner1.getZ(), corner2.getZ());
        double maxX = Math.max(corner1.getX(), corner2.getX());
        double maxY = Math.max(corner1.getY(), corner2.getY());
        double maxZ = Math.max(corner1.getZ(), corner2.getZ());
        return new Vec3(
                (minX + maxX) * 0.5D,
                (minY + maxY) * 0.5D,
                (minZ + maxZ) * 0.5D
        );
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    protected boolean updateInWaterStateAndDoFluidPushing() {
        // A zone's bounding box is the whole zone (updateInteractionBounds), and vanilla's baseTick sweeps every block
        // inside it for fluids each tick: a big fail zone (a whole room floor) costs millions of block lookups per tick.
        // Zones never interact with fluids (same as SurfRampEntity).
        return false;
    }

    @Override
    protected void pushEntities() {
        // Zones are intangible triggers: skip vanilla's per-tick entity query over the (zone-sized) bounding box.
    }

    @Override
    protected void doPush(Entity entity) {
    }

    @Override
    public boolean isFree(double offsetX, double offsetY, double offsetZ) {
        return true;
    }

    @Override
    public void playerTouch(Player player) { }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (this.level().isClientSide()) {
            return player.getItemInHand(hand).is(ModItems.BOUNDS_STICK.get()) ? InteractionResult.SUCCESS : InteractionResult.PASS;
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }
        if (!player.getItemInHand(hand).is(ModItems.BOUNDS_STICK.get())) {
            return InteractionResult.PASS;
        }
        ZonePlacementManager.openEditor(serverPlayer, this);
        return InteractionResult.SUCCESS;
    }
}
