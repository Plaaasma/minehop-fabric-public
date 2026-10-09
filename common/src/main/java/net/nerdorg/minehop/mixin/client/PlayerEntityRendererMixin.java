package net.nerdorg.minehop.mixin.client;


import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.nerdorg.minehop.config.ConfigWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
public abstract class PlayerEntityRendererMixin {
    // hide others: skip rendering OTHER players entirely (model, nametag, shadow). The old hook
    // cancelled updateRenderState, which only froze the model (no animation) but still drew it —
    // that was the bug. shouldRender is gated in WorldRenderer before the entity is dispatched, so
    // returning false is a full skip. shouldRender is declared ONLY in EntityRenderer (neither
    // LivingEntityRenderer nor PlayerEntityRenderer override it), so this is the method that runs
    // for players. Param erases to Entity so the mixin AP resolves the remapped (production) refmap.
    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private void minehop$hideOtherPlayers(Entity entity, Frustum frustum, double x, double y, double z, CallbackInfoReturnable<Boolean> cir) {
        // Watching a replay on this client: the (spectating, parked) local player is not part of the picture.
        if (net.nerdorg.minehop.client.replay.ReplayPlayback.isActive() && entity == Minecraft.getInstance().player) {
            cir.setReturnValue(false);
            return;
        }
        if (!net.nerdorg.minehop.client.ClientVisibility.hideOthers()) {
            return;
        }
        if (!(entity instanceof Player)) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null) {
            return;
        }
        if (entity != client.player) {
            cir.setReturnValue(false);
        }
    }
}
