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

public class ReactorControlRodItem extends ReactorRodItem {

    public ReactorControlRodItem(Properties properties) {
        super(properties.durability(500));
    }

    @Override
    public ReactorCellType getCellType() { return ReactorCellType.CONTROL; }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, ctx, display, tooltip, flag);
        Integer control = stack.get(OmniTechDataComponents.ROD_CONTROL.get());
        if (control != null) {
            tooltip.accept(Component.literal("Insertion: " + control + "%")
                    .withStyle(s -> s.withColor(0xFF44AAFF)));
        }
    }

    // ── Control attribute (0–100, percent insertion) ──────────────────────────

    public static int getControl(ItemStack stack) {
        Integer v = stack.get(OmniTechDataComponents.ROD_CONTROL.get());
        return v != null ? v : 0;
    }

    public static void setControl(ItemStack stack, int percent) {
        stack.set(OmniTechDataComponents.ROD_CONTROL.get(),
                  Math.max(0, Math.min(100, percent)));
    }
}
