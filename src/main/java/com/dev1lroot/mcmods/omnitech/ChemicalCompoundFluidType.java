/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech;

import com.dev1lroot.mcmods.omnitech.chemistry.CompoundNaming;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;

/**
 * {@link FluidType} for the generic {@code omnitech:chemical_compound} fluid: unlike every other
 * OmniTech fluid (one static name per registered id), a chemical-compound stack's identity lives
 * entirely in its {@link OmniTechDataComponents#SMILES} component, so its display name has to be
 * derived per-stack instead of coming from a fixed lang key.
 */
public class ChemicalCompoundFluidType extends FluidType {

    public ChemicalCompoundFluidType(Properties properties) {
        super(properties);
    }

    @Override
    public Component getDescription(FluidStack stack) {
        String smiles = stack.get(OmniTechDataComponents.SMILES.get());
        return smiles == null || smiles.isBlank()
                ? super.getDescription(stack)
                : Component.literal(CompoundNaming.nameOf(smiles));
    }
}
