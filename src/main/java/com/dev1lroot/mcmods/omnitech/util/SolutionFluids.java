/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.util;

import com.dev1lroot.mcmods.omnitech.OmniTechDataComponents;
import com.dev1lroot.mcmods.omnitech.OmniTechFluids;
import com.dev1lroot.mcmods.omnitech.items.Mixture;
import com.dev1lroot.mcmods.omnitech.items.Solution;
import net.minecraft.core.component.DataComponentHolder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.List;

/**
 * Bridge between a {@link Solution} and a plain {@link FluidStack}, so mixtures can move through the
 * same pipes, tanks and machine handlers as any other fluid.
 *
 * <p>A mixture is a stack of the {@code omnitech:solution} fluid carrying a {@link Mixture}
 * (ratios), plus the usual temperature / pressure components for the <em>whole</em> mixture.
 * A solution that is just one dissolved fluid is <b>not</b> a mixture: it collapses to that plain
 * fluid, so existing machines and recipes that only know single fluids keep working untouched.
 */
public final class SolutionFluids {
    private SolutionFluids() {}

    public static final int AMBIENT_TEMP = 20;
    public static final int AMBIENT_PRESSURE = 101;

    /** The registered {@code omnitech:solution} fluid. */
    public static Fluid fluid() {
        return OmniTechFluids.get("solution").source.get();
    }

    /** True if {@code stack} is a mixture (a {@code solution} stack), as opposed to a plain fluid. */
    public static boolean isMixture(FluidStack stack) {
        return !stack.isEmpty() && stack.is(fluid());
    }

    /**
     * The contents of {@code stack} in absolute mB (summing to the stack's amount): the mixture's
     * components if it is one, otherwise the single dissolved fluid.
     */
    public static Solution toSolution(FluidStack stack) {
        if (stack.isEmpty()) return Solution.EMPTY;
        if (isMixture(stack)) {
            Mixture mixture = stack.get(OmniTechDataComponents.MIXTURE.get());
            return mixture == null ? Solution.EMPTY : mixture.toSolution(stack.getAmount());
        }
        return Solution.EMPTY.plus(stack.getFluid(), stack.getAmount(), true);
    }

    /** {@link #toSolution(FluidStack)} for a resource + amount pair, as handlers receive them. */
    public static Solution toSolution(net.neoforged.neoforge.transfer.fluid.FluidResource resource, int amount) {
        return resource.isEmpty() || amount <= 0 ? Solution.EMPTY : toSolution(resource.toStack(amount));
    }

    /** {@code solution} as a stack at ambient temperature and pressure. */
    public static FluidStack toStack(Solution solution) {
        return toStack(solution, AMBIENT_TEMP, AMBIENT_PRESSURE);
    }

    /**
     * {@code solution} as a stack: {@link FluidStack#EMPTY} if empty, the plain fluid if it is a
     * single dissolved component, otherwise a {@code solution} stack — with the given whole-mixture
     * temperature (°C) and pressure (kPa).
     */
    public static FluidStack toStack(Solution solution, int tempC, int pressureKPa) {
        if (solution.isEmpty()) return FluidStack.EMPTY;
        List<Solution.Part> parts = solution.components();

        FluidStack out;
        if (parts.size() == 1 && parts.get(0).dissolved()) {
            out = new FluidStack(parts.get(0).fluid(), parts.get(0).amount());
        } else {
            out = new FluidStack(fluid(), solution.totalAmount());
            out.set(OmniTechDataComponents.MIXTURE.get(), Mixture.of(solution));
        }
        FluidNetworkUtil.applyAttributes(out, tempC, pressureKPa);
        return out;
    }

    // ── Conditions carried by a container item (flask / pipette) ──────────────

    /** Temperature (°C) of a container item's contents; ambient if it carries none. */
    public static int temperatureOf(DataComponentHolder holder) {
        Integer t = holder.get(OmniTechDataComponents.FLUID_TEMPERATURE.get());
        return t != null ? t : AMBIENT_TEMP;
    }

    /** Pressure (kPa) of a container item's contents; ambient if it carries none. */
    public static int pressureOf(DataComponentHolder holder) {
        Integer p = holder.get(OmniTechDataComponents.FLUID_PRESSURE.get());
        return p != null ? p : AMBIENT_PRESSURE;
    }

    /** Stamps the whole-contents temperature / pressure onto a container item (ambient = no component). */
    public static void setConditions(ItemStack stack, int tempC, int pressureKPa) {
        if (tempC != AMBIENT_TEMP) stack.set(OmniTechDataComponents.FLUID_TEMPERATURE.get(), tempC);
        else                       stack.remove(OmniTechDataComponents.FLUID_TEMPERATURE.get());
        if (pressureKPa != AMBIENT_PRESSURE) stack.set(OmniTechDataComponents.FLUID_PRESSURE.get(), pressureKPa);
        else                                 stack.remove(OmniTechDataComponents.FLUID_PRESSURE.get());
    }

    /** Volume-weighted average of two conditions (used when two contents merge). */
    public static int blend(int a, int amountA, int b, int amountB) {
        int total = amountA + amountB;
        return total <= 0 ? b : (int) (((long) a * amountA + (long) b * amountB) / total);
    }
}
