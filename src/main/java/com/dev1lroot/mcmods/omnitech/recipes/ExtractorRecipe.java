package com.dev1lroot.mcmods.omnitech.recipes;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * One Extractor recipe: item → fluid + item (residue).
 *
 * <p>JSON layout (placed in {@code data/omnitech/recipe/extractor/}):
 * <pre>{@code
 * {
 *   "requiredKineticForce": 4.0,
 *   "inputItem":    { "item": "minecraft:gravel",        "amount": 1 },
 *   "outputFluid":  { "fluid": "omnitech:mineral_oil",   "amount": 250 },
 *   "residueItem":  { "item": "minecraft:sand",          "amount": 1 }
 * }
 * }</pre>
 *
 * {@code residueItem} is optional — omit or set {@code "item": "none"} for no residue.
 * {@code outputFluid} is required.
 */
public class ExtractorRecipe {

    private final String id;
    private final float  requiredKineticForce;

    private final Identifier inputItemId;
    private final int        inputItemAmount;

    private final Identifier outputFluidId;
    private final int        outputFluidAmount;

    /** Null when there is no residue item. */
    private final Identifier residueItemId;
    private final int        residueItemAmount;

    // Lazy-resolved caches
    private Item       cachedInputItem  = null;
    private boolean    inputResolved    = false;
    private FluidStack cachedOutputFluid = null;
    private Item       cachedResidueItem = null;
    private boolean    residueResolved  = false;

    public ExtractorRecipe(String id,
                           float requiredKineticForce,
                           String inputItem,   int inputItemAmount,
                           String outputFluid, int outputFluidAmount,
                           String residueItem, int residueItemAmount) {
        this.id                   = id;
        this.requiredKineticForce = requiredKineticForce;
        this.inputItemId          = parseId(inputItem,   "minecraft");
        this.inputItemAmount      = inputItemAmount;
        this.outputFluidId        = parseId(outputFluid, "omnitech");
        this.outputFluidAmount    = outputFluidAmount;
        this.residueItemId        = (residueItem == null || residueItem.isBlank()
                                        || residueItem.equalsIgnoreCase("none"))
                                    ? null : parseId(residueItem, "omnitech");
        this.residueItemAmount    = residueItemAmount;
    }

    private static Identifier parseId(String raw, String defaultNs) {
        return raw.contains(":") ? Identifier.parse(raw)
                                 : Identifier.fromNamespaceAndPath(defaultNs, raw);
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public String getId()                   { return id; }
    public float  getRequiredKineticForce() { return requiredKineticForce; }
    public int    getInputItemAmount()      { return inputItemAmount; }

    public Item getInputItem() {
        if (!inputResolved) {
            inputResolved   = true;
            cachedInputItem = BuiltInRegistries.ITEM.getOptional(inputItemId).orElse(null);
        }
        return cachedInputItem;
    }

    public FluidStack getOutputFluid() {
        if (cachedOutputFluid == null) {
            Fluid f = BuiltInRegistries.FLUID.getValue(outputFluidId);
            cachedOutputFluid = f != null ? new FluidStack(f, outputFluidAmount) : FluidStack.EMPTY;
        }
        return cachedOutputFluid.copy();
    }

    public ItemStack getResidueItem() {
        if (!residueResolved) {
            residueResolved  = true;
            cachedResidueItem = (residueItemId != null)
                    ? BuiltInRegistries.ITEM.getOptional(residueItemId).orElse(null)
                    : null;
        }
        return cachedResidueItem != null
                ? new ItemStack(cachedResidueItem, residueItemAmount)
                : ItemStack.EMPTY;
    }

    public boolean hasResidue() { return residueItemId != null; }

    // ── Matching ──────────────────────────────────────────────────────────────

    public boolean matches(ItemStack item) {
        if (item.isEmpty()) return false;
        Item needed = getInputItem();
        if (needed == null || !item.is(needed)) return false;
        return item.getCount() >= inputItemAmount;
    }
}
