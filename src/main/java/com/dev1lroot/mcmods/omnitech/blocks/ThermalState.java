package com.dev1lroot.mcmods.omnitech.blocks;

import net.minecraft.util.StringRepresentable;

/**
 * Blockstate enum that reflects a machine's temperature range.
 *
 * <ul>
 *   <li>{@link #COOL} — temperature &le; 0 (at or below freezing)</li>
 *   <li>{@link #IDLE} — temperature 1–50 (near-ambient, not yet useful)</li>
 *   <li>{@link #HOT}  — temperature &gt; 50 (hot enough to matter)</li>
 * </ul>
 *
 * <p>Use {@link #of(int)} to derive the correct value from a raw temperature.
 * Register the property with {@link net.minecraft.world.level.block.state.properties.EnumProperty#create}.
 */
public enum ThermalState implements StringRepresentable {

    COOL("cool"),
    IDLE("idle"),
    HOT("hot");

    private final String name;

    ThermalState(String name) { this.name = name; }

    @Override
    public String getSerializedName() { return name; }

    /** Derive the correct {@link ThermalState} from a raw temperature value. */
    public static ThermalState of(int temperature) {
        if (temperature <= 0)  return COOL;
        if (temperature <= 50) return IDLE;
        return HOT;
    }
}
