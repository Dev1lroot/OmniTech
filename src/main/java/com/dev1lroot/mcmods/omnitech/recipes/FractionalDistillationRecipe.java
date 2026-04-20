package com.dev1lroot.mcmods.omnitech.recipes;

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

    private final String id;
    private final int    requiredTemperature;
    private final int    productionTime;

    private final Identifier inputFluidId;
    private final int        inputAmount;

    /** Ordered list of (fluidId, amount) — index matches structure layer. */
    private final List<Identifier> outputFluidIds;
    private final List<Integer>    outputAmounts;

    /** Lazily resolved fluid references. */
    private Fluid       cachedInputFluid                 = null;
    private Fluid[]     cachedOutputFluids               = null;

    public FractionalDistillationRecipe(String id,
                                        int requiredTemperature,
                                        int productionTime,
                                        String inputFluid, int inputAmount,
                                        List<String> outputFluids, List<Integer> outputAmounts) {
        this.id                  = id;
        this.requiredTemperature = requiredTemperature;
        this.productionTime      = productionTime;
        this.inputFluidId        = parseId(inputFluid);
        this.inputAmount         = inputAmount;
        this.outputFluidIds      = outputFluids.stream().map(FractionalDistillationRecipe::parseId).toList();
        this.outputAmounts       = List.copyOf(outputAmounts);
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

    /** Returns a copy of the i-th output as a FluidStack. */
    public FluidStack getOutputStack(int i) {
        Fluid f = getOutputFluid(i);
        return f == null ? FluidStack.EMPTY : new FluidStack(f, outputAmounts.get(i));
    }

    // ── Matching helpers ──────────────────────────────────────────────────────

    /** True if the given tank fluid matches this recipe's input. */
    public boolean matchesInput(FluidStack tank) {
        if (tank.isEmpty()) return false;
        Fluid f = getInputFluid();
        return f != null && tank.is(f) && tank.getAmount() >= inputAmount;
    }

    /**
     * True if {@code storedHeat} satisfies the temperature requirement.
     * Positive required temperature = hot recipe (need heat ≥ threshold).
     * Negative required temperature = cold recipe (need heat ≤ threshold).
     */
    public boolean temperatureMet(int storedHeat) {
        if (requiredTemperature >= 0) return storedHeat >= requiredTemperature;
        return storedHeat <= requiredTemperature;
    }
}
