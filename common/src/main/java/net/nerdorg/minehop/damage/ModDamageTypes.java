package net.nerdorg.minehop.damage;

import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageType;

public class ModDamageTypes {
    public static ResourceKey<DamageType> INSTAGIB = ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.parse("instagib"));

    public static void bootstrap(BootstrapContext<DamageType> damageTypeRegisterable) {
        damageTypeRegisterable.register(INSTAGIB, new DamageType("instagib", 0.0F));
    }
}
