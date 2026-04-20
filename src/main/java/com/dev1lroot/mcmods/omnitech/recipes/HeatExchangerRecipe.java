package com.dev1lroot.mcmods.omnitech.recipes;

import com.dev1lroot.mcmods.omnitech.io.IHeatReceiver;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * One Heat Exchanger recipe.
 *
 * <p>JSON layout (placed in {@code data/omnitech/heat_exchanger_recipes/}):
 * <pre>{@code
 * {
 *   "maxHeat": 200,
 *   "productionHeat": 50,
 *   "inputFluid":  { "fluid": "omnitech:condensed_heated_air", "amount": 1000 },
 *   "outputFluid": { "fluid": "omnitech:condensed_air",        "amount": 1000 }
 * }
 * }</pre>
 *
 * <p>{@code maxHeat} — the machine's stored-heat ceiling while this recipe is
 * active.  Processing is blocked whenever {@code storedHeat >= maxHeat}, so the
 * machine must radiate accumulated heat away (to adjacent {@link
 * IHeatReceiver} blocks) before it can run
 * the next batch.
 *
 * <p>{@code productionHeat} — degrees Celsius added to the machine's own
 * {@code storedHeat} after every completed processing batch.
 */
public class HeatExchangerRecipe {

    private final String id;
    private final int    maxHeat;
    private final int    productionHeat;

    private final Identifier inputFluidId;
    private final int        inputFluidAmount;

    private final Identifier outputFluidId;
    private final int        outputFluidAmount;

    // Lazy-resolved caches
    private FluidStack cachedInputFluid  = null;
    private FluidStack cachedOutputFluid = null;

    public HeatExchangerRecipe(String id, int maxHeat, int productionHeat,
                               String inputFluidId,  int inputFluidAmount,
                               String outputFluidId, int outputFluidAmount) {
        this.id               = id;
        this.maxHeat          = maxHeat;
        this.productionHeat   = productionHeat;
        this.inputFluidId     = parseId(inputFluidId,  "minecraft");
        this.inputFluidAmount = inputFluidAmount;
        this.outputFluidId    = parseId(outputFluidId, "omnitech");
        this.outputFluidAmount = outputFluidAmount;
    }

    private static Identifier parseId(String raw, String defaultNs) {
        return raw.contains(":") ? Identifier.parse(raw)
                                 : Identifier.fromNamespaceAndPath(defaultNs, raw);
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public String getId()             { return id; }
    /** Stored-heat ceiling — processing is blocked when {@code storedHeat >= maxHeat}. */
    public int    getMaxHeat()        { return maxHeat; }
    /** Heat added to the machine's stored heat after each completed batch. */
    public int    getProductionHeat() { return productionHeat; }
    public int    getInputFluidAmount()  { return inputFluidAmount; }
    public int    getOutputFluidAmount() { return outputFluidAmount; }

    /** A copy of the required input FluidStack (resolved lazily). */
    public FluidStack getInputFluid() {
        if (cachedInputFluid == null) {
            Fluid f = BuiltInRegistries.FLUID.getValue(inputFluidId);
            cachedInputFluid = (f != null) ? new FluidStack(f, inputFluidAmount) : FluidStack.EMPTY;
        }
        return cachedInputFluid.copy();
    }

    /** A copy of the output FluidStack (resolved lazily). */
    public FluidStack getOutputFluid() {
        if (cachedOutputFluid == null) {
            Fluid f = BuiltInRegistries.FLUID.getValue(outputFluidId);
            cachedOutputFluid = (f != null) ? new FluidStack(f, outputFluidAmount) : FluidStack.EMPTY;
        }
        return cachedOutputFluid.copy();
    }

    /** Returns {@code true} when the current input-tank contents satisfy this recipe. */
    public boolean matches(FluidStack inputFluid) {
        if (inputFluid.isEmpty()) return false;
        if (!inputFluid.is(getInputFluid().getFluid())) return false;
        return inputFluid.getAmount() >= inputFluidAmount;
    }
}
