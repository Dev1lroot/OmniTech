/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.function.Consumer;

/**
 * Vanilla {@link BucketItem} with a hazard tooltip (Toxic / Flammable / Radioactive N)
 * appended — see {@link FluidHazardUtil}. Registered per-fluid by
 * {@link OmniTechFluids.FluidObject} in place of the plain vanilla class so every
 * OmniTech bucket shows the same hazard info as canisters and flasks.
 */
public class OmniTechBucketItem extends BucketItem {

    private final Fluid fluid;

    public OmniTechBucketItem(Fluid content, Properties properties) {
        super(content, properties);
        this.fluid = content;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
            TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, tooltip, flag);
        FluidHazardUtil.appendHazardTooltip(new FluidStack(fluid, 1), tooltip);
        String smiles = FluidChemistryRegistry.get(fluid);
        if (smiles != null) {
            com.dev1lroot.mcmods.omnitech.util.ChemistryTooltipUtil.appendLines(smiles, tooltip);
        }
    }

    @Override
    public java.util.Optional<net.minecraft.world.inventory.tooltip.TooltipComponent> getTooltipImage(ItemStack stack) {
        String smiles = FluidChemistryRegistry.get(fluid);
        return smiles == null
                ? super.getTooltipImage(stack)
                : com.dev1lroot.mcmods.omnitech.util.ChemistryTooltipUtil.structureImage(smiles);
    }
}
