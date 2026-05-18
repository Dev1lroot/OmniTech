/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.blocks;

import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * Base class for all OmniTech ore blocks. Drops 0–2 XP on mining.
 * Spawn configuration lives in {@code data/omnitech/worldgen/ore/<id>.json}
 * and is loaded at startup by {@link com.dev1lroot.mcmods.omnitech.OreSpawnLoader}.
 */
public class OmniTechOreBlock extends DropExperienceBlock {

    public OmniTechOreBlock(BlockBehaviour.Properties props) {
        super(UniformInt.of(0, 2), props);
    }
}
