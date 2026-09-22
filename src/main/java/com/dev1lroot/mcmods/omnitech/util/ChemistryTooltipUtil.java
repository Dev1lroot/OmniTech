/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.util;

import com.dev1lroot.mcmods.omnitech.chemistry.CompoundNaming;
import com.dev1lroot.mcmods.omnitech.chemistry.Molecule;
import com.dev1lroot.mcmods.omnitech.chemistry.StructureLayout;
import com.dev1lroot.mcmods.omnitech.gui.tooltip.MoleculeStructureTooltipData;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.tooltip.TooltipComponent;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * Client-side tooltip rendering for anything carrying a
 * {@link com.dev1lroot.mcmods.omnitech.OmniTechDataComponents#SMILES} string: the generic
 * {@code omnitech:chemical_compound} fluid/dust, and any pre-registered fluid (ethanol, acetone,
 * …) that opts in via {@code "smiles"} in its JSON — see
 * {@link com.dev1lroot.mcmods.omnitech.FluidChemistryRegistry}. Naming itself (safe to call
 * common-side) lives in {@link CompoundNaming}; this class only adds the client-only structure
 * diagram and "hold shift" hint on top.
 */
public final class ChemistryTooltipUtil {

    private ChemistryTooltipUtil() {}

    /**
     * Appends the standard tooltip lines for a SMILES-bearing stack: molecular formula, the raw
     * SMILES code, and — while the structure is drawable — a shift hint (the diagram itself is
     * shown as the tooltip image, see {@link #structureImage}). No-op if {@code smiles} is blank.
     */
    public static void appendLines(String smiles, Consumer<Component> tooltip) {
        if (smiles == null || smiles.isBlank()) return;
        Molecule m = CompoundNaming.parse(smiles);
        if (!m.isEmpty()) {
            tooltip.accept(Component.literal(m.molecularFormula()).withStyle(ChatFormatting.AQUA));
        }
        tooltip.accept(Component.literal(smiles).withStyle(ChatFormatting.DARK_GRAY));
        if (!m.isEmpty() && !Minecraft.getInstance().hasShiftDown()) {
            tooltip.accept(Component.translatable("tooltip.omnitech.hold_shift")
                    .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        }
    }

    /**
     * The structure-diagram tooltip image for {@code smiles}, only while shift is held (same
     * "hold shift" convention as {@link #appendLines}). Wire into
     * {@code Item#getTooltipImage(ItemStack)}.
     */
    public static Optional<TooltipComponent> structureImage(String smiles) {
        if (smiles == null || smiles.isBlank() || !Minecraft.getInstance().hasShiftDown()) return Optional.empty();
        Molecule m = CompoundNaming.parse(smiles);
        if (m.isEmpty()) return Optional.empty();
        return Optional.of(new MoleculeStructureTooltipData(StructureLayout.layout(m)));
    }
}
