/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech;

import net.minecraft.world.item.CreativeModeTab;

/**
 * @deprecated Replaced by {@link BlockLoader} + {@link OreSpawnLoader}.
 */
@Deprecated(forRemoval = true)
public final class MineralSet {
    private MineralSet() {}

    /** @deprecated Use {@link BlockLoader#addAllToTab}. */
    @Deprecated(forRemoval = true)
    public static void addAllToTab(CreativeModeTab.Output output) {
        BlockLoader.addAllToTab(output);
    }
}
