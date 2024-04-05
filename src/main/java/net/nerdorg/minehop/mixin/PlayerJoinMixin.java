package net.nerdorg.minehop.mixin;

import me.shedaniel.autoconfig.AutoConfig;
import net.minecraft.network.ClientConnection;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.*;
import net.nerdorg.minehop.util.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Mixin(PlayerManager.class)
public class PlayerJoinMixin {
    @Shadow @Final private MinecraftServer server;
    @Unique
    private final ThreadLocal<ServerPlayerEntity> cachedPlayer = new ThreadLocal<>();

    @Inject(method = "broadcast(Lnet/minecraft/text/Text;Z)V", at = @At("HEAD"), cancellable = true)
    public void broadcast(Text message, boolean overlay, CallbackInfo ci) {
        if (message.toString().contains("multiplayer.player.joined")) {
            List<Text> unstyledMessage = message.withoutStyle();
            if (unstyledMessage.size() == 1) {
                String playerName = message.withoutStyle().get(0).getString();
                Logger.logGlobal(this.server,
                        Text.literal(playerName + " joined!"));
            }
            else {
                String prefix = message.withoutStyle().get(0).getString();
                String playerName = message.withoutStyle().get(1).getString();
                Logger.logGlobal(this.server,
                        Text.literal(prefix + playerName + " joined!"));
            }
            ci.cancel();
        }
        else if (message.toString().contains("multiplayer.player.left")) {
            List<Text> unstyledMessage = message.withoutStyle();
            if (unstyledMessage.size() == 1) {
                String playerName = message.withoutStyle().get(0).getString();
                Logger.logGlobal(this.server,
                        Text.literal(playerName + " left!"));
            }
            else {
                String prefix = message.withoutStyle().get(0).getString();
                String playerName = message.withoutStyle().get(1).getString();
                Logger.logGlobal(this.server,
                        Text.literal(prefix + playerName + " left!"));
            }
            ci.cancel();
        }
    }
}
