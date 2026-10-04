package net.nerdorg.minehop.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.nerdorg.minehop.block.BoostBlock;
import net.nerdorg.minehop.networking.PacketHandler;

public class BoostBlockEntity extends BlockEntity {
    private double x_power = 0;
    private double y_power = 0;
    private double z_power = 0;

    public BoostBlockEntity(BlockPos blockPos, BlockState blockState) {
        super(ModBlockEntities.BOOST_BE.get(), blockPos, blockState);
    }

    public BoostBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public void setXPower(double x_power) {
        this.x_power = x_power;
        this.setChanged();
        if (this.level instanceof ServerLevel) {
            for (ServerPlayer playerEntity : this.level.getServer().getPlayerList().getPlayers()) {
                PacketHandler.sendPower(playerEntity, this.x_power, this.y_power, this.z_power, this.worldPosition);
            }
        }
    }

    public void setYPower(double y_power) {
        this.y_power = y_power;
        this.setChanged();
        if (this.level instanceof ServerLevel) {
            for (ServerPlayer playerEntity : this.level.getServer().getPlayerList().getPlayers()) {
                PacketHandler.sendPower(playerEntity, this.x_power, this.y_power, this.z_power, this.worldPosition);
            }
        }
    }

    public void setZPower(double z_power) {
        this.z_power = z_power;
        this.setChanged();
        if (this.level instanceof ServerLevel) {
            for (ServerPlayer playerEntity : this.level.getServer().getPlayerList().getPlayers()) {
                PacketHandler.sendPower(playerEntity, this.x_power, this.y_power, this.z_power, this.worldPosition);
            }
        }
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
    protected void saveAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
        super.saveAdditional(nbt, registries);
        nbt.putDouble("x_power", this.x_power);
        nbt.putDouble("y_power", this.y_power);
        nbt.putDouble("z_power", this.z_power);
    }

    @Override
    protected void loadAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
        super.loadAdditional(nbt, registries);
        this.x_power = nbt.getDouble("x_power");
        this.y_power = nbt.getDouble("y_power");
        this.z_power = nbt.getDouble("z_power");
        this.setChanged();
    }
}
