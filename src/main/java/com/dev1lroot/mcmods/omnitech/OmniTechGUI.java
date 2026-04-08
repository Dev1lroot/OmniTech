package com.dev1lroot.mcmods.omnitech;

import com.dev1lroot.mcmods.omnitech.gui.BoilerScreen;
import com.dev1lroot.mcmods.omnitech.gui.AlloyFurnaceScreen;
import com.dev1lroot.mcmods.omnitech.gui.ElectricCapacitorScreen;
import com.dev1lroot.mcmods.omnitech.gui.ElectricEngineScreen;
import com.dev1lroot.mcmods.omnitech.gui.ElectricFurnaceScreen;
import com.dev1lroot.mcmods.omnitech.gui.FoundryScreen;
import com.dev1lroot.mcmods.omnitech.gui.FluidTankScreen;
import com.dev1lroot.mcmods.omnitech.gui.HeaterScreen;
import com.dev1lroot.mcmods.omnitech.gui.KineticGeneratorScreen;
import com.dev1lroot.mcmods.omnitech.gui.ManualCentrifugeScreen;
import com.dev1lroot.mcmods.omnitech.gui.ManualMaceratorScreen;
import com.dev1lroot.mcmods.omnitech.gui.SmelterScreen;
import com.dev1lroot.mcmods.omnitech.gui.SolarPanelScreen;
import com.dev1lroot.mcmods.omnitech.gui.ElectrolysisMachineScreen;
import com.dev1lroot.mcmods.omnitech.gui.DecompressorScreen;
import com.dev1lroot.mcmods.omnitech.gui.HeatExchangerScreen;
import com.dev1lroot.mcmods.omnitech.gui.RotaryCompressorScreen;
import com.dev1lroot.mcmods.omnitech.gui.SolvationMachineScreen;
import com.dev1lroot.mcmods.omnitech.gui.SorterScreen;
import com.dev1lroot.mcmods.omnitech.gui.StirlingEngineScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class OmniTechGUI {
    public static final DeferredRegister<CreativeModeTab> REGISTRY =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, OmniTech.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MATERIALS_TAB =
            REGISTRY.register("omnitech.materials", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.omnitech.materials"))
                    .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
                    .icon(() -> OmniTechMaterials.TUNGSTEN.item("%_ingot").get().getDefaultInstance())
                    .displayItems((parameters, output) -> MaterialSet.addAllToTab(output))
                    .build());

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MACHINES_TAB =
            REGISTRY.register("omnitech.machines", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.omnitech.machines"))
                    .withTabsBefore(MATERIALS_TAB.getKey())
                    .icon(() -> OmniTechItems.BOILER.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        // Генерация и обработка
                        output.accept(OmniTechItems.BOILER.get());
                        output.accept(OmniTechItems.STIRLING_ENGINE_ITEM.get());
                        output.accept(OmniTechItems.KF_GENERATOR_ITEM.get());
                        output.accept(OmniTechItems.HEATER_ITEM.get());

                        // Станки
                        output.accept(OmniTechItems.ALLOY_FURNACE_ITEM.get());
                        output.accept(OmniTechItems.MANUAL_MACERATOR_ITEM.get());
                        output.accept(OmniTechItems.MANUAL_CENTRIFUGE_ITEM.get());
                        output.accept(OmniTechItems.SMELTER_ITEM.get());
                        output.accept(OmniTechItems.FOUNDRY_ITEM.get());

                        // Логистика и трубы
                        output.accept(OmniTechItems.CRANK_ITEM.get());
                        output.accept(OmniTechItems.KF_PIPE_ITEM.get());
                        output.accept(OmniTechItems.KF_REDUCTOR_ITEM.get());
                        output.accept(OmniTechItems.CONVEYOR_BELT_ITEM.get());
                        output.accept(OmniTechItems.SORTER_ITEM.get());
                        output.accept(OmniTechItems.FLUID_PIPE_ITEM.get());
                        output.accept(OmniTechItems.PUMP_ITEM.get());
                        output.accept(OmniTechItems.FLUID_TANK_ITEM.get());

                        // Electricity
                        output.accept(OmniTechItems.ELECTRIC_ENGINE_ITEM.get());
                        output.accept(OmniTechItems.ELECTRIC_WIRE_ITEM.get());
                        output.accept(OmniTechItems.ELECTRIC_CAPACITOR_ITEM.get());
                        output.accept(OmniTechItems.ELECTRIC_FURNACE_ITEM.get());
                        output.accept(OmniTechItems.SOLAR_PANEL_ITEM.get());
                        output.accept(OmniTechItems.SOLVATION_MACHINE_ITEM.get());
                        output.accept(OmniTechItems.ELECTROLYSIS_MACHINE_ITEM.get());
                        output.accept(OmniTechItems.ROTARY_COMPRESSOR_ITEM.get());
                        output.accept(OmniTechItems.FLUID_COLLECTOR_ITEM.get());
                        output.accept(OmniTechItems.HEAT_EXCHANGER_ITEM.get());
                        output.accept(OmniTechItems.DECOMPRESSOR_ITEM.get());
                    }).build());

    public static void register(IEventBus modEventBus) {
        REGISTRY.register(modEventBus);
        modEventBus.addListener(OmniTechGUI::addCreative);
    }

    public static void registerScreens(RegisterMenuScreensEvent event)
    {
        event.register(OmniTechMenuTypes.BOILER.get(), BoilerScreen::new);
        event.register(OmniTechMenuTypes.ALLOY_FURNACE.get(), AlloyFurnaceScreen::new);
        event.register(OmniTechMenuTypes.MANUAL_MACERATOR.get(), ManualMaceratorScreen::new);
        event.register(OmniTechMenuTypes.MANUAL_CENTRIFUGE.get(), ManualCentrifugeScreen::new);
        event.register(OmniTechMenuTypes.KF_GENERATOR.get(), KineticGeneratorScreen::new);
        event.register(OmniTechMenuTypes.HEATER.get(), HeaterScreen::new);
        event.register(OmniTechMenuTypes.STIRLING_ENGINE.get(), StirlingEngineScreen::new);
        event.register(OmniTechMenuTypes.FLUID_TANK.get(), FluidTankScreen::new);
        event.register(OmniTechMenuTypes.SORTER.get(), SorterScreen::new);
        event.register(OmniTechMenuTypes.SMELTER.get(), SmelterScreen::new);
        event.register(OmniTechMenuTypes.FOUNDRY.get(), FoundryScreen::new);
        event.register(OmniTechMenuTypes.ELECTRIC_ENGINE.get(), ElectricEngineScreen::new);
        event.register(OmniTechMenuTypes.ELECTRIC_CAPACITOR.get(), ElectricCapacitorScreen::new);
        event.register(OmniTechMenuTypes.ELECTRIC_FURNACE.get(), ElectricFurnaceScreen::new);
        event.register(OmniTechMenuTypes.SOLAR_PANEL.get(), SolarPanelScreen::new);
        event.register(OmniTechMenuTypes.SOLVATION_MACHINE.get(), SolvationMachineScreen::new);
        event.register(OmniTechMenuTypes.ELECTROLYSIS_MACHINE.get(), ElectrolysisMachineScreen::new);
        event.register(OmniTechMenuTypes.ROTARY_COMPRESSOR.get(), RotaryCompressorScreen::new);
        event.register(OmniTechMenuTypes.HEAT_EXCHANGER.get(), HeatExchangerScreen::new);
        event.register(OmniTechMenuTypes.DECOMPRESSOR.get(), DecompressorScreen::new);
    }

    private static void addCreative(BuildCreativeModeTabContentsEvent event)
    {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(OmniTechItems.ALLOY_FURNACE_ITEM);
            event.accept(OmniTechItems.MANUAL_MACERATOR_ITEM);
            event.accept(OmniTechItems.MANUAL_CENTRIFUGE_ITEM);
            event.accept(OmniTechItems.CRANK_ITEM);
        }
        if (event.getTabKey() == CreativeModeTabs.NATURAL_BLOCKS) {
            event.accept(OmniTechMaterials.TIN.blockItem("%_ore"));
        }
    }
}
