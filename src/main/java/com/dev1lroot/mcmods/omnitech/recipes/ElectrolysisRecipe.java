package com.dev1lroot.mcmods.omnitech.recipes;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * One Electrolysis Machine recipe.
 *
 * <p>JSON layout (placed in {@code data/omnitech/electrolysis_recipes/}):
 * <pre>{@code
 * {
 *   "energyRequired": 200.0,
 *   "inputFluid":    { "fluid": "omnitech:brine",            "amount": 1000 },
 *   "outputAnode":   { "fluid": "omnitech:chlorine",         "amount": 1000 },
 *   "outputCathode": { "fluid": "omnitech:hydrogen",         "amount": 1000 },
 *   "outputSolution":{ "fluid": "omnitech:sodium_hydroxide", "amount": 1000 },
 *   "anode":         { "item":  "omnitech:nickel_rod",       "damage": 1    },
 *   "cathode":       { "item":  "omnitech:graphite_rod",     "damage": 1    }
 * }
 * }</pre>
 * {@code energyRequired} is optional and defaults to 200.0 EU per cycle.
 */
public class ElectrolysisRecipe {

    private final String id;
    private final float  energyRequired;

    private final Identifier inputFluidId;
    private final int        inputFluidAmount;

    private final Identifier outputAnodeId;
    private final int        outputAnodeAmount;
    private final Identifier outputCathodeId;
    private final int        outputCathodeAmount;
    private final Identifier outputSolutionId;
    private final int        outputSolutionAmount;

    private final Identifier anodeItemId;
    private final int        anodeDamage;
    private final Identifier cathodeItemId;
    private final int        cathodeDamage;

    // Lazy-resolved caches
    private FluidStack cachedInputFluid    = null;
    private FluidStack cachedOutputAnode   = null;
    private FluidStack cachedOutputCathode = null;
    private FluidStack cachedOutputSolution= null;
    private Item       cachedAnodeItem     = null;
    private Item       cachedCathodeItem   = null;

    public ElectrolysisRecipe(String id, float energyRequired,
                              String inputFluidId,    int inputFluidAmount,
                              String outputAnodeId,   int outputAnodeAmount,
                              String outputCathodeId, int outputCathodeAmount,
                              String outputSolutionId,int outputSolutionAmount,
                              String anodeItemId,     int anodeDamage,
                              String cathodeItemId,   int cathodeDamage) {
        this.id                   = id;
        this.energyRequired       = energyRequired;
        this.inputFluidId         = parseId(inputFluidId,    "minecraft");
        this.inputFluidAmount     = inputFluidAmount;
        this.outputAnodeId        = parseId(outputAnodeId,   "omnitech");
        this.outputAnodeAmount    = outputAnodeAmount;
        this.outputCathodeId      = parseId(outputCathodeId, "omnitech");
        this.outputCathodeAmount  = outputCathodeAmount;
        this.outputSolutionId     = parseId(outputSolutionId,"omnitech");
        this.outputSolutionAmount = outputSolutionAmount;
        this.anodeItemId          = parseId(anodeItemId,     "omnitech");
        this.anodeDamage          = anodeDamage;
        this.cathodeItemId        = parseId(cathodeItemId,   "omnitech");
        this.cathodeDamage        = cathodeDamage;
    }

    private static Identifier parseId(String raw, String defaultNs) {
        return raw.contains(":") ? Identifier.parse(raw)
                                 : Identifier.fromNamespaceAndPath(defaultNs, raw);
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public String getId()            { return id; }
    public float  getEnergyRequired(){ return energyRequired; }
    public int    getInputFluidAmount()    { return inputFluidAmount; }
    public int    getOutputAnodeAmount()   { return outputAnodeAmount; }
    public int    getOutputCathodeAmount() { return outputCathodeAmount; }
    public int    getOutputSolutionAmount(){ return outputSolutionAmount; }
    public int    getAnodeDamage()   { return anodeDamage; }
    public int    getCathodeDamage() { return cathodeDamage; }

    public FluidStack getInputFluid() {
        if (cachedInputFluid == null) {
            Fluid f = BuiltInRegistries.FLUID.getValue(inputFluidId);
            cachedInputFluid = f != null ? new FluidStack(f, inputFluidAmount) : FluidStack.EMPTY;
        }
        return cachedInputFluid.copy();
    }

    public FluidStack getOutputAnode() {
        if (cachedOutputAnode == null) {
            Fluid f = BuiltInRegistries.FLUID.getValue(outputAnodeId);
            cachedOutputAnode = f != null ? new FluidStack(f, outputAnodeAmount) : FluidStack.EMPTY;
        }
        return cachedOutputAnode.copy();
    }

    public FluidStack getOutputCathode() {
        if (cachedOutputCathode == null) {
            Fluid f = BuiltInRegistries.FLUID.getValue(outputCathodeId);
            cachedOutputCathode = f != null ? new FluidStack(f, outputCathodeAmount) : FluidStack.EMPTY;
        }
        return cachedOutputCathode.copy();
    }

    public FluidStack getOutputSolution() {
        if (cachedOutputSolution == null) {
            Fluid f = BuiltInRegistries.FLUID.getValue(outputSolutionId);
            cachedOutputSolution = f != null ? new FluidStack(f, outputSolutionAmount) : FluidStack.EMPTY;
        }
        return cachedOutputSolution.copy();
    }

    public Item getAnodeItem() {
        if (cachedAnodeItem == null)
            cachedAnodeItem = BuiltInRegistries.ITEM.getValue(anodeItemId);
        return cachedAnodeItem;
    }

    public Item getCathodeItem() {
        if (cachedCathodeItem == null)
            cachedCathodeItem = BuiltInRegistries.ITEM.getValue(cathodeItemId);
        return cachedCathodeItem;
    }

    /**
     * Returns {@code true} when inputs satisfy this recipe's requirements,
     * including item type, fluid type/amount, and remaining item durability.
     */
    public boolean matches(FluidStack inputFluid, ItemStack anodeSlot, ItemStack cathodeSlot) {
        if (inputFluid.isEmpty() || !inputFluid.is(getInputFluid().getFluid())) return false;
        if (inputFluid.getAmount() < inputFluidAmount) return false;
        if (anodeSlot.isEmpty()  || !anodeSlot.is(getAnodeItem()))   return false;
        if (cathodeSlot.isEmpty()|| !cathodeSlot.is(getCathodeItem()))return false;
        // If damage is required, the item must still be intact
        if (anodeDamage > 0 && anodeSlot.isDamageableItem()
                && anodeSlot.getDamageValue() >= anodeSlot.getMaxDamage()) return false;
        if (cathodeDamage > 0 && cathodeSlot.isDamageableItem()
                && cathodeSlot.getDamageValue() >= cathodeSlot.getMaxDamage()) return false;
        return true;
    }
}
