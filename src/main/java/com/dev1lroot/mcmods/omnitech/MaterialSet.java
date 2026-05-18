/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech;

import net.minecraft.world.item.CreativeModeTab;

/**
 * @deprecated Replaced by {@link MineralSet}. This shim forwards the static
 *             methods still referenced by legacy code during the transition.
 */
@Deprecated(forRemoval = true)
public final class MaterialSet {
    private MaterialSet() {}

    /** @deprecated Use {@link MineralSet#addAllToTab}. */
    @Deprecated(forRemoval = true)
    public static void addAllToTab(CreativeModeTab.Output output) {
        MineralSet.addAllToTab(output);
    }
}
