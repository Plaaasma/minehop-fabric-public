package net.nerdorg.minehop.commands;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.block.entity.BoostBlockEntity;
import net.nerdorg.minehop.data.DataManager;
import net.nerdorg.minehop.util.Logger;

public class BoostCommands {
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
            LiteralArgumentBuilder.<CommandSourceStack>literal("booster")
                .requires(source -> source.hasPermission(4))
                .then(RequiredArgumentBuilder.<CommandSourceStack, Double>argument("x_power", DoubleArgumentType.doubleArg())
                    .then(RequiredArgumentBuilder.<CommandSourceStack, Double>argument("y_power", DoubleArgumentType.doubleArg())
                        .then(RequiredArgumentBuilder.<CommandSourceStack, Double>argument("z_power", DoubleArgumentType.doubleArg())
                            .executes(context -> {
                                handleEditBooster(context);
                                return Command.SINGLE_SUCCESS;
                            })
                        )
                    )
                )
            ));
    }

    private static void handleEditBooster(CommandContext<CommandSourceStack> context) {
        ServerPlayer serverPlayerEntity = context.getSource().getPlayer();
        double x_power = DoubleArgumentType.getDouble(context, "x_power");
        double y_power = DoubleArgumentType.getDouble(context, "y_power");
        double z_power = DoubleArgumentType.getDouble(context, "z_power");

        Vec3 eyePosition = serverPlayerEntity.getEyePosition();
        Vec3 viewVector = serverPlayerEntity.getForward();
        Vec3 traceEnd = eyePosition.add(viewVector.x * 5, viewVector.y * 5, viewVector.z * 5);

        ClipContext clipContext = new ClipContext(eyePosition, traceEnd, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, serverPlayerEntity);
        BlockHitResult blockHitResult = serverPlayerEntity.serverLevel().clip(clipContext);
        BlockPos hitPos = blockHitResult.getBlockPos();
        BlockEntity blockEntityAtHitPos = context.getSource().getLevel().getBlockEntity(hitPos);
        if (blockEntityAtHitPos instanceof BoostBlockEntity boostBlockEntity) {
            boostBlockEntity.setXPower(x_power);
            boostBlockEntity.setYPower(y_power);
            boostBlockEntity.setZPower(z_power);
            Logger.logSuccess(serverPlayerEntity, "Set the booster power vector to " + x_power + ", " + y_power + ", " + z_power + ".");
        }
        else {
            Logger.logFailure(serverPlayerEntity, "You are not looking at a booster.");
        }
    }
}
