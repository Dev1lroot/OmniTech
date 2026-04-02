package com.dev1lroot.mcmods.omnitech;

import com.dev1lroot.mcmods.omnitech.gui.AlloyFurnaceScreen;
import com.dev1lroot.mcmods.omnitech.gui.FluidTankScreen;
import com.dev1lroot.mcmods.omnitech.gui.HeaterScreen;
import com.dev1lroot.mcmods.omnitech.gui.KineticGeneratorScreen;
import com.dev1lroot.mcmods.omnitech.gui.ManualCentrifugeScreen;
import com.dev1lroot.mcmods.omnitech.gui.ManualMaceratorScreen;
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

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> OMNITECH_TAB =
            REGISTRY.register("omnitech_tab", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.omnitech"))
                    .withTabsBefore(CreativeModeTabs.COMBAT)
                    .icon(() -> OmniTechItems.STEEL_INGOT.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(OmniTechItems.ALLOY_FURNACE_ITEM.get());
                        output.accept(OmniTechItems.MANUAL_MACERATOR_ITEM.get());
                        output.accept(OmniTechItems.MANUAL_CENTRIFUGE_ITEM.get());
                        output.accept(OmniTechItems.CRANK_ITEM.get());
                        //output.accept(OmniTechMaterials.TIN.blockItem("%").get());
                        output.accept(OmniTechItems.KF_GENERATOR_ITEM.get());
                        output.accept(OmniTechItems.KF_PIPE_ITEM.get());
                        output.accept(OmniTechItems.KF_REDUCTOR_ITEM.get());
                        output.accept(OmniTechItems.HEATER_ITEM.get());
                        output.accept(OmniTechItems.STIRLING_ENGINE_ITEM.get());
                        output.accept(OmniTechItems.CONVEYOR_BELT_ITEM.get());
                        output.accept(OmniTechItems.FLUID_PIPE_ITEM.get());
                        output.accept(OmniTechItems.PUMP_ITEM.get());
                        output.accept(OmniTechItems.FLUID_TANK_ITEM.get());
                        output.accept(OmniTechItems.STEEL_INGOT.get());
                    }).build());

    public static void register(IEventBus modEventBus) {
        REGISTRY.register(modEventBus);
        modEventBus.addListener(OmniTechGUI::addCreative);
    }

    public static void registerScreens(RegisterMenuScreensEvent event)
    {
        event.register(OmniTechMenuTypes.ALLOY_FURNACE.get(), AlloyFurnaceScreen::new);
        event.register(OmniTechMenuTypes.MANUAL_MACERATOR.get(), ManualMaceratorScreen::new);
        event.register(OmniTechMenuTypes.MANUAL_CENTRIFUGE.get(), ManualCentrifugeScreen::new);
        event.register(OmniTechMenuTypes.KF_GENERATOR.get(), KineticGeneratorScreen::new);
        event.register(OmniTechMenuTypes.HEATER.get(), HeaterScreen::new);
        event.register(OmniTechMenuTypes.STIRLING_ENGINE.get(), StirlingEngineScreen::new);
        event.register(OmniTechMenuTypes.FLUID_TANK.get(), FluidTankScreen::new);
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
        if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
            event.accept(OmniTechItems.STEEL_INGOT);
        }
    }
}
