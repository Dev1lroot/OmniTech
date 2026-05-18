/*
 * Copyright (c) 2026 David Eichendorf <admin@dev1lroot.com>
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.dev1lroot.mcmods.omnitech.io;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

public interface IFluidContainer
{
    default float getCapacity()
    {
        return 0.0F;
    }

    default String getFluidType()
    {
        return "water"; // TODO: регистрация жидкостей
    }

    default boolean isConnectable(BlockState state, Direction face)
    {
        return true;
    }

    default boolean isInput(BlockState state, Direction face)
    {
        return true;
    }

    default boolean isOutput(BlockState state, Direction face)
    {
        return true;
    }
}
