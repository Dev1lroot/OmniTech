package com.dev1lroot.mcmods.omnitech;

import com.dev1lroot.mcmods.omnitech.gui.BoilerScreen;
import com.dev1lroot.mcmods.omnitech.gui.CokeOvenScreen;
import com.dev1lroot.mcmods.omnitech.gui.AlloyFurnaceScreen;
import com.dev1lroot.mcmods.omnitech.gui.ElectricChargerScreen;
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
import com.dev1lroot.mcmods.omnitech.gui.FractionalDistillerScreen;
import com.dev1lroot.mcmods.omnitech.gui.HeatExchangerScreen;
import com.dev1lroot.mcmods.omnitech.gui.RotaryCompressorScreen;
import com.dev1lroot.mcmods.omnitech.gui.SolvationMachineScreen;
import com.dev1lroot.mcmods.omnitech.gui.ChemicalReactorScreen;
import com.dev1lroot.mcmods.omnitech.gui.FluidFillerScreen;
import com.dev1lroot.mcmods.omnitech.gui.RocketScreen;
import com.dev1lroot.mcmods.omnitech.gui.SorterScreen;
import com.dev1lroot.mcmods.omnitech.gui.StirlingEngineScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

public class OmniTechGUI {
    public static final DeferredRegister<CreativeModeTab> REGISTRY =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, OmniTech.MODID);

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
        event.register(OmniTechMenuTypes.FRACTIONAL_DISTILLER.get(), FractionalDistillerScreen::new);
        event.register(OmniTechMenuTypes.CHEMICAL_REACTOR.get(), ChemicalReactorScreen::new);
        event.register(OmniTechMenuTypes.FLUID_FILLER.get(), FluidFillerScreen::new);
        event.register(OmniTechMenuTypes.ROCKET.get(), RocketScreen::new);
        event.register(OmniTechMenuTypes.ELECTRIC_CHARGER.get(), ElectricChargerScreen::new);
        event.register(OmniTechMenuTypes.COKE_OVEN.get(), CokeOvenScreen::new);
    }

    private static void addCreative(BuildCreativeModeTabContentsEvent event)
    {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(OmniTechItems.COKE_BRICK_ITEM);
            event.accept(OmniTechItems.ALLOY_FURNACE_ITEM);
            event.accept(OmniTechItems.MANUAL_MACERATOR_ITEM);
            event.accept(OmniTechItems.MANUAL_CENTRIFUGE_ITEM);
            event.accept(OmniTechItems.CRANK_ITEM);
            event.accept(OmniTechItems.ELECTRIC_CHARGER_ITEM);
        }
        if (event.getTabKey() == CreativeModeTabs.NATURAL_BLOCKS) {
            event.accept(OmniTechMinerals.TIN.blockItem("%_ore"));
            event.accept(OmniTechMinerals.TUNGSTEN.blockItem("%_ore"));
            event.accept(OmniTechMinerals.CHROMIUM.blockItem("%_ore"));
        }
        if (event.getTabKey() == CreativeModeTabs.COMBAT) {
            event.accept(OmniTechItems.SPACE_SUIT_HELMET);
            event.accept(OmniTechItems.SPACE_SUIT_CHESTPLATE);
            event.accept(OmniTechItems.SPACE_SUIT_LEGGINGS);
            event.accept(OmniTechItems.SPACE_SUIT_BOOTS);
            for (ArmorSet set : ArmorSet.ALL_SETS) {
                for (var piece : set.allPieces()) event.accept(piece);
            }
            for (ToolSet set : ToolSet.ALL_SETS) {
                event.accept(set.tool("%_sword"));
                event.accept(set.tool("%_axe"));
            }
        }
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            for (ToolSet set : ToolSet.ALL_SETS) {
                event.accept(set.tool("%_pickaxe"));
                event.accept(set.tool("%_shovel"));
                event.accept(set.tool("%_axe"));
                event.accept(set.tool("%_hoe"));
            }
            event.accept(OmniTechItems.BASIC_BORE);
            event.accept(OmniTechItems.ADVANCED_BORE);
            event.accept(OmniTechItems.INDUSTRIAL_BORE);
        }
    }
}
