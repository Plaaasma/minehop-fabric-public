package net.nerdorg.minehop.block.entity;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.nerdorg.minehop.block.BoostBlock;

public class BoostBlockEntity extends BlockEntity {
    private double x_power = 0;
    private double y_power = 0;
    private double z_power = 0;

    public BoostBlockEntity(BlockPos blockPos, BlockState blockState) {
        super(ModBlockEntities.BOOST_BE, blockPos, blockState);
    }

    public BoostBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public void setXPower(double x_power) {
        this.x_power = x_power;
        this.markDirty();
    }

    public void setYPower(double y_power) {
        this.y_power = y_power;
        this.markDirty();
    }

    public void setZPower(double z_power) {
        this.z_power = z_power;
        this.markDirty();
    }

    public double getXPower() {
        return this.x_power;
    }

    public double getYPower() {
        return this.y_power;
    }

    public double getZPower() {
        return this.z_power;
    }

    @Override
    protected void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        nbt.putDouble("x_power", this.x_power);
        nbt.putDouble("y_power", this.y_power);
        nbt.putDouble("z_power", this.z_power);
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        this.x_power = nbt.getDouble("x_power");
        this.y_power = nbt.getDouble("y_power");
        this.z_power = nbt.getDouble("z_power");
        this.markDirty();
    }
}
