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
    public static final FluidObject STEAM = new FluidObject("steam", gasProps());
    public static final FluidObject HYDROGEN = new FluidObject("hydrogen", gasProps());
    public static final FluidObject OXYGEN = new FluidObject("oxygen", gasProps());
    public static final FluidObject HYDRAZINE = new FluidObject("hydrazine", gasProps());
    public static final FluidObject NITROGEN = new FluidObject("nitrogen", gasProps());
    public static final FluidObject AMMONIA = new FluidObject("ammonia", gasProps());
    public static final FluidObject CHLORINE = new FluidObject("chlorine", gasProps());
    public static final FluidObject SODIUM = new FluidObject("sodium", gasProps());
    public static final FluidObject BRINE = new FluidObject("brine", gasProps());
    public static final FluidObject DISTILLED_WATER = new FluidObject("distilled_water", gasProps());
    public static final FluidObject SODIUM_HYDROXIDE = new FluidObject("sodium_hydroxide", gasProps());
    public static final FluidObject MOLTEN_BRASS = new FluidObject("molten_brass",
            FluidType.Properties.create().density(8900).viscosity(3000).temperature(1200));

    // Вспомогательный метод для свойств газа
    private static FluidType.Properties gasProps() {
        return FluidType.Properties.create().density(-1000).viscosity(10).temperature(373);
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