package com.dev1lroot.mcmods.omnitech.recipes;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * One Chemical Infuser recipe: fluid + item → item.
 *
 * <p>JSON layout (placed in {@code data/omnitech/recipe/chemical_infuser/}):
 * <pre>{@code
 * {
 *   "requiredKineticForce": 5.0,
 *   "requiredTemperature": 300,
 *   "inputFluid":  { "fluid": "minecraft:water", "amount": 1000 },
 *   "inputItem":   { "item": "minecraft:iron_ingot", "amount": 1 },
 *   "outputItem":  { "item": "omnitech:steel_ingot",  "amount": 1 }
 * }
 * }</pre>
 *
 * {@code requiredTemperature} may be negative to require cold.
 * The machine's current temperature must be ≥ the required value.
 */
public class ChemicalInfuserRecipe {

    private final String id;
    private final float  requiredKineticForce;
    private final int    requiredTemperature;

    private final Identifier inputFluidId;
    private final int        inputFluidAmount;

    private final Identifier inputItemId;
    private final int        inputItemAmount;

    private final Identifier outputItemId;
    private final int        outputItemAmount;

    // Lazy-resolved caches
    private FluidStack cachedInputFluid = null;
    private Item       cachedInputItem  = null;
    private boolean    inputItemResolved = false;
    private Item       cachedOutputItem  = null;
    private boolean    outputItemResolved = false;

    public ChemicalInfuserRecipe(String id,
                                 float requiredKineticForce,
                                 int requiredTemperature,
                                 String inputFluid,  int inputFluidAmount,
                                 String inputItem,   int inputItemAmount,
                                 String outputItem,  int outputItemAmount) {
        this.id                   = id;
        this.requiredKineticForce = requiredKineticForce;
        this.requiredTemperature  = requiredTemperature;
        this.inputFluidId         = parseId(inputFluid,  "minecraft");
        this.inputFluidAmount     = inputFluidAmount;
        this.inputItemId          = parseId(inputItem,   "minecraft");
        this.inputItemAmount      = inputItemAmount;
        this.outputItemId         = parseId(outputItem,  "omnitech");
        this.outputItemAmount     = outputItemAmount;
    }

    private static Identifier parseId(String raw, String defaultNs) {
        return raw.contains(":") ? Identifier.parse(raw)
                                 : Identifier.fromNamespaceAndPath(defaultNs, raw);
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public String getId()                   { return id; }
    public float  getRequiredKineticForce() { return requiredKineticForce; }
    public int    getRequiredTemperature()  { return requiredTemperature; }
    public int    getInputFluidAmount()     { return inputFluidAmount; }
    public int    getInputItemAmount()      { return inputItemAmount; }

    public FluidStack getInputFluid() {
        if (cachedInputFluid == null) {
            Fluid f = BuiltInRegistries.FLUID.getValue(inputFluidId);
            cachedInputFluid = f != null ? new FluidStack(f, inputFluidAmount) : FluidStack.EMPTY;
        }
        return cachedInputFluid.copy();
    }

    public Item getInputItem() {
        if (!inputItemResolved) {
            inputItemResolved = true;
            cachedInputItem = BuiltInRegistries.ITEM.getOptional(inputItemId).orElse(null);
        }
        return cachedInputItem;
    }

    public ItemStack getOutputItem() {
        if (!outputItemResolved) {
            outputItemResolved = true;
            cachedOutputItem = BuiltInRegistries.ITEM.getOptional(outputItemId).orElse(null);
        }
        return cachedOutputItem != null
                ? new ItemStack(cachedOutputItem, outputItemAmount)
                : ItemStack.EMPTY;
    }

    // ── Matching ──────────────────────────────────────────────────────────────

    public boolean matches(FluidStack fluid, ItemStack item, int currentTemp) {
        if (currentTemp < requiredTemperature) return false;
        if (fluid.isEmpty() || !fluid.is(getInputFluid().getFluid())) return false;
        if (fluid.getAmount() < inputFluidAmount) return false;
        if (item.isEmpty()) return false;
        Item needed = getInputItem();
        if (needed == null || !item.is(needed)) return false;
        return item.getCount() >= inputItemAmount;
    }
}
