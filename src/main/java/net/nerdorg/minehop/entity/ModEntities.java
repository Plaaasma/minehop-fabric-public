package net.nerdorg.minehop.entity;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.entity.custom.*;

public class ModEntities {
    public static final EntityType<GamemodeEntity> GAMEMODE_ENTITY = Registry.register(Registries.ENTITY_TYPE,
            Identifier.of(Minehop.MOD_ID, "gamemode_entity"),
            FabricEntityTypeBuilder.create(SpawnGroup.MISC, GamemodeEntity::new)
                    .dimensions(EntityDimensions.fixed(1f, 1f)).build(RegistryKey.of(RegistryKeys.ENTITY_TYPE, Identifier.of(Minehop.MOD_ID, "gamemode_entity"))));


    public static final EntityType<ResetEntity> RESET_ENTITY = Registry.register(Registries.ENTITY_TYPE,
            Identifier.of(Minehop.MOD_ID, "reset_entity"),
            FabricEntityTypeBuilder.create(SpawnGroup.MISC, ResetEntity::new)
                .dimensions(EntityDimensions.fixed(1f, 1f)).build(RegistryKey.of(RegistryKeys.ENTITY_TYPE, Identifier.of(Minehop.MOD_ID, "reset_entity"))));

    public static final EntityType<StartEntity> START_ENTITY = Registry.register(Registries.ENTITY_TYPE,
            Identifier.of(Minehop.MOD_ID, "start_entity"),
            FabricEntityTypeBuilder.create(SpawnGroup.MISC, StartEntity::new)
                    .dimensions(EntityDimensions.fixed(1f, 1f)).build(RegistryKey.of(RegistryKeys.ENTITY_TYPE, Identifier.of(Minehop.MOD_ID, "start_entity"))));

    public static final EntityType<EndEntity> END_ENTITY = Registry.register(Registries.ENTITY_TYPE,
            Identifier.of(Minehop.MOD_ID, "end_entity"),
            FabricEntityTypeBuilder.create(SpawnGroup.MISC, EndEntity::new)
                    .dimensions(EntityDimensions.fixed(1f, 1f)).build(RegistryKey.of(RegistryKeys.ENTITY_TYPE, Identifier.of(Minehop.MOD_ID, "end_entity"))));

    public static final EntityType<ReplayEntity> REPLAY_ENTITY = Registry.register(Registries.ENTITY_TYPE,
            Identifier.of(Minehop.MOD_ID, "replay_entity"),
            FabricEntityTypeBuilder.create(SpawnGroup.MISC, ReplayEntity::new)
                    .dimensions(EntityDimensions.fixed(1f, 2f)).build(RegistryKey.of(RegistryKeys.ENTITY_TYPE, Identifier.of(Minehop.MOD_ID, "replay_entity"))));

    public static final EntityType<SurfRampEntity> SURF_RAMP_ENTITY = Registry.register(Registries.ENTITY_TYPE,
            Identifier.of(Minehop.MOD_ID, "surf_ramp_entity"),
            FabricEntityTypeBuilder.create(SpawnGroup.MISC, SurfRampEntity::new)
                    .dimensions(EntityDimensions.fixed(1f, 1f))
                    // Large ramps can span many chunks; track far enough that endpoints still render.
                    .trackRangeBlocks(2048)
                    .trackedUpdateRate(1)
                    .build(RegistryKey.of(RegistryKeys.ENTITY_TYPE, Identifier.of(Minehop.MOD_ID, "surf_ramp_entity"))));
}
