package net.nerdorg.minehop.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.nerdorg.minehop.spectate.SpectateSessions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * While a player is in a spectate session the server decides what their camera is. Vanilla would otherwise let the
 * spectator leave it at will: sneaking (ServerPlayer#tick), attacking another entity in spectator mode, or the
 * spectator teleport menu all reset or switch the camera - which, with no-clip spectator flight, let a spectating
 * player fly anywhere.
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerSpectateMixin {
    @Inject(method = "setCamera", at = @At("HEAD"), cancellable = true)
    private void minehop$keepSessionCamera(Entity camera, CallbackInfo ci) {
        if (!SpectateSessions.allowCameraChange((ServerPlayer) (Object) this, camera)) {
            ci.cancel();
        }
    }
}
