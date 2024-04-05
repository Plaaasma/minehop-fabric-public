// MADE BY hatninja ON GITHUB

package net.nerdorg.minehop.mixin;

import net.minecraft.entity.*;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.block.ModBlocks;
import net.nerdorg.minehop.config.ConfigWrapper;
import net.nerdorg.minehop.config.MinehopConfig;
import net.nerdorg.minehop.util.MovementUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(ServerPlayNetworkHandler.class)
public abstract class ServerPlayNetworkHandlerMixin {
    @ModifyConstant(method = "onPlayerMove", constant = @Constant(floatValue = 100.0F))
    private float toofast_PlayerMaxSpeed(float speed) {
        return Float.MAX_VALUE;
    }

    @ModifyConstant(method = "onPlayerMove", constant = @Constant(floatValue = 300.0F))
    private float toofast_ElytraMaxSpeed(float speed) {
        return Float.MAX_VALUE;
    }

    @ModifyConstant(method = "onVehicleMove", constant = @Constant(doubleValue = 100.0))
    private double toofast_VehicleMaxSpeed(double speed) {
        return Double.MAX_VALUE;
    }
}
