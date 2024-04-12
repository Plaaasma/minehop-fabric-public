package net.nerdorg.minehop.block;

import com.google.common.collect.ImmutableMap;
import com.mojang.serialization.MapCodec;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.client.util.ParticleUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleType;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.nerdorg.minehop.block.entity.BoostBlockEntity;
import net.nerdorg.minehop.block.entity.ModBlockEntities;
import net.nerdorg.minehop.networking.PacketHandler;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;

public class BoostBlock extends BlockWithEntity implements BlockEntityProvider {
    private static final VoxelShape SHAPE = VoxelShapes.cuboid(0f, 0f, 0f, 1f, 0.0001f, 1f);

    public BoostBlock(Settings settings) {
        super(settings);
    }

    @Override
    public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
        ParticleUtil.spawnParticle(world, pos, Random.create(), ParticleTypes.CLOUD);
        super.randomDisplayTick(state, world, pos, random);
    }

    @Override
    public void onEntityCollision(BlockState state, World world, BlockPos pos, Entity entity) {
        if (world instanceof ServerWorld serverWorld) {
            if (serverWorld.getTime() % 4 == 0) {
                BoostBlockEntity boostBlockEntity = (BoostBlockEntity) serverWorld.getBlockEntity(pos);
                if (boostBlockEntity != null) {
                    for (ServerPlayerEntity playerEntity : serverWorld.getServer().getPlayerManager().getPlayerList()) {
                        PacketHandler.sendPower(playerEntity, boostBlockEntity.getXPower(), boostBlockEntity.getYPower(), boostBlockEntity.getZPower(), boostBlockEntity.getPos());
                    }
                }
            }
        }
        super.onEntityCollision(state, world, pos, entity);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPE;
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPE;
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new BoostBlockEntity(ModBlockEntities.BOOST_BE, pos, state);
    }
}
