package com.dev1lroot.mcmods.omnitech.recipes;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * One Solvation Machine recipe.
 *
 * <p>JSON layout (placed in {@code data/omnitech/solvation_recipes/}):
 * <pre>{@code
 * {
 *   "requiredKineticForce": 3.0,
 *   "inputFluid":  { "fluid": "minecraft:water",         "amount": 1000 },
 *   "inputItem":   { "item":  "omnitech:sodium_chlorine", "amount": 1   },
 *   "outputFluid": { "fluid": "omnitech:brine",           "amount": 1000 }
 * }
 * }</pre>
 */
public class SolvationRecipe {

    private final String id;
    private final float  requiredKineticForce;

    private final Identifier inputFluidId;
    private final int        inputFluidAmount;

    private final Identifier inputItemId;
    private final int        inputItemAmount;

    private final Identifier outputFluidId;
    private final int        outputFluidAmount;

    // Lazy-resolved caches (deferred so registries are fully bound before use)
    private Item       cachedInputItem   = null;
    private FluidStack cachedInputFluid  = null;
    private FluidStack cachedOutputFluid = null;

    public SolvationRecipe(String id, float requiredKineticForce,
                           String inputFluidId, int inputFluidAmount,
                           String inputItemId,  int inputItemAmount,
                           String outputFluidId, int outputFluidAmount) {
        this.id                   = id;
        this.requiredKineticForce = requiredKineticForce;
        this.inputFluidId         = parseId(inputFluidId,  "minecraft");
        this.inputFluidAmount     = inputFluidAmount;
        this.inputItemId          = parseId(inputItemId,   "minecraft");
        this.inputItemAmount      = inputItemAmount;
        this.outputFluidId        = parseId(outputFluidId, "omnitech");
        this.outputFluidAmount    = outputFluidAmount;
    }

    private static Identifier parseId(String raw, String defaultNs) {
        return raw.contains(":") ? Identifier.parse(raw)
                                 : Identifier.fromNamespaceAndPath(defaultNs, raw);
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public String getId()                  { return id; }
    public float  getRequiredKineticForce(){ return requiredKineticForce; }
    public int    getInputFluidAmount()    { return inputFluidAmount; }
    public int    getInputItemAmount()     { return inputItemAmount; }

    /** The required ingredient item (resolved lazily). */
    public Item getInputItem() {
        if (cachedInputItem == null)
            cachedInputItem = BuiltInRegistries.ITEM.getValue(inputItemId);
        return cachedInputItem;
    }

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

    /**
     * Returns {@code true} when the item slot and the current input-tank
     * contents satisfy this recipe's requirements.
     */
    public boolean matches(ItemStack itemSlot, FluidStack inputFluid) {
        if (itemSlot.isEmpty()) return false;
        if (!itemSlot.is(getInputItem())) return false;
        if (itemSlot.getCount() < inputItemAmount) return false;
        if (inputFluid.isEmpty()) return false;
        if (!inputFluid.is(getInputFluid().getFluid())) return false;
        return inputFluid.getAmount() >= inputFluidAmount;
    }
}
