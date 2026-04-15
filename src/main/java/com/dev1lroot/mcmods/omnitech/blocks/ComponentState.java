package com.dev1lroot.mcmods.omnitech.blocks;

import net.minecraft.util.StringRepresentable;

/**
 * Describes where a Fractional Distiller segment sits within its vertical multiblock column.
 * Used as a BlockState property so the correct model (cap, middle tube, connector) can be
 * selected automatically when segments are placed or removed.
 */
public enum ComponentState implements StringRepresentable {
    /** Single block — no distiller above or below.  Has both a top cap and a bottom base. */
    SINGLE("single"),
    /** Bottom of a column — no distiller below, one above.  Has a bottom base and a top connector. */
    BOTTOM("bottom"),
    /** Middle of a column — distiller both above and below.  Both faces are connectors. */
    MIDDLE("middle"),
    /** Top of a column — distiller below, none above.  Has a bottom connector and a top cap. */
    TOP("top");

    private final String name;

    ComponentState(String name) { this.name = name; }

    @Override
    public String getSerializedName() { return name; }
}
