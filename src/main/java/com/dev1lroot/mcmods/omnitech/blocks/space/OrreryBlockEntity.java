/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.blocks.space;

import com.dev1lroot.mcmods.omnitech.OmniTechBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class OrreryBlockEntity extends BlockEntity {

    public OrreryBlockEntity(BlockPos pos, BlockState state) {
        super(OmniTechBlockEntities.ORRERY.get(), pos, state);
    }
}
