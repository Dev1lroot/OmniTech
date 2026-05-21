/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.items;

import com.dev1lroot.mcmods.omnitech.OmniTechDataComponents;
import com.dev1lroot.mcmods.omnitech.blocks.logic.reactor.ReactorCellType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

/**
 * A spent reactor fuel rod: no longer reacts, cools slowly (1°C / 40 ticks),
 * and cannot be ejected from a reactor cell while temperature ≥ 50°C.
 */
public class DepletedReactorFuelRodItem extends ReactorRodItem {

    public DepletedReactorFuelRodItem(Properties properties) {
        super(properties);
    }

    @Override
    public ReactorCellType getCellType() { return ReactorCellType.DEPLETED; }

    @Override
    public boolean isTooHotToEject(ItemStack stack) {
        Integer t = stack.get(OmniTechDataComponents.ROD_TEMPERATURE.get());
        return t != null && t >= 50;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        Integer temp = stack.get(OmniTechDataComponents.ROD_TEMPERATURE.get());
        if (temp != null) {
            String status = temp >= 50 ? " — too hot to remove" : temp > 0 ? " — cooling…" : " — safe";
            tooltip.accept(Component.literal("Temperature: " + temp + "°C" + status)
                    .withStyle(s -> s.withColor(temp >= 50 ? 0xFFCC5533 : temp > 0 ? 0xFFCCAA00 : 0xFF66BB66)));
        }
    }
}
