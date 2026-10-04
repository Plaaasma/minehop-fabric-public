package net.nerdorg.minehop.forge.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.nerdorg.minehop.forge.platform.ForgeEventHelper;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Function;

/**
 * Forge has no event for broadcast game/system messages: fire {@code IEventHelper#onGameMessage} listeners where Fabric
 * API fires {@code ServerMessageEvents.GAME_MESSAGE} (tail of the 3-argument broadcastSystemMessage, which the
 * 2-argument overload delegates to).
 */
@Mixin(PlayerList.class)
public abstract class PlayerListMixin {
    @Shadow
    @Final
    private MinecraftServer server;

    @Inject(method = "broadcastSystemMessage(Lnet/minecraft/network/chat/Component;Ljava/util/function/Function;Z)V", at = @At("TAIL"))
    private void minehop$onGameMessage(Component message, Function<ServerPlayer, Component> playerMessageFactory, boolean overlay, CallbackInfo ci) {
        ForgeEventHelper.fireGameMessage(this.server, message, overlay);
    }
}
