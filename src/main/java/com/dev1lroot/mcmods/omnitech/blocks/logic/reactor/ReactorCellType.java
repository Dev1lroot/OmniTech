package com.dev1lroot.mcmods.omnitech.blocks.logic.reactor;

import net.minecraft.util.StringRepresentable;

public enum ReactorCellType implements StringRepresentable {
    EMPTY("empty"), FUEL("fuel"), CONTROL("control"), OTHER("other");

    private final String name;

    ReactorCellType(String name) { this.name = name; }

    @Override
    public String getSerializedName() { return name; }
}
