package net.nerdorg.minehop.entity;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.nerdorg.minehop.Minehop;
import net.nerdorg.minehop.entity.custom.*;

public class ModEntities {
    public static final EntityType<GamemodeEntity> GAMEMODE_ENTITY = Registry.register(BuiltInRegistries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(Minehop.MOD_ID, "gamemode_entity"),
            FabricEntityTypeBuilder.create(MobCategory.MISC, GamemodeEntity::new)
                    .dimensions(EntityDimensions.fixed(1f, 1f)).build(ResourceKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(Minehop.MOD_ID, "gamemode_entity"))));


    public static final EntityType<ResetEntity> RESET_ENTITY = Registry.register(BuiltInRegistries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(Minehop.MOD_ID, "reset_entity"),
            FabricEntityTypeBuilder.create(MobCategory.MISC, ResetEntity::new)
                .dimensions(EntityDimensions.fixed(1f, 1f)).build(ResourceKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(Minehop.MOD_ID, "reset_entity"))));

    public static final EntityType<StartEntity> START_ENTITY = Registry.register(BuiltInRegistries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(Minehop.MOD_ID, "start_entity"),
            FabricEntityTypeBuilder.create(MobCategory.MISC, StartEntity::new)
                    .dimensions(EntityDimensions.fixed(1f, 1f)).build(ResourceKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(Minehop.MOD_ID, "start_entity"))));

    public static final EntityType<EndEntity> END_ENTITY = Registry.register(BuiltInRegistries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(Minehop.MOD_ID, "end_entity"),
            FabricEntityTypeBuilder.create(MobCategory.MISC, EndEntity::new)
                    .dimensions(EntityDimensions.fixed(1f, 1f)).build(ResourceKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(Minehop.MOD_ID, "end_entity"))));

    public static final EntityType<ReplayEntity> REPLAY_ENTITY = Registry.register(BuiltInRegistries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(Minehop.MOD_ID, "replay_entity"),
            FabricEntityTypeBuilder.create(MobCategory.MISC, ReplayEntity::new)
                    .dimensions(EntityDimensions.fixed(1f, 2f)).build(ResourceKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(Minehop.MOD_ID, "replay_entity"))));

    public static final EntityType<SurfRampEntity> SURF_RAMP_ENTITY = Registry.register(BuiltInRegistries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(Minehop.MOD_ID, "surf_ramp_entity"),
            FabricEntityTypeBuilder.create(MobCategory.MISC, SurfRampEntity::new)
                    .dimensions(EntityDimensions.fixed(1f, 1f))
                    // Large ramps can span many chunks; track far enough that endpoints still render.
                    .trackRangeBlocks(2048)
                    .trackedUpdateRate(1)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(Minehop.MOD_ID, "surf_ramp_entity"))));
}
