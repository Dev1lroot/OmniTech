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

    // Универсальная регистрация через вспомогательный объект
    // Steam rises (density < 0 → fills from top downward in networks).
    public static final FluidObject STEAM = new FluidObject("steam", gasProps());

    // Pressurized industrial gases (density = 0 → equal spread to all containers).
    public static final FluidObject HYDROGEN  = new FluidObject("hydrogen",  pressGasProps());
    public static final FluidObject OXYGEN    = new FluidObject("oxygen",    pressGasProps());
    public static final FluidObject HYDRAZINE = new FluidObject("hydrazine", pressGasProps());
    public static final FluidObject NITROGEN  = new FluidObject("nitrogen",  pressGasProps());
    public static final FluidObject AMMONIA   = new FluidObject("ammonia",   pressGasProps());
    public static final FluidObject CHLORINE  = new FluidObject("chlorine",  pressGasProps());
    public static final FluidObject CHLORAMINE = new FluidObject("chloramine", pressGasProps());

    public static final FluidObject SODIUM = new FluidObject("sodium",
            FluidType.Properties.create().density(10).viscosity(10).temperature(20));
    public static final FluidObject BRINE = new FluidObject("brine",
            FluidType.Properties.create().density(10).viscosity(10).temperature(20));
    public static final FluidObject DISTILLED_WATER = new FluidObject("distilled_water",
            FluidType.Properties.create().density(10).viscosity(10).temperature(20));
    public static final FluidObject SODIUM_HYDROXIDE = new FluidObject("sodium_hydroxide",
            FluidType.Properties.create().density(10).viscosity(10).temperature(20));
    public static final FluidObject SODIUM_HYPOCHLORITE = new FluidObject("sodium_hypochlorite",
            FluidType.Properties.create().density(10).viscosity(10).temperature(20));

    public static final FluidObject MOLTEN_BRASS     = new FluidObject("molten_brass",     moltProps(8900,  1200));
    public static final FluidObject MOLTEN_TIN       = new FluidObject("molten_tin",       moltProps(7300,  505));
    public static final FluidObject MOLTEN_LEAD      = new FluidObject("molten_lead",      moltProps(11340, 600));
    public static final FluidObject MOLTEN_ZINC      = new FluidObject("molten_zinc",      moltProps(7134,  693));
    public static final FluidObject MOLTEN_ALUMINIUM = new FluidObject("molten_aluminium", moltProps(2700,  933));
    public static final FluidObject MOLTEN_COBALT    = new FluidObject("molten_cobalt",    moltProps(8900,  1768));
    public static final FluidObject MOLTEN_NICKEL    = new FluidObject("molten_nickel",    moltProps(8908,  1728));
    public static final FluidObject MOLTEN_STEEL     = new FluidObject("molten_steel",     moltProps(7870,  1783));
    public static final FluidObject MOLTEN_URANIUM   = new FluidObject("molten_uranium",   moltProps(19100, 1405));
    public static final FluidObject MOLTEN_TITANIUM  = new FluidObject("molten_titanium",  moltProps(4507,  1941));
    public static final FluidObject MOLTEN_CHROMIUM  = new FluidObject("molten_chromium",  moltProps(7190,  2180));
    public static final FluidObject MOLTEN_TUNGSTEN  = new FluidObject("molten_tungsten",  moltProps(19300, 3695));
    public static final FluidObject MOLTEN_IRON      = new FluidObject("molten_iron",      moltProps(7874,  1811));
    public static final FluidObject MOLTEN_COPPER    = new FluidObject("molten_copper",    moltProps(8960,  1358));

    public static final FluidObject ARGON = new FluidObject("argon", pressGasProps());

    public static final FluidObject AIR = new FluidObject("air", pressGasProps());
    public static final FluidObject COMPRESSED_HEATED_AIR = new FluidObject("compressed_heated_air",
            FluidType.Properties.create().density(0).viscosity(10).temperature(0));
    public static final FluidObject COMPRESSED_AIR = new FluidObject("compressed_air",
            FluidType.Properties.create().density(0).viscosity(10).temperature(0));
    public static final FluidObject LIQUEFIED_AIR = new FluidObject("liquefied_air",
            FluidType.Properties.create().density(10).viscosity(10).temperature(0));
    public static final FluidObject CRUDE_HYDRAZINE_SOLUTION = new FluidObject("crude_hydrazine_solution",
            FluidType.Properties.create().density(10).viscosity(10).temperature(0));
    public static final FluidObject HYDRAZINE_HYDRATE = new FluidObject("hydrazine_hydrate",
            FluidType.Properties.create().density(10).viscosity(10).temperature(0));

    // Steam: density < 0 → lighter than air → fills network from top downward.
    private static FluidType.Properties gasProps() {
        return FluidType.Properties.create().density(-1000).viscosity(10).temperature(373);
    }

    // Pressurized industrial gas: density = 0 → neutral buoyancy → equal spread to all nodes.
    private static FluidType.Properties pressGasProps() {
        return FluidType.Properties.create().density(0).viscosity(10).temperature(293);
    }

    // Molten metal fluid properties (density kg/m³, temperature K)
    private static FluidType.Properties moltProps(int density, int tempKelvin) {
        return FluidType.Properties.create().density(density).viscosity(5000).temperature(tempKelvin);
    }

    /**
     * Компактный контейнер для холдеров одной жидкости
     */
    public static class FluidObject {
        public final DeferredHolder<FluidType, FluidType> type;
        public final DeferredHolder<Fluid, Fluid> source;
        public final DeferredHolder<Fluid, Fluid> flowing;

        public FluidObject(String name, FluidType.Properties typeProps) {
            this.type = TYPE_REGISTRY.register(name, () -> new FluidType(typeProps.descriptionId("fluid.omnitech." + name)));

            // Регистрируем Source и Flowing, передавая ссылки друг на друга через "this"
            this.source = REGISTRY.register(name,
                    () -> new BaseFlowingFluid.Source(this.makeProperties()));
            this.flowing = REGISTRY.register("flowing_" + name,
                    () -> new BaseFlowingFluid.Flowing(this.makeProperties()));
        }

        private BaseFlowingFluid.Properties makeProperties() {
            return new BaseFlowingFluid.Properties(type, source, flowing);
        }
    }

    public static void register(IEventBus modEventBus) {
        TYPE_REGISTRY.register(modEventBus);
        REGISTRY.register(modEventBus);
    }
}