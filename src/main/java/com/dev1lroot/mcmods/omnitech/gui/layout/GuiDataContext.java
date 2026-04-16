package com.dev1lroot.mcmods.omnitech.gui.layout;

import net.neoforged.neoforge.fluids.FluidStack;

import java.util.HashMap;
import java.util.Map;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Binds named data sources to runtime suppliers so the renderer can query live
 * values without knowing the concrete menu type.
 *
 * <p>Build one per screen in {@code init()} and pass it to
 * {@link GuiLayoutRenderer}:
 * <pre>{@code
 * dataCtx = new GuiDataContext()
 *     .fluid("input", menu::getInputFluid,
 *            menu::getInputFluidAmount, menu::getInputFluidCapacity)
 *     .value("cook_progress", menu::getCookProgressScaled)
 *     .value("energy_stored", menu::getEnergyStored)
 *     .value("energy_max",    menu::getMaxEu);
 * }</pre>
 */
public class GuiDataContext {

    // ── Internal storage ─────────────────────────────────────────────────────

    private record FluidEntry(
            Supplier<FluidStack> stack,
            IntSupplier amount,
            IntSupplier capacity) {}

    private final Map<String, FluidEntry>      fluids = new HashMap<>();
    private final Map<String, Supplier<Float>> floats = new HashMap<>();

    // ── Builder methods ───────────────────────────────────────────────────────

    /**
     * Registers a named fluid source.
     *
     * @param id       key used in JSON {@code "source"} field
     * @param stack    supplier for the current {@link FluidStack}
     * @param amount   supplier for the current fill amount (mB)
     * @param capacity supplier for the tank capacity (mB)
     */
    public GuiDataContext fluid(String id,
            Supplier<FluidStack> stack,
            IntSupplier amount,
            IntSupplier capacity) {
        fluids.put(id, new FluidEntry(stack, amount, capacity));
        return this;
    }

    /**
     * Registers a named float value (e.g. progress 0-100, energy stored).
     *
     * @param id    key used in JSON {@code "source"} or fixed data-key field
     * @param value supplier returning the current float value
     */
    public GuiDataContext value(String id, Supplier<Float> value) {
        floats.put(id, value);
        return this;
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public FluidStack getFluid(String id) {
        FluidEntry e = fluids.get(id);
        return e != null ? e.stack().get() : FluidStack.EMPTY;
    }

    public int getFluidAmount(String id) {
        FluidEntry e = fluids.get(id);
        return e != null ? e.amount().getAsInt() : 0;
    }

    public int getFluidCapacity(String id) {
        FluidEntry e = fluids.get(id);
        return e != null ? e.capacity().getAsInt() : 0;
    }

    public float getFloat(String id) {
        Supplier<Float> s = floats.get(id);
        return s != null ? s.get() : 0f;
    }
}
