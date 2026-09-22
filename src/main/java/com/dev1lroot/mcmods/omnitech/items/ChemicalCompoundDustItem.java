/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.items;

import com.dev1lroot.mcmods.omnitech.OmniTechDataComponents;
import com.dev1lroot.mcmods.omnitech.chemistry.CompoundNaming;
import com.dev1lroot.mcmods.omnitech.util.ChemistryTooltipUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * The solid (dust) form of a generic chemical compound: a powder whose entire identity is a
 * {@link OmniTechDataComponents#SMILES} structural code, exactly like the fluid form
 * ({@code omnitech:chemical_compound} — see {@link com.dev1lroot.mcmods.omnitech.ChemicalCompoundFluidType}).
 * Unlike the mod's hand-authored compounds (ethanol, acetone, …), nothing about this item is
 * pre-registered: its name, molecular formula and structure diagram are all derived live from
 * whatever SMILES it carries, via {@link CompoundNaming} / {@link ChemistryTooltipUtil} — the
 * same "structure code, not a cached name" approach {@link ChemicalFormulaItem} already uses.
 */
public class ChemicalCompoundDustItem extends Item {

    public ChemicalCompoundDustItem(Properties properties) {
        super(properties);
    }

    public static String getSmiles(ItemStack stack) {
        return stack.getOrDefault(OmniTechDataComponents.SMILES.get(), "");
    }

    public static ItemStack of(String smiles) {
        ItemStack stack = new ItemStack(com.dev1lroot.mcmods.omnitech.OmniTechItems.CHEMICAL_COMPOUND_DUST.get());
        stack.set(OmniTechDataComponents.SMILES.get(), smiles);
        return stack;
    }

    @Override
    public Component getName(ItemStack stack) {
        String smiles = getSmiles(stack);
        return smiles.isBlank() ? super.getName(stack) : Component.literal(CompoundNaming.nameOf(smiles));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
            TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        String smiles = getSmiles(stack);
        if (smiles.isBlank()) {
            tooltip.accept(Component.literal("Blank").withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        ChemistryTooltipUtil.appendLines(smiles, tooltip);
    }

    @Override
    public Optional<TooltipComponent> getTooltipImage(ItemStack stack) {
        return ChemistryTooltipUtil.structureImage(getSmiles(stack));
    }
}
