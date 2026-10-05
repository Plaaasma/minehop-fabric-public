package net.nerdorg.minehop.forge.mixin.client;

import net.minecraft.resources.Identifier;
import net.minecraftforge.network.NetworkContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Set;

/**
 * Forge remembers per connection which channels it already announced ({@code minecraft:register}) and never announces
 * them again on that connection. See {@link ClientPacketListenerMixin}.
 */
@Mixin(value = NetworkContext.class, remap = false)
public interface NetworkContextAccessor {
    @Accessor("sentChannels")
    Set<Identifier> minehop$getSentChannels();
}
