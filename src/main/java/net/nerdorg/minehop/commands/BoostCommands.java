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
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.block.entity.BoostBlockEntity;
import net.nerdorg.minehop.data.DataManager;
import net.nerdorg.minehop.util.Logger;

public class BoostCommands {
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
            LiteralArgumentBuilder.<ServerCommandSource>literal("booster")
                .requires(source -> source.hasPermissionLevel(4))
                .then(RequiredArgumentBuilder.<ServerCommandSource, Double>argument("x_power", DoubleArgumentType.doubleArg())
                    .then(RequiredArgumentBuilder.<ServerCommandSource, Double>argument("y_power", DoubleArgumentType.doubleArg())
                        .then(RequiredArgumentBuilder.<ServerCommandSource, Double>argument("z_power", DoubleArgumentType.doubleArg())
                            .executes(context -> {
                                handleEditBooster(context);
                                return Command.SINGLE_SUCCESS;
                            })
                        )
                    )
                )
            ));
    }

    private static void handleEditBooster(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity serverPlayerEntity = context.getSource().getPlayer();
        double x_power = DoubleArgumentType.getDouble(context, "x_power");
        double y_power = DoubleArgumentType.getDouble(context, "y_power");
        double z_power = DoubleArgumentType.getDouble(context, "z_power");

        Vec3d eyePosition = serverPlayerEntity.getEyePos();
        Vec3d viewVector = serverPlayerEntity.getRotationVecClient();
        Vec3d traceEnd = eyePosition.add(viewVector.x * 5, viewVector.y * 5, viewVector.z * 5);

        RaycastContext clipContext = new RaycastContext(eyePosition, traceEnd, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, serverPlayerEntity);
        BlockHitResult blockHitResult = serverPlayerEntity.getServerWorld().raycast(clipContext);
        BlockPos hitPos = blockHitResult.getBlockPos();
        BlockEntity blockEntityAtHitPos = context.getSource().getWorld().getBlockEntity(hitPos);
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
