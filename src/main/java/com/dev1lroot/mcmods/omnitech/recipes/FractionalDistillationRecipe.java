/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.recipes;

import com.dev1lroot.mcmods.omnitech.OmniTechDataComponents;
import com.dev1lroot.mcmods.omnitech.blocks.labware.FractionalDistillerBlockEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.List;

/**
 * One fractional-distillation recipe.
 *
 * <p>A recipe converts a single input fluid into an ordered list of output
 * fluids at a specified temperature.  The number of outputs must match the
 * height of the {@link FractionalDistillerBlockEntity}
 * multiblock structure for the recipe to be selected.
 *
 * <p>Output index 0 is dispensed from the bottom block's back face, index 1
 * from the second block, and so on.
 */
public class FractionalDistillationRecipe {

    private static final int UNSET = Integer.MIN_VALUE;

    private final String id;
    private final int    requiredTemperature;
    private final int    productionTime;

    private final Identifier inputFluidId;
    private final int        inputAmount;

    /** Optional input fluid T/P constraints.  {@code null} = no constraint. */
    private final Integer minInputTemp;
    private final Integer maxInputTemp;
    private final Integer minInputPressure;
    private final Integer maxInputPressure;

    /** Ordered list of (fluidId, amount) — index matches structure layer. */
    private final List<Identifier> outputFluidIds;
    private final List<Integer>    outputAmounts;

    /**
     * Per-output temperature (°C) and pressure (kPa).
     * {@code UNSET} means "fall back to machine heat / ambient pressure".
     */
    private final int[] outputTemps;
    private final int[] outputPressures;

    /** Lazily resolved fluid references. */
    private Fluid   cachedInputFluid  = null;
    private Fluid[] cachedOutputFluids = null;

    public FractionalDistillationRecipe(String id,
                                        int requiredTemperature,
                                        int productionTime,
                                        String inputFluid, int inputAmount,
                                        Integer minInputTemp, Integer maxInputTemp,
                                        Integer minInputPressure, Integer maxInputPressure,
                                        List<String> outputFluids, List<Integer> outputAmounts,
                                        int[] outputTemps, int[] outputPressures) {
        this.id                  = id;
        this.requiredTemperature = requiredTemperature;
        this.productionTime      = productionTime;
        this.inputFluidId        = parseId(inputFluid);
        this.inputAmount         = inputAmount;
        this.minInputTemp        = minInputTemp;
        this.maxInputTemp        = maxInputTemp;
        this.minInputPressure    = minInputPressure;
        this.maxInputPressure    = maxInputPressure;
        this.outputFluidIds      = outputFluids.stream().map(FractionalDistillationRecipe::parseId).toList();
        this.outputAmounts       = List.copyOf(outputAmounts);
        this.outputTemps         = outputTemps.clone();
        this.outputPressures     = outputPressures.clone();
    }

    private static Identifier parseId(String raw) {
        return raw.contains(":") ? Identifier.parse(raw)
                                 : Identifier.fromNamespaceAndPath("omnitech", raw);
    }

    // ── Lazy fluid resolution ─────────────────────────────────────────────────

    public Fluid getInputFluid() {
        if (cachedInputFluid == null)
            cachedInputFluid = BuiltInRegistries.FLUID.getValue(inputFluidId);
        return cachedInputFluid;
    }

    public Fluid getOutputFluid(int i) {
        if (cachedOutputFluids == null) cachedOutputFluids = new Fluid[outputFluidIds.size()];
        if (cachedOutputFluids[i] == null)
            cachedOutputFluids[i] = BuiltInRegistries.FLUID.getValue(outputFluidIds.get(i));
        return cachedOutputFluids[i];
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public String getId()               { return id; }
    public int getRequiredTemperature() { return requiredTemperature; }
    public int getProductionTime()      { return productionTime; }
    public int getInputAmount()         { return inputAmount; }
    public int getOutputCount()         { return outputFluidIds.size(); }
    public int getOutputAmount(int i)   { return outputAmounts.get(i); }

    /** True if this output slot has an explicit temperature set in the recipe. */
    public boolean hasOutputTemp(int i)     { return outputTemps[i] != UNSET; }
    /** Explicit output temperature in °C.  Only valid when {@link #hasOutputTemp} is true. */
    public int     getOutputTemp(int i)     { return outputTemps[i]; }

    /** True if this output slot has an explicit pressure set in the recipe. */
    public boolean hasOutputPressure(int i)     { return outputPressures[i] != UNSET; }
    /** Explicit output pressure in kPa.  Only valid when {@link #hasOutputPressure} is true. */
    public int     getOutputPressure(int i)     { return outputPressures[i]; }

    /** Returns a plain copy of the i-th output as a FluidStack (no T/P stamped). */
    public FluidStack getOutputStack(int i) {
        Fluid f = getOutputFluid(i);
        return f == null ? FluidStack.EMPTY : new FluidStack(f, outputAmounts.get(i));
    }

    // ── Matching helpers ──────────────────────────────────────────────────────

    /** True if the given tank fluid matches this recipe's input (type, amount, optional T/P). */
    public boolean matchesInput(FluidStack tank) {
        if (tank.isEmpty()) return false;
        Fluid f = getInputFluid();
        if (f == null || !tank.is(f) || tank.getAmount() < inputAmount) return false;

        if (minInputTemp != null || maxInputTemp != null) {
            Integer box = tank.get(OmniTechDataComponents.FLUID_TEMPERATURE.get());
            int temp = box != null ? box : 20;
            if (minInputTemp != null && temp < minInputTemp) return false;
            if (maxInputTemp != null && temp > maxInputTemp) return false;
        }
        if (minInputPressure != null || maxInputPressure != null) {
            Integer box = tank.get(OmniTechDataComponents.FLUID_PRESSURE.get());
            int pressure = box != null ? box : 101;
            if (minInputPressure != null && pressure < minInputPressure) return false;
            if (maxInputPressure != null && pressure > maxInputPressure) return false;
        }
        return true;
    }

    /**
     * True if the machine's {@code temperature} (°C) satisfies the recipe threshold.
     * Positive requiredTemperature = hot recipe (temperature ≥ threshold).
     * Negative requiredTemperature = cold recipe (temperature ≤ threshold).
     */
    public boolean temperatureMet(float temperature) {
        if (requiredTemperature >= 0) return temperature >= requiredTemperature;
        return temperature <= requiredTemperature;
    }
}
