package com.dev1lroot.mcmods.omnitech.blocks;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.EnumProperty;

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
