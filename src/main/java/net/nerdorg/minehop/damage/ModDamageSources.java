package net.nerdorg.minehop.damage;

import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.RegistryWrapper;
import org.jetbrains.annotations.Nullable;

public class ModDamageSources {
    public static RegistryWrapper<DamageType> registryWrapper;
    public static DamageSource instagib;

    public ModDamageSources(DynamicRegistryManager registryManager) {
        registryWrapper = registryManager.getOptional(RegistryKeys.DAMAGE_TYPE).get();
        instagib = create(ModDamageTypes.INSTAGIB);
    }

    public static DamageSource create(RegistryKey<DamageType> key) {
        RegistryEntry<DamageType> entry = registryWrapper.getOptional(key).get();
        return new DamageSource(entry);
    }

    public static DamageSource create(RegistryKey<DamageType> key, @Nullable Entity attacker) {
        RegistryEntry<DamageType> entry = registryWrapper.getOptional(key).get();
        return new DamageSource(entry, attacker);
    }
}
