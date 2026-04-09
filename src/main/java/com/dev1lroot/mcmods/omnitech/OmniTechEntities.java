package com.dev1lroot.mcmods.omnitech;

import com.dev1lroot.mcmods.omnitech.entities.RocketEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
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

    public static void register(IEventBus modEventBus) {
        REGISTRY.register(modEventBus);
    }
}
