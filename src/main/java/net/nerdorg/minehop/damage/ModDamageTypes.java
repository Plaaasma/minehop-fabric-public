package net.nerdorg.minehop.damage;

import net.minecraft.entity.damage.DamageEffects;
import net.minecraft.entity.damage.DamageScaling;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.entity.damage.DeathMessageType;
import net.minecraft.registry.Registerable;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

public class ModDamageTypes {
    public static RegistryKey<DamageType> INSTAGIB = RegistryKey.of(RegistryKeys.DAMAGE_TYPE, new Identifier("instagib"));

    public static void bootstrap(Registerable<DamageType> damageTypeRegisterable) {
        damageTypeRegisterable.register(INSTAGIB, new DamageType("instagib", 0.0F));
    }
}
