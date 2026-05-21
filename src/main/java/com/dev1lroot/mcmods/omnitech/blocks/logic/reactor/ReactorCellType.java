/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.blocks.logic.reactor;

import net.minecraft.util.StringRepresentable;

public enum ReactorCellType implements StringRepresentable {
    EMPTY("empty"), FUEL("fuel"), CONTROL("control"), OTHER("other"), DEPLETED("depleted");

    private final String name;

    ReactorCellType(String name) { this.name = name; }

    @Override
    public String getSerializedName() { return name; }
}
