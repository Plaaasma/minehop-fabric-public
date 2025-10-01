package net.nerdorg.minehop.commands;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.loader.impl.util.UrlUtil;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Urls;
import net.minecraft.util.Util;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.nerdorg.minehop.block.entity.BoostBlockEntity;
import net.nerdorg.minehop.util.Logger;

import java.net.URI;
import java.net.URL;

public class SocialsCommands {
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
            LiteralArgumentBuilder.<ServerCommandSource>literal("discord")
                .executes(context -> {
                    handleDiscord(context);
                    return Command.SINGLE_SUCCESS;
                })
            ));
    }

    private static void handleDiscord(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity serverPlayerEntity = context.getSource().getPlayer();

        Text urlText = Text.literal("https://discord.gg/hMs97RHEgF")
                .styled(style -> style
                        .withColor(Formatting.BLUE)
                        .withClickEvent(new ClickEvent.CopyToClipboard("https://discord.gg/hMs97RHEgF"))
                        .withHoverEvent(new HoverEvent.ShowText( Text.literal("Join or else....")))
                        .withUnderline(true));

        Logger.log(serverPlayerEntity, urlText);
    }
}
