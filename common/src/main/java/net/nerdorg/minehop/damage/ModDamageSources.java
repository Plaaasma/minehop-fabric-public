package net.nerdorg.minehop.damage;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

public class ModDamageSources {
    public static Registry<DamageType> registryWrapper;
    public static DamageSource instagib;

    public ModDamageSources(RegistryAccess registryManager) {
        registryWrapper = registryManager.registry(Registries.DAMAGE_TYPE).get();
        instagib = create(ModDamageTypes.INSTAGIB);
    }

    public static DamageSource create(ResourceKey<DamageType> key) {
        Holder<DamageType> entry = registryWrapper.getHolder(key).get();
        return new DamageSource(entry);
    }

    public static DamageSource create(ResourceKey<DamageType> key, @Nullable Entity attacker) {
        Holder<DamageType> entry = registryWrapper.getHolder(key).get();
        return new DamageSource(entry, attacker);
    }
}
