package com.dev1lroot.mcmods.omnitech;

import com.dev1lroot.mcmods.omnitech.gui.AlloyFurnaceMenu;
import com.dev1lroot.mcmods.omnitech.gui.HeaterMenu;
import com.dev1lroot.mcmods.omnitech.gui.KineticGeneratorMenu;
import com.dev1lroot.mcmods.omnitech.gui.ManualCentrifugeMenu;
import com.dev1lroot.mcmods.omnitech.gui.ManualMaceratorMenu;
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

    public static void register(IEventBus modEventBus) {
        REGISTRY.register(modEventBus);
    }
}
