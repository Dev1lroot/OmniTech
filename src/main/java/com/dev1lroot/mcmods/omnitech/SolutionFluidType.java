/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech;

import com.dev1lroot.mcmods.omnitech.chemistry.MixtureNaming;
import com.dev1lroot.mcmods.omnitech.util.SolutionFluids;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;

/**
 * {@link FluidType} for the {@code omnitech:solution} mixture fluid: a mixture that is something
 * familiar gets its everyday name per stack (e.g. {@code "40% Alcohol"}, see
 * {@link MixtureNaming}); anything else stays a plain "Solution".
 */
public class SolutionFluidType extends FluidType {

    public SolutionFluidType(Properties properties) {
        super(properties);
    }

    @Override
    public Component getDescription(FluidStack stack) {
        Component name = MixtureNaming.nameOf(SolutionFluids.toSolution(stack));
        return name != null ? name : super.getDescription(stack);
    }
}
