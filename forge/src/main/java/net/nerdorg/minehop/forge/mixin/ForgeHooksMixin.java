package net.nerdorg.minehop.forge.mixin;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraftforge.common.ForgeHooks;
import net.nerdorg.minehop.forge.platform.ForgeNetworkHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Vanilla's custom payload codec falls back to {@code ForgeHooks.getCustomPayloadCodec} for ids it does not know. For
 * Forge channel ids that codec only encodes Forge's own {@code ForgePayload}; for Minehop's payload ids this lets it
 * encode the typed payload objects too (same bytes), so Minehop can send {@code Clientbound/ServerboundCustomPayloadPacket}
 * with the real payload object, exactly like Fabric (see {@link ForgeNetworkHelper}).
 */
@Mixin(value = ForgeHooks.class, remap = false)
public abstract class ForgeHooksMixin {
    @Inject(method = "getCustomPayloadCodec", at = @At("RETURN"), cancellable = true)
    private static <B extends FriendlyByteBuf> void minehop$encodeTypedPayloads(Identifier id, int max, CallbackInfoReturnable<StreamCodec<B, ? extends CustomPacketPayload>> cir) {
        StreamCodec<B, CustomPacketPayload> wrapped = ForgeNetworkHelper.wrapPayloadCodec(id, cir.getReturnValue());
        if (wrapped != null) {
            cir.setReturnValue(wrapped);
        }
    }
}
