package com.dev1lroot.mcmods.omnitech;

import com.dev1lroot.mcmods.omnitech.gui.BoilerMenu;
import com.dev1lroot.mcmods.omnitech.gui.AlloyFurnaceMenu;
import com.dev1lroot.mcmods.omnitech.gui.ElectricCapacitorMenu;
import com.dev1lroot.mcmods.omnitech.gui.ElectricEngineMenu;
import com.dev1lroot.mcmods.omnitech.gui.ElectricFurnaceMenu;
import com.dev1lroot.mcmods.omnitech.gui.FoundryMenu;
import com.dev1lroot.mcmods.omnitech.gui.FluidTankMenu;
import com.dev1lroot.mcmods.omnitech.gui.HeaterMenu;
import com.dev1lroot.mcmods.omnitech.gui.KineticGeneratorMenu;
import com.dev1lroot.mcmods.omnitech.gui.ManualCentrifugeMenu;
import com.dev1lroot.mcmods.omnitech.gui.ManualMaceratorMenu;
import com.dev1lroot.mcmods.omnitech.gui.SmelterMenu;
import com.dev1lroot.mcmods.omnitech.gui.SolarPanelMenu;
import com.dev1lroot.mcmods.omnitech.gui.ElectrolysisMachineMenu;
import com.dev1lroot.mcmods.omnitech.gui.DecompressorMenu;
import com.dev1lroot.mcmods.omnitech.gui.FractionalDistillerMenu;
import com.dev1lroot.mcmods.omnitech.gui.HeatExchangerMenu;
import com.dev1lroot.mcmods.omnitech.gui.RotaryCompressorMenu;
import com.dev1lroot.mcmods.omnitech.gui.SolvationMachineMenu;
import com.dev1lroot.mcmods.omnitech.gui.ChemicalReactorMenu;
import com.dev1lroot.mcmods.omnitech.gui.FluidFillerMenu;
import com.dev1lroot.mcmods.omnitech.gui.RocketMenu;
import com.dev1lroot.mcmods.omnitech.gui.SorterMenu;
import com.dev1lroot.mcmods.omnitech.gui.StirlingEngineMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class OmniTechMenuTypes {
    public static final DeferredRegister<MenuType<?>> REGISTRY =
            DeferredRegister.create(Registries.MENU, OmniTech.MODID);

    public static final Supplier<MenuType<BoilerMenu>> BOILER =
            REGISTRY.register("boiler",
                    () -> IMenuTypeExtension.create(BoilerMenu::new));

    public static final Supplier<MenuType<AlloyFurnaceMenu>> ALLOY_FURNACE =
            REGISTRY.register("alloy_furnace",
                    () -> IMenuTypeExtension.create(AlloyFurnaceMenu::new));

    public static final Supplier<MenuType<ManualMaceratorMenu>> MANUAL_MACERATOR =
            REGISTRY.register("manual_macerator",
                    () -> IMenuTypeExtension.create(ManualMaceratorMenu::new));

    public static final Supplier<MenuType<ManualCentrifugeMenu>> MANUAL_CENTRIFUGE =
            REGISTRY.register("manual_centrifuge",
                    () -> IMenuTypeExtension.create(ManualCentrifugeMenu::new));

    public static final Supplier<MenuType<KineticGeneratorMenu>> KF_GENERATOR =
            REGISTRY.register("kf_generator",
                    () -> IMenuTypeExtension.create(KineticGeneratorMenu::new));

    public static final Supplier<MenuType<HeaterMenu>> HEATER =
            REGISTRY.register("heater",
                    () -> IMenuTypeExtension.create(HeaterMenu::new));

    public static final Supplier<MenuType<StirlingEngineMenu>> STIRLING_ENGINE =
            REGISTRY.register("stirling_engine",
                    () -> IMenuTypeExtension.create(StirlingEngineMenu::new));

    public static final Supplier<MenuType<FluidTankMenu>> FLUID_TANK =
            REGISTRY.register("fluid_tank",
                    () -> IMenuTypeExtension.create(FluidTankMenu::new));

    public static final Supplier<MenuType<SorterMenu>> SORTER =
            REGISTRY.register("sorter",
                    () -> IMenuTypeExtension.create(SorterMenu::new));

    public static final Supplier<MenuType<SmelterMenu>> SMELTER =
            REGISTRY.register("smelter",
                    () -> IMenuTypeExtension.create(SmelterMenu::new));

    public static final Supplier<MenuType<FoundryMenu>> FOUNDRY =
            REGISTRY.register("foundry",
                    () -> IMenuTypeExtension.create(FoundryMenu::new));

    public static final Supplier<MenuType<ElectricEngineMenu>> ELECTRIC_ENGINE =
            REGISTRY.register("electric_engine",
                    () -> IMenuTypeExtension.create(ElectricEngineMenu::new));

    public static final Supplier<MenuType<ElectricCapacitorMenu>> ELECTRIC_CAPACITOR =
            REGISTRY.register("electric_capacitor",
                    () -> IMenuTypeExtension.create(ElectricCapacitorMenu::new));

    public static final Supplier<MenuType<ElectricFurnaceMenu>> ELECTRIC_FURNACE =
            REGISTRY.register("electric_furnace",
                    () -> IMenuTypeExtension.create(ElectricFurnaceMenu::new));

    public static final Supplier<MenuType<SolarPanelMenu>> SOLAR_PANEL =
            REGISTRY.register("solar_panel",
                    () -> IMenuTypeExtension.create(SolarPanelMenu::new));

    public static final Supplier<MenuType<SolvationMachineMenu>> SOLVATION_MACHINE =
            REGISTRY.register("solvation_machine",
                    () -> IMenuTypeExtension.create(SolvationMachineMenu::new));

    public static final Supplier<MenuType<ElectrolysisMachineMenu>> ELECTROLYSIS_MACHINE =
            REGISTRY.register("electrolysis_machine",
                    () -> IMenuTypeExtension.create(ElectrolysisMachineMenu::new));

    public static final Supplier<MenuType<RotaryCompressorMenu>> ROTARY_COMPRESSOR =
            REGISTRY.register("rotary_compressor",
                    () -> IMenuTypeExtension.create(RotaryCompressorMenu::new));

    public static final Supplier<MenuType<HeatExchangerMenu>> HEAT_EXCHANGER =
            REGISTRY.register("heat_exchanger",
                    () -> IMenuTypeExtension.create(HeatExchangerMenu::new));

    public static final Supplier<MenuType<DecompressorMenu>> DECOMPRESSOR =
            REGISTRY.register("decompressor",
                    () -> IMenuTypeExtension.create(DecompressorMenu::new));

    public static final Supplier<MenuType<FractionalDistillerMenu>> FRACTIONAL_DISTILLER =
            REGISTRY.register("fractional_distiller",
                    () -> IMenuTypeExtension.create(FractionalDistillerMenu::new));

    public static final Supplier<MenuType<ChemicalReactorMenu>> CHEMICAL_REACTOR =
            REGISTRY.register("chemical_reactor",
                    () -> IMenuTypeExtension.create(ChemicalReactorMenu::new));

    public static final Supplier<MenuType<FluidFillerMenu>> FLUID_FILLER =
            REGISTRY.register("fluid_filler",
                    () -> IMenuTypeExtension.create(FluidFillerMenu::new));

    public static final Supplier<MenuType<RocketMenu>> ROCKET =
            REGISTRY.register("rocket",
                    () -> IMenuTypeExtension.create(RocketMenu::new));

    public static void register(IEventBus modEventBus) {
        REGISTRY.register(modEventBus);
    }
}
