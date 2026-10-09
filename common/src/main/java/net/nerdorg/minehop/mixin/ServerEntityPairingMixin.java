package net.nerdorg.minehop.mixin;

import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.nerdorg.minehop.spectate.SpectateSessions;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Tells SpectateSessions whenever an entity is (re)sent to a player. A client keeps its camera on the entity object it
 * had, so when a spectated entity is dropped and sent again (e.g. a ghost jumps further than the tracking range and the
 * viewer catches up within the same tick) the camera has to be sent again too, after this spawn.
 */
@Mixin(ServerEntity.class)
public abstract class ServerEntityPairingMixin {
    @Shadow
    @Final
    private Entity entity;

    @Inject(method = "addPairing", at = @At("TAIL"))
    private void minehop$onSentToPlayer(ServerPlayer player, CallbackInfo ci) {
        SpectateSessions.onEntitySentTo(this.entity, player);
    }
}
