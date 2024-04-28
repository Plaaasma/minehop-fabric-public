package net.nerdorg.minehop.block;

import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.util.ParticleUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.*;
import net.minecraft.util.ActionResult;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.nerdorg.minehop.block.entity.BoostBlockEntity;
import net.nerdorg.minehop.block.entity.ModBlockEntities;
import net.nerdorg.minehop.networking.PacketHandler;
import org.jetbrains.annotations.Nullable;

public class RampBlock extends HorizontalFacingBlock {
    public static final IntProperty RAMP_HEIGHT;

    public RampBlock(Settings settings) {
        super(settings);
        this.setDefaultState((this.stateManager.getDefaultState()).with(FACING, Direction.NORTH).with(RAMP_HEIGHT, 0));
    }

    @Nullable
    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        return (this.stateManager.getDefaultState()).with(FACING, ctx.getHorizontalPlayerFacing().getOpposite()).with(RAMP_HEIGHT, 0);
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(new Property[]{FACING, RAMP_HEIGHT});
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (!world.isClient) {  // Ensure logic only executes on the server side
            if (player.isCreative()) {
                int currentHeight = state.get(RAMP_HEIGHT); // Get current height from block state
                if (player.isSneaky()) {
                    currentHeight--; // Decrease height if sneaking.
                }
                else {
                    currentHeight++; // Increase height if not sneaking.
                }
                if (currentHeight > 8 || currentHeight < 0) {
                    currentHeight = 0; // If we are over/below the maximum height allowed by minecraft, reset to 0
                }
                world.setBlockState(pos, state.with(RAMP_HEIGHT, currentHeight), 3);  // Update block state with new height
            }
            return ActionResult.SUCCESS;
        }
        return ActionResult.CONSUME;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        Direction facing = state.get(FACING);
        int ramp_height = state.get(RAMP_HEIGHT);

        VoxelShape shape = VoxelShapes.empty();

        // Define ramp with finer steps
        int steps = 24; // More steps for a smoother ramp
        for (int i = 0; i < steps; i++) {
            double height = (i + 1) * (8.0 / steps) + ramp_height; // Incrementally increasing height
            double depthStart = 0;
            double depthEnd = 16 - i * (16.0 / steps);
            switch (facing) {
                case NORTH:
                    shape = VoxelShapes.union(shape, Block.createCuboidShape(0, i * (8.0 / steps), 16 - depthEnd, 16, height, 16 - depthStart));
                    break;
                case SOUTH:
                    shape = VoxelShapes.union(shape, Block.createCuboidShape(0, i * (8.0 / steps), depthStart, 16, height, depthEnd));
                    break;
                case EAST:
                    shape = VoxelShapes.union(shape, Block.createCuboidShape(depthStart, i * (8.0 / steps), 0, depthEnd, height, 16));
                    break;
                case WEST:
                    shape = VoxelShapes.union(shape, Block.createCuboidShape(16 - depthEnd, i * (8.0 / steps), 0, 16 - depthStart, height, 16));
                    break;
            }
        }
        return shape;
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return getCollisionShape(state, world, pos, context);
    }

    static {
        RAMP_HEIGHT = ModProperties.RAMP_HEIGHT;
    }
}
