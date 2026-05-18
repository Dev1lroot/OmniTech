/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.items;

import com.dev1lroot.mcmods.omnitech.OmniTechDataComponents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

public class FloppyDiskItem extends Item
{
    public static final int CAPACITY     = 1_474_560; // 1.44 MB
    public static final int SECTOR_SIZE  = 512;
    public static final int SECTOR_COUNT = CAPACITY / SECTOR_SIZE; // 2880

    public static final int DEFAULT_COLOR = 0x222222;

    public FloppyDiskItem(Properties props) {
        super(props);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag)
    {
        OmniTechDataComponents.ByteData data = stack.get(OmniTechDataComponents.FLOPPY_DATA.get());
        int read_only = stack.getOrDefault(OmniTechDataComponents.RAM_CAPACITY.get(), 0);

        int used = data != null ? data.data().length : 0;
        tooltip.accept(Component.literal(String.format("%.1f KB / 1440 KB", used / 1024.0))
                .withStyle(s -> s.withColor(0xFF88AAFF)));
        String s1 = (read_only == 1) ? "true" : "false";
        tooltip.accept(Component.literal("Read-Only: " + s1)
                .withStyle(s -> s.withColor(0x999999FF)));


//        if (flag.isAdvanced()) {
//            int color = DyedItemColor.getOrDefault(stack, DEFAULT_COLOR);
//            tooltip.accept(Component.literal(String.format("Color: #%06X", color & 0xFFFFFF))
//                    .withStyle(s -> s.withColor(0xFFAAAAAA)));
//        }
    }
}