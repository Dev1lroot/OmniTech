package com.dev1lroot.mcmods.omnitech.blocks.logic.reactor;

import net.minecraft.util.StringRepresentable;

public enum ReactorCellState implements StringRepresentable {
    COOL("cool"), HEAT("heat"), MELTDOWN("meltdown");

    private final String name;

    ReactorCellState(String name) { this.name = name; }

    @Override
    public String getSerializedName() { return name; }
}
