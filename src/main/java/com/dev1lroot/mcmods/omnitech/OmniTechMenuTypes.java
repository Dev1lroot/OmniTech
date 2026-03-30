package com.dev1lroot.mcmods.omnitech;

import com.dev1lroot.mcmods.omnitech.gui.AlloyFurnaceMenu;
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

    public static void register(IEventBus modEventBus) {
        REGISTRY.register(modEventBus);
    }
}
