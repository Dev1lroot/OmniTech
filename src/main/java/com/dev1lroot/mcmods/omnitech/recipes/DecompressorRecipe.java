package com.dev1lroot.mcmods.omnitech.recipes;

import com.dev1lroot.mcmods.omnitech.io.IColdReceiver;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * One Decompressor recipe.
 *
 * <p>JSON layout (placed in {@code data/omnitech/decompressor_recipes/}):
 * <pre>{@code
 * {
 *   "maxCold": 200,
 *   "productionCold": 50,
 *   "inputFluid":  { "fluid": "omnitech:compressed_air", "amount": 1000 },
 *   "outputFluid": { "fluid": "omnitech:air",             "amount": 1000 }
 * }
 * }</pre>
 *
 * <p>{@code maxCold} — the machine's stored-cold ceiling while this recipe is
 * active.  Processing is blocked whenever {@code storedCold >= maxCold}, so the
 * machine must radiate its accumulated cold to adjacent
 * {@link IColdReceiver} blocks before the
 * next batch can run.
 *
 * <p>{@code productionCold} — degrees Celsius of cold added to the machine's
 * own {@code storedCold} after every completed processing batch.
 */
public class DecompressorRecipe {

    private final String id;
    private final int    maxCold;
    private final int    productionCold;

    private final Identifier inputFluidId;
    private final int        inputFluidAmount;

    private final Identifier outputFluidId;
    private final int        outputFluidAmount;

    private FluidStack cachedInputFluid  = null;
    private FluidStack cachedOutputFluid = null;

    public DecompressorRecipe(String id, int maxCold, int productionCold,
                              String inputFluidId,  int inputFluidAmount,
                              String outputFluidId, int outputFluidAmount) {
        this.id               = id;
        this.maxCold          = maxCold;
        this.productionCold   = productionCold;
        this.inputFluidId     = parseId(inputFluidId,  "omnitech");
        this.inputFluidAmount = inputFluidAmount;
        this.outputFluidId    = parseId(outputFluidId, "omnitech");
        this.outputFluidAmount = outputFluidAmount;
    }

    private static Identifier parseId(String raw, String defaultNs) {
        return raw.contains(":") ? Identifier.parse(raw)
                                 : Identifier.fromNamespaceAndPath(defaultNs, raw);
    }

    public String getId()              { return id; }
    /** Stored-cold ceiling — processing is blocked when {@code storedCold >= maxCold}. */
    public int    getMaxCold()         { return maxCold; }
    /** Cold added to the machine's stored cold after each completed batch. */
    public int    getProductionCold()  { return productionCold; }
    public int    getInputFluidAmount()  { return inputFluidAmount; }
    public int    getOutputFluidAmount() { return outputFluidAmount; }

    public FluidStack getInputFluid() {
        if (cachedInputFluid == null) {
            Fluid f = BuiltInRegistries.FLUID.getValue(inputFluidId);
            cachedInputFluid = (f != null) ? new FluidStack(f, inputFluidAmount) : FluidStack.EMPTY;
        }
        return cachedInputFluid.copy();
    }

    public FluidStack getOutputFluid() {
        if (cachedOutputFluid == null) {
            Fluid f = BuiltInRegistries.FLUID.getValue(outputFluidId);
            cachedOutputFluid = (f != null) ? new FluidStack(f, outputFluidAmount) : FluidStack.EMPTY;
        }
        return cachedOutputFluid.copy();
    }

    public boolean matches(FluidStack inputFluid) {
        if (inputFluid.isEmpty()) return false;
        if (!inputFluid.is(getInputFluid().getFluid())) return false;
        return inputFluid.getAmount() >= inputFluidAmount;
    }
}
