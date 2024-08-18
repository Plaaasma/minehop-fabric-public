package net.nerdorg.minehop.entity;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.entity.custom.EndEntity;
import net.nerdorg.minehop.entity.custom.ReplayEntity;
import net.nerdorg.minehop.entity.custom.ResetEntity;
import net.nerdorg.minehop.entity.custom.StartEntity;

public class ModEntities {
    public static final EntityType<ResetEntity> RESET_ENTITY = Registry.register(Registries.ENTITY_TYPE,
            Identifier.of(Minehop.MOD_ID, "reset_entity"),
            FabricEntityTypeBuilder.create(SpawnGroup.MISC, ResetEntity::new)
                .dimensions(EntityDimensions.fixed(1f, 1f)).build());

    public static final EntityType<StartEntity> START_ENTITY = Registry.register(Registries.ENTITY_TYPE,
            Identifier.of(Minehop.MOD_ID, "start_entity"),
            FabricEntityTypeBuilder.create(SpawnGroup.MISC, StartEntity::new)
                    .dimensions(EntityDimensions.fixed(1f, 1f)).build());

    public static final EntityType<EndEntity> END_ENTITY = Registry.register(Registries.ENTITY_TYPE,
            Identifier.of(Minehop.MOD_ID, "end_entity"),
            FabricEntityTypeBuilder.create(SpawnGroup.MISC, EndEntity::new)
                    .dimensions(EntityDimensions.fixed(1f, 1f)).build());

    public static final EntityType<ReplayEntity> REPLAY_ENTITY = Registry.register(Registries.ENTITY_TYPE,
            Identifier.of(Minehop.MOD_ID, "replay_entity"),
            FabricEntityTypeBuilder.create(SpawnGroup.MISC, ReplayEntity::new)
                    .dimensions(EntityDimensions.fixed(1f, 2f)).build());
}
