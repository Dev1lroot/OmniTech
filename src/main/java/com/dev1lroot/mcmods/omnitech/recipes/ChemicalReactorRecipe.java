package com.dev1lroot.mcmods.omnitech.recipes;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * One Chemical Reactor recipe.
 *
 * <p>JSON layout (placed in {@code data/omnitech/chemical_reactor_recipes/}):
 * <pre>{@code
 * {
 *   "requiredTemperature": 0,
 *   "productionTime": 100,
 *   "inputs": [
 *     { "fluid": "omnitech:fluid_a", "amount": 1000 },
 *     { "fluid": "omnitech:fluid_b", "amount": 1000 }
 *   ],
 *   "output": { "fluid": "omnitech:product", "amount": 1000 },
 *   "catalyst": { "item": "omnitech:iron_rod", "damage": 1 }
 * }
 * }</pre>
 *
 * {@code catalyst} is optional — omit or set {@code "item": "none"} for no catalyst.
 * {@code requiredTemperature} defaults to 0 (room temperature works).
 * {@code productionTime} defaults to 100 ticks.
 *
 * <p>The two inputs are order-independent: the machine may hold fluid A in tank 1
 * and fluid B in tank 2, or vice versa — both orderings match.
 */
public class ChemicalReactorRecipe {

    private final String id;
    private final int    requiredTemperature;
    private final int    productionTime;

    private final Identifier input1FluidId;
    private final int        input1Amount;
    private final Identifier input2FluidId;
    private final int        input2Amount;

    private final Identifier outputFluidId;
    private final int        outputAmount;

    /** Null when no catalyst is required. */
    private final Identifier catalystItemId;
    private final int        catalystDamage;

    // Lazy-resolved caches
    private FluidStack cachedInput1  = null;
    private FluidStack cachedInput2  = null;
    private FluidStack cachedOutput  = null;
    private Item       cachedCatalyst = null;
    private boolean    catalystResolved = false;

    public ChemicalReactorRecipe(String id,
                                 int requiredTemperature,
                                 int productionTime,
                                 String input1Fluid, int input1Amount,
                                 String input2Fluid, int input2Amount,
                                 String outputFluid,  int outputAmount,
                                 String catalystItem, int catalystDamage) {
        this.id                   = id;
        this.requiredTemperature  = requiredTemperature;
        this.productionTime       = productionTime;
        this.input1FluidId        = parseId(input1Fluid,  "omnitech");
        this.input1Amount         = input1Amount;
        this.input2FluidId        = parseId(input2Fluid,  "omnitech");
        this.input2Amount         = input2Amount;
        this.outputFluidId        = parseId(outputFluid,  "omnitech");
        this.outputAmount         = outputAmount;
        this.catalystItemId       = (catalystItem == null || catalystItem.isBlank()
                                        || catalystItem.equalsIgnoreCase("none"))
                                    ? null : parseId(catalystItem, "omnitech");
        this.catalystDamage       = catalystDamage;
    }

    private static Identifier parseId(String raw, String defaultNs) {
        return raw.contains(":") ? Identifier.parse(raw)
                                 : Identifier.fromNamespaceAndPath(defaultNs, raw);
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public String getId()                  { return id; }
    public int    getRequiredTemperature() { return requiredTemperature; }
    public int    getProductionTime()      { return productionTime; }
    public int    getInput1Amount()        { return input1Amount; }
    public int    getInput2Amount()        { return input2Amount; }
    public int    getCatalystDamage()      { return catalystDamage; }

    public FluidStack getInput1Fluid() {
        if (cachedInput1 == null) {
            Fluid f = BuiltInRegistries.FLUID.getValue(input1FluidId);
            cachedInput1 = f != null ? new FluidStack(f, input1Amount) : FluidStack.EMPTY;
        }
        return cachedInput1.copy();
    }

    public FluidStack getInput2Fluid() {
        if (cachedInput2 == null) {
            Fluid f = BuiltInRegistries.FLUID.getValue(input2FluidId);
            cachedInput2 = f != null ? new FluidStack(f, input2Amount) : FluidStack.EMPTY;
        }
        return cachedInput2.copy();
    }

    public FluidStack getOutput() {
        if (cachedOutput == null) {
            Fluid f = BuiltInRegistries.FLUID.getValue(outputFluidId);
            cachedOutput = f != null ? new FluidStack(f, outputAmount) : FluidStack.EMPTY;
        }
        return cachedOutput.copy();
    }

    /** Returns the required catalyst item, or {@code null} if no catalyst is needed. */
    public Item getCatalystItem() {
        if (!catalystResolved) {
            catalystResolved = true;
            cachedCatalyst = (catalystItemId != null)
                    ? BuiltInRegistries.ITEM.getValue(catalystItemId)
                    : null;
        }
        return cachedCatalyst;
    }

    public boolean requiresCatalyst() {
        return catalystItemId != null;
    }

    // ── Matching ──────────────────────────────────────────────────────────────

    /**
     * Returns {@code true} when the given tanks and catalyst slot satisfy this recipe.
     *
     * <p>The two input fluids are checked in both orderings, so the caller does not
     * need to know which tank holds which fluid.
     *
     * @param tank1    contents of input tank 1
     * @param tank2    contents of input tank 2
     * @param catalyst item in the catalyst slot (may be empty)
     * @param heat     current stored heat in the machine (°C)
     */
    public boolean matches(FluidStack tank1, FluidStack tank2,
                           ItemStack catalyst, int heat) {
        // Temperature check
        if (heat < requiredTemperature) return false;

        // Catalyst check
        if (requiresCatalyst()) {
            Item needed = getCatalystItem();
            if (needed == null) return false;
            if (catalyst.isEmpty() || !catalyst.is(needed)) return false;
            if (catalystDamage > 0 && catalyst.isDamageableItem()
                    && catalyst.getDamageValue() >= catalyst.getMaxDamage()) return false;
        }

        // Fluid check (order-independent)
        FluidStack f1 = getInput1Fluid();
        FluidStack f2 = getInput2Fluid();

        boolean directMatch =
                fluidMatches(tank1, f1.getFluid(), input1Amount) &&
                fluidMatches(tank2, f2.getFluid(), input2Amount);

        boolean swappedMatch =
                fluidMatches(tank1, f2.getFluid(), input2Amount) &&
                fluidMatches(tank2, f1.getFluid(), input1Amount);

        return directMatch || swappedMatch;
    }

    private static boolean fluidMatches(FluidStack tank, Fluid fluid, int minAmount) {
        if (tank.isEmpty()) return false;
        if (!tank.is(fluid)) return false;
        return tank.getAmount() >= minAmount;
    }
}
