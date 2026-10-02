/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.items;

import com.dev1lroot.mcmods.omnitech.OmniTechDataComponents;
import com.dev1lroot.mcmods.omnitech.pcb.ResistorCode;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.List;
import java.util.function.Consumer;

/**
 * Carbon / metal film resistor. Crafted blank; its value is painted on afterwards as
 * colour bands in a crafting grid (see {@link com.dev1lroot.mcmods.omnitech.recipe.ResistorCodingRecipe}).
 * The bands are read like a real part, so the name shows the decoded value.
 */
public class ResistorItem extends Item {

    public ResistorItem(Properties props) {
        super(props);
    }

    public static List<Integer> getBands(ItemStack stack) {
        return stack.getOrDefault(OmniTechDataComponents.RESISTOR_BANDS.get(), List.of());
    }

    public static ItemStack withBands(ItemStack stack, List<Integer> bands) {
        stack.set(OmniTechDataComponents.RESISTOR_BANDS.get(), List.copyOf(bands));
        return stack;
    }

    public static ResistorCode.Value value(ItemStack stack) {
        return ResistorCode.decodeCodes(getBands(stack));
    }

    @Override
    public Component getName(ItemStack stack) {
        ResistorCode.Value v = value(stack);
        if (v == null) return Component.translatable("item.omnitech.resistor.blank");
        return Component.translatable("item.omnitech.resistor.value", ResistorCode.format(v.ohms()));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
            Consumer<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, tooltip, flag);
        List<ResistorCode.Band> bands = ResistorCode.fromCodes(getBands(stack));
        ResistorCode.Value v = ResistorCode.decode(bands);
        if (v == null) {
            tooltip.accept(Component.translatable("tooltip.omnitech.resistor.blank").withStyle(ChatFormatting.GRAY));
            return;
        }
        tooltip.accept(Component.literal(ResistorCode.format(v.ohms()) + " " + ResistorCode.formatTolerance(v.tolerance()))
                .withStyle(ChatFormatting.AQUA));
        MutableComponent line = Component.empty();
        for (int i = 0; i < bands.size(); i++) {
            if (i > 0) line.append(Component.literal(" "));
            ResistorCode.Band b = bands.get(i);
            line.append(Component.literal("■").withColor(b.rgb))
                .append(Component.translatable("color.omnitech.band." + b.key()).withStyle(ChatFormatting.GRAY));
        }
        tooltip.accept(line);
        tooltip.accept(Component.translatable(bands.size() >= 5
                ? "tooltip.omnitech.resistor.metal_film" : "tooltip.omnitech.resistor.carbon_film")
                .withStyle(ChatFormatting.DARK_GRAY));
        if (v.tempco() > 0) {
            tooltip.accept(Component.translatable("tooltip.omnitech.resistor.tempco", v.tempco())
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
