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

public class RomItem extends Item {

    public static final String TYPE_LINUX    = "linux";
    public static final String TYPE_FIRMWARE = "firmware";

    private final String romType;

    public RomItem(String romType, Properties props) {
        super(props);
        this.romType = romType;
    }

    public String getRomType() { return romType; }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx,
            TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        if (TYPE_LINUX.equals(romType)) {
            tooltip.accept(Component.literal("Buildroot Linux (BusyBox)")
                    .withStyle(s -> s.withColor(0xFF44FF44)));
            tooltip.accept(Component.literal("Boot ROM — read-only")
                    .withStyle(s -> s.withColor(0xFF888888)));
        } else {
            OmniTechDataComponents.ByteData data = stack.get(OmniTechDataComponents.ROM_DATA.get());
            if (data == null || data.data().length == 0) {
                tooltip.accept(Component.literal("Empty (no firmware)")
                        .withStyle(s -> s.withColor(0xFF888888)));
            } else {
                tooltip.accept(Component.literal(data.data().length + " bytes")
                        .withStyle(s -> s.withColor(0xFF4488FF)));
            }
            tooltip.accept(Component.literal("Use Programming Station to edit")
                    .withStyle(s -> s.withColor(0xFF666666)));
        }
    }
}
