/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.items;

import com.dev1lroot.mcmods.omnitech.OmniTechDataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

public class RamCardItem extends Item {

    public RamCardItem(Properties props) {
        super(props);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx,
            TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        int capacity = stack.getOrDefault(OmniTechDataComponents.RAM_CAPACITY.get(), 1024);
        tooltip.accept(Component.literal("RAM: " + capacity + " bytes")
                .withStyle(s -> s.withColor(0xFF44FF88)));
        Integer addrStart = stack.get(OmniTechDataComponents.RAM_ADDR_START.get());
        Integer addrEnd   = stack.get(OmniTechDataComponents.RAM_ADDR_END.get());
        if (addrStart != null && addrEnd != null) {
            tooltip.accept(Component.literal(
                    String.format("0x%04X – 0x%04X", addrStart, addrEnd))
                    .withStyle(s -> s.withColor(0xFF88CCFF)));
        }
        if (flag.isAdvanced()) {
            OmniTechDataComponents.ByteData data = stack.get(OmniTechDataComponents.RAM_DATA.get());
            int used = data != null ? data.data().length : 0;
            if (used > 0) {
                tooltip.accept(Component.literal(used + " bytes stored")
                        .withStyle(s -> s.withColor(0xFF888888)));
            }
        }
    }
}
