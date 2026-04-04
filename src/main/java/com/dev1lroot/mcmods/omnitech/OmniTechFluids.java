package com.dev1lroot.mcmods.omnitech;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public class OmniTechFluids
{
    public static final DeferredRegister<Fluid> REGISTRY =
            DeferredRegister.create(Registries.FLUID, OmniTech.MODID);
    public static final DeferredRegister<FluidType> TYPE_REGISTRY =
            DeferredRegister.create(NeoForgeRegistries.FLUID_TYPES, OmniTech.MODID);

    public static final DeferredHolder<FluidType, FluidType> STEAM_TYPE = TYPE_REGISTRY.register("steam",
            () -> new FluidType(FluidType.Properties.create()
                    .descriptionId("fluid.omnitech.steam")
                    .density(-1000)
                    .viscosity(10)
                    .temperature(373)));

    public static final DeferredHolder<Fluid, Fluid> STEAM = REGISTRY.register("steam",
            () -> new BaseFlowingFluid.Source(createProperties()));

    public static final DeferredHolder<Fluid, Fluid> FLOWING_STEAM = REGISTRY.register("flowing_steam",
            () -> new BaseFlowingFluid.Flowing(createProperties()));

    private static BaseFlowingFluid.Properties createProperties() {
        return new BaseFlowingFluid.Properties(
                STEAM_TYPE,
                STEAM,
                FLOWING_STEAM
        );
    }

    public static void register(IEventBus modEventBus) {
        TYPE_REGISTRY.register(modEventBus);
        REGISTRY.register(modEventBus);
    }
}