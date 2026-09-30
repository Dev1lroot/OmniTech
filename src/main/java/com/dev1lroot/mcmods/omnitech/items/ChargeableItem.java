/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.items;

import net.minecraft.world.item.ItemStack;

/**
 * An item that holds electric energy (kJ) and can be topped up by the Electric Charger —
 * a {@link BatteryItem} directly, or a {@link BoreItem} through the battery plugged into it.
 */
public interface ChargeableItem {

    /** Energy currently stored, kJ. */
    int getEnergy(ItemStack stack);

    /** Maximum energy, kJ (0 = cannot hold charge right now, e.g. a bore without battery). */
    int getCapacity(ItemStack stack);

    /** Sets the stored energy, clamped to {@code [0, capacity]}. */
    void setEnergy(ItemStack stack, int kj);
}
