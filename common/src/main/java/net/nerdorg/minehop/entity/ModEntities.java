package net.nerdorg.minehop.entity;

import com.google.common.collect.ImmutableSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.flag.FeatureFlags;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.entity.custom.*;
import net.nerdorg.minehop.platform.Services;
import net.nerdorg.minehop.platform.registry.RegistryEntry;

public class ModEntities {
    // 1.20.1: the entity types are constructed exactly like FabricEntityTypeBuilder.create(MobCategory.MISC, factory)
    // .dimensions(EntityDimensions.fixed(w, h)).build() did before the multiloader migration (Fabric API 0.92.x builds
    // the EntityType directly: FIXED dimensions, saveable, summonable, not fire immune, MISC => can spawn far from
    // players, no spawn blocks, client tracking range 5 chunks, update interval 3 ticks, vanilla feature set, no data
    // fixer lookup). Vanilla 1.20.1's EntityType.Builder would make the dimensions scalable instead.
    public static final RegistryEntry<EntityType<GamemodeEntity>> GAMEMODE_ENTITY = register("gamemode_entity",
            GamemodeEntity::new, 1f, 1f, 5, 3);


    public static final RegistryEntry<EntityType<ResetEntity>> RESET_ENTITY = register("reset_entity",
            ResetEntity::new, 1f, 1f, 5, 3);

    public static final RegistryEntry<EntityType<StartEntity>> START_ENTITY = register("start_entity",
            StartEntity::new, 1f, 1f, 5, 3);

    public static final RegistryEntry<EntityType<EndEntity>> END_ENTITY = register("end_entity",
            EndEntity::new, 1f, 1f, 5, 3);

    // Ghosts move every tick: send their position every tick too (the default 3-tick interval made them choppy
    // and lag behind). Server-side setting only; clients are unaffected.
    public static final RegistryEntry<EntityType<ReplayEntity>> REPLAY_ENTITY = register("replay_entity",
            ReplayEntity::new, 1f, 2f, 5, 1);

    public static final RegistryEntry<EntityType<SurfRampEntity>> SURF_RAMP_ENTITY = register("surf_ramp_entity",
            SurfRampEntity::new, 1f, 1f,
            // Large ramps can span many chunks; track far enough that endpoints still render.
            // 2048 blocks, converted to chunks like FabricEntityTypeBuilder#trackRangeBlocks did (= 128).
            (2048 + 15) / 16,
            1);

    private static <T extends Entity> RegistryEntry<EntityType<T>> register(String name, EntityType.EntityFactory<T> factory,
                                                                            float width, float height,
                                                                            int clientTrackingRange, int updateInterval) {
        ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, new ResourceLocation(Minehop.MOD_ID, name));
        return Services.REGISTRY.register(key, () -> new EntityType<>(factory, MobCategory.MISC, true, true, false, true,
                ImmutableSet.of(), EntityDimensions.fixed(width, height), clientTrackingRange, updateInterval,
                FeatureFlags.VANILLA_SET));
    }
}
