/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.blocks.logic.display;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class DisplayBlockMk3 extends DisplayBlock {

    public static final MapCodec<DisplayBlockMk3> CODEC = simpleCodec(DisplayBlockMk3::new);

    public DisplayBlockMk3(Properties props) { super(props); }

    @Override
    protected MapCodec<? extends DisplayBlock> codec() { return CODEC; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DisplayBlockEntityMk3(pos, state);
    }
}
