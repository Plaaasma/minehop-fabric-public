package net.nerdorg.minehop.block;

import com.google.common.collect.ImmutableMap;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ParticleUtils;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.nerdorg.minehop.block.entity.BoostBlockEntity;
import net.nerdorg.minehop.block.entity.ModBlockEntities;
import net.nerdorg.minehop.networking.PacketHandler;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;

public class BoostBlock extends BaseEntityBlock implements EntityBlock {
    private static final VoxelShape SHAPE = Shapes.box(0f, 0f, 0f, 1f, 0.0001f, 1f);

    public BoostBlock(Properties settings) {
        super(settings);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return null;
    }

    @Override
    public void animateTick(BlockState state, Level world, BlockPos pos, RandomSource random) {
        ParticleUtils.spawnParticleBelow(world, pos, RandomSource.create(), ParticleTypes.CLOUD);
        super.animateTick(state, world, pos, random);
    }

    @Override
    public void entityInside(BlockState state, Level world, BlockPos pos, Entity entity) {
        if (world instanceof ServerLevel serverWorld) {
            if (serverWorld.getGameTime() % 4 == 0) {
                BoostBlockEntity boostBlockEntity = (BoostBlockEntity) serverWorld.getBlockEntity(pos);
                if (boostBlockEntity != null) {
                    for (ServerPlayer playerEntity : serverWorld.getServer().getPlayerList().getPlayers()) {
                        PacketHandler.sendPower(playerEntity, boostBlockEntity.getXPower(), boostBlockEntity.getYPower(), boostBlockEntity.getZPower(), boostBlockEntity.getBlockPos());
                    }
                }
            }
        }
        super.entityInside(state, world, pos, entity);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BoostBlockEntity(ModBlockEntities.BOOST_BE.get(), pos, state);
    }
}
