package net.nerdorg.minehop.damage;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.AbstractFireballEntity;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.WitherSkullEntity;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.explosion.Explosion;
import org.jetbrains.annotations.Nullable;

public class ModDamageSources {
    public static Registry<DamageType> registry;
    public static DamageSource instagib;

    public ModDamageSources(DynamicRegistryManager registryManager) {
        registry = registryManager.get(RegistryKeys.DAMAGE_TYPE);
        instagib = create(ModDamageTypes.INSTAGIB);
    }

    public static DamageSource create(RegistryKey<DamageType> key) {
        return new DamageSource(registry.entryOf(key));
    }

    public static DamageSource create(RegistryKey<DamageType> key, @Nullable Entity attacker) {
        return new DamageSource(registry.entryOf(key), attacker);
    }
}
