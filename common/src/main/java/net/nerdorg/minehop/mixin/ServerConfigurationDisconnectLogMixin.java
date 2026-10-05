package net.nerdorg.minehop.mixin;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Vanilla logs a configuration-phase disconnect with the whole GameProfile (behind a proxy that includes the signed
 * skin texture blob) and the full kick reason, which for Fabric API's registry-sync refusal of clients without the
 * mod is several more lines. Log the player name and the first line of the reason instead.
 */
@Mixin(ServerConfigurationPacketListenerImpl.class)
public abstract class ServerConfigurationDisconnectLogMixin {
    @Redirect(method = "onDisconnect", at = @At(value = "INVOKE", target = "Lorg/slf4j/Logger;info(Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;)V", remap = false))
    private void minehop$logShortDisconnect(Logger logger, String format, Object profile, Object reason) {
        String name = profile instanceof GameProfile gameProfile ? gameProfile.getName() : String.valueOf(profile);
        String text = String.valueOf(reason).strip();
        int lineEnd = text.indexOf('\n');
        logger.info(format, name, lineEnd < 0 ? text : text.substring(0, lineEnd).strip());
    }
}
