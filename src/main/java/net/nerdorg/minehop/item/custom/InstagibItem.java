package net.nerdorg.minehop.item.custom;

import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.nerdorg.minehop.data.DataManager;
import net.nerdorg.minehop.util.Logger;
import net.nerdorg.minehop.util.ZoneUtil;

import java.util.*;

public class InstagibItem extends Item {
    private static final HashMap<String, Integer> gibDelayList = new HashMap<>();
    private final Random random = new Random();

    public InstagibItem(Properties settings) {
        super(settings);
    }

    private EntityHitResult raycastEntities(ServerPlayer player, Vec3 startPos, Vec3 endPos, double maxDistance) {
        ServerLevel world = player.level();
        EntityHitResult nearestHitResult = null;
        double nearestDistanceSquared = maxDistance * maxDistance;

        // Ray trace for blocks first to see if there's a block obstructing the view
        BlockHitResult blockHitResult = world.clip(new ClipContext(startPos, endPos, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));

        // If a block was hit, adjust endPos to the block hit position to ensure entities behind blocks are not considered
        if (blockHitResult.getType() != HitResult.Type.MISS && !blockInWhitelist(world.getBlockState(blockHitResult.getBlockPos()))) {
            endPos = blockHitResult.getLocation();
            // Recalculate max distance based on new endPos
            nearestDistanceSquared = startPos.distanceToSqr(endPos);
        }

        for (Entity entity : world.getAllEntities()) {
            // Ensure not self-targeting and other appropriate filters
            if (entity != player && entity.getBoundingBox() != null) {
                Optional<Vec3> optionalIntersection = entity.getBoundingBox().clip(startPos, endPos);
                if (optionalIntersection.isPresent()) {
                    double distanceSquared = startPos.distanceToSqr(optionalIntersection.get());
                    if (distanceSquared < nearestDistanceSquared) {
                        // Additional check: ensure no blocks between startPos and the point of intersection
                        BlockHitResult intermediateBlockHitResult = world.clip(new ClipContext(startPos, optionalIntersection.get(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
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
                || block instanceof IronBarsBlock
                || block instanceof VegetationBlock
                || block instanceof DoorBlock
                || block instanceof FenceBlock
                || block instanceof WallBlock;
    }

    @Override
    public InteractionResult use(Level world, Player user, InteractionHand hand) {
        if (world instanceof ServerLevel serverWorld) {
            ServerPlayer serverPlayerEntity = (ServerPlayer) user;
            if (!gibDelayList.containsKey(user.getScoreboardName()) || serverWorld.getServer().getTickCount() > gibDelayList.get(user.getScoreboardName()) + 15) {
                Vec3 startPos = serverPlayerEntity.getEyePosition(1.0F);

                // Direction - based on where the player is looking
                Vec3 lookVec = serverPlayerEntity.getViewVector(1.0F);

                // Calculate the end position 64 blocks away in the look direction
                Vec3 endPos = startPos.add(lookVec.x * 64, lookVec.y * 64, lookVec.z * 64);
                playSoundToPlayer(serverPlayerEntity, SoundEvents.WARDEN_ATTACK_IMPACT, SoundSource.PLAYERS, 1f, 1f);
                serverWorld.playSound(user, user.blockPosition(), SoundEvents.WARDEN_ATTACK_IMPACT, SoundSource.PLAYERS, 1f, 1f);

                EntityHitResult entityHitResult = raycastEntities(serverPlayerEntity, startPos, endPos, 64);
                if (entityHitResult != null) {
                    endPos = entityHitResult.getLocation();
                    Entity hitEntity = entityHitResult.getEntity();
                    playSoundToPlayer(serverPlayerEntity, SoundEvents.PLAYER_HURT, SoundSource.PLAYERS, 1f, 1f);
                    handleInstaGibHit(serverPlayerEntity, hitEntity);
                    handleGibParticles(serverWorld, startPos, endPos);
                }
                else {
                    handleGibParticles(serverWorld, startPos, endPos);
                }

                gibDelayList.put(user.getScoreboardName(), serverWorld.getServer().getTickCount());
            }
            return InteractionResult.CONSUME;// (user.getStackInHand(hand));
        }
        return InteractionResult.CONSUME; //TypedActionResult.consume(user.getStackInHand(hand));
    }

    private void handleGibParticles(ServerLevel world, Vec3 startPos, Vec3 endPos) {
        Vec3 direction = endPos.subtract(startPos);
        double distance = direction.length();
        Vec3 step = direction.normalize().scale(0.5); // Normalize then multiply by 0.5 to get the step vector

        // Calculate the number of steps to take
        int steps = (int) (distance / 0.5);

        for (int i = 0; i <= steps; i++) {
            Vec3 currentPos = startPos.add(step.scale(i));

            // If the remaining distance is less than the step length, adjust the final step to exactly reach endPos
            if (i == steps) {
                currentPos = endPos;
                // Use endPos here
            }

            world.sendParticles(ParticleTypes.CRIT, currentPos.x, currentPos.y, currentPos.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    private void handleInstaGibHit(ServerPlayer attacker, Entity target) {
        String mapName = ZoneUtil.getCurrentMapName(target);
        if (mapName != null) {
            DataManager.MapData mapData = DataManager.getMap(mapName);
            if (mapData != null) {
                ServerLevel foundWorld = null;
                for (ServerLevel serverWorld : attacker.level().getServer().getAllLevels()) {
                    if (serverWorld.dimension().toString().equals(mapData.worldKey)) {
                        foundWorld = serverWorld;
                        break;
                    }
                }
                if (foundWorld != null) {
                    List<Vec3> spawnCheck = new ArrayList<>();
                    spawnCheck.add(new Vec3(mapData.x, mapData.y, mapData.z));
                    spawnCheck.add(new Vec3(mapData.xrot, mapData.yrot, 0));

                    List<List<Vec3>> checkpointPositions = new ArrayList<>();
                    if (mapData.checkpointPositions != null) {
                        checkpointPositions.addAll(mapData.checkpointPositions);
                    }
                    checkpointPositions.add(spawnCheck);

                    List<Vec3> randomCheckpoint = checkpointPositions.get(random.nextInt(0, checkpointPositions.size()));
                    Vec3 targetPos = randomCheckpoint.get(0);
                    Vec3 rotPos = randomCheckpoint.get(1);
                    target.teleport(ZoneUtil.makeTeleportTarget(foundWorld, targetPos, (float) rotPos.y(), (float) rotPos.x()));
                    if (target instanceof ServerPlayer targetPlayerEntity) {
                        Logger.logSuccess(attacker, "You shot " + targetPlayerEntity.getScoreboardName() + ".");
                        Logger.logFailure(targetPlayerEntity, "You were shot by " + attacker.getScoreboardName() + ".");
                        playSoundToPlayer(targetPlayerEntity, SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 1f, 1f);
                    }
                }
            }
        }
    }

    // ServerPlayerEntity#playSoundToPlayer was removed after 1.21.4; same packet it used to send.
    private static void playSoundToPlayer(ServerPlayer player, SoundEvent sound, SoundSource category, float volume, float pitch) {
        playSoundToPlayer(player, BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound), category, volume, pitch);
    }

    private static void playSoundToPlayer(ServerPlayer player, Holder<SoundEvent> sound, SoundSource category, float volume, float pitch) {
        player.connection.send(new ClientboundSoundPacket(sound, category, player.getX(), player.getY(), player.getZ(), volume, pitch, player.getRandom().nextLong()));
    }
}
