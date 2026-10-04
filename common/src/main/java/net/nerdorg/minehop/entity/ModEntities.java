package net.nerdorg.minehop.entity;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.entity.custom.*;
import net.nerdorg.minehop.platform.Services;
import net.nerdorg.minehop.platform.registry.RegistryEntry;

public class ModEntities {
    // The vanilla builder settings below are exactly what FabricEntityTypeBuilder.create(MobCategory.MISC, factory)
    // .dimensions(EntityDimensions.fixed(w, h)).build(key) produced before the multiloader migration: it handed the
    // size to Builder#sized (scalable dimensions) and otherwise kept the vanilla defaults (client tracking range
    // 5 chunks, update interval 3 ticks, MISC => can spawn far from players).
    public static final RegistryEntry<EntityType<GamemodeEntity>> GAMEMODE_ENTITY = register("gamemode_entity",
            EntityType.Builder.of(GamemodeEntity::new, MobCategory.MISC)
                    .sized(1f, 1f));


    public static final RegistryEntry<EntityType<ResetEntity>> RESET_ENTITY = register("reset_entity",
            EntityType.Builder.of(ResetEntity::new, MobCategory.MISC)
                .sized(1f, 1f));

    public static final RegistryEntry<EntityType<StartEntity>> START_ENTITY = register("start_entity",
            EntityType.Builder.of(StartEntity::new, MobCategory.MISC)
                    .sized(1f, 1f));

    public static final RegistryEntry<EntityType<EndEntity>> END_ENTITY = register("end_entity",
            EntityType.Builder.of(EndEntity::new, MobCategory.MISC)
                    .sized(1f, 1f));

    public static final RegistryEntry<EntityType<ReplayEntity>> REPLAY_ENTITY = register("replay_entity",
            EntityType.Builder.of(ReplayEntity::new, MobCategory.MISC)
                    .sized(1f, 2f));

    public static final RegistryEntry<EntityType<SurfRampEntity>> SURF_RAMP_ENTITY = register("surf_ramp_entity",
            EntityType.Builder.of(SurfRampEntity::new, MobCategory.MISC)
                    .sized(1f, 1f)
                    // Large ramps can span many chunks; track far enough that endpoints still render.
                    // 2048 blocks, converted to chunks like FabricEntityTypeBuilder#trackRangeBlocks did (= 128).
                    .clientTrackingRange((2048 + 15) / 16)
                    .updateInterval(1));

    private static <T extends Entity> RegistryEntry<EntityType<T>> register(String name, EntityType.Builder<T> builder) {
        ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(Minehop.MOD_ID, name));
        return Services.REGISTRY.register(key, () -> builder.build(key));
    }
}
