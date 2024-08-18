package net.nerdorg.minehop.item.custom;

import com.mojang.logging.LogUtils;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroupEntries;
import net.minecraft.block.*;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageSources;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.predicate.entity.DamageSourcePredicate;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.damage.ModDamageSources;
import net.nerdorg.minehop.damage.ModDamageTypes;
import net.nerdorg.minehop.data.DataManager;
import net.nerdorg.minehop.util.Logger;
import net.nerdorg.minehop.util.ZoneUtil;
import org.apache.logging.log4j.core.jmx.Server;

import java.util.*;

public class InstagibItem extends Item {
    private static final HashMap<String, Integer> gibDelayList = new HashMap<>();
    private final Random random = new Random();

    public InstagibItem(Settings settings) {
        super(settings);
    }

    private EntityHitResult raycastEntities(ServerPlayerEntity player, Vec3d startPos, Vec3d endPos, double maxDistance) {
        ServerWorld world = player.getServerWorld();
        EntityHitResult nearestHitResult = null;
        double nearestDistanceSquared = maxDistance * maxDistance;

        // Ray trace for blocks first to see if there's a block obstructing the view
        BlockHitResult blockHitResult = world.raycast(new RaycastContext(startPos, endPos, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));

        // If a block was hit, adjust endPos to the block hit position to ensure entities behind blocks are not considered
        if (blockHitResult.getType() != HitResult.Type.MISS && !blockInWhitelist(world.getBlockState(blockHitResult.getBlockPos()))) {
            endPos = blockHitResult.getPos();
            // Recalculate max distance based on new endPos
            nearestDistanceSquared = startPos.squaredDistanceTo(endPos);
        }

        for (Entity entity : world.iterateEntities()) {
            // Ensure not self-targeting and other appropriate filters
            if (entity != player && entity.getBoundingBox() != null) {
                Optional<Vec3d> optionalIntersection = entity.getBoundingBox().raycast(startPos, endPos);
                if (optionalIntersection.isPresent()) {
                    double distanceSquared = startPos.squaredDistanceTo(optionalIntersection.get());
                    if (distanceSquared < nearestDistanceSquared) {
                        // Additional check: ensure no blocks between startPos and the point of intersection
                        BlockHitResult intermediateBlockHitResult = world.raycast(new RaycastContext(startPos, optionalIntersection.get(), RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
                        if (intermediateBlockHitResult.getType() == HitResult.Type.MISS || blockInWhitelist(world.getBlockState(intermediateBlockHitResult.getBlockPos()))) {
                            nearestHitResult = new EntityHitResult(entity, optionalIntersection.get());
                            nearestDistanceSquared = distanceSquared;
                        }
                    }
                }
            }
        }

        return nearestHitResult;
    }

    private boolean blockInWhitelist(BlockState blockState) {
        Block block = blockState.getBlock();
        return block instanceof TransparentBlock
                || block instanceof PaneBlock
                || block instanceof PlantBlock
                || block instanceof DoorBlock
                || block instanceof FenceBlock
                || block instanceof WallBlock;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        if (world instanceof ServerWorld serverWorld) {
            ServerPlayerEntity serverPlayerEntity = (ServerPlayerEntity) user;
            if (!gibDelayList.containsKey(user.getNameForScoreboard()) || serverWorld.getServer().getTicks() > gibDelayList.get(user.getNameForScoreboard()) + 15) {
                Vec3d startPos = serverPlayerEntity.getCameraPosVec(1.0F);

                // Direction - based on where the player is looking
                Vec3d lookVec = serverPlayerEntity.getRotationVec(1.0F);

                // Calculate the end position 64 blocks away in the look direction
                Vec3d endPos = startPos.add(lookVec.x * 64, lookVec.y * 64, lookVec.z * 64);
                serverPlayerEntity.playSoundToPlayer(SoundEvents.ENTITY_WARDEN_ATTACK_IMPACT, SoundCategory.PLAYERS, 1f, 1f);
                serverWorld.playSound(user, user.getBlockPos(), SoundEvents.ENTITY_WARDEN_ATTACK_IMPACT, SoundCategory.PLAYERS, 1f, 1f);

                EntityHitResult entityHitResult = raycastEntities(serverPlayerEntity, startPos, endPos, 64);
                if (entityHitResult != null) {
                    endPos = entityHitResult.getPos();
                    Entity hitEntity = entityHitResult.getEntity();
                    serverPlayerEntity.playSoundToPlayer(SoundEvents.ENTITY_PLAYER_HURT, SoundCategory.PLAYERS, 1f, 1f);
                    handleInstaGibHit(serverPlayerEntity, hitEntity);
                    handleGibParticles(serverWorld, startPos, endPos);
                }
                else {
                    handleGibParticles(serverWorld, startPos, endPos);
                }

                gibDelayList.put(user.getNameForScoreboard(), serverWorld.getServer().getTicks());
            }
            return TypedActionResult.consume(user.getStackInHand(hand));
        }
        return TypedActionResult.consume(user.getStackInHand(hand));
    }

    private void handleGibParticles(ServerWorld world, Vec3d startPos, Vec3d endPos) {
        Vec3d direction = endPos.subtract(startPos);
        double distance = direction.length();
        Vec3d step = direction.normalize().multiply(0.5); // Normalize then multiply by 0.5 to get the step vector

        // Calculate the number of steps to take
        int steps = (int) (distance / 0.5);

        for (int i = 0; i <= steps; i++) {
            Vec3d currentPos = startPos.add(step.multiply(i));

            // If the remaining distance is less than the step length, adjust the final step to exactly reach endPos
            if (i == steps) {
                currentPos = endPos;
                // Use endPos here
            }

            world.spawnParticles(ParticleTypes.CRIT, currentPos.x, currentPos.y, currentPos.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    private void handleInstaGibHit(ServerPlayerEntity attacker, Entity target) {
        String mapName = ZoneUtil.getCurrentMapName(target);
        if (mapName != null) {
            DataManager.MapData mapData = DataManager.getMap(mapName);
            if (mapData != null) {
                ServerWorld foundWorld = null;
                for (ServerWorld serverWorld : attacker.getServer().getWorlds()) {
                    if (serverWorld.getRegistryKey().toString().equals(mapData.worldKey)) {
                        foundWorld = serverWorld;
                        break;
                    }
                }
                if (foundWorld != null) {
                    List<Vec3d> spawnCheck = new ArrayList<>();
                    spawnCheck.add(new Vec3d(mapData.x, mapData.y, mapData.z));
                    spawnCheck.add(new Vec3d(mapData.xrot, mapData.yrot, 0));

                    List<List<Vec3d>> checkpointPositions = new ArrayList<>();
                    if (mapData.checkpointPositions != null) {
                        checkpointPositions.addAll(mapData.checkpointPositions);
                    }
                    checkpointPositions.add(spawnCheck);

                    List<Vec3d> randomCheckpoint = checkpointPositions.get(random.nextInt(0, checkpointPositions.size()));
                    Vec3d targetPos = randomCheckpoint.get(0);
                    Vec3d rotPos = randomCheckpoint.get(1);
                    target.teleport(foundWorld, targetPos.getX(), targetPos.getY(), targetPos.getZ(), PositionFlag.VALUES, (float) rotPos.getY(), (float) rotPos.getX());
                    if (target instanceof ServerPlayerEntity targetPlayerEntity) {
                        Logger.logSuccess(attacker, "You shot " + targetPlayerEntity.getNameForScoreboard() + ".");
                        Logger.logFailure(targetPlayerEntity, "You were shot by " + attacker.getNameForScoreboard() + ".");
                        targetPlayerEntity.playSoundToPlayer(SoundEvents.ENTITY_ITEM_BREAK, SoundCategory.PLAYERS, 1f, 1f);
                    }
                }
            }
        }
    }
}
