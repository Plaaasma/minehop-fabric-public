package net.nerdorg.minehop.neoforge.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.nerdorg.minehop.neoforge.platform.NeoForgeEventHelper;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Function;

/**
 * {@code IEventHelper#onGameMessage} on NeoForge, which has no event for it: fires where Fabric fires
 * {@code ServerMessageEvents.GAME_MESSAGE} (tail of {@code PlayerList#broadcastSystemMessage}).
 */
@Mixin(PlayerList.class)
public abstract class PlayerListMixin {
    @Shadow
    @Final
    private MinecraftServer server;

    @Inject(method = "broadcastSystemMessage(Lnet/minecraft/network/chat/Component;Ljava/util/function/Function;Z)V", at = @At("TAIL"))
    private void minehop$fireGameMessage(Component message, Function<ServerPlayer, Component> playerMessageFactory, boolean overlay, CallbackInfo ci) {
        NeoForgeEventHelper.fireGameMessage(this.server, message, overlay);
    }
}
