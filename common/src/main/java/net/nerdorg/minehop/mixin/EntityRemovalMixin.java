package net.nerdorg.minehop.mixin;

import net.minecraft.world.entity.Entity;
import net.nerdorg.minehop.entity.custom.SurfRampEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 1.21.1 port: 1.21.2+ calls {@code Entity#onRemoval(RemovalReason)} at the end of {@code setRemoved}, which
 * {@link SurfRampEntity} overrides to mark its ramp registry dirty (also for unloaded ramps, which are removed
 * without {@code remove()}). 1.21.1 has no such hook, so call it from the same place.
 */
@Mixin(Entity.class)
public abstract class EntityRemovalMixin {
    @Inject(method = "setRemoved", at = @At("TAIL"))
    private void minehop$onRemoval(Entity.RemovalReason reason, CallbackInfo ci) {
        if ((Object) this instanceof SurfRampEntity ramp) {
            ramp.onRemoval(reason);
        }
    }
}
