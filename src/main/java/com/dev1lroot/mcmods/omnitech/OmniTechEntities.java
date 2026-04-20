package com.dev1lroot.mcmods.omnitech;

import com.dev1lroot.mcmods.omnitech.entities.AbyssalEelEntity;
import com.dev1lroot.mcmods.omnitech.entities.CokeOvenEntity;
import com.dev1lroot.mcmods.omnitech.entities.RocketEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class OmniTechEntities {
    public static final DeferredRegister<EntityType<?>> REGISTRY =
            DeferredRegister.create(Registries.ENTITY_TYPE, OmniTech.MODID);

    public static final Supplier<EntityType<RocketEntity>> ROCKET =
            REGISTRY.register("rocket",
                    () -> EntityType.Builder.<RocketEntity>of(RocketEntity::new, MobCategory.MISC)
                            .sized(3.0f, 3.0f)
                            .passengerAttachments(3.1f)
                            .clientTrackingRange(10)
                            .updateInterval(1)
                            .build(ResourceKey.create(Registries.ENTITY_TYPE,
                                    Identifier.fromNamespaceAndPath(OmniTech.MODID, "rocket"))));

    public static final Supplier<EntityType<CokeOvenEntity>> COKE_OVEN =
            REGISTRY.register("coke_oven",
                    () -> EntityType.Builder.<CokeOvenEntity>of(CokeOvenEntity::new, MobCategory.MISC)
                            .sized(3.0f, 3.0f)
                            .noSummon()
                            .clientTrackingRange(8)
                            .updateInterval(4)
                            .build(ResourceKey.create(Registries.ENTITY_TYPE,
                                    Identifier.fromNamespaceAndPath(OmniTech.MODID, "coke_oven"))));

    /** Europa's deep-sea predator — hostile fish, 2× salmon size, below Y = 40 only. */
    public static final Supplier<EntityType<AbyssalEelEntity>> ABYSSAL_EEL =
            REGISTRY.register("abyssal_eel",
                    () -> EntityType.Builder.<AbyssalEelEntity>of(AbyssalEelEntity::new, MobCategory.WATER_CREATURE)
                            // Base hitbox for a medium salmon (getSalmonScale will double it)
                            .sized(0.7f, 0.4f)
                            .clientTrackingRange(8)
                            .build(ResourceKey.create(Registries.ENTITY_TYPE,
                                    Identifier.fromNamespaceAndPath(OmniTech.MODID, "abyssal_eel"))));

    public static void registerSpawnPlacements(RegisterSpawnPlacementsEvent event) {
        event.register(
                ABYSSAL_EEL.get(),
                SpawnPlacementTypes.IN_WATER,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                AbyssalEelEntity::checkSpawnRules,
                RegisterSpawnPlacementsEvent.Operation.REPLACE
        );
    }

    public static void register(IEventBus modEventBus) {
        REGISTRY.register(modEventBus);
    }
}
